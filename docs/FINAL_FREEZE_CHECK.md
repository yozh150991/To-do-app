# Final freeze verification — Task Manager v2.6.1

**Стан:** специфікація виправлена і погоджена для старту Ф0; **код, APK, серверні тести й cross-platform PoC не виконані**. Усі пункти нижче — перевірка документа й traceability, не результат runtime-виконання.

| ID | Contract in v2.6.1 | QA/phase evidence required later |
| --- | --- | --- |
| FIX-01 | SQLite/WAL-safe snapshot + integrity/checksum before migration | Ф0, усі DB upgrades: recovery/crash/disk full |
| FIX-02 | Deferred Todoist recurring root **with transitive descendants** | Ф1a/Ф2: source IDs, no orphan parent, atomic graph import |
| FIX-03 | Trusted Edge recurrence evaluator, atomic server revision validation | Ф5b: Kotlin/Deno parity, concurrency, stale plan |
| FIX-04 | ClaimBootstrapReceipt, monotonic local_seq, no legacy replay | Ф5a: 1000 historical operations, crash/retry |
| FIX-05 | Task recurrence_end_reason column uniquely defined | Ф0 database schema review |
| FIX-06 | Android pass mandatory; Web/Desktop pass or blocked with ADR | Ф0 TECH_COMPATIBILITY.md |
| DOC-01 | Spec history separate from application releases | Ф0 document ownership |

**Freeze:** нові продуктові вимоги не додавати до Ф0–Ф6c без окремого погодження. Фактичні результати перевіряються за фази, не виводяться зі специфікації.
