# IMPORT_COVERAGE — матриця покриття імпорту з Todoist

- **Статус:** нормативний текст специфікації v2.6.1 (§9.3), перенесений з [`task-manager-scope-roadmap-v2.6.1.md`](../task-manager-scope-roadmap-v2.6.1.md) без змін змісту (3 листопада 2026 року, §13.4). Змінюється лише через ADR + [`docs/SPEC_CHANGELOG.md`](SPEC_CHANGELOG.md).
- **Фази:** Ф1a (одноразовий імпорт), Ф5.1 (cutover), Ф6a (фільтри).
- Заголовки перенесених розділів мають знак «§» і номер зі специфікації; посилання «§N» у тексті ведуть на розділи специфікації (де лежить кожен розділ — у таблиці §0.3 індексу).
- Сам процес перенесення — [`docs/MIGRATION_TODOIST.md`](MIGRATION_TODOIST.md).
- Фактичні dry-run counts і рішення про cutover додаються в кінець цього файлу з Ф1a.

### §9.3. Import Coverage Matrix / Cutover evidence (обов'язково для Ф5.1)

*Статус означає підтримку **нашого імпортера**; доступність поля в Todoist API й тарифних планах перевіряється під час реалізації, не припускається автоматично. Raw metadata ніколи не дорівнює працюючій функції.*

| Ресурс Todoist | Очікувана дія | Фаза активації | Якщо API/формат не підтриманий |
| --- | --- | --- | --- |
| Активні tasks, projects, sections, labels, priority | Імпорт із `ExternalIdentity`, ієрархією та інваріантами | Ф1a/Ф5.1 | `partial` + ID і причина |
| `due`/time/timezone/deadline/duration | Повний мапінг лише валідних полів, timezone/DST QA | Ф1a/Ф5.1 | `partial` + raw field |
| Recurrence raw text + language | У Ф1a не імпортується разом із транзитивними дітьми (всі `DeferredTodoistNode` у звіті); Ф2 — атомарний append-only доімпорт **усього піддерева**, `ImportPending` для нерозпізнаного; Ф5.1 — серверний імпорт | Ф2/Ф5.1 | `metadata_only`, `needs_review`, не тихе Complete |
| Time/custom/recurring reminders | Перенести лише підтримувані API типи; поведінку прив'язати до LifecyclePolicy | Ф3/Ф5.1 | `partial` і звіт; не вигадувати notification permissions |
| Saved filters + view options | Зберегти raw `FilterImportPending`, source ID, order, description, favorite і сумісні metadata | raw Ф5.1 → AST/active Ф6a | `metadata_only` / `needs_review`, до cutover явно показати |
| Comments / project notes | Імпортувати доступний текст/Markdown зі збереженням raw, посилань на батьків | Ф6c/Ф5.1 raw | `partial`/`metadata_only` + ID |
| Completed history / completion events | Експортувати доступні через API записи, не заявляти повної історичної повноти без перевірки | Ф5.1/Ф6c | `partial` + межі API, періоди й кількість |
| Attachments / files | Фіксувати metadata, links/count якщо доступні; binary files не в scope | Ф9 | `unsupported` з явною втратою файлів у cutover report |
| User/project/filter display preferences | Зберегти сумісне, raw для невідомого | Ф6c/Ф5.1 | `partial` / `metadata_only` |
| Shared projects / assignment / team workspaces | Свідомо не імпортувати як collaborative behavior; одиничні personal tasks тільки після перевірки прав | поза MVP | `unsupported` + обсяг і виключення |

**Формат звіту:** `import_run_id`, `resource_type`, `source_count`, `mapped_count`, `activated_count`, `needs_review_count`, `unsupported_count`, `field_exclusions`, `source_ids`, `errors`, `sync_token_checkpoint`, `timestamp`, `parser_version`; JSON + читабельний Markdown без access token чи персонального тексту понад потребу. Статуси `imported` / `partial` / `metadata_only` / `unsupported`. **Cutover gate:** всі source entities враховані в матриці; жодних «зниклих без статусу»; для `metadata_only` (включно з фільтрами до Ф6a) користувач бачить явний backlog до Full Android Replacement, а відмова від Todoist потребує підтвердження неактивованих важливих функцій. Не змішувати перевірку повноти raw migration Ф5.1 з готовністю Smart Views Ф6a.

