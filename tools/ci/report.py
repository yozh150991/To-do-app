#!/usr/bin/env python3
"""Builds the Markdown CI report posted to pull requests.

GitHub Actions logs are the primary record; this report copies the evidence a reviewer
(and the Ф0 gates in spec §11) needs into the PR itself: test counts, failures, compiler
errors, SQLite runtime evidence, artifact sizes and any Room schema that is not committed yet.

Usage: report.py <build-log> <gradle-outcome> <commit-sha>
"""
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

MAX_CHARS = 60000


def read(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return ""


def test_summary():
    rows, failures = {}, []
    for path in sorted(glob.glob("**/build/test-results/**/*.xml", recursive=True)):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        module = path.split("/build/")[0]
        task = path.split("/test-results/")[1].split("/")[0]
        key = f"{module} ({task})"
        row = rows.setdefault(key, [0, 0, 0, 0])
        for suite in suites:
            for case in suite.findall("testcase"):
                row[0] += 1
                failure = case.find("failure")
                if failure is None:
                    failure = case.find("error")
                if failure is not None:
                    row[1] += 1
                    text = (failure.get("message") or "") + "\n" + (failure.text or "")
                    failures.append((f"{case.get('classname')}.{case.get('name')}", text.strip()))
                elif case.find("skipped") is not None:
                    row[2] += 1
                else:
                    row[3] += 1
    return rows, failures


def what_went_wrong(log):
    blocks, current = [], None
    for line in log.splitlines():
        if line.startswith("* What went wrong:"):
            current = []
        elif current is not None and (line.startswith("* Try:") or line.startswith("* Exception is:")):
            blocks.append("\n".join(current).strip())
            current = None
        elif current is not None:
            current.append(line)
    return blocks


def main():
    log_path, outcome, sha = sys.argv[1], sys.argv[2], sys.argv[3]
    log = read(log_path)
    out = ["<!-- ci-report -->", f"### CI report — `{sha[:10]}`", ""]
    icon = "✅" if outcome == "success" else "❌"
    out.append(f"**Gradle:** {icon} `{outcome}`")
    out.append("")

    rows, failures = test_summary()
    if rows:
        out += ["| Test task | Total | Failed | Skipped | Passed |", "| --- | --- | --- | --- | --- |"]
        for key, (total, failed, skipped, passed) in rows.items():
            out.append(f"| `{key}` | {total} | {failed} | {skipped} | {passed} |")
        out.append("")
    else:
        out += ["_No test results were produced._", ""]

    if failures:
        out.append("#### Failed tests")
        for name, text in failures[:25]:
            snippet = "\n".join(text.splitlines()[:25])
            out += [f"<details><summary><code>{name}</code></summary>", "", "```", snippet, "```", "</details>"]
        out.append("")

    compile_errors = [l for l in log.splitlines() if re.match(r"^e: ", l)]
    if compile_errors:
        out += ["#### Compiler errors", "```"] + compile_errors[:80] + ["```", ""]

    blocks = what_went_wrong(log)
    if blocks:
        out.append("#### What went wrong")
        for block in blocks[:8]:
            out += ["```", "\n".join(block.splitlines()[:40]), "```"]
        out.append("")

    evidence = sorted(glob.glob("**/build/reports/runtime-evidence/*.txt", recursive=True))
    if evidence:
        out.append("#### Runtime evidence")
        for path in evidence:
            out += [f"`{path}`", "```", read(path).strip(), "```"]
        out.append("")

    apks = sorted(glob.glob("androidApp/build/outputs/apk/**/*.apk", recursive=True))
    jars = sorted(glob.glob("desktopApp/build/libs/*.jar"))
    if apks or jars:
        out.append("#### Artifacts")
        for path in apks + jars:
            out.append(f"- `{path}` — {os.path.getsize(path) / 1024 / 1024:.2f} MiB")
        out.append("")

    status = subprocess.run(
        ["git", "status", "--porcelain", "--", "core/database/schemas"],
        capture_output=True, text=True, check=False,
    ).stdout.strip()
    if status:
        out.append("#### Room schema not committed")
        out.append("Generated schema differs from the repository; commit it:")
        for line in status.splitlines():
            path = line[3:].strip()
            files = [path] if os.path.isfile(path) else sorted(glob.glob(os.path.join(path, "**/*.json"), recursive=True))
            for f in files:
                out += [f"<details><summary><code>{f}</code></summary>", "", "```json", read(f), "```", "</details>"]
        out.append("")

    if outcome != "success":
        tail = log.splitlines()[-60:]
        out += ["<details><summary>Log tail</summary>", "", "```"] + tail + ["```", "</details>"]

    text = "\n".join(out)
    if len(text) > MAX_CHARS:
        text = text[: MAX_CHARS - 200] + "\n\n_…report truncated; see the workflow artifacts._\n```\n"
    print(text)


if __name__ == "__main__":
    main()
