# Модель даних — локальна БД

**Схема:** Room 3, версія **2** (Ф0). Перша частина — фактична реалізація й угоди зберігання; друга — нормативний текст специфікації v2.6.1 §4 (перенесено без змін, 3 листопада 2026 року, §13.4). Поведінка складних переходів (§4.3) — у [`docs/RECURRENCE_CONTRACT.md`](RECURRENCE_CONTRACT.md).
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

---

Нижче — нормативний текст §4 специфікації v2.6.1 без змін. Заголовки мають знак «§»; зміни — лише через ADR + [`docs/SPEC_CHANGELOG.md`](SPEC_CHANGELOG.md). Якщо реалізація вище розходиться з цим текстом, це помилка реалізації або привід для ADR.

## §4. Модель даних

**ID:** UUID генерується клієнтом. Синхронізовані записи мають `id`, `user_id` на сервері, `created_at`, `updated_at`, `deleted_at`, `revision` (серверна версія); клієнтський timestamp не є авторитетом порядку конфліктних операцій. Локальна база має `account_scope`: до Ф5 це `local:<workspace_uuid>`, після контрольованого claim — ідентифікатор зв'язаного акаунта. `LocalWorkspace.workspace_id` і `ExternalIdentity` **не змінюються** від переходу на хмару; системний Inbox **один на власника**, при злитті зв'язки переназначаються транзакційно.

**Часова модель:** `due_date` = `LocalDate`; `due_time` = `LocalTime` або null; `due_tz` = IANA timezone або null (floating local time). Абсолютні моменти (`completed_at`, `created_at`, `remind_at`) — UTC instant. Ніякої прихованої конвертації календарної дати у UTC-північ. Нижче — нормативний контракт (не TODO) для всіх платформ, імпортера, віджетів та сповіщень.

### §4.0.1. Нормативний контракт часу й повторів (V25-TIME)

