#!/usr/bin/env python3
"""Compares the app database before and after an in-place upgrade (tools/ci/upgrade-test.sh).

Pass criteria: the same workspace and device identity, exactly one Inbox, no row lost in any
table that existed before, the current schema version, integrity "ok", and a pre-upgrade
snapshot next to the database whenever the schema version changed (FIX-01).
"""
import os
import re
import sqlite3
import sys

before_dir, after_dir = sys.argv[1], sys.argv[2]
source = open("core/database/src/commonMain/kotlin/io/github/yozh150991/todo/core/database/AppDatabase.kt").read()
expected_version = int(re.search(r"SCHEMA_VERSION = (\d+)", source).group(1))
problems = []


def inspect(directory):
    path = os.path.join(directory, "todo.db")
    if not os.path.exists(path):
        problems.append(f"{directory}: no database file was created")
        return None
    con = sqlite3.connect(path)
    tables = [r[0] for r in con.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' "
        "AND name NOT IN ('room_master_table','android_metadata') ORDER BY name")]
    info = {
        "user_version": con.execute("PRAGMA user_version").fetchone()[0],
        "integrity": con.execute("PRAGMA integrity_check").fetchone()[0],
        "counts": {t: con.execute(f"SELECT COUNT(*) FROM `{t}`").fetchone()[0] for t in tables},
        "workspace": con.execute("SELECT workspace_id, device_id FROM local_workspace").fetchall(),
        "inboxes": con.execute("SELECT COUNT(*) FROM project WHERE is_inbox = 1").fetchone()[0],
        "listing": open(os.path.join(directory, "listing.txt")).read() if os.path.exists(os.path.join(directory, "listing.txt")) else "",
    }
    con.close()
    return info


before = inspect(before_dir)
after = inspect(after_dir)
if before and after:
    print(f"before: schema v{before['user_version']}, rows {before['counts']}")
    print(f"after:  schema v{after['user_version']}, rows {after['counts']}")
    if len(before["workspace"]) != 1:
        problems.append(f"previous version did not create exactly one workspace: {before['workspace']}")
    if before["workspace"] != after["workspace"]:
        problems.append(f"workspace identity changed: {before['workspace']} -> {after['workspace']}")
    if after["inboxes"] != 1:
        problems.append(f"expected one Inbox after upgrade, found {after['inboxes']}")
    if after["user_version"] != expected_version:
        problems.append(f"schema v{after['user_version']} after upgrade, expected v{expected_version}")
    if after["integrity"] != "ok":
        problems.append(f"integrity_check after upgrade: {after['integrity']}")
    for table, n in before["counts"].items():
        if after["counts"].get(table, -1) < n:
            problems.append(f"rows lost in {table}: {n} -> {after['counts'].get(table)}")
    if before["user_version"] != after["user_version"]:
        if ".snapshot" not in after["listing"]:
            problems.append("schema changed but no pre-upgrade snapshot was found")
        else:
            print(f"pre-upgrade snapshot present (v{before['user_version']} -> v{after['user_version']})")
    else:
        print(f"same schema version v{after['user_version']}: no migration needed, data preserved")

if problems:
    print("UPGRADE CHECK FAILED")
    for p in problems:
        print(" - " + p)
    sys.exit(1)
print("UPGRADE CHECK PASSED: install -r kept workspace, Inbox and all rows")
