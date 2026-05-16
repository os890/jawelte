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
# Two modes:
#
#   bash verify-all.sh
#     Full matrix — install full reactor, then sweep every test
#     module under every applicable {owb, weld} × {jta-*} profile,
#     then aggregate coverage. Use before finishing a topic.
#
#   bash verify-all.sh wip
#     Iteration mode — install full reactor, then run only those
#     test modules whose pom.xml declares a `<id>wip</id>` profile,
#     activating that profile. Lets the in-flight topic's scenarios
#     run fast without sweeping everything. Skips the coverage
#     aggregation phase.
#
# Both modes fail fast: any single phase's non-zero exit aborts
# the script (the `set -euo pipefail` envelope plus an explicit
# FAIL banner from the `run` helper).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MVN="$REPO_ROOT/mvnw"
MVN_ARGS=(-B -ntp)

WIP_MODE=false
case "${1:-}" in
    "")
        ;;
    wip|--wip)
        WIP_MODE=true
        ;;
    *)
        echo "Usage: $(basename "$0") [wip]" >&2
        exit 2
        ;;
esac

start_epoch=$(date +%s)
phase=0

run() {
    local label="$1"; shift
    local dir="$1"; shift
    phase=$((phase + 1))
    local phase_start
    phase_start=$(date +%s)
    echo
    echo "=================================================================="
    printf "  Phase %02d: %s\n" "$phase" "$label"
    echo "  in:   $dir"
    echo "  args: $*"
    echo "=================================================================="
    if ! ( cd "$dir" && "$MVN" "${MVN_ARGS[@]}" "$@" ); then
        echo
        echo ">>> FAILED at phase $phase: $label" >&2
        exit 1
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
run "clean install full reactor (-DskipTests)" \
    "$REPO_ROOT" -DskipTests clean install

if [ "$WIP_MODE" = true ]; then
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
    # tests/ejb-module, tests/testcontrol-module: CDI-runtime sweep
    # only (owb default + weld).
    for cdi in owb weld; do
        run "tests/cdi-module [$cdi]"         "$REPO_ROOT/tests/cdi-module"         -P "$cdi" verify
        run "tests/scope-module [$cdi]"       "$REPO_ROOT/tests/scope-module"       -P "$cdi" verify
        run "tests/jpa-module [$cdi]"         "$REPO_ROOT/tests/jpa-module"         -P "$cdi" verify
        run "tests/ejb-module [$cdi]"         "$REPO_ROOT/tests/ejb-module"         -P "$cdi" verify
        run "tests/testcontrol-module [$cdi]" "$REPO_ROOT/tests/testcontrol-module" -P "$cdi" verify
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
    # Run from the repo root with `-pl :coverage-report -am`, not from
    # inside coverage-report/. The `jacoco:report-aggregate` goal binds
    # to verify but it discovers per-module `target/jacoco.exec` files
    # by walking the active Maven reactor session — running from
    # coverage-report/ alone gives it a single-module session that
    # contains no exec data, so it silently overwrites the previously
    # populated aggregate with an empty report. `-am` brings every
    # dependency module into the session; with the local repo already
    # warm from Phase 01 install, each transitive module reports as
    # ~0.015s SUCCESS (up-to-date, nothing to recompile) so the
    # overhead is single-digit seconds. `-DskipTests` keeps Surefire
    # from re-running tests we already executed in Phases 02-17.
    run "coverage-report" "$REPO_ROOT" \
        -pl :coverage-report -am -DskipTests verify
fi

# --- Summary ---------------------------------------------------------
total_elapsed=$(( $(date +%s) - start_epoch ))
echo
echo "=================================================================="
if [ "$WIP_MODE" = true ]; then
    printf "  WIP PASS GREEN  —  %d phase(s)  —  total %dm %ds\n" \
           "$phase" "$((total_elapsed / 60))" "$((total_elapsed % 60))"
else
    printf "  ALL %d PHASES GREEN  —  total %dm %ds\n" \
           "$phase" "$((total_elapsed / 60))" "$((total_elapsed % 60))"
fi
echo "=================================================================="