1. **Типи:** `all_day` = дата без часу та без UTC instant; `fixed_zone` = локальна дата/час + IANA `due_tz`, яка переживає подорожі без зміщення місцевого часу в зоні задачі; `floating_local` = дата/час без `due_tz` (виконується о вказаному настінному часі кожного пристрою, instant нагадування може різнитися між пристроями). Зміна timezone пристрою **не переписує** збережену `LocalDate`/`LocalTime`; для floating перескладає локальні аларми.
2. **Канонічний якір:** кожна задача з повтором має `recurrence_dtstart_date` та `recurrence_dtstart_time?` (доменний DTSTART), `recurrence_zone_mode = all_day | fixed_zone | floating_local`, `recurrence_zone_id?` (лише fixed zone), `recurrence_policy_version` і `recurrence_end_reason?`. Це **конфігурація серії**, не дубль активної `TaskOccurrence`: occurrence + похідний кеш Task.due лишаються джерелом поточної наступної дати. Створення без початкової дати обирає **першу допустиму локальну календарну дату >= локального сьогодні на пристрої створення**, а для заданого часу на сьогодні, що вже минув, — наступну допустиму; цю дату записують як DTSTART та синхронізують. Для `fixed_zone` «сьогодні» обчислюється у заданій IANA-зоні, для floating — у зоні пристрою створення. При явному `починаючи з` використовуємо саме вказану дату, навіть якщо вона в минулому; UI попереджає про overdue.
3. **`COUNT` і `UNTIL`:** `DTSTART` є кандидатом №1 лише якщо він відповідає шаблону. `COUNT` рахує **допустимі** появи з початку серії (включно з уже завершеними/пропущеними), а не кількість виконань. `UNTIL` у UI — включна календарна дата в календарі серії; реалізація зберігає канонічний `until_local_date`, а при серіалізації в RFC 5545 використовує тип і UTC-конверсію `UNTIL` відповідно до типу DTSTART. Не плутати граничну локальну дату користувача з UTC-кінцем дня. `COUNT` + `UNTIL` одночасно заборонені в UI v1, імпорт такої комбінації → `needs_review`. Для `completion_interval` `COUNT` позначає кількість **створених появ** (включно зі skipped), а `UNTIL` обмежує їхню target civil date включно; date-last completion і skipped завершують серію за звичайним state machine.
4. **DST:** для **генерованих RRULE появ** неіснуючий локальний час у DST gap **зсувається вперед на довжину розриву** (02:30 → 03:30 за новим офсетом, тобто той самий instant, що й 02:30 за офсетом до розриву) і **рахується в COUNT**. RFC 5545 §3.3.10 тут суперечить сам собі: в одному місці такі появи ігноруються, в іншому трактуються як явний DATE-TIME за §3.3.5; обрано варіант, за якого задача не зникає і який збігається зі стандартною поведінкою java.time. Однакову поведінку інших iCalendar-клієнтів при експорті не гарантуємо; для дубльованого часу в DST overlap обираємо **перший** із двох offset (раніший instant). Для **одноразового явно заданого** неіснуючого часу повертаємо validation error з вибором користувача, а не переносимо на годину мовчки. Час нагадування визначається за тим самим resolver; якщо відповідного валідного instant немає, показуємо зрозумілу причину і не створюємо неправдивий alarm. Переведення зони/правил ОС не може змінювати самі збережені civil date/time.
5. **Після виконання:** `fixed_schedule` (`DAILY/WEEKLY/MONTHLY/YEARLY`, BYxxx) рахує наступну **допустиму заплановану** появу після `max(completion_local_datetime, current_effective_due)`, не змінюючи DTSTART та не створюючи історичних completion за автоматично пропущені дати; кількість пропущених календарних кандидатів журналюється лише як `auto_advanced_count` в activity. Для `skip_to_next` так само беремо наступну допустиму появу **строго після max(now, effective_due)**, без `TaskCompletion`. `reschedule_current` зберігає одиничний override і не зсуває глобальний fixed anchor. `edit_recurrence_rule` змінює весь шаблон і встановлює новий версійний anchor із preview.
6. **Completion-based** (`recur_from_completion=true`) є **окремим доменним режимом** `completion_interval` для `N` календарних днів/тижнів/місяців/років, а не сирим RFC 5545 RRULE: після успішного completion беремо `completed_at` (UTC) і `completion_zone_id` (IANA-зону, зафіксовану в операції), переводимо у local date, додаємо N календарних одиниць та використовуємо **початковий запланований час** серії. Якщо в target month немає відповідного дня — переміщаємо на останній день того місяця **тільки** для `completion_interval` (нормативне рішення продукту; fixed RRULE недійсні календарні дні пропускає). Після `skip` базою стає `max(today, skipped_effective_date)` у зафіксованій зоні операції; no completion log. Без `completion_zone_id` completion-based sync operation не приймається. Для `floating_local` зберігаємо використану completion zone в операції, щоби два клієнти отримали **однакову наступну civil date**, хоча локальні alarms можуть мати різні UTC instants.
7. **Відтворюваність:** усі next/preview/notifications викликають одну чисту функцію `nextOccurrence(rule, anchor, action, now: Instant, actionZone: IANA, policyVersion)`; не читають приховано системний `now`/timezone. Зміна default timezone в налаштуваннях — явна операція з preview; для існуючої fixed серії не змінює zone автоматично. Для правил із неоднозначною семантикою імпорту Todoist → `needs_review`, а не самовільна зміна графіка.

**Golden fixtures до реалізації (вхід → очікування; контракт Ф1a/Ф2):**

| ID | Вхід | Очікуваний результат |
| --- | --- | --- |
| TIME-01 | `Europe/Warsaw`, `fixed_zone`, DAILY 02:30, `COUNT=3`, DTSTART 2026-03-28 | 28.03 02:30, **29.03 03:30** (DST gap: зсув уперед, 01:30Z), 30.03 02:30; COUNT=3 — рівно три появи, зсунута рахується, на 31.03 появи немає |
| TIME-02 | `Europe/Warsaw`, WEEKLY SU 02:30, 2026-10-25 | Двічі можливий локальний час: обрати перше входження, **00:30Z**, не створювати два occurrences |
| TIME-03 | `fixed_zone=Europe/Warsaw`, DAILY 09:00; телефон переходить у `America/New_York` | Цивільний час задачі лишається **09:00 Варшава**; локальний вигляд/instant перераховується, RRULE anchor не змінюється |
| TIME-04 | `floating_local`, DAILY 09:00; пристрій змінює зону | Stored `LocalDate`/09:00 не змінюються; alarms на цьому пристрої переплановано на **09:00 поточної зони**, sync не змінює due civil fields |
| TIME-05 | `fixed_schedule` щопонеділка 09:00, `DTSTART=2026-11-02`, completion 2026-11-11 | Наступна active = **2026-11-16 09:00**; проміжна дата не породжує фіктивне `TaskCompletion` |
| TIME-06 | `completion_interval` щомісяця о 09:00, complete 2026-01-31 у `Europe/Warsaw`, N=1 month | Наступна active = **2026-02-28 09:00**; записати completion_zone_id, правило short month працює лише для completion_interval |
| TIME-07 | `all_day` `DTSTART=2026-01-01`, `FREQ=DAILY;COUNT=2` + `UNTIL` окремо | COUNT → 01.01, 02.01; `UNTIL=2026-01-02` → ті ж дві появи; комбінація COUNT+UNTIL без review заборонена |
| TIME-08 | Explicit one-shot 2026-03-29 02:30 `Europe/Warsaw` | **Validation error + вибір** іншого допустимого часу, жодного silent shift/alarm |

