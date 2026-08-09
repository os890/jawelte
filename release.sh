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
# Release driver for the jawelte project.
#
# Cuts a release with the maven-release-plugin and publishes the artifacts to
# https://github.com/os890/os890-maven-repo — a plain Maven repository served
# over GitHub Pages.
#
# Nothing has to exist on the machine beforehand. The target repository is
# cloned into a temporary directory for the duration of the release, `deploy`
# writes the Maven layout into that clone, and the clone is committed and
# pushed. A fresh checkout of jawelte and push rights on the target repository
# are the whole prerequisite — no shared directory, no settings.xml, no
# credentials beyond the ones git already uses.
#
# The clone deliberately does NOT live in target/: release:prepare runs
# `clean verify`, and `clean` would delete it — leaving the deploy to write
# into a directory that is no longer a git checkout and cannot be pushed.
#
# Deploying into a clone of the *current* remote state rather than into an
# empty staging directory is what keeps maven-metadata.xml correct: the deploy
# plugin merges the new version into the existing version list instead of
# replacing it with a list of one.
#
#   bash release.sh                     release the current version, ask for
#                                       the numbers interactively
#   bash release.sh 0.1.0 0.2.0-SNAPSHOT
#                                       release 0.1.0, continue development on
#                                       0.2.0-SNAPSHOT, no prompts
#   bash release.sh --dry-run 0.1.0 0.2.0-SNAPSHOT
#                                       rehearse: no tag, no commit, no push
#   bash release.sh --publish-only v0.1.0
#                                       build an existing tag and publish it,
#                                       without tagging anything. For a run
#                                       whose prepare succeeded and whose
#                                       publication failed: the tag is already
#                                       pushed, so re-running the whole thing
#                                       would only fail on the existing tag.
#
# Fails fast: any step's non-zero exit aborts the script.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MVN="$REPO_ROOT/mvnw"
MVN_ARGS=(-B -ntp)

# The repository the artifacts are published to. Overridable so a fork can
# rehearse the whole procedure against its own target.
PUBLISH_REPO_URL="${JAWELTE_PUBLISH_REPO_URL:-https://github.com/os890/os890-maven-repo.git}"
PUBLISH_REPO_BRANCH="${JAWELTE_PUBLISH_REPO_BRANCH:-main}"
PUBLISH_CHECKOUT=""

DRY_RUN=false
PUBLISH_ONLY_TAG=""
case "${1:-}" in
    --dry-run)
        DRY_RUN=true
        shift
        ;;
    --publish-only)
        PUBLISH_ONLY_TAG="${2:-}"
        [[ -n "$PUBLISH_ONLY_TAG" ]] || { echo ">>> --publish-only needs a tag, e.g. v0.1.0" >&2; exit 1; }
        shift 2
        ;;
esac

RELEASE_VERSION="${1:-}"
DEVELOPMENT_VERSION="${2:-}"

fail() {
    echo ">>> $*" >&2
    [[ -n "$PUBLISH_CHECKOUT" ]] &&
        echo ">>> the publication clone is left at $PUBLISH_CHECKOUT — nothing was pushed to it" >&2
    exit 1
}

step() {
    printf '\n==================================================================\n  %s\n==================================================================\n' "$1"
}

# --- preconditions ---------------------------------------------------------

step "Checking preconditions"

[[ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]] &&
    fail "the working tree is not clean — release:prepare would commit the pending changes"

CURRENT_BRANCH="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"

if [[ -n "$PUBLISH_ONLY_TAG" ]]; then
    git -C "$REPO_ROOT" rev-parse --verify --quiet "refs/tags/$PUBLISH_ONLY_TAG" >/dev/null ||
        fail "no tag '$PUBLISH_ONLY_TAG' in this checkout"
    echo "  tag:     $PUBLISH_ONLY_TAG (publish only — nothing is tagged or version-bumped)"
else
    if [[ "$CURRENT_BRANCH" != "main" ]]; then
        echo "WARN: releasing from '$CURRENT_BRANCH' rather than main — the tag will not be on main" >&2
    fi

    git -C "$REPO_ROOT" fetch --quiet origin "$CURRENT_BRANCH"
    if [[ -n "$(git -C "$REPO_ROOT" log "origin/$CURRENT_BRANCH..$CURRENT_BRANCH" --oneline)" ]]; then
        fail "'$CURRENT_BRANCH' has commits that are not pushed — push them first, release:prepare pushes its own two commits on top"
    fi

    echo "  branch:  $CURRENT_BRANCH"
fi

echo "  target:  $PUBLISH_REPO_URL ($PUBLISH_REPO_BRANCH)"
echo "  dry-run: $DRY_RUN"

# --- the publication target ------------------------------------------------
#
# Outside the project: `clean` runs as part of release:prepare and of the
# perform build, and would delete a clone kept under target/.
#
# A full clone, not --depth 1: the push at the end has to fast-forward the
# remote, and a shallow clone cannot be pushed from without --force.

