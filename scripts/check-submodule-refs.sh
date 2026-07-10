#!/usr/bin/env bash
# Verifies every submodule's currently-pinned commit actually exists on that
# submodule's own remote - catches "committed a submodule pointer bump
# without pushing the submodule's own commits" before a tag/release workflow
# hits it as a cryptic `actions/checkout` failure deep inside submodule
# fetching ("fatal: remote error: upload-pack: not our ref ...") - exactly
# what broke the v0.0.28 Alpha Release build this script exists to prevent
# a repeat of. Wired into .github/workflows/submodule-check.yml, which
# triggers on every push (any branch or tag) and PR, deliberately broader
# than ci.yml's main/PR-only trigger, since the pointer can go stale on any
# branch and the actual breakage only surfaces later, at tag time.
#
# Usage: scripts/check-submodule-refs.sh (run from the repo root)

set -euo pipefail

status=0

while IFS= read -r line; do
  # `git submodule status` lines look like " <sha> <path> (<describe>)",
  # optionally prefixed with `-` (not initialized) or `+` (checked-out SHA
  # differs from the index) - strip either prefix, keep the SHA and path.
  sha=$(echo "$line" | sed 's/^[-+]//' | awk '{print $1}')
  path=$(echo "$line" | sed 's/^[-+]//' | awk '{print $2}')
  url=$(git config -f .gitmodules --get "submodule.${path}.url" || true)

  if [ -z "$url" ]; then
    echo "::warning::No .gitmodules URL found for submodule '$path' - skipping"
    continue
  fi

  echo "Checking $path @ $sha against $url ..."
  scratch=$(mktemp -d)
  git init --quiet --bare "$scratch"
  if git -C "$scratch" fetch --quiet --depth=1 "$url" "$sha" >/dev/null 2>&1; then
    echo "  ok - reachable"
  else
    echo "::error::Submodule '$path' is pinned to $sha, which is NOT present on its remote ($url)."
    echo "::error::Did you commit inside the submodule without pushing it? Run: (cd $path && git push origin HEAD), then re-push the superproject."
    status=1
  fi
  rm -rf "$scratch"
done < <(git submodule status)

exit $status
