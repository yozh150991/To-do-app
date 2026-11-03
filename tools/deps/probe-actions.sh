#!/usr/bin/env bash
# Lists the GitHub Actions used in .github/workflows with the version in use, the latest release
# and the Node.js runtime of that release (`runs.using` in its action.yml). GitHub runners
# deprecate Node 20 actions, so majors are bumped by hand after reading this table.
# Needs GH_TOKEN (gh api). Output is Markdown for the dependency probe comment.
set -uo pipefail

echo "| Action | In use | Latest release | Runtime of latest |"
echo "| --- | --- | --- | --- |"
grep -ho 'uses: [A-Za-z0-9_.-]*/[A-Za-z0-9_./-]*@[A-Za-z0-9_.-]*' .github/workflows/*.yml \
  | sed 's/^uses: //' | sort -u \
  | awk -F@ '{ used[$1] = used[$1] (used[$1] ? ", " : "") $2 } END { for (a in used) print a "\t" used[a] }' \
  | sort | while IFS=$'\t' read -r action versions; do
      owner_repo="$(echo "$action" | cut -d/ -f1-2)"
      subdir="$(echo "$action" | cut -d/ -f3-)"
      tag="$(gh api "repos/$owner_repo/releases/latest" --jq '.tag_name' 2>/dev/null)"
      runtime="—"
      if [ -n "$tag" ]; then
        path="${subdir:+$subdir/}action.yml"
        runtime="$(gh api "repos/$owner_repo/contents/$path?ref=$tag" --jq '.content' 2>/dev/null \
          | base64 -d 2>/dev/null | grep -E '^\s*using:' | head -n 1 | sed -E "s/.*using:\s*['\"]?([^'\"]*)['\"]?.*/\1/")"
      fi
      echo "| \`$action\` | $versions | ${tag:-—} | ${runtime:-—} |"
    done
echo