step "Cloning the publication target"

PUBLISH_CHECKOUT="$(mktemp -d "${TMPDIR:-/tmp}/jawelte-publish.XXXXXX")"
git clone --quiet --branch "$PUBLISH_REPO_BRANCH" "$PUBLISH_REPO_URL" "$PUBLISH_CHECKOUT/repo"
PUBLISH_CHECKOUT="$PUBLISH_CHECKOUT/repo"
echo "  cloned into $PUBLISH_CHECKOUT"

# --- prepare ---------------------------------------------------------------

if [[ -n "$PUBLISH_ONLY_TAG" ]]; then
    # release:perform takes the tag to build from release.properties, which
    # release:clean deletes at the end of every run. Writing the two entries it
    # actually reads is what lets the standard goal rebuild an existing tag.
    printf 'scm.url=scm:git:%s\nscm.tag=%s\n' \
        "$(git -C "$REPO_ROOT" remote get-url origin)" "$PUBLISH_ONLY_TAG" \
        > "$REPO_ROOT/release.properties"
    RELEASED_TAG="$PUBLISH_ONLY_TAG"
    RELEASED_VERSION="${RELEASED_TAG#v}"
else

step "release:prepare"

PREPARE_ARGS=()
[[ -n "$RELEASE_VERSION" ]] && PREPARE_ARGS+=("-DreleaseVersion=$RELEASE_VERSION")
[[ -n "$DEVELOPMENT_VERSION" ]] && PREPARE_ARGS+=("-DdevelopmentVersion=$DEVELOPMENT_VERSION")
if [[ -n "$RELEASE_VERSION" && -n "$DEVELOPMENT_VERSION" ]]; then
    PREPARE_ARGS+=(-DautoVersionSubmodules=true)
else
    # no numbers on the command line: let the plugin ask
    MVN_ARGS=(-ntp)
fi

if [[ "$DRY_RUN" == true ]]; then
    "$MVN" "${MVN_ARGS[@]}" release:clean release:prepare -DdryRun=true "${PREPARE_ARGS[@]}"
    step "Dry run finished"
    echo "  No tag, no commit and no push were made. release.properties and the"
    echo "  pom.xml.tag / pom.xml.next files show what a real run would produce;"
    echo "  'bash release.sh' without --dry-run performs it."
    rm -rf "$(dirname "$PUBLISH_CHECKOUT")"
    exit 0
fi

"$MVN" "${MVN_ARGS[@]}" release:clean release:prepare "${PREPARE_ARGS[@]}"

# release:perform runs release:clean at the end, which deletes this file, so
# the version has to be read now.
RELEASED_TAG="$(sed -n 's/^scm\.tag=//p' "$REPO_ROOT/release.properties" | tr -d '\r')"
RELEASED_VERSION="${RELEASED_TAG#v}"
[[ -n "$RELEASED_VERSION" ]] || fail "could not read the released version from release.properties"

fi

# --- perform ---------------------------------------------------------------

step "release:perform — deploying $RELEASED_VERSION into the clone"

"$MVN" "${MVN_ARGS[@]}" release:perform \
    -Darguments="-Dos890.maven.repo.directory=$PUBLISH_CHECKOUT"

# --- publish ---------------------------------------------------------------

step "Publishing $RELEASED_VERSION"

git -C "$PUBLISH_CHECKOUT" rev-parse --git-dir >/dev/null 2>&1 ||
    fail "$PUBLISH_CHECKOUT is not a git checkout any more — something deleted it mid-release"

if [[ -z "$(git -C "$PUBLISH_CHECKOUT" status --porcelain)" ]]; then
    fail "the deploy wrote nothing into $PUBLISH_CHECKOUT — nothing to publish"
fi

git -C "$PUBLISH_CHECKOUT" add -A
git -C "$PUBLISH_CHECKOUT" status --short
git -C "$PUBLISH_CHECKOUT" commit --quiet -m "jawelte $RELEASED_VERSION"
git -C "$PUBLISH_CHECKOUT" push --quiet origin "$PUBLISH_REPO_BRANCH"

PUBLISHED_COMMIT="$(git -C "$PUBLISH_CHECKOUT" rev-parse --short HEAD)"
rm -rf "$(dirname "$PUBLISH_CHECKOUT")"

step "Released $RELEASED_TAG"
echo "  tag pushed to       $(git -C "$REPO_ROOT" remote get-url origin)"
echo "  artifacts pushed to $PUBLISH_REPO_URL ($PUBLISHED_COMMIT)"
echo
echo "  GitHub Pages needs a moment to serve the new files. Afterwards:"
echo "    https://os890.github.io/os890-maven-repo/org/os890/jawelte/"
