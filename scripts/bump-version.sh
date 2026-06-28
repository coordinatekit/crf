#!/usr/bin/env bash
#
# Bumps the project version across all tracked files.
#
# Two distinct versions are updated:
#   * The build version (the in-development "-SNAPSHOT" coordinate), read from
#     Gradle.
#   * The previously released version referenced in documentation install
#     snippets, read from the latest "v*" git tag. These point at the last
#     published artifact, so they lag the build's SNAPSHOT version and have to
#     be bumped against a different source string.
#
# Usage:
#   scripts/bump-version.sh <new-version> [<current-version>] [<previous-version>]
#
# If <current-version> is omitted, it is read from Gradle (the build version).
# If <previous-version> is omitted, it is read from the latest "v*" git tag.

set -euo pipefail

# Ensure sed handles all byte sequences (binary files in git ls-files)
export LC_ALL=C

NEW_VERSION="${1:?Usage: bump-version.sh <new-version> [<current-version>] [<previous-version>]}"
CURRENT_VERSION="${2:-}"
PREVIOUS_VERSION="${3:-}"

if [ -z "$CURRENT_VERSION" ]; then
  CURRENT_VERSION=$(./gradlew properties -q 2>/dev/null | sed -n 's/^version: //p')
  if [ -z "$CURRENT_VERSION" ]; then
    echo "Error: could not read current version from Gradle" >&2
    exit 1
  fi
  echo "Detected current version: $CURRENT_VERSION"
fi

if [ "$CURRENT_VERSION" = "$NEW_VERSION" ]; then
  echo "Error: current version and new version are the same ($CURRENT_VERSION)" >&2
  exit 1
fi

if [ -z "$PREVIOUS_VERSION" ]; then
  # The most recent release tag reachable from HEAD. Empty on a first release.
  PREVIOUS_VERSION=$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null | sed 's/^v//')
  if [ -n "$PREVIOUS_VERSION" ]; then
    echo "Detected previous released version: $PREVIOUS_VERSION"
  fi
fi

# Escape dots for use in regex
ESCAPED_CURRENT=$(printf '%s' "$CURRENT_VERSION" | sed 's/\./\\./g')

# Verify the current version exists in at least one tracked file
if ! git ls-files -z | xargs -0 grep -l "$CURRENT_VERSION" > /dev/null 2>&1; then
  echo "Error: current version '$CURRENT_VERSION' not found in any tracked file" >&2
  exit 1
fi

# Count matches before replacing
MATCH_COUNT=$(git ls-files -z | xargs -0 grep -c "$CURRENT_VERSION" 2>/dev/null | awk -F: '{s+=$NF} END {print s}')
echo "Found $MATCH_COUNT occurrence(s) of '$CURRENT_VERSION' to evaluate"

# Detect sed in-place flag (macOS requires '' argument, Linux does not)
if sed --version > /dev/null 2>&1; then
  SED_INPLACE=(sed -i -E)
else
  SED_INPLACE=(sed -i '' -E)
fi

# Replace the build version (the in-development "-SNAPSHOT" coordinate).
git ls-files -z | xargs -0 "${SED_INPLACE[@]}" \
  -e "s/version = \"$ESCAPED_CURRENT\"/version = \"$NEW_VERSION\"/g" \
  -e "s/(crf:[A-Za-z0-9._-]+:)$ESCAPED_CURRENT/\1$NEW_VERSION/g" \
  -e "s|<version>$ESCAPED_CURRENT</version>|<version>$NEW_VERSION</version>|g"

# Replace the previously released version, but ONLY in dependency-coordinate
# position. The bare version also appears in CHANGELOG history and in the
# changelog's `fromRevision` anchor, which must stay pinned to the prior release.
if [ -n "$PREVIOUS_VERSION" ] && [ "$PREVIOUS_VERSION" != "$NEW_VERSION" ]; then
  ESCAPED_PREVIOUS=$(printf '%s' "$PREVIOUS_VERSION" | sed 's/\./\\./g')
  git ls-files -z | xargs -0 "${SED_INPLACE[@]}" \
    -e "s/(crf:[A-Za-z0-9._-]+:)$ESCAPED_PREVIOUS/\1$NEW_VERSION/g" \
    -e "s|<version>$ESCAPED_PREVIOUS</version>|<version>$NEW_VERSION</version>|g"
fi

# Report what changed
CHANGED_FILES=$(git diff --name-only)
if [ -z "$CHANGED_FILES" ]; then
  echo "No files were changed. The current version may not match any known patterns."
  exit 1
fi

echo "Updated version: $CURRENT_VERSION -> $NEW_VERSION"
echo "Changed files:"
echo "$CHANGED_FILES" | sed 's/^/  /'

# Guard: fail if a stale version survived in a declaration or dependency
# coordinate (preceded by `version = "`, `:`, or `<version>`). Catches install
# snippets in formats the replacements above did not anticipate, so a missed
# file fails loudly instead of silently shipping stale docs. Scoping to those
# positions leaves alone the version strings that legitimately stay put:
# CHANGELOG history, the `fromRevision` anchor, and this script's own examples.
STALE=""
COORDINATE_PREFIX='(version = "|:|<version>)'
for STALE_VERSION in "$CURRENT_VERSION" "$PREVIOUS_VERSION"; do
  [ -n "$STALE_VERSION" ] || continue
  [ "$STALE_VERSION" = "$NEW_VERSION" ] && continue
  ESCAPED_STALE=$(printf '%s' "$STALE_VERSION" | sed 's/\./\\./g')
  if LEFTOVER=$(git ls-files -z | xargs -0 grep -nE "$COORDINATE_PREFIX$ESCAPED_STALE" 2>/dev/null); then
    STALE+=$'\n'"Stale version '$STALE_VERSION' still present in a coordinate:"$'\n'"$LEFTOVER"
  fi
done

if [ -n "$STALE" ]; then
  echo "Error: stale version references remain after bump:$STALE" >&2
  exit 1
fi
