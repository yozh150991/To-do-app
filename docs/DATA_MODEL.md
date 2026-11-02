# Модель даних — локальна БД

**Схема:** Room 3, версія **2** (Ф0). Нормативне джерело полів — spec v2.6.1 §4; тут — фактична реалізація й угоди зберігання.
Згенеровані схеми Room: `core/database/schemas/` (комітяться в репозиторій; CI показує незакомічену схему у звіті PR). Схема v1 заморожена й додатково збережена як DDL-фікстура `core/testing/.../SchemaV1Fixture.kt` для тестів міграції.

## Угоди зберігання

| Тип даних | Як зберігається | Чому |
| --- | --- | --- |
| ID сутностей | UUID-рядок, генерує клієнт (`IdGenerator`) | spec §4 |
| Абсолютні моменти (`created_at`, `completed_at`, `deleted_at`…) | UTC epoch мілісекунди (`INTEGER`) | spec §4 «Часова модель» |
| Цивільні дати й час (`due_date`, `due_time`, `deadline`) | ISO-8601 текст (`2026-11-02`, `09:30`) | Дата ніколи не перетворюється на UTC-північ |
| Перелічення | Стабільні `wire`-імена з `:core:model` (`pending`, `task.create`…) | Ці рядки стануть частиною sync-контракту; перейменування заборонене |
| JSON (`field_patch`, `field_changes`) | Текст JSON-об'єкта | Типізовані патчі outbox і журнал змін |
| Порядок (`child_order`, `section_order`, `item_order`) | Fractional key (base-62, `OrderKeys`) | Reorder змінює рівно один рядок |

**Цивільний час → момент.** Збережені `due_date`/`due_time`/`due_tz` ніколи не перераховуються; момент для сповіщення чи сортування щоразу обчислює `CivilTime.resolve` (`core/model`) зі зоною, переданою явно: `due_tz` для `fixed_zone`, зона пристрою для floating. DST-розрив: згенерована поява зсувається вперед на довжину розриву (02:30 → 03:30) і рахується в COUNT; явний одноразовий час у розриві — помилка з вибором (03:00 або 03:30). DST-перекриття — раніший момент. Golden fixtures — `core/testing/TimeGoldenFixtures` (spec §4.0.1). На старих Android база часових зон може бути застарілою (на API 26 в емуляторі — tzdb 2017b): правила, змінені після неї, такий пристрій порахує інакше.

**Ізоляція акаунтів.** Один файл БД = один `LocalWorkspace` (рядок `local_workspace.singleton = 1` під UNIQUE-індексом). Окремих колонок `account_scope` у кожній таблиці немає: новий акаунт отримує новий файл (інваріант §2.1.6).

**Правила «не більше одного».** Room-анотації не підтримують часткові індекси, а сторонні індекси зламали б валідацію схеми після міграцій. Тому використовується nullable-маркер під UNIQUE-індексом: `project.inbox_marker = 1` лише для системного Inbox, у решти `NULL` (`NULL` у UNIQUE не конфліктують). Той самий прийом буде для «0..1 active occurrence» у Ф2.

## Таблиці

| Таблиця | З версії | Призначення | Ключі та обмеження |
| --- | --- | --- | --- |
| `local_workspace` | v1 | Ідентичність офлайн-workspace, `device_id`, лічильник `last_local_seq` | PK `workspace_id`; UNIQUE `singleton` |
| `project` | v1 | Проєкти, системний Inbox | PK `id`; FK `parent_id → project`; UNIQUE `inbox_marker` |
| `task` | v1 | Задачі, усі поля §4.1 (поля повторів зарезервовані до Ф2) | PK `id`; FK `project_id → project`, `parent_id → task`, `section_id → section` (з v2) |
| `task_activity` | v1 | Історія змін | PK `id`; без FK на task — історія переживає видалення |
| `outbox_operation` | v1 | Незмінні локальні операції для майбутньої синхронізації | PK `operation_id`; UNIQUE `(workspace_id, local_seq)` |
| `external_identity` | v1 | Відповідність `source_id` імпорту → локальний UUID | PK `(source, entity_type, source_id)` |
| `section` | v2 | Секції проєкту | PK `id`; FK `project_id → project` |
| `label` | v2 | Мітки (назви не унікальні: дублікати зливаються явно, §5.2) | PK `id` |
| `task_label` | v2 | Зв'язок задача ↔ мітка | PK `(task_id, label_id)`; FK на `task` і `label` |

