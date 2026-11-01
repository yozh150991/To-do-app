# Модель даних — локальна БД

**Схема:** Room 3, версія **1** (Ф0). Нормативне джерело полів — spec v2.6.1 §4; тут — фактична реалізація й угоди зберігання.
Згенерована схема Room: `core/database/schemas/` (комітиться в репозиторій і перевіряється CI).

## Угоди зберігання

| Тип даних | Як зберігається | Чому |
| --- | --- | --- |
| ID сутностей | UUID-рядок, генерує клієнт (`IdGenerator`) | spec §4 |
| Абсолютні моменти (`created_at`, `completed_at`, `deleted_at`…) | UTC epoch мілісекунди (`INTEGER`) | spec §4 «Часова модель» |
| Цивільні дати й час (`due_date`, `due_time`, `deadline`) | ISO-8601 текст (`2026-11-02`, `09:30`) | Дата ніколи не перетворюється на UTC-північ |
| Перелічення | Стабільні `wire`-імена з `:core:model` (`pending`, `task.create`…) | Ці рядки стануть частиною sync-контракту; перейменування заборонене |
| JSON (`field_patch`, `field_changes`) | Текст JSON-об'єкта | Типізовані патчі outbox і журнал змін |
| Порядок (`child_order`) | Fractional key (base-62, `OrderKeys`) | Reorder змінює рівно один рядок |

**Ізоляція акаунтів.** Один файл БД = один `LocalWorkspace` (рядок `local_workspace.singleton = 1` під UNIQUE-індексом). Окремих колонок `account_scope` у кожній таблиці немає: новий акаунт отримує новий файл (інваріант §2.1.6).

**Правила «не більше одного».** Room-анотації не підтримують часткові індекси, а сторонні індекси зламали б валідацію схеми після міграцій. Тому використовується nullable-маркер під UNIQUE-індексом: `project.inbox_marker = 1` лише для системного Inbox, у решти `NULL` (`NULL` у UNIQUE не конфліктують). Той самий прийом буде для «0..1 active occurrence» у Ф2.

## Таблиці schema v1

| Таблиця | Призначення | Ключі та обмеження |
| --- | --- | --- |
| `local_workspace` | Ідентичність офлайн-workspace, `device_id`, лічильник `last_local_seq` | PK `workspace_id`; UNIQUE `singleton` |
| `project` | Проєкти, системний Inbox | PK `id`; FK `parent_id → project`; UNIQUE `inbox_marker` |
| `task` | Задачі, усі поля §4.1 (поля повторів зарезервовані до Ф2) | PK `id`; FK `project_id → project`, `parent_id → task`; індекси `project_id`, `parent_id`, `due_date`, `deadline`, `deleted_at` |
| `task_activity` | Історія змін (пишеться з Ф1a, зараз — з Ф0) | PK `id`; без FK на task — історія переживає видалення |
| `outbox_operation` | Незмінні локальні операції для майбутньої синхронізації | PK `operation_id`; UNIQUE `(workspace_id, local_seq)` |
| `external_identity` | Відповідність `source_id` імпорту → локальний UUID | PK `(source, entity_type, source_id)` |

`section_id` у `task` поки без FK: таблиця `section` з'явиться в Ф1b разом із міграцією v1 → v2.

## Транзакційний контракт

Усі локальні мутації йдуть через `TaskRepository` → `LocalStore.write { … }` (одна SQLite write-транзакція):

1. змінити сутність;
2. записати `task_activity`;
3. `UPDATE local_workspace SET last_local_seq = last_local_seq + 1`, прочитати нове значення;
4. вставити `outbox_operation` з цим `local_seq`.

Будь-який виняток на будь-якому кроці відкочує всі чотири. `local_seq` береться з лічильника, а не з `MAX(local_seq)`, тож видалення підтверджених операцій у майбутньому не призведе до повторного використання номера.

Тести контракту: `core/domain/src/jvmTest/.../TaskRepositoryTest.kt` (rollback при збої outbox, виняток посеред транзакції, безперервність `local_seq` при 100 паралельних записах, збереження після reopen).

## Налаштування Room (ADR-001)

- Драйвер: `BundledSQLiteDriver` на Android і JVM.
- Журнал: WAL (`JournalMode.WRITE_AHEAD_LOGGING`, перевіряється тестом).
- `fallbackToDestructiveMigration` не використовується.
- FTS5-токенайзер: кандидат `unicode61 remove_diacritics 2`; остаточний вибір фіксується тут після тесту на Android API 26 (ADR-001).
