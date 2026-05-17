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

run() {
    local label="$1"; shift
    local dir="$1"; shift
    phase=$((phase + 1))
    local phase_start
    phase_start=$(date +%s)
    local banner
    banner=$(printf '\n==================================================================\n  Phase %02d: %s\n  in:   %s\n  args: %s\n==================================================================' "$phase" "$label" "$dir" "$*")
    echo "$banner"
    [ -n "$LNP_LOG" ] && echo "$banner" >> "$LNP_LOG"
    # In LNP mode tee maven's stdout into LNP_LOG so the html report
    # at the end has the full set of [perf] lines + phase banners
    # without depending on the caller's external redirect.
    # `pipefail` is already on, so a non-zero from mvn surfaces through
    # the pipe.
    if [ -n "$LNP_LOG" ]; then
        if ! ( cd "$dir" && "$MVN" "${MVN_ARGS[@]}" "$@" | tee -a "$LNP_LOG" ); then
            echo
            echo ">>> FAILED at phase $phase: $label" >&2
            exit 1
        fi
    else
        if ! ( cd "$dir" && "$MVN" "${MVN_ARGS[@]}" "$@" ); then
            echo
            echo ">>> FAILED at phase $phase: $label" >&2
            exit 1
        fi
    fi
    local phase_elapsed=$(( $(date +%s) - phase_start ))
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
    run "clean install full reactor [skipTests]" \
        "$REPO_ROOT/verify-all" -DskipTests clean install
else
    run "clean install full reactor" \
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
    for cdi in owb weld; do
        run "tests/lnp-module [$cdi,lnp]" \
            "$REPO_ROOT/tests/lnp-module" -P "$cdi,lnp" verify
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
        run "$(basename "$wip_dir") [wip]" "$wip_dir" -P wip verify
    done
else
    # --- full matrix mode --------------------------------------------
    # tests/core: no CDI / JTA profile to sweep.
    run "tests/core" "$REPO_ROOT/tests/core" verify

    # tests/cdi-module, tests/scope-module, tests/jpa-module,
    # tests/ejb-module, tests/testcontrol-module,
    # tests/spring-data-module: CDI-runtime sweep only (owb default
    # + weld).
    for cdi in owb weld; do
        run "tests/cdi-module [$cdi]"         "$REPO_ROOT/tests/cdi-module"         -P "$cdi" verify
        run "tests/scope-module [$cdi]"       "$REPO_ROOT/tests/scope-module"       -P "$cdi" verify
        run "tests/jpa-module [$cdi]"         "$REPO_ROOT/tests/jpa-module"         -P "$cdi" verify
        run "tests/ejb-module [$cdi]"         "$REPO_ROOT/tests/ejb-module"         -P "$cdi" verify
        run "tests/testcontrol-module [$cdi]" "$REPO_ROOT/tests/testcontrol-module" -P "$cdi" verify
        run "tests/spring-data-module [$cdi]" "$REPO_ROOT/tests/spring-data-module" -P "$cdi" verify
    done

    # tests/content-diff-module: utility library — does not bootstrap a
    # CDI container, so the owb/weld profiles are no-ops. One verify
    # pass covers every scenario.
    run "tests/content-diff-module" "$REPO_ROOT/tests/content-diff-module" verify

    # tests/jta-module: CDI-runtime × JTA-impl sweep.
    # 4 combos: {owb, weld} × {jta-geronimo, jta-narayana}.
    for cdi in owb weld; do
        for jta in jta-geronimo jta-narayana; do
            run "tests/jta-module [$cdi,$jta]" \
                "$REPO_ROOT/tests/jta-module" -P "$cdi,$jta" verify
        done
    done

    # Atomikos coverage is not a separate axis — scenarios 50 + 51
    # pin Atomikos's jakarta-classifier deps + an
    # AtomikosTransactionManagerProvider META-INF/services override
    # at the scenario level, so they run against Atomikos inside every
    # {owb, weld} × {jta-geronimo, jta-narayana} phase above. The 32
    # general-purpose scenarios in the same phase remain on the
    # profile-active TM (Geronimo / Narayana) and are unaffected.

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
    run "coverage-report" "$REPO_ROOT/verify-all" \
        -pl :coverage-report -am -DskipTests verify
fi

# --- Summary ---------------------------------------------------------
total_elapsed=$(( $(date +%s) - start_epoch ))
echo
echo "=================================================================="
if [ "$LNP_MODE" = true ]; then
    printf "  LNP PASS GREEN  —  %d phase(s)  —  total %dm %ds\n" \
           "$phase" "$((total_elapsed / 60))" "$((total_elapsed % 60))"
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
