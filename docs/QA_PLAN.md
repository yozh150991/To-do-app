# QA_PLAN — стратегія тестування й результати

- **Статус:** нормативний текст специфікації v2.6.1 (§12), перенесений з [`task-manager-scope-roadmap-v2.6.1.md`](../task-manager-scope-roadmap-v2.6.1.md) без змін змісту (3 листопада 2026 року, §13.4). Змінюється лише через ADR + [`docs/SPEC_CHANGELOG.md`](SPEC_CHANGELOG.md).
- **Фази:** усі; Definition of Done — §12.4.
- Заголовки перенесених розділів мають знак «§» і номер зі специфікації; посилання «§N» у тексті ведуть на розділи специфікації (де лежить кожен розділ — у таблиці §0.3 індексу).

## §12. QA-стратегія та тестовий каталог

### §12.1. Рівні

- **Unit/commonTest:** domain invariants, Quick Add 3 мовами, recurrence, timezone, filters AST, merging conflicts, UI-independent validation.
- **Property-based / fuzz:** довільні послідовності створення/редагування/видалення/повтору/синхронізації; інваріанти без дублів і convergence.
- **Room DAO/integration:** транзакції, FK/indexes, FTS, migrations, soft-delete restore, rollback after failure; **WAL-safe pre-migration snapshot** (`integrity_check`, checksum, crash, disk full, recovery).
- **Backend integration:** authoritative recurrence Edge Deno evaluator + trusted-only `apply_ops`, cross-runtime Kotlin/Deno fixture parity, concurrent Complete/Skip, stale plan; bootstrap claim snapshot/`ClaimBootstrapReceipt` + outbox absorption; RLS policy tests, RPC authorization, **direct-write rejection + gateway coverage for importer/restore**, operation receipts, seq ordering, epoch/full reconciliation, claim first-login/Inbox merge, concurrent writes, pending rebase.
- **Compose Android UI:** CRUD, drag, Quick Add, Undo, bulk operations, accessibility.
- **Real device:** alarms, Glance, Doze, Samsung-specific restrictions, system locale/timezone.
- **Playwright (Ф7):** browser end-to-end, keyboard workflows, navigation, offline reconnect, responsive layouts.
- **Cross-version:** старий APK + новий сервер, backup зі старішої schema, підтримувані міграції; перевірка нового акаунта/old local workspace та зовнішнього source identity.

**Інструменти (версії фіксуються у Ф0):**

| Призначення | Інструмент |
| --- | --- |
| Property-based тести в `commonTest` | Kotest property |
| Flow і корутини | Turbine |
| RLS, RPC, міграції Postgres | pgTAP через `supabase test db` |
| Backend integration | локальний Supabase (`supabase start`) |
| Обриви й затримки мережі в sync-сценаріях | Toxiproxy між клієнтом і локальним Supabase |
| Android E2E поверх Compose UI tests | Maestro |
| Screenshot-регресія, обрізані uk/pl рядки | Roborazzi |
| Web E2E (Ф7) | Playwright |

### §12.2. Тест-дизайн (обов'язково використовувати 5 технік)

| Техніка | Приклади |
| --- | --- |
| Equivalence Class Partitioning | Task без/з датою, recurring/non-recurring, account online/offline, exact allowed/denied |
| Boundary Value Analysis | Підзадача рівні 0/1/4/5, 31-ше число, 29 лютого, min/max duration, 10k задач |
| State Transition | Active → Completed/Skipped → Next active або `exhausted_completed`/`exhausted_skipped`; imported unknown → blocked Complete → review; claim local → Bound/Retry; reminder one-shot/occurrence/task lifecycle |
| Pairwise | platform × locale × timezone × online status × notification permission |
| Error Guessing | Crash після push до ack, duplicate sync requests, corrupt JSON, expired token, DST overlap |

### §12.3. Обов'язкова матриця регресії

