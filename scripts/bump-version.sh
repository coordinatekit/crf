#!/usr/bin/env bash
#
# Bumps the project version across all tracked files.
#
# Usage:
#   scripts/bump-version.sh <new-version> [<current-version>]
#
# If <current-version> is omitted, it is read from build.gradle.

set -euo pipefail

# Ensure sed handles all byte sequences (binary files in git ls-files)
export LC_ALL=C

NEW_VERSION="${1:?Usage: bump-version.sh <new-version> [<current-version>]}"
CURRENT_VERSION="${2:-}"

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

# Perform targeted replacements across all tracked files
git ls-files -z | xargs -0 "${SED_INPLACE[@]}" \
  -e "s/version = \"$ESCAPED_CURRENT\"/version = \"$NEW_VERSION\"/g" \
  -e "s/(crf:[A-Za-z0-9._-]+:)$ESCAPED_CURRENT/\1$NEW_VERSION/g" \
  -e "s|<version>$ESCAPED_CURRENT</version>|<version>$NEW_VERSION</version>|g"

# Report what changed
CHANGED_FILES=$(git diff --name-only)
if [ -z "$CHANGED_FILES" ]; then
  echo "No files were changed. The current version may not match any known patterns."
  exit 1
fi

echo "Updated version: $CURRENT_VERSION -> $NEW_VERSION"
echo "Changed files:"
echo "$CHANGED_FILES" | sed 's/^/  /'
