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
# Full-matrix verification of the jawelte project.
#
#   Phase 1 — full reactor install (-DskipTests) so every test module
#             can resolve its dependencies from the local Maven repo.
#   Phase 2 — verify each test module under each applicable profile
#             combination, sequentially. Sequential is required:
#             parallel mvn invocations clobber each other's target/
#             directories.
#   Phase 3 — aggregate JaCoCo coverage.
#
# Fails fast: any single phase's non-zero exit aborts the script (the
# `set -euo pipefail` envelope plus an explicit FAIL banner from the
# `run` helper).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MVN="$REPO_ROOT/mvnw"
MVN_ARGS=(-B -ntp)

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
run "install full reactor (-DskipTests)" \
    "$REPO_ROOT" -DskipTests install

# --- Phase 2: test matrix --------------------------------------------
# tests/core: no CDI / JTA profile to sweep.
run "tests/core" "$REPO_ROOT/tests/core" verify

# tests/cdi-module, tests/scope-module, tests/jpa-module:
# CDI-runtime sweep only (owb default + weld).
for cdi in owb weld; do
    run "tests/cdi-module [$cdi]"   "$REPO_ROOT/tests/cdi-module"   -P "$cdi" verify
    run "tests/scope-module [$cdi]" "$REPO_ROOT/tests/scope-module" -P "$cdi" verify
    run "tests/jpa-module [$cdi]"   "$REPO_ROOT/tests/jpa-module"   -P "$cdi" verify
done

# tests/jta-module: CDI-runtime × JTA-impl sweep.
# 4 combos: {owb, weld} × {jta-geronimo, jta-narayana}.
for cdi in owb weld; do
    for jta in jta-geronimo jta-narayana; do
        run "tests/jta-module [$cdi,$jta]" \
            "$REPO_ROOT/tests/jta-module" -P "$cdi,$jta" verify
    done
done

# Atomikos coverage is not a separate axis — scenarios 50 + 51
# pin Atomikos's jakarta-classifier deps + an AtomikosTransactionManagerProvider
# META-INF/services override at the scenario level, so they run
# against Atomikos inside every {owb, weld} × {jta-geronimo,
# jta-narayana} phase above. The 32 general-purpose scenarios in
# the same phase remain on the profile-active TM (Geronimo /
# Narayana) and are unaffected.

# --- Phase 3: aggregated coverage ------------------------------------
run "coverage-report" "$REPO_ROOT/coverage-report" verify

# --- Summary ---------------------------------------------------------
total_elapsed=$(( $(date +%s) - start_epoch ))
echo
echo "=================================================================="
printf "  ALL %d PHASES GREEN  —  total %dm %ds\n" \
       "$phase" "$((total_elapsed / 60))" "$((total_elapsed % 60))"
echo "=================================================================="
