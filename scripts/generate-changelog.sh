#!/usr/bin/env bash
# Regenerates CHANGELOG.md from this repo's own annotated tag history - each
# release tag (v0.0.1, v0.0.2, ...) already carries a real subject + body
# written at tag time (see `git tag -n99 --sort=-v:refname`), so this is a
# single source of truth rather than a second, hand-maintained changelog that
# could drift from what was actually tagged. Run manually before/after
# cutting a release (see CONTRIBUTING.md's "Cutting a release" section) -
# deliberately not auto-committed by CI, the same "finalization is a manual
# step" precedent this project's ADR process already sets for ADR numbering.
#
# Usage: scripts/generate-changelog.sh [output-path]  (defaults to CHANGELOG.md)

set -euo pipefail

output="${1:-CHANGELOG.md}"

{
  echo "# Changelog"
  echo
  echo "Generated from this repo's own annotated git tags (\`scripts/generate-changelog.sh\`) -"
  echo "do not edit by hand. Newest first."
  echo

  for tag in $(git tag --sort=-v:refname); do
    date=$(git log -1 --format=%ad --date=short "$tag")
    subject=$(git for-each-ref --format='%(contents:subject)' "refs/tags/$tag")
    body=$(git for-each-ref --format='%(contents:body)' "refs/tags/$tag")

    echo "## $tag — $date"
    echo
    echo "$subject"
    echo
    if [ -n "$body" ]; then
      echo "$body"
      echo
    fi
  done
} > "$output"

echo "Wrote $output from $(git tag | wc -l) tags."