### §4.1. Task

| Поле | Тип | Семантика |
| --- | --- | --- |
| `id` | UUID | Стабільний ID |
| `content` | text | Назва; обов'язкова після trim |
| `description` | text | З Ф6c — безпечний Markdown plain-source, без вкладених файлів у v1; до Ф6c — plain text зі збереженням символів без втрат |
| `source_url` | text? | Опційне явне URL-джерело із Share Target (нормалізація, дозволені лише підтримувані безпечні схеми) |
| `project_id` | UUID | Завжди існує; за замовчуванням Inbox |
| `section_id` | UUID? | Якщо секція задана, вона з того самого проєкту |
| `parent_id` | UUID? | До 4 рівнів; заборонені цикли |
| `child_order` | text | Fractional ranking у батька/секції |
| `day_order` | text? | Ручний порядок у Today |
| `priority` | int 1..4 | `1` = найвищий p1 |
| `due_date` | date? | Коли заплановано виконати; для повторюваної задачі — кеш `effective_due` її активної появи, оновлюється в тій самій транзакції |
| `due_time` | time? | null = задача на весь день |
| `due_tz` | text? | IANA timezone; null = плаваючий час |
| `deadline` | date? | Крайній строк окремо від due |
| `rrule` | text? | Підтримуваний fixed-schedule RRULE subset; для completion-based — окремий доменний interval із версією політики |
| `recur_from_completion` | bool | false = fixed calendar schedule; true = completion_interval із явною calendar unit/interval, не RRULE наступної дати |
| `recurrence_end_reason` | enum? | null, `exhausted_completed`, `exhausted_skipped`, `manually_stopped`; не створює `TaskCompletion` при Skip |
| `recurrence_dtstart_date`, `recurrence_dtstart_time` | date, time? | Незмінний якір DTSTART серії; відокремлено від effective_due активної появи |
| `recurrence_zone_mode`, `recurrence_zone_id` | enum, IANA? | all_day/fixed_zone/floating_local; zone_id тільки для fixed_zone |
| `recurrence_interval_unit`, `recurrence_interval_n` | enum?, int? | Для completion_interval: DAY/WEEK/MONTH/YEAR і N>=1 |
| `recurrence_policy_version` | int | Версія детермінованої політики next occurrence, перевіряється клієнтом і сервером |
| `duration_min` | int? | Для календаря/time blocking, додатне число |
| `completed_at` | timestamp? | Для неповторюваної задачі / кешована похідна ознака; **не** історія повторів |
| `is_completable` | bool | Типово true; false — інформаційні задачі/заголовки (префікс `* ` у Quick Add) |
| `revision` | bigint | Остання підтверджена сервером ревізія; локальні pending зміни окремо |
| `created_at`, `updated_at`, `deleted_at` | timestamp? | Аудит; час пристрою не вирішує конфлікти |

**Повтори — одне джерело правди.** Для повторюваної задачі існує **від нуля до однієї** `TaskOccurrence` у стані `active` (унікальний частковий індекс). Нуль допустимий після `UNTIL`/`COUNT`, явного `Stop recurrence`, до завершення імпортного `needs_review` або для завершеної/видаленої задачі. **Що бачить користувач:** якщо правило вичерпалось **Complete** останньої появи (`COUNT`/`UNTIL`), задача стає виконаною (`completed_at`, `recurrence_end_reason=exhausted_completed`) і зникає з активних списків; після **Skip** останньої появи розклад завершується (`exhausted_skipped`), **але задача лишається невиконаною** без дати, з позначкою «Повтор завершився без виконання» і можливістю перепланувати; після явного `Stop recurrence` задача лишається відкритою, без дати й без повтору (`manually_stopped`); задача з `needs_review` лишається відкритою з попередженням. Якщо активної появи немає, похідний кеш `Task.due_date`/`due_time` для повтору — `null`; історія лишається. Якщо є — кеш відповідає `effective_due` і змінюється в одній транзакції. Разове перенесення — `override` **активної** появи, а не Task.

