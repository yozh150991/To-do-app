#!/usr/bin/env python3
"""Builds the Markdown CI report posted to pull requests.

GitHub Actions logs are the primary record; this report copies the evidence a reviewer
(and the Ф0 gates in spec §11) needs into the PR itself: per-job outcome, test counts
(JVM and Android emulators), failures, compiler errors, runtime evidence, artifact sizes
and any Room schema that is not committed yet.

Usage:
  report.py --sha <commit> --job "<name>=<outcome>=<log file>" [--job ...]
"""
import argparse
import glob
import os
import re
import subprocess
import xml.etree.ElementTree as ET

MAX_CHARS = 60000
RESULT_GLOBS = [
    "**/build/test-results/**/*.xml",
    "**/build/outputs/androidTest-results/**/*.xml",
]


def read(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return ""


def suite_key(path):
    module = path.split("/build/")[0]
    if "/androidTest-results/" in path:
        name = os.path.basename(path)
        name = re.sub(r"^TEST-", "", name)
        name = re.sub(r"\.xml$", "", name)
        return f"{module} (device: {name})"
    task = path.split("/test-results/")[1].split("/")[0]
    return f"{module} ({task})"


def test_summary():
    rows, failures, seen = {}, [], set()
    paths = sorted({p for pattern in RESULT_GLOBS for p in glob.glob(pattern, recursive=True)})
    for path in paths:
        if path in seen:
            continue
        seen.add(path)
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        key = suite_key(path)
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
                    failures.append((key, f"{case.get('classname')}.{case.get('name')}", text.strip()))
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


def job_section(name, outcome, log_path):
    out = []
    log = read(log_path)
    ok = outcome.startswith("success")
    out.append(f"| {name} | {'✅' if ok else '❌'} `{outcome}` |")
    details = []
    errors = [l for l in log.splitlines() if re.match(r"^e: ", l)]
    if errors:
        details += [f"**{name} — compiler errors**", "```"] + errors[:60] + ["```"]
    blocks = what_went_wrong(log)
    if blocks:
        details.append(f"**{name} — what went wrong**")
        for block in blocks[:6]:
            details += ["```", "\n".join(block.splitlines()[:40]), "```"]
    if not ok and log:
        tail = log.splitlines()[-50:]
        details += [f"<details><summary>{name} — log tail</summary>", "", "```"] + tail + ["```", "</details>"]
    elif not ok:
        details.append(f"_{name}: no log file (`{log_path}`) — the job may have failed before running Gradle._")
    return out, details


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sha", required=True)
    parser.add_argument("--job", action="append", default=[], help="name=outcome=log")
    args = parser.parse_args()

    out = ["<!-- ci-report -->", f"### CI report — `{args.sha[:10]}`", "", "| Job | Result |", "| --- | --- |"]
    all_details = []
    for spec in args.job:
        name, outcome, log_path = spec.split("=", 2)
        rows, details = job_section(name, outcome, log_path)
        out += rows
        all_details += details
    out.append("")

    rows, failures = test_summary()
    if rows:
        out += ["| Test suite | Total | Failed | Skipped | Passed |", "| --- | --- | --- | --- | --- |"]
        for key, (total, failed, skipped, passed) in rows.items():
            out.append(f"| `{key}` | {total} | {failed} | {skipped} | {passed} |")
        out.append("")
    else:
        out += ["_No test results were produced._", ""]

    if failures:
        out.append("#### Failed tests")
        for key, name, text in failures[:25]:
            snippet = "\n".join(text.splitlines()[:25])
            out += [f"<details><summary><code>{name}</code> — {key}</summary>", "", "```", snippet, "```", "</details>"]
        out.append("")

    if all_details:
        out.append("#### Failures")
        out += all_details
        out.append("")

    evidence = sorted(glob.glob("**/build/reports/runtime-evidence/*.txt", recursive=True))
    if evidence:
        out.append("#### Runtime evidence")
        for path in evidence:
            text = re.sub(r"compile_options: .*", "compile_options: (see artifact)", read(path).strip())
            if len(text) > 6000:
                text = text[:6000] + "\n…(truncated; full file in the workflow artifacts)"
            out += [f"`{path}`", "```", text, "```"]
        out.append("")

    apks = sorted(glob.glob("androidApp/build/outputs/apk/**/*.apk", recursive=True) + glob.glob("dist/*.apk"))
    jars = sorted(glob.glob("desktopApp/build/libs/*.jar"))
    web = sorted(p for p in glob.glob("webApp/build/dist/wasmJs/productionExecutable/*") if os.path.isfile(p))
    if apks or jars or web:
        out.append("#### Artifacts")
        for path in apks + jars:
            out.append(f"- `{path}` — {os.path.getsize(path) / 1024 / 1024:.2f} MiB")
        if web:
            total = sum(os.path.getsize(p) for p in web)
            out.append(f"- Web build `webApp/build/dist/wasmJs/productionExecutable/` — {total / 1024 / 1024:.2f} MiB in {len(web)} files:")
            for path in web:
                out.append(f"  - `{os.path.basename(path)}` — {os.path.getsize(path) / 1024:.0f} KiB")
        out.append("")

    # Kotlin/Wasm writes the lock file it resolved to build/wasm/yarn.lock; with a stale committed lock
    # the build stops at kotlinWasmStoreYarnLock, so show the resolved one to commit.
    for generated, committed in (("build/wasm/yarn.lock", "kotlin-js-store/wasm/yarn.lock"),):
        if os.path.isfile(generated) and read(generated) != read(committed):
            out += [
                "#### Resolved yarn.lock differs from the committed one",
                f"Copy `{generated}` to `{committed}`:",
                "<details><summary><code>" + committed + "</code></summary>", "", "```text", read(generated), "```", "</details>", "",
            ]

    status = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=all", "--", "core/database/schemas", "kotlin-js-store"],
        capture_output=True, text=True, check=False,
    ).stdout.strip()
    if status:
        out.append("#### Generated files not committed")
        out.append("Room schemas or the Kotlin/JS yarn.lock differ from the repository; commit them:")
        for line in status.splitlines():
            path = line[3:].strip()
            files = [path] if os.path.isfile(path) else sorted(
                glob.glob(os.path.join(path, "**/*.json"), recursive=True) + glob.glob(os.path.join(path, "**/*.lock"), recursive=True)
            )
            for f in files:
                fence = "json" if f.endswith(".json") else "text"
                out += [f"<details><summary><code>{f}</code></summary>", "", f"```{fence}", read(f), "```", "</details>"]
        out.append("")

    text = "\n".join(out)
    if len(text) > MAX_CHARS:
        text = text[: MAX_CHARS - 200] + "\n\n_…report truncated; see the workflow artifacts._\n```\n"
    print(text)


if __name__ == "__main__":
    main()