---

## Перевірка Todoist API v1 перед Ф1a (3 листопада 2026 року)

Spec §9.1 вимагає перевірити схему, поля й ліміти API під час реалізації. Це перша перевірка — для плану PR 9 Ф1a (`docs/plans/F1A_PLAN.md`); на першому dry-run з реальним токеном назви полів звіряються ще раз.

**Джерела:** офіційна документація [Todoist API v1](https://developer.todoist.com/api/v1/) (автентифікація, Sync API) і типи офіційного TypeScript SDK `@doist/todoist-sdk` 15.13.1 (npm, 2 листопада 2026 року; змінив назву з `@doist/todoist-api-typescript`). SDK перетворює `snake_case` API на `camelCase`; нижче — назви API.

### Доступ і вибірка

| Що | Факт | Наслідок для `tools/todoist-oneshot/` |
| --- | --- | --- |
| Адреса й токен | `https://api.todoist.com/api/v1/`, заголовок `Authorization: Bearer <token>`; персональний токен — у налаштуваннях Todoist, розділ «Інтеграції» | Токен лише зі змінної оточення на час запуску (§9.0), у звіт і лог не потрапляє |
| Повна вибірка | `POST /api/v1/sync`, `sync_token=*`, `resource_types` — JSON-масив (`["all"]` або перелік) | Один запит на `projects`, `sections`, `labels`, `items`, `notes`, `project_notes`, `filters`, `reminders` |
| Incremental catch-up | Відповідь має `sync_token`, `full_sync`, `full_sync_date_utc`; для великих акаунтів повна вибірка може відставати, документація радить одразу incremental із тим самим токеном | Збігається з §9.0: full → incremental одразу після нього; обидва `sync_token` — у звіт як checkpoint |
| Ліміти | Документація відсилає до розділу «Request Limits» (чисел у доступному тексті немає); 429 і `error_extra.retry_after` у секундах | Імпорт робить кілька запитів за запуск; при 429 чекати `retry_after`, не повторювати одразу |
| ID | Рядки (base32 або числові); `tmp-` — лише тимчасові ID команд | `source_id` зберігається як рядок без перетворень |
| Зміна ID | `GET /api/v1/id_mappings/{obj}/{ids}` (старі ↔ нові ID: projects, sections, tasks, comments, reminders) і `GET /api/v1/moved_ids/{obj}` (ID, змінені після переміщення: sections, tasks, comments, reminders) | **Ризик для Ф5.1:** `source_id` задачі може змінитись після переміщення в Todoist. Ф1a не зачіпає (одноразово), але серверний імпорт Ф5.1 має перевіряти `moved_ids` перед зіставленням з `ExternalIdentity` |
| Виконані задачі | Окремі REST-ендпоїнти: `tasks/completed/by_completion_date` і `by_due_date` (вікно `since`/`until`, `cursor`, `limit`); за [issue #694 SDK](https://github.com/Doist/todoist-sdk-typescript/issues/694) перший повертає лише остаточно виконані, без появ повторюваних задач | У Ф1a не імпортуються (лише лічильник); для Ф5.1/Ф6c — `partial` з межами періоду |
| Backups Todoist | `GET /api/v1/backups` і завантаження — власні резервні копії Todoist | Не джерело імпорту (немає `source_id` у потрібному вигляді), але корисні як страховка перед cutover Ф5.1 |

### Поля задачі (`items`) → наша модель, Ф1a

| Поле Todoist | Наше поле | Статус Ф1a |
| --- | --- | --- |
| `id` | `ExternalIdentity(source='todoist', entity_type='task', source_id)` | `imported` |
| `content` | `task.content`; префікс `* ` → `is_completable = false` (SDK так і рахує `isUncompletable`) | `imported` |
| `description` | `task.description` (Markdown як сирий текст, §5.8) | `imported` |
| `project_id`, `section_id`, `parent_id` | Через `ExternalIdentity` відповідних сутностей | `imported`; нащадки повторюваних коренів — `deferred` (FIX-02) |
| `child_order` (ціле) | `task.child_order` (рядковий ключ): сортувати братів за `child_order` і видати ключі `OrderKeys` по черзі | `imported` |
| `priority` 1–4 (4 = p1) | `5 - priority` (§9.1) | `imported` |
| `due.date` | `due_date` | `imported` |
| `due.datetime` без `Z` / з `Z` і `due.timezone` | Плаваючий час → `due_time`, `due_tz = null`; фіксований → `due_time` у зоні `due.timezone`, `due_tz = timezone` (spec §4.0.1) | `imported`; golden-перевірка з кейсами DST |
| `due.is_recurring`, `due.string`, `due.lang` | Повторювана → увесь підграф у `deferred_todoist_node` (§9.0) | `deferred` → Ф2 `--recurring-only` |
| `deadline.date` (`deadline.lang`) | `task.deadline` | `imported` |
| `duration.amount` + `unit` (`minute` / `day`) | `duration_min`: хвилини як є | `imported` для `minute`; для `day` — `partial`: сирі значення в звіт, бо в нас немає багатоденної тривалості |
| `labels` (масив **назв**) | `task_label` через мітки з `labels` | `imported` для особистих міток; назва, якої немає серед особистих (спільна мітка), — `partial` у звіт: дедуплікація за назвою заборонена (§9.0) |
| `added_at` | `task.created_at` | `imported` |
| `updated_at` | Вхід `source_hash` / `last_seen_at` | метадані |
| `checked`, `completed_at`, `is_deleted` | Ф1a бере лише активні | — |
| `day_order` | Ручний порядок «Сьогодні» у Todoist; у API позначено застарілим на користь `user_item_orders` | не переноситься (лічильник у звіті) |
| `is_collapsed` | Стан інтерфейсу | не переноситься |
| `responsible_uid`, `assigned_by_uid`, `added_by_uid` | Спільна робота | `unsupported` (кількість задач із виконавцем у звіт) |

### Проєкти, секції, мітки

| Сутність | Поля Todoist | Мапінг і статус Ф1a |
| --- | --- | --- |
| `projects` | `id`, `name`, `description`, `color` (назва кольору Todoist), `parent_id`, `child_order`, `inbox_project`, `is_favorite`, `is_archived`, `view_style`, `is_shared`, `is_collapsed` | Проєкт Inbox Todoist → наш системний Inbox (його `ExternalIdentity` вказує на локальний `inbox_id`); `color` → найближчий колір нашої палітри, сира назва — у звіт; `is_shared` / проєкти робочих просторів — `unsupported` (кількість); `view_style` — метадані до Ф6b |
| `sections` | `id`, `project_id`, `name`, `description`, `section_order`, `is_archived`, `is_collapsed` | `imported` (екран секцій — Ф1b) |
| `labels` | `id`, `name`, `color`, `order`, `is_favorite` (особисті мітки) | `imported`; спільні мітки (`labels/shared`) мають лише назву — `partial` |
| `notes`, `project_notes` | Коментарі | `metadata_only`: кількість у звіт (коментарі — Ф6c) |
| `reminders` | Нагадування Todoist | `metadata_only`: кількість за типами (наше авто-нагадування Ф1a створюється з `due_time`, повні — Ф3) |
| `filters` | Збережені фільтри | `metadata_only`: кількість (raw — Ф5.1, активація — Ф6a) |

### Питання до реалізації PR 9

1. **Архівні проєкти** (`is_archived`): імпортувати як архівні з їхніми задачами чи лише лічити? Spec говорить про «активні» сутності; рішення — під час PR 9, з лічильником у звіті в будь-якому разі.
2. **Тривалість у днях** (`unit = day`): чи потрібна в нас багатоденна тривалість, чи досить звіту.
3. **Ліміти запитів:** звірити числа з розділом «Request Limits» документації на першому запуску.