### §4.2. Інші сутності

| Сутність | Ключові поля / правила | Обмін |
| --- | --- | --- |
| `Project` | `name`, `description`, `color`, `parent_id`, `child_order`, `is_inbox`, `view_mode`, `is_favorite`, `archived_at` | sync |
| `Section` | `project_id`, `name`, `description`, `section_order`, `collapsed` | sync; `collapsed` можна зробити локальним налаштуванням пізніше |
| `Label` | `name`, `color`, `item_order`, `is_favorite` | sync |
| `TaskLabel` | `task_id`, `label_id`; унікальна пара, операційне додавання/видалення | sync |
| `Reminder` | `task_id`, `type` (absolute/relative/deadline/independent_recurring), `scope` (`one_shot`/`occurrence_bound`/`task_until_completed`), `occurrence_id?`, `remind_at`, `offset_min`, `enabled`, `is_auto`, `repeat_rule?`, `stop_on_task_complete`; `Task.rrule` та `Reminder.repeat_rule` незалежні | sync |
| `ReminderDelivery` | `reminder_id`, `occurrence_id`, `device_id`, `planned_at`, `fired_at`, `snoozed_until`, `dismissed_at` | локально за замовчуванням |
| `TaskOccurrence` | `id`, `task_id`, `scheduled_for`, `state`, `effective_due`, `override`; не більше однієї `active` на задачу (частковий унікальний індекс), завершені й пропущені лишаються як історія | sync |
| `TaskCompletion` | `id`, `task_id`, `occurrence_id?`, `scheduled_for`, `completed_at`, `undone_at`, `device_id`, `operation_id`, `completion_zone_id` (IANA для completion-based) | sync |
| `TaskActivity` | `id`, `task_id`, `event_type`, `field_changes`, `created_at`, `device_id`, `operation_id` | sync (обмежити retention окремо) |
| `TaskTemplate` | `name`, `description`, snapshot полів, дочірні шаблони, relative date rules | sync |
| `Comment` | `task_id`, `content`, `created_at` | sync |
| `Filter` | `name`, `color`, `item_order`, `definition` (версійний JSON `{groups: [{name, ast}], dedup_policy}`; для одногрупового фільтра groups має 1 елемент), `is_favorite` | sync |
| `ViewSettings` | `view_key`, `layout`, `group_by`, `sort_by`, `show_completed`, optional options | sync |
| `WidgetConfig` | `widget_id`, `view_key`, `theme`, `opacity`, `text_scale`, `compact`, `max_items`, `show_due`, `show_priority`, optional quick_switch | локально |
| `OutboxOperation` | `operation_id`, `local_seq` (монотонний `BIGINT` у межах `workspace_id`, видається транзакційно), `workspace_id`, `entity`, `entity_id`, `op_type`, `field_patch`, `base_revision`, `device_id`, `created_at`, `status` (`pending`/`acked`/`absorbed_by_bootstrap`/`needs_resolution`), `attempts`, `last_error`; immutable payload до Ack/explicit resolve; унікально `(workspace_id, local_seq)` | локально |
| `Device` | `device_id`, `user_id`, `platform`, `last_seen`, `last_ack_seq`, `sync_epoch`, `revoked_at` | server + local cache |
| `SyncCursor` | `device_id`, `last_applied_change_seq`, `sync_epoch` | локально |
| `LocalWorkspace` | `workspace_id`, `account_scope`, `inbox_id`, `claim_status` (`local`/`preparing`/`bound`/`needs_resolution`), `bound_user_id?`, `claim_operation_id?`, `backup_checkpoint?` | локально з Ф0 |
| `ConfirmedSnapshot` | `entity`, `entity_id`, `server_revision`, `payload`, `last_change_seq` — остання підтверджена серверна основа; з'являється з Ф5b і створюється **лише для сутностей із pending-операціями** (тіньова копія), видаляється після Ack + Pull | локально |
| `ConflictRecovery` | `operation_id`, `entity`, `entity_id`, `local_patch`, `server_snapshot`, `reason`, `created_at`, `resolution_status` — зберігає відхилене незалежно від життя Task | локально; UI Sync Health |
| `ExternalIdentity` | `source` (`todoist`), `entity_type`, `source_id`, `local_id`, `source_hash`, `import_version`, `last_seen_at`; унікально `(workspace/account_scope, source, entity_type, source_id)` | з Ф1a: локально, з Ф5: sync |
| `AccountClaim` | `claim_operation_id`, `workspace_id`, `target_user_id`, `status`, `inbox_merge_map`, `checkpoint`, `local_cutoff_seq`, `source_outbox_digest`, `error` — результат прив'язки/повтору без подвійного ефекту | локально + server receipt |
| `ClaimBootstrapReceipt` | `claim_operation_id`, `workspace_id`, `cutoff_seq`, `source_outbox_digest`, `snapshot_checksum`, `committed_seq`, `result`, `absorbed_operation_ids_digest`; однозначно зв'язує bootstrap snapshot і старий outbox | сервер + локальна копія після claim |
| `ServerChangeLog` | `change_seq`, `user_id`, `entity`, `entity_id`, `revision`, `operation_id`, `change_payload`, `tombstone`, `epoch` | лише сервер |
| `ServerOperationReceipt` | `user_id`, `operation_id`, `result`, `applied_revision`, `committed_change_seq`, `epoch` | лише сервер |
| `SyncConflict` | `entity`, `entity_id`, `base`, `local_patch`, `server_state`, `status`, `created_at` | Ф9, лише якщо знадобиться повний UI конфліктів; у v1 перезаписані значення фіксуються в `TaskActivity` |
| `TrashEntry` | `entity`, `entity_id`, `deleted_at`, `restore_before`, snapshot/reference | sync |
| `BackupManifest` | `backup_version`, `schema_version`, `account_scope`, `created_at`, `checksum` | у файлі експорту |
| `DeferredTodoistNode` | `source_id`, `source_parent_id?`, `source_project_id?`, `source_section_id?`, `root_recurring_source_id`, `depth`, `deferred_reason`, `source_hash`, `import_run_id`; **тільки metadata** до Ф2, без вигаданого `Task.parent_id` | Ф1a: backup/report; Ф2: закрити після атомарного доімпорту підграфа |
| `ImportPending` | `task_id`, `source` (`oneshot_recurring` — доімпорт у Ф2 / `todoist_sync`), `raw_due_string`, `lang`, `source_hash`, `parser_version?`, `status` (`pending`/`parsed`/`needs_review`), `conversion_operation_id?` | sync; повтори з імпорту, які ідемпотентно перетворює в RRULE клієнтський парсер |
| `FilterImportPending` | `source_filter_id`, `ExternalIdentity`, `local_filter_id?` (nullable до Ф6a, FK лише після створення `Filter`), `source_query`, `source_lang?`, `source_description?`, `source_view_options?`, `source_hash`, `parser_version?`, `status` (`raw`/`converted`/`needs_review`/`activated`), `conversion_error?` | sync; raw дані з Ф5.1, активація у Ф6a |
| `ImportCoverageReport` | `import_run_id`, `resource_type`, `source_count`, `target_count`, `status` (`imported`/`partial`/`metadata_only`/`unsupported`), `reason`, `examples` | локальний/exported звіт Ф1a/Ф5.1; жодних токенів |
| `TodoistMap` | `entity`, `todoist_id`, `local_id`, `last_source_version`, `last_import_hash`; **серверна проєкція з `ExternalIdentity`, а не єдине джерело маппінгу** | лише сервер |
| `Heartbeat` | `source` (id пристрою або `github`), `last_seen` | лише сервер |

**Індекси й перевірки:** `project_id`, `parent_id`, `due_date`, `deadline`, `(user_id, deleted_at)`, `task_id` в історії, `change_seq` у server log; унікальні `operation_id`/`TaskLabel`/Inbox, `(account_scope, source, entity_type, source_id)` для `ExternalIdentity` і частковий індекс `TaskOccurrence(task_id) WHERE state = 'active'`. FK/каскади — через міграції; soft delete вимагає керованих каскадних переходів. При claim не змінювати UUID задач; лише прозоро зіставити системний Inbox та зовнішні ID.
