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