**Чому секції й мітки вже у Ф0.** Їхній UI — Ф1b, але одноразовий імпорт Ф1a має зберегти `ExternalIdentity` для проєктів, секцій, міток і задач (spec §9.0, приймання Ф1a). Таблиці потрібні раніше за екрани. До того ж міграція v1 → v2 з перебудовою `task` (нова FK) — реальна перевірка механізму FIX-01, а не синтетичний приклад.

## Транзакційний контракт

Усі локальні мутації йдуть через `TaskRepository` → `LocalStore.write { … }` (одна SQLite write-транзакція):

1. змінити сутність;
2. записати `task_activity`;
3. `UPDATE local_workspace SET last_local_seq = last_local_seq + 1`, прочитати нове значення;
4. вставити `outbox_operation` з цим `local_seq`.

Будь-який виняток на будь-якому кроці відкочує всі чотири. `local_seq` береться з лічильника, а не з `MAX(local_seq)`, тож видалення підтверджених операцій у майбутньому не призведе до повторного використання номера.

## Контракти `LocalWorkspace`, `ExternalIdentity` і confirmed / pending / effective

Таблиці `local_workspace` і `external_identity` існують з v1; `ConfirmedSnapshot` і `ConflictRecovery` з'являються у Ф5b. Нижче — правила, яких мусить дотримуватися будь-який код, що їх торкається (spec §3.2, §4.2, §8.1.1, §8.3.1; інваріанти §2.1).

### `LocalWorkspace`

| Поле | Правило |
| --- | --- |
| `workspace_id` | UUID, створюється разом із файлом бази й ніколи не змінюється. Переживає backup/restore і перший login (claim переносить його в хмару без змін) |
| `account_scope` | `local:<workspace_id>` до claim. Інший акаунт = інший файл бази (інваріант §2.1.6), тож scope у межах файлу не змінюється на чужий |
| `inbox_id` | ID системного Inbox (`project.inbox_marker = 1`). Створюється в тій самій транзакції, що й workspace, з операцією `project.create` в outbox |
| `device_id` | UUID встановлення; підписує кожну `outbox_operation` |
| `claim_status` | `local` → `preparing` → `bound` або `needs_resolution`. Змінюється лише сценарієм `ClaimLocalWorkspace` (Ф5a); до Ф5a завжди `local` |
| `bound_user_id`, `claim_operation_id` | Порожні до claim. `claim_operation_id` один і той самий при кожному повторі claim (ідемпотентність) |
| `backup_checkpoint` | Посилання на останній перевірений backup перед claim |
| `last_local_seq` | Лічильник для `outbox_operation.local_seq`; лише зростає, у тій самій транзакції, що й зміна (див. «Транзакційний контракт») |

**Операції:**

- **Створення** — `TaskRepository.workspace()` при першому зверненні до порожньої бази: workspace + Inbox + `project.create` в outbox однією транзакцією. Повторний виклик повертає наявний рядок (UNIQUE `singleton` не дає створити другий).
- **Backup (Ф1a)** містить рядок workspace і всі `external_identity`. Restore у чисте сховище зберігає `workspace_id`, `inbox_id` і `last_local_seq`. Restore поверх непорожньої бази — лише явною дією з попереднім backup поточних даних.
- **Claim (Ф5a, spec §8.3.1)** — єдиний шлях змінити `claim_status`, `account_scope`, `bound_user_id`. Операції до `local_cutoff_seq` поглинаються bootstrap-квитанцією (`absorbed_by_bootstrap`), `task_activity` не видаляється.

### `ExternalIdentity`

- Ключ — `(source, entity_type, source_id)`; значення — `local_id`, `source_hash`, `import_version`, `last_seen_at`. Джерело зараз одне: `todoist`.
- **Імпорт шукає спершу за ключем.** Знайдено → оновлює наявну сутність (якщо змінився `source_hash`) і `last_seen_at`; не знайдено → створює сутність із новим UUID і рядок identity в одній транзакції. Зіставлення за назвою заборонене (spec §8.3.1, §9).
- `local_id` після створення не змінюється. Видалення локальної сутності не видаляє identity: повторний імпорт того самого `source_id` не воскрешає задачу мовчки, а показує її в звіті імпорту.
- Identity переживає backup/restore, claim і синхронізацію (інваріант §2.1.12).

### Confirmed / pending / effective (Ф5b)

