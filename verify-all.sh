#!/usr/bin/env bash
#
# Copyright 2026 os890
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Verification driver for the jawelte project.
#
# Three modes:
#
#   bash verify-all.sh
#     Full matrix — install full reactor, then sweep every test
#     module under every applicable {owb, weld} × {jta-*} profile,
#     then aggregate coverage. Use before finishing a topic.
#     LNP scenarios are NOT run in this mode (they are skipTests-by-
#     default and require -P lnp to execute).
#
#   bash verify-all.sh wip
#     Iteration mode — install full reactor, then run only those
#     test modules whose pom.xml declares a `<id>wip</id>` profile,
#     activating that profile. Lets the in-flight topic's scenarios
#     run fast without sweeping everything. Skips the coverage
#     aggregation phase. LNP scenarios are NOT run in this mode.
#
#   bash verify-all.sh lnp
#     Load-and-performance mode — install full reactor, then sweep
#     ONLY tests/lnp-module under the {owb, weld} × lnp profile
#     combinations. Surefire produces a per-class timing/heap table
#     in System.out plus a final aggregated summary. Skips the
#     coverage aggregation phase. None of the normal correctness
#     test modules run in this mode.
#
# All three modes fail fast: any single phase's non-zero exit aborts
# the script (the `set -euo pipefail` envelope plus an explicit
# FAIL banner from the `run` helper).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MVN="$REPO_ROOT/mvnw"
# -T 1 forces single-threaded reactor builds even if a future
# .mvn/maven.config or environment override turns on parallel
# builds — verify-all is the canonical correctness gate and must
# stay deterministic. Combined with the absence of any `parallel` /
# `forkCount > 1` / `threadCount` surefire configuration in the
# poms, every phase is fully sequential: one module at a time,
# one test class at a time, one test method at a time.
MVN_ARGS=(-B -ntp -T 1)

WIP_MODE=false
LNP_MODE=false
case "${1:-}" in
    "")
        ;;
    wip|--wip)
        WIP_MODE=true
        ;;
    lnp|--lnp)
        LNP_MODE=true
        ;;
    *)
        echo "Usage: $(basename "$0") [wip|lnp]" >&2
        exit 2
        ;;
esac

start_epoch=$(date +%s)
phase=0

# LNP-mode-only: capture every phase's stdout into a log file we can
# parse for the html overview at the end. Truncated at script start
# so each run starts clean. In non-LNP modes LNP_LOG stays empty and
# run() falls through its default no-tee path.
LNP_REPORT_DIR="$REPO_ROOT/target/lnp-report"
LNP_LOG=""
if [ "$LNP_MODE" = true ]; then
    mkdir -p "$LNP_REPORT_DIR"
    LNP_LOG="$LNP_REPORT_DIR/run.log"
    : > "$LNP_LOG"
fi

# Full-mode-only: every per-profile sweep snapshots its scenarios'
# target/surefire-reports/TEST-*.xml into target/verify-report/data/
# under a per-combo, per-module subdir. An EXIT trap walks that tree
# and emits target/verify-report/index.html — installed even on
# failure paths so the report shows which combo aborted the sweep.
# In wip / LNP modes VERIFY_DATA_ROOT stays empty, snapshot_surefire
# no-ops, and the trap finds nothing to render.
VERIFY_REPORT_DIR="$REPO_ROOT/target/verify-report"
VERIFY_DATA_ROOT=""
if [ "$WIP_MODE" = false ] && [ "$LNP_MODE" = false ]; then
    rm -rf "$VERIFY_REPORT_DIR"
    VERIFY_DATA_ROOT="$VERIFY_REPORT_DIR/data"
    mkdir -p "$VERIFY_DATA_ROOT"
fi

render_verify_report() {
    local exit_code=$?
    if [ -n "$VERIFY_DATA_ROOT" ] && [ -d "$VERIFY_DATA_ROOT" ]; then
        local html_out="$VERIFY_REPORT_DIR/index.html"
        if python3 "$REPO_ROOT/verify-report.py" \
                "$VERIFY_DATA_ROOT" "$html_out" 2>/dev/null; then
            echo
            echo "verify-all overview written to file://$html_out"
        else
            echo "WARN: verify-report.py failed; raw data under $VERIFY_DATA_ROOT" >&2
        fi
    fi
    return "$exit_code"
}
trap render_verify_report EXIT