- **Offline:** повний CRUD, reorder, bulk, Restore, повторне відкриття без мережі.
- **Sync:** 2 пристрої без мережі, concurrent field edits, confirmed/pending/effective rebase, Ack-before-Pull crash, `ConflictRecovery`, claim у порожню хмару та варіанти (а)/(б) для непорожньої, direct canonical write rejection, delete/edit, stale cursor 45/180/365/400 днів; checkpoint/min_available_seq, epoch barrier та cursor-floor guard, GC-NORMAL без зайвого full download, corrupt staging, unknown ACK (без auto replay), receipt GC, operation replay, split network, server pause.
- **Recurrence:** Ф1a без повторів (повторювані корені та транзитивні діти не імпортуються); Ф2 atomic append-only доімпорт із parent mapping і Complete guard для `needs_review`; детермінований `nextOccurrence` із injected clock/zone, floating travel vs fixed IANA zone, nonexistent DST hour shifted forward (02:30 → 03:30, рахується в COUNT), overlap first offset, RFC5545 UNTIL value type; daily/weekdays/nth weekday/last day/monthly 31/leap-year; complete-last → `exhausted_completed`, skip-last → `exhausted_skipped` без completion; `skip_to_next`/`reschedule_current`/`edit_recurrence_rule` з absolute/relative/completion-based anchors; starting/ending inclusive, reset subtasks, Undo older occurrence with next active, future virtual projections, timezone travel/DST.
- **Backup:** schema version, corrupt checksum, file truncation, clean restore, **SQLite WAL snapshot під навантаженням, зупинка writer/читачів, збій під час копіювання, нестача місця, `integrity_check`, повернення pre-upgrade DB**, restore after sync, `ExternalIdentity`/LocalWorkspace retained, secrets absent, дамп у локальний Supabase **та на чистий новий Supabase-проєкт** з owner remap/Auth/RLS/epoch.
- **Notifications:** exact grant/deny/revoke, snooze, one-shot/occurrence-bound/task-until-completed lifecycle, recurring task + recurring reminder combinations, Skip/Undo/cancel old alarm, post-reboot/Doze/update/permission denied.
- **Import:** one-shot (Ф1a) зі source IDs без повторюваних коренів **і залежного піддерева**, `DeferredTodoistNode` та звіт no-orphans; `--recurring-only` атомарний доімпорт subtree у Ф2 + Complete guard для `needs_review`, Todoist full + incremental catch-up, `ExternalIdentity`→`TodoistMap`, duplicate source IDs, source deletions, local edit conflict, parallel parser conversion, raw filter Ф5.1 → active AST Ф6a, Import Coverage Matrix, `needs_review` warning, cutover.
- **Security:** server-RPC RLS, another user's ID, session expiry, logout/switch account, tokens in logs, signing secrets in CI.
- **Release:** оновлення поверх попередньої версії, підпис із резервної копії keystore, монотонний `versionCode`, **консистентний SQLite/WAL snapshot до міграції + verify/restore**, а не копія одного `.db`.
- **UI/UX:** small screens/large font/TalkBack, en/pl/uk, search 10k tasks, long titles; Quick Add smart-date toggle/buttons/default project, multiline and `//` escaping, Share Target multiple URLs/unsafe URI, Markdown sanitization, widget independent style, calendar unscheduled tray/3-day/slot-create, Filter Smart Groups and deferred raw migration.

### §12.4. Definition of Done кожної фази

- [ ] Acceptance критерії фази перевірені, не лише «код скомпілювався».
- [ ] Тести додані до repository й реально запускалися; звіт із командою, версією інструментів та результатом.
- [ ] CI зелений, релевантні release artifacts збираються, перевірки секретів пройдені.
- [ ] Немає відкритих P0/P1 регресій; кожен відомий виняток задокументований окремо.
- [ ] Нова DB schema має forward migrations, **WAL-safe verified snapshot**, `integrity_check` і restore-test; destructive migration у production відсутня.
- [ ] Оновлення з попередньої релізної версії поверх (тим самим ключем) проходить без втрати даних.
- [ ] README, CHANGELOG, roadmap і decision log оновлено; конфлікти з іншими PR перевірено.
- [ ] Фактичні обмеження (OEM, free backend, браузер) не приховані.

---

## Результати

Автоматичні результати — у звіті CI на кожному PR (коментар «CI report» і артефакти `runtime-evidence`) та зведено по Ф0 у [`docs/TECH_COMPATIBILITY.md`](TECH_COMPATIBILITY.md). Ручні перевірки фаз записуються сюди: дата, фаза, пристрій, сценарій, результат і посилання на доказ (скріншот, лог, PR).

| Дата | Фаза | Пристрій / браузер | Сценарій | Результат | Доказ |
| --- | --- | --- | --- | --- | --- |
| 03.11.2026 | Ф0 | Xiaomi 24094RAD4G, Android 16 (API 36), arm64-v8a | `v0.0.2` поверх `v0.0.1`: задача з `v0.0.1` на місці; «Про застосунок»: `0.0.2 (2)`, SQLite 3.50.1, FTS5 — так, схема v2, WAL, `quick_check ok`, задач 1, у черзі синхронізації 2 | pass | [скріншот](evidence/f0-v0.0.2-about-xiaomi-android16.jpg) |