- **Effective state** — звичайні таблиці Room (`task`, `project`…). Лише їх читають екрани, віджети й планувальник нагадувань. Так уже працює зараз.
- **Pending state** — `outbox_operation` зі статусом `pending` у порядку `local_seq`. Кожна операція незмінна до Ack або явного розв'язання; `base_revision` — серверна ревізія, від якої зроблено зміну (`null` для локально створеної сутності).
- **Confirmed state** — `ConfirmedSnapshot` (Ф5b): тіньова копія серверної версії **лише** тих сутностей, що мають pending-операції. Перша локальна зміна синхронізованої сутності в одній транзакції: зберегти snapshot зі стану **до** зміни → змінити effective row → записати activity → вставити операцію. Наступні зміни беруть основу з наявного snapshot. Для нових локальних сутностей snapshot не створюється.
- **Pull/rebase:** серверні зміни застосовуються до confirmed state, далі pending-операції перепрограються в порядку `local_seq`. Конфліктні патчі переходять у `ConflictRecovery` і лишаються доступними («Повернути моє»), не стираються. Snapshot видаляється лише після Ack **і** Pull за відсутності інших pending-операцій.
- До Ф5b таблиць `confirmed_snapshot` і `conflict_recovery` немає; вони додаються новою версією схеми з міграцією й тестами FIX-01.

## Відкриття бази й оновлення схеми (FIX-01, ADR-001)

Продуктовий код відкриває базу тільки через `openAppDatabase(...)` → `DatabaseOpener` усередині `LocalStoreProvider` (один раз на процес, поза головним потоком; до завершення ніщо інше не звертається до файлу).

1. **Залишки збою.** Видаляються тимчасові файли `<db>.*.tmp`. Якщо лишився маркер `<db>.upgrade-in-progress.json`, попереднє оновлення не завершило перевірку: файл бази відновлюється зі snapshot з маркера (з перевіркою sha256), маркер видаляється, оновлення запускається заново.
2. **Версія на диску** (`PRAGMA user_version`): новіша за застосунок → відмова відкривати (`UnsupportedDowngradeException`), файл не змінюється; та сама або новий файл → звичайне відкриття.
3. **Перед оновленням:** перевірка вільного місця (2 × (БД + WAL) + 8 MiB), інакше `InsufficientStorageException` без жодних змін. `PRAGMA integrity_check` джерела; `VACUUM INTO` у тимчасовий файл (консистентна копія з урахуванням незакомічених у основний файл WAL-фреймів); перевірка копії (`integrity_check`, кількість рядків кожної таблиці, `user_version`); sha256; атомарне перейменування в `<db>.pre-v<N>-to-v<M>.snapshot` + маніфест `.json`.
4. **Маркер** `upgrade-in-progress` записується атомарно; Room виконує міграцію.
5. **Перевірка результату** (`MigrationVerifier.Strict`): `integrity_check = ok`, немає порушень FK, нова версія схеми, кожна таблиця, що існувала до оновлення, має ту саму кількість рядків. Міграція, яка свідомо видаляє чи ділить таблицю, має постачати власне правило перевірки.
6. **Збій** будь-якого кроку → закрити Room, видалити `-wal`/`-shm`, відновити snapshot, перевірити відновлений файл, `MigrationFailedException` (UI показує помилку, дані на старій версії). **Успіх** → маркер видаляється; зберігається лише найновіший snapshot.

Тести (однакові на JVM і на Android-емуляторах API 26 і 37, `core/testing/.../DatabaseScenarios.kt`): міграція v1 → v2 зі збереженням даних і новою FK, WAL-фрейми потрапляють у snapshot, відкат при невдалій перевірці, відновлення після симульованого збою на кожному кроці (`UpgradeFault`), нестача місця, відмова від downgrade, прибирання тимчасових файлів, rollback entity + outbox, безперервність `local_seq`, збереження після перезапуску.

## Налаштування Room (ADR-001)

- Драйвер: `BundledSQLiteDriver` на Android і JVM.
- Журнал: WAL (`JournalMode.WRITE_AHEAD_LOGGING`, перевіряється тестом).
- **Web (ADR-004):** `WebWorkerSQLiteDriver` і власний worker на SQLite WASM 3.54.1 з VFS `opfs-sahpool` (файли в Origin Private File System браузера), журнал `TRUNCATE`, одне з'єднання, одна вкладка. Схема, DAO і `TaskRepository` ті самі. Міграція v1 → v2 у браузері — та сама Room AutoMigration; захист snapshot (FIX-01) на Web — до релізу Web у Ф7, примітив `VACUUM INTO` у браузері вже перевірено.
- `fallbackToDestructiveMigration` не використовується.
- FTS5-токенайзер: **`unicode61 remove_diacritics 2`** (ADR-001 п. 5). Перевірено на JVM, Android API 26 і API 37, а також у браузерах (Chromium, Firefox, WebKit): точний збіг, регістр кирилиці, префіксний пошук і пошук польських слів без діакритики (`prad` → `prąd`). Сама FTS-таблиця для задач з'явиться у Ф6a.