# Snapshot every target/surefire-reports/TEST-*.xml under $dir into
# $VERIFY_DATA_ROOT/<combo-slug>/<module-slug>/<scenario-slug>/, plus a
# `_meta.txt` capturing phase duration / exit code / label. No-op when
# $VERIFY_DATA_ROOT is empty (wip / LNP mode).
snapshot_surefire() {
    local module_dir=$1
    local combo=$2
    local label=$3
    local duration=$4
    local exit_code=$5
    [ -z "$VERIFY_DATA_ROOT" ] && return 0
    local combo_slug=${combo//,/-}
    [ -z "$combo_slug" ] && combo_slug="default"
    local module_slug
    module_slug=$(basename "$module_dir")
    local dst_root="$VERIFY_DATA_ROOT/$combo_slug/$module_slug"
    mkdir -p "$dst_root"
    {
        echo "label=$label"
        echo "combo=$combo"
        echo "module=$module_slug"
        echo "duration=$duration"
        echo "exit=$exit_code"
    } > "$dst_root/_meta.txt"
    # Each scenario submodule writes its own
    # target/surefire-reports/TEST-*.xml. Aggregator poms have no
    # surefire output of their own — skip them by name match against
    # the module dir itself.
    while IFS= read -r reports_dir; do
        local scenario_dir
        scenario_dir=$(dirname "$(dirname "$reports_dir")")
        local scenario_slug
        scenario_slug=$(basename "$scenario_dir")
        if [ "$scenario_slug" = "$module_slug" ]; then
            continue
        fi
        local dst="$dst_root/$scenario_slug"
        mkdir -p "$dst"
        find "$reports_dir" -maxdepth 1 -name 'TEST-*.xml' -exec cp {} "$dst/" \; 2>/dev/null || true
    done < <(find "$module_dir" -path '*/target/surefire-reports' -type d 2>/dev/null)
}

run() {
    local label="$1"; shift
    # Profile-combination tag used for the report grouping (e.g.
    # "owb", "owb,jta-narayana", or empty for no-profile phases like
    # tests/core / tests/content-diff-module / Phase 1 install).
    local combo="$1"; shift
    local dir="$1"; shift
    phase=$((phase + 1))
    local phase_start
    phase_start=$(date +%s)
    # Wipe scenario-level target/surefire-reports before the phase
    # runs so snapshot_surefire only captures TEST-*.xml from THIS
    # mvn invocation. Without this, an `-P quarkus` phase whose
    # surefire <includes> matches only `**/*QuarkusTest.java` would
    # still see leftover `TEST-*Test.xml` reports from the previous
    # `-P owb` or `-P weld` phase and snapshot them into the wrong
    # combo bucket. Aggregator/parent target/ is left alone — its
    # surefire-reports is always empty in pom-packaging projects.
    if [ -n "$VERIFY_DATA_ROOT" ]; then
        find "$dir" -path '*/target/surefire-reports' -type d \
            -exec rm -rf {} + 2>/dev/null || true
    fi
    local banner
    banner=$(printf '\n==================================================================\n  Phase %02d: %s\n  in:   %s\n  args: %s\n==================================================================' "$phase" "$label" "$dir" "$*")
    echo "$banner"
    [ -n "$LNP_LOG" ] && echo "$banner" >> "$LNP_LOG"
    # In LNP mode tee maven's stdout into LNP_LOG so the html report
    # at the end has the full set of [perf] lines + phase banners
    # without depending on the caller's external redirect.
    # `pipefail` is already on, so a non-zero from mvn surfaces through
    # the pipe.
    local exit_code=0
    if [ -n "$LNP_LOG" ]; then
        ( cd "$dir" && "$MVN" "${MVN_ARGS[@]}" "$@" | tee -a "$LNP_LOG" ) || exit_code=$?
    else
        ( cd "$dir" && "$MVN" "${MVN_ARGS[@]}" "$@" ) || exit_code=$?
    fi
    local phase_elapsed=$(( $(date +%s) - phase_start ))
    # Snapshot regardless of exit code so the report includes the
    # failing combo's partial data — surefire writes TEST-*.xml for
    # every test class before the build aborts.
    snapshot_surefire "$dir" "$combo" "$label" "$phase_elapsed" "$exit_code"
    if [ "$exit_code" -ne 0 ]; then
        echo
        echo ">>> FAILED at phase $phase: $label" >&2
        exit "$exit_code"
    fi
    printf "  ok (%ds)\n" "$phase_elapsed"
}

# --- Phase 1 ---------------------------------------------------------
# Always `clean install` (not just `install`) — without `clean`,
# Maven's incremental build leaves stale target/ artefacts behind
# when a source file is deleted (most painfully: stale
# META-INF/services entries that ServiceLoader.load then fails on
# at test time even after the source file is gone). One clean pass
# at the start avoids the whole class of "ghost file in target/"
# false failures.
#
# Driven through verify-all/pom.xml — the dedicated aggregator
# that lists core + modules + tests + coverage-report. Running
# from there ensures the test scenarios and the JaCoCo aggregate
# report get built; a normal `mvn clean install` from the repo
# root only builds core + modules (the framework code), so the
# regular developer build stays fast.
#
# Default / wip modes: no `-DskipTests` — no `-P` is active in this
# phase, so the test scenarios skip their runtime-gated surefire
# runs by themselves; the explicit CDI / JTA per-profile sweeps in
# the later phases are where the tests actually execute. Other
# scenarios (non-profile-gated ones) DO run during Phase 1, which
# is the intended cross-cutting smoke pass for default + wip modes.
#
# LNP mode: `-DskipTests` — `lnp` is supposed to exercise ONLY the
# lnp-module scenarios, so running every other module's surefire
# during Phase 1 wastes minutes and conflates LNP timing with
# unrelated test-suite work. Skipping tests here still installs
# every artifact the LNP sweeps need; the LNP scenarios run in
# Phase 2+ with the explicit -P lnp profile.
if [ "$LNP_MODE" = true ]; then
    run "clean install full reactor [skipTests]" "" \
        "$REPO_ROOT/verify-all" -DskipTests clean install
else
    run "clean install full reactor" "" \
        "$REPO_ROOT/verify-all" clean install
fi

if [ "$LNP_MODE" = true ]; then
    # --- lnp mode ----------------------------------------------------
    # Load-and-performance sweep. Surefire is skipTests=true in the
    # lnp-module aggregator's base build config; activating -P lnp
    # flips it back on. Combined with -P owb / -P weld this picks a
    # CDI runtime AND turns on the perf execution. Each lnp scenario
    # produces a per-class table via PerformanceExtension and a final
    # aggregated summary via FinalSummaryTest. Coverage aggregation is
    # skipped on purpose - perf runs are not coverage runs and the
    # huge per-method volume would skew the coverage exec data.
    GATLING_SRC="$REPO_ROOT/tests/lnp-module/scenario-07-full-crud-with-gatling/target/gatling"

    # Snapshot scenario-07's HTML Gatling reports per constellation
    # so the four runtime/provider combinations stay side-by-side
    # under the LNP report dir instead of clobbering each other.
    snapshot_gatling() {
        local constellation=$1
        local dst="$LNP_REPORT_DIR/gatling/$constellation"
        if [ ! -d "$GATLING_SRC" ]; then
            return
        fi
        rm -rf "$dst"
        mkdir -p "$dst"
        cp -R "$GATLING_SRC/." "$dst/"
        # Drop the old snapshot's class folders inside the live tree
        # so the NEXT constellation's run sees a clean slate instead
        # of accumulating sims from prior phases.
        rm -rf "$GATLING_SRC"
    }

    for cdi in owb weld; do
        run "tests/lnp-module [$cdi,lnp]" "" \
            "$REPO_ROOT/tests/lnp-module" -P "$cdi,lnp" verify
        snapshot_gatling "$cdi-cxf"
    done

    # JAX-RS scenarios under RESTEasy. The cxf profile is
    # activeByDefault on scenarios 05 and 06, so the sweep above only
    # exercises CXF. Repeat just those two scenarios with `-Presteasy`
    # (and `-P-cxf` to deactivate the default) so the LNP report can
    # compare CXF vs RESTEasy overhead per CDI runtime. Scenarios
    # 01-04 carry no JAX-RS code, so re-running them under resteasy
    # would be wasted wall time — keep the matrix narrow.
    for cdi in owb weld; do
        for scen in scenario-05-full-crud-rest-with-dbunit \
                    scenario-06-full-crud-roundtrip \
                    scenario-07-full-crud-with-gatling; do
            run "tests/lnp-module/$scen [$cdi,resteasy,lnp]" "" \
                "$REPO_ROOT/tests/lnp-module/$scen" \
                -P "$cdi,lnp,-cxf,resteasy" verify
        done
        snapshot_gatling "$cdi-resteasy"
    done
elif [ "$WIP_MODE" = true ]; then
    # --- wip mode ----------------------------------------------------
    # Find every tests/<module>/pom.xml that declares a wip profile.
    # Each ticket-in-flight adds the profile to the relevant test
    # aggregator; when the ticket ships, the profile is removed.
    wip_dirs=()
    while IFS= read -r pom_path; do
        wip_dirs+=("$(dirname "$pom_path")")
    done < <(grep -l "<id>wip</id>" "$REPO_ROOT"/tests/*/pom.xml 2>/dev/null || true)

    if [ ${#wip_dirs[@]} -eq 0 ]; then
        echo
        echo "=================================================================="
        echo "  No tests/*/pom.xml declares a <id>wip</id> profile."
        echo "  Add a wip profile to the test aggregator of the ticket"
        echo "  currently in flight, listing the scenarios you want this"
        echo "  command to run."
        echo "=================================================================="
        exit 0
    fi

    for wip_dir in "${wip_dirs[@]}"; do
        run "$(basename "$wip_dir") [wip]" "" "$wip_dir" -P wip verify
    done
else
    # --- full matrix mode --------------------------------------------
    # tests/core: no CDI / JTA profile to sweep.
    run "tests/core" "" "$REPO_ROOT/tests/core" verify

    # tests/cdi-module, tests/scope-module, tests/jpa-module,
    # tests/ejb-module, tests/testcontrol-module,
    # tests/spring-data-module, tests/wiremock-module,
    # tests/db-testdata-module: {owb, weld, quarkus} CDI-runtime sweep.
    # `quarkus` activates the per-module -Pquarkus profile that pulls
    # impl-arc + deployment + quarkus-junit5 and restricts surefire
    # to **/*QuarkusTest.java — scenarios with no companion give a
    # zero-test cell, which is the intended matrix-shape signal.
    for cdi in owb weld quarkus; do
        run "tests/cdi-module [$cdi]"         "$cdi" "$REPO_ROOT/tests/cdi-module"         -P "$cdi" verify
        run "tests/scope-module [$cdi]"       "$cdi" "$REPO_ROOT/tests/scope-module"       -P "$cdi" verify
        run "tests/jpa-module [$cdi]"         "$cdi" "$REPO_ROOT/tests/jpa-module"         -P "$cdi" verify
        run "tests/ejb-module [$cdi]"         "$cdi" "$REPO_ROOT/tests/ejb-module"         -P "$cdi" verify
        run "tests/testcontrol-module [$cdi]" "$cdi" "$REPO_ROOT/tests/testcontrol-module" -P "$cdi" verify
        run "tests/spring-data-module [$cdi]" "$cdi" "$REPO_ROOT/tests/spring-data-module" -P "$cdi" verify
        run "tests/wiremock-module [$cdi]"    "$cdi" "$REPO_ROOT/tests/wiremock-module"    -P "$cdi" verify
        run "tests/db-testdata-module [$cdi]" "$cdi" "$REPO_ROOT/tests/db-testdata-module" -P "$cdi" verify
    done

    # tests/batch-module: {owb, weld} only. Quarkus has no first-party
    # JSR-352 extension and the batchee/jberet split is already
    # handled at the scenario level (each scenario's pom hardcodes
    # one runtime), so the cdi-axis sweep alone covers both
    # impls naturally.
    for cdi in owb weld; do
        run "tests/batch-module [$cdi]" "$cdi" "$REPO_ROOT/tests/batch-module" -P "$cdi" verify
    done

    # tests/content-diff-module: utility library — does not bootstrap a
    # CDI container, so the owb/weld profiles are no-ops. One verify
    # pass covers every scenario.
    run "tests/content-diff-module" "" "$REPO_ROOT/tests/content-diff-module" verify

    # tests/jta-module: {owb, weld} × {jta-geronimo, jta-narayana}
    # plus a single `quarkus` cell (Narayana implicit via
    # quarkus-narayana-jta).
    for cdi in owb weld; do
        for jta in jta-geronimo jta-narayana; do
            run "tests/jta-module [$cdi,$jta]" "$cdi,$jta" \
                "$REPO_ROOT/tests/jta-module" -P "$cdi,$jta" verify
        done
    done
    run "tests/jta-module [quarkus]" "quarkus" \
        "$REPO_ROOT/tests/jta-module" -P "quarkus" verify

    # Atomikos coverage is not a separate axis — scenarios 50 + 51
    # pin Atomikos's jakarta-classifier deps + an
    # AtomikosTransactionManagerProvider META-INF/services override
    # at the scenario level, so they run against Atomikos inside every
    # {owb, weld} × {jta-geronimo, jta-narayana} phase above. The 32
    # general-purpose scenarios in the same phase remain on the
    # profile-active TM (Geronimo / Narayana) and are unaffected.

    # tests/jaxrs-module: {owb, weld} × {cxf, resteasy} plus a single
    # `quarkus` cell (Quarkus REST implicit, no cxf/resteasy axis
    # under quarkus). The cxf profile is activeByDefault, so the
    # explicit -Pcxf phases below also pass -P-cxf-disable not
    # needed; the cdi+impl combo carries cxf or resteasy explicitly.
    for cdi in owb weld; do
        for jaxrs in cxf resteasy; do
            run "tests/jaxrs-module [$cdi,$jaxrs]" "$cdi,$jaxrs" \
                "$REPO_ROOT/tests/jaxrs-module" -P "$cdi,$jaxrs" verify
        done
    done
    run "tests/jaxrs-module [quarkus]" "quarkus" \
        "$REPO_ROOT/tests/jaxrs-module" -P "quarkus" verify

    # --- Coverage aggregation ----------------------------------------
    # Run from the verify-all aggregator (where coverage-report is a
    # listed module) with `-pl :coverage-report -am`, not from inside
    # coverage-report/ and not from the repo root. The repo-root pom
    # only lists core + modules, so `-pl :coverage-report` from there
    # fails immediately with "Could not find the selected project in
    # the reactor: :coverage-report"; verify-all/pom.xml lists
    # core + modules + tests + coverage-report, which gives `-pl`
    # something to resolve.
    #
    # Why not run inside coverage-report/ directly: the
    # `jacoco:report-aggregate` goal binds to verify but it discovers
    # per-module `target/jacoco.exec` files by walking the active
    # Maven reactor session — running from coverage-report/ alone
    # gives it a single-module session that contains no exec data, so
    # it silently overwrites the previously populated aggregate with
    # an empty report. `-am` brings every dependency module into the
    # session; with the local repo already warm from Phase 01 install,
    # each transitive module reports as ~0.015s SUCCESS (up-to-date,
    # nothing to recompile) so the overhead is single-digit seconds.
    # `-DskipTests` keeps Surefire from re-running tests we already
    # executed in the per-profile phases above.
    run "coverage-report" "" "$REPO_ROOT/verify-all" \
        -pl :coverage-report -am -DskipTests verify
fi

# --- Summary ---------------------------------------------------------
total_elapsed=$(( $(date +%s) - start_epoch ))
echo
echo "=================================================================="
if [ "$LNP_MODE" = true ]; then
    printf "  LNP PASS GREEN  —  %d phase(s)  —  total %dm %ds\n" \
           "$phase" "$((total_elapsed / 60))" "$((total_elapsed % 60))"
    echo   "  lnp-module ONLY  —  correctness modules were NOT verified."
    echo   "  Run 'bash verify-all.sh' (full sweep) before finishing a ticket."
elif [ "$WIP_MODE" = true ]; then
    printf "  WIP PASS GREEN  —  %d phase(s)  —  total %dm %ds\n" \
           "$phase" "$((total_elapsed / 60))" "$((total_elapsed % 60))"
else
    printf "  ALL %d PHASES GREEN  —  total %dm %ds\n" \
           "$phase" "$((total_elapsed / 60))" "$((total_elapsed % 60))"
fi
echo "=================================================================="

# LNP mode: append the final banner to the captured log so the html
# report renders the green/fail banner, then render the html overview.
if [ "$LNP_MODE" = true ] && [ -n "$LNP_LOG" ]; then
    printf '\n  LNP PASS GREEN  —  %d phase(s)  —  total %dm %ds\n' \
        "$phase" "$((total_elapsed / 60))" "$((total_elapsed % 60))" \
        >> "$LNP_LOG"
    LNP_HTML="$LNP_REPORT_DIR/index.html"
    python3 "$REPO_ROOT/lnp-report.py" "$LNP_LOG" "$LNP_HTML" \
        || echo "WARN: lnp-report.py failed; see $LNP_LOG" >&2
    if [ -f "$LNP_HTML" ]; then
        echo
        echo "LNP overview written to file://$LNP_HTML"
    fi
fi

# Full-mode verify-report.py rendering is handled by the EXIT trap
# installed near the top of the script so it runs on both the
# success path AND on phase failures (where `exit` short-circuits
# this tail).
