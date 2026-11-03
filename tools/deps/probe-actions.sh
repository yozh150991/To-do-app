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

# Every newer major of each action: its latest tag and runtime, and the notes of its first release
# (breaking changes are announced there).
grep -ho 'uses: [A-Za-z0-9_.-]*/[A-Za-z0-9_./-]*@v[0-9]*' .github/workflows/*.yml \
  | sed 's/^uses: //' | sort -u \
  | awk -F@ '{ m = substr($2, 2) + 0; if (!($1 in low) || m < low[$1]) low[$1] = m } END { for (a in low) print a "\t" low[a] }' \
  | sort | while IFS=$'\t' read -r action lowest; do
      owner_repo="$(echo "$action" | cut -d/ -f1-2)"
      subdir="$(echo "$action" | cut -d/ -f3-)"
      tags="$(gh api "repos/$owner_repo/releases?per_page=100" --paginate --jq '.[] | select(.prerelease | not) | .tag_name' 2>/dev/null \
        | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V)"
      echo "<details><summary><code>$action</code>: majors after v$lowest</summary>"
      echo
      for major in $(echo "$tags" | sed -E 's/^v([0-9]+)\..*/\1/' | sort -n -u); do
        [ "$major" -gt "$lowest" ] || continue
        first="$(echo "$tags" | grep -E "^v$major\." | head -n 1)"
        last="$(echo "$tags" | grep -E "^v$major\." | tail -n 1)"
        path="${subdir:+$subdir/}action.yml"
        runtime="$(gh api "repos/$owner_repo/contents/$path?ref=$last" --jq '.content' 2>/dev/null \
          | base64 -d 2>/dev/null | grep -E '^\s*using:' | head -n 1 | sed -E "s/.*using:\s*['\"]?([^'\"]*)['\"]?.*/\1/")"
        echo "**v$major** — latest $last ($runtime); first $first:"
        echo
        echo '```'
        gh api "repos/$owner_repo/releases/tags/$first" --jq '.body' 2>/dev/null | tr -d '\r' | grep -v '^\s*$' | head -n 25
        echo '```'
        echo
      done
      echo "</details>"
      echo
    done
