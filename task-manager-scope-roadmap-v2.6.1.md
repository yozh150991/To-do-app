# Особистий таск-менеджер — скоуп, архітектура й роадмап

**Версія документа:** 2.6.1 (консолідований фінальний patch baseline)  
**Оновлено:** 1 листопада 2026 року  
**Основа:** v2.6 від 1 листопада 2026 року; v2.6.1 — цільові виправлення без зміни функціонального скоупу або загального порядку фаз. Історія специфікації v2.0–v2.6 і незалежний аудит v2.5 — у `docs/SPEC_CHANGELOG.md`; релізи застосунку — в окремому `CHANGELOG.md`.  
**Статус:** **заморожений передімплементаційний baseline v2.6.1; готовий до старту Ф0**. Жодні runtime-збірки/кросплатформні тести/серверні контракти цим документом не підтверджені; кожна фаза має пройти власні gates. Після Ф0 зміни нормативних рішень — лише ADR + адресне оновлення `docs/` + `docs/SPEC_CHANGELOG.md`, зміни реалізації — `CHANGELOG.md`; нових теоретичних перевидань roadmap не планується.  
**Головний принцип:** спершу безпека даних і надійна офлайн-робота, потім широта функціоналу.

## 0. Чинна редакція

Документ містить **лише чинні правила**. Історію версій v2.0–v2.6 (таблиці змін) і незалежний аудит v2.5 винесено в `docs/SPEC_CHANGELOG.md`. Цей файл є **історичним**, не нормативним. `CHANGELOG.md` призначений **лише для релізів застосунку**. Якщо історія змін і ця специфікація розходяться, чинним є цей документ.

**Правило статусу:** критерії й fallback ухвалені в специфікації. Runtime-сумісність конкретних версій, поведінка Web-браузерів і end-to-end full reconciliation можуть отримати статус `verified` **лише після фактичних тестів із логами у Ф0/Ф5b**. Вони не позначаються як виконані на підставі документа.

**Чинні ADR:** перелік у `docs/ADR/README.md`. Ухвалені ADR мають пріоритет над розділами цієї специфікації, які вони явно замінюють: ADR-001 — драйвер SQLite (`BundledSQLiteDriver`), ADR-002 — розрахунок повторів на клієнті замість Deno evaluator (FIX-03). Версія специфікації при цьому не змінюється.

### 0.1. Фінальні поправки v2.6.1

| ID | Точкова зміна | Розділи / QA-gate |
| --- | --- | --- |
| FIX-01 | Консистентний SQLite backup із врахуванням WAL перед міграцією; integrity/checksum та recovery | §3.4, Ф0, §12, §16 |
| FIX-02 | При відкладеному імпорті повторюваного батька відкладати **весь його залежний підграф**; без orphaned subtasks | §4.2, §9.0/9.3, Ф1a/Ф2, §12/16 |
| FIX-03 | Серверний канонічний розрахунок наступної появи через trusted Edge evaluator, atomic RPC validation та ті самі golden fixtures | §8.1/8.2, Ф5b, §12/16 |
| FIX-04 | Однозначний первинний snapshot claim: старий локальний outbox поглинається через receipt, без повторного replay | §8.3.1, Ф5a, §12/16 |
| FIX-05 | Усунуто повторний опис `recurrence_end_reason` у Task | §4.1 |
| FIX-06 | Приймання Ф0: Android = обов'язковий pass; Web/Desktop = pass **або** документований blocked/ADR із планом перевірки у своїй фазі | §3.3, Ф0, §12/16 |
| DOC-01 | Історія специфікації відокремлена від історії релізів застосунку | §0, §13, `docs/SPEC_CHANGELOG.md`, `CHANGELOG.md` |

### 0.2. Що змінилося у v2.6

| Тема | У v2.5 | У v2.6 | Де |
| --- | --- | --- | --- |
| DST-розрив у повторах | Неіснуючий локальний час пропускається й не рахується в `COUNT` (з посиланням на RFC 5545) | Неіснуючий час зсувається вперед на довжину розриву (02:30 → 03:30) і рахується в `COUNT`. RFC 5545 §3.3.10 тут суперечить сам собі; обрано варіант, за якого задача не зникає і який збігається зі стандартною поведінкою java.time | §4.0.1 (п. 4, TIME-01), §12.3 |
| Історія змін | §0.0–§0.5 і §17 усередині документа | Перенесено в історичний файл специфікації; документ містить лише чинні правила | §0, §13.3, §13.4, `docs/SPEC_CHANGELOG.md` |
| Повтори у Ф1a | Мінімальний recurrence engine, `basic_parsed`, Complete guard для невідомих повторів | Ф1a не імпортує повторювані задачі: вони лишаються в Todoist до Ф2, звіт імпорту містить їх перелік. У Ф2 скрипт доімпортовує їх режимом `--recurring-only` (append-only, дедуплікація за `ExternalIdentity`) | §4.3, §5.5, §9.0, §9.3, Ф1a, Ф2, §12.3, §14, §16 |
| Web fallback | Окремий `LocalStore` на IndexedDB на рівні Repository | Рівень 1 — worker для `WebWorkerSQLiteDriver` на SQLite-WASM зі сховищем поверх IndexedDB (та сама Room-схема, SQL, FTS5, міграції); рівень 2 — `LocalStore` на IndexedDB | §3.3.1, Ф0, §14 |

**Класи пріоритету:** `P0` — безпека даних і базова працездатність; `P1` — важлива функціональність першого повного продукту; `P2` — необов'язкові розширення.  
**Фази:** Ф0, Ф1a, Ф1b, Ф2–Ф4, Ф5a, Ф5b, Ф5.1, Ф6a, Ф6b, Ф6c, Ф7–Ф9 (згадка «Ф5» без літери означає Ф5a + Ф5b). Нумерація з вихідного плану збережена наскільки можливо.

---

## 1. Мета і рамки

Особистий менеджер задач за зразком функціональності Todoist: **Android спочатку**, пізніше **Web та Desktop**. Один користувач, офлайн-перший підхід, локальна база як робоче джерело даних, синхронізація між власними пристроями через Supabase. Встановлення Android через підписаний власним ключем APK без Google Play. Основні мови інтерфейсу: **українська, англійська, польська**.

### 1.1. Функціональна мета

- Зберігати всі задачі, проєкти, секції, мітки, дедлайни та історію без втрати даних.
- Створювати задачу з мінімумом дій, зокрема природною мовою, віджетом і системною командою.
- Планувати: Today, Upcoming, довільні фільтри, календар, повтори, нагадування, шаблони.
- Працювати повністю офлайн; після повернення мережі синхронізувати без дублювання та тихого перезапису змін.
- Безпечно перейти з Todoist з можливістю порівняти імпортовані дані до вимкнення імпортера.

### 1.2. У скоупі

1. Задачі, підзадачі (до 4 рівнів), пріоритети, дати, час, дедлайни, тривалість.
2. Проєкти, вкладені проєкти, секції, мітки, обране, архів і кошик.
3. Inbox, Today, Upcoming, Project, Label, Filter; список, дошка, календар.
4. Quick Add трьома мовами, настроюваний розбір дат/кнопки, опціональний опис і багаторядкове створення; app shortcuts, Quick Settings tile, Share target із безпечним прийманням URL/тексту.
5. Повтори, історія виконань, різні Skip/Reschedule/Edit rule, стан закінчення після пропуску, віртуальні майбутні появи для календаря.
6. Локальні Android-нагадування, snooze, кілька нагадувань і базові нагадування за власним повтором.
7. Android-віджети з незалежною конфігурацією, параметрами вигляду та швидкою зміною джерела.
8. Фільтри з чіткими правилами due/deadline, пошук, сортування, групування, Smart Views, шаблони, коментарі, історія; клікабельні URL та безпечний Markdown в описах.
9. Локальний backup/restore, пізніше захищені резервні копії.
10. Supabase Auth/RLS/SyncEngine, контрольована прив’язка локального workspace та односторонній перехідний імпорт із Todoist зі збереженням ID джерела.
11. Web і Desktop через Kotlin Multiplatform, системні можливості на відповідних платформах.

### 1.3. Свідомо не робимо в основному релізі

- Спільні проєкти, ролі, команди, делегування та призначення задач іншій особі.
- iOS.
- Універсальні інтеграції зі сторонніми сервісами; виняток — перехідний імпорт Todoist. Інтеграцію з календарями розглянути у Ф9.
- Гейміфікацію, KPI продуктивності й аналітичні дашборди.
- Файлові вкладення у першій повній версії (Ф9).
- AI-сервіс та складне голосове введення як обов'язкову частину MVP.
- Повноцінне end-to-end encryption; опційний PIN/біометрія й шифрований backup у майбутньому.

**Юридична межа:** повторюємо загальнодоступні ідеї функціональності, але не назву, логотип, іконографіку, фірмові екрани або тексти Todoist. UI має власну візуальну систему.

---

## 2. Нефункціональні вимоги та інваріанти

### 2.1. Інваріанти даних (`P0`)

1. **Жодного втраченого підтвердженого запису:** локальна зміна фіксується в Room і outbox однією транзакцією; результат видимий офлайн негайно.
2. **Ідемпотентність:** одна й та сама операція із `operation_id` може бути надіслана 2, 10 чи 100 разів без повторного ефекту.
3. **Збіжність:** після успішного обміну даними клієнти бачать однаковий серверно підтверджений стан; невирішені конфлікти не замовчуються.
4. **Без тихого видалення:** при конфлікті чи перезастарілому курсорі локальний вміст зберігається в історії/журналі й лишається доступним для відновлення; факт перезапису видно в Sync Health, а не лише випадково.
5. **Відновлюваність:** будь-яку підтримувану резервну копію можна відновити на чистому пристрої, з перевіркою цілісності та версії схеми.
6. **Відсутність витоків між акаунтами:** при виході/зміні користувача локальні дані старого акаунта не доступні новому сеансу.
7. **Немає операцій мережі з віджета:** дії віджета/сповіщення проходять через Repository і локальну транзакцію, потім синхронізуються.
8. **Версійна сумісність:** новий сервер не має ламати старий клієнт без явного повідомлення й безпечного режиму оновлення.
9. **Оновлення без втрати даних:** нова версія застосунку встановлюється поверх старої тим самим ключем підпису; «видалити й поставити заново» ніколи не є штатним шляхом оновлення (§3.4).
10. **Безпечна прив’язка workspace:** перший вхід у Supabase не стирає локальні дані, не створює другий системний Inbox і не приховує конфлікти; до підтвердження claim доступні backup/rollback.
11. **Неперезаписуваний pending state:** Pull та повторні підтвердження не затирають незавершені офлайн-операції; effective state обчислюється після ребейзу.
12. **Неперервність ідентичності імпорту:** `source_id` із Todoist переживає backup, перший login, міграцію та подальшу синхронізацію; повторний імпорт не клонує сутності.
13. **Жодного хибного виконання імпортного повтору:** `ImportPending` із невідомим правилом не може бути завершено як підтверджену одноразову задачу без явно розв'язаного правила.
14. **Без прихованої втрати при імпорті:** кожний підтримуваний тип Todoist має звіт про imported/partial/metadata_only/unsupported; несумісні фільтри не зникають.
15. **Атомарна основа для rebase:** перший локальний pending edit зберігає незмінну серверну основу до мутації, в одній транзакції з outbox та effective state.

### 2.2. Якість, швидкість, доступність

- Орієнтир для вимірювань на реальному середньому Android: відкриття вже підготовленого Today без відчутної затримки; конкретний бюджет (наприклад, час відкриття Today при 10 000 задач на основному телефоні) вимірюється й фіксується у Ф1b і стає gate-критерієм Ф4.
- Тестові набори мінімум на **10 000 задач**, **1 000 проєктів/міток сукупно**, великий Inbox і довгі описи.
- Плавне прокручування, debounce для фільтрів, пошуку та віджетів, індекси для основних запитів.
- Доступність: великі шрифти, TalkBack, достатній контраст, коректні touch-targets, portrait/landscape та системні UI-insets.
- UI адаптується до різних розмірів дисплея, включно із планшетом та розділеним екраном.
- Сповіщення в умовах Doze та обмежень виробника мають найкращу можливу доставку, але не декларуємо недосяжну 100% точність на будь-якому Android.

---

## 3. Стек та архітектура

Kotlin Multiplatform (KMP) з Ф0. Спільними є доменна логіка, Repository, парсери, повтори, фільтри, більша частина Compose UI та мережевий контракт. Платформні фічі реалізуються окремо.

| Шар | Рішення | Примітки |
| --- | --- | --- |
| Мова | Kotlin Multiplatform | Android, JVM/Desktop, Web/Wasm |
| UI | Compose Multiplatform, Material 3 | Адаптивні макети, власна тема |
| Локальна БД | Room 3 (`androidx.room3`) + SQLiteDriver | Schema migrations, індекси, FTS5; перевірити конкретні таргети в Ф0 |
| Browser persistence | `WebWorkerSQLiteDriver` + власний worker (SQLite WASM, OPFS) | Драйвер постачається без готового worker — беремо приклад з androidx як локальний npm-пакет. PoC для OPFS, COOP/COEP, storage eviction, закриття вкладки; fallback-рішення до Ф7 |
| Асинхронність | Coroutines + Flow | Усе без блокування UI |
| DI | Koin | Платформні адаптери |
| Дати/час | kotlinx-datetime | Локальна дата, IANA timezone, UTC instants |
| Серіалізація | kotlinx.serialization | Версійні DTO, backup JSON |
| Backend | Supabase Postgres/Auth/Realtime/Edge Functions | RPC/транзакції, RLS, журнал змін |
| Клієнт backend | supabase-kt | Валідувати для всіх цільових таргетів Ф0 |
| Налаштування | DataStore на Android; platform-specific adapter | Спільний інтерфейс, без припущення, що Android DataStore доступний на Web |
| Віджети | Jetpack Glance | Android |
| Нагадування | AlarmManager + notification APIs | Android; exact при наявності дозволу, fallback |
| Підтримка фонових задач | WorkManager | Android, періодичність обмежена ОС |
| Android | minSdk 26; targetSdk — актуальний стабільний | Фіксувати у build config і перевіряти на тестових версіях |
| CI | GitHub Actions | Unit, lint, UI/test suites, збірки; захищений release signing; публікація підписаних APK у GitHub Releases |

**Технічна примітка:** станом на 01.11.2026 Room 3.0.3 — стабільний реліз. Відповідність Kotlin/Compose/KSP/AGP/Room/supabase-kt має бути зафіксована у version catalog після перевірки, а не припускатися з документа.

### 3.1. Модулі

- `core:model` — доменні об'єкти, інваріанти, серіалізація, типізовані операції.
- `core:database` — Room 3, DAO, migrations, FTS та локальний журнал.
- `core:domain` — Repository, повтори, дати, розклад, фільтри та парсер Quick Add.
- `core:sync` — SyncEngine, підтверджений стан + pending/effective projection, rebase, outbox, курсори, конфлікти, DTO, правила імпорту/експорту й claim workspace.
- `ui` — спільні екрани, ViewModels, дизайн-система, ресурси.
- `androidApp` — Android entry point, Glance, AlarmManager, WorkManager, Share Target, shortcuts, tile.
- `webApp` (прототип у Ф0, продукт у Ф7) — Compose/Wasm entry point.
- `desktopApp` (прототип у Ф0, продукт у Ф8) — Compose Desktop entry point, tray, global shortcut.
- `supabase/` — SQL migrations, RLS, RPC (зокрема `heartbeat`), журнали змін, Edge Functions, Todoist importer, pgTAP-тести.
- `tools/todoist-oneshot/` (Ф1a) — локальний TypeScript-скрипт одноразового імпорту з Todoist у формат backup; модуль маппінгу спільний з Edge Function Ф5.1.
- `ops/` (Ф5a) — скрипти серверного backup (`pg_dump` + шифрування) і перевірки відновлення.
- `.github/workflows/` — перевірки, тестування, підпис і реліз APK; **опційний** scheduled heartbeat лише через захищену Edge Function (§8.6).

Модульність — логічна. Якщо Gradle-модулі заважатимуть швидкому Ф0, допустимо тимчасово об'єднати частину `core` у фізичний модуль зі збереженням меж пакетів та API.

### 3.2. Правила доступу до даних

- UI → ViewModel → Repository → DAO/Room; мережевий запис/читання здійснює тільки SyncEngine та явно виділені adapters.
- Усі локальні мутації, включно з віджетом, notification action, імпортом і відновленням, проходять через однакову валідацію. Винятки (bulk sync apply/restore) — окремі контрольовані транзакційні сценарії, без циклу локальних outbox-операцій.
- В одній транзакції: **якщо перша pending-операція над уже синхронізованою сутністю, спочатку зберегти immutable `ConfirmedSnapshot` зі стану ДО правки**; після цього змінити сутність/effective projection, збільшити локальну версію, записати `TaskActivity` (за потреби), додати типізовану `OutboxOperation`. Новостворена локальна сутність до першого серверного Ack має `base_revision = null` і явний стан `locally_created`, без фальшивого server snapshot. Після Ф5 підтверджений серверний snapshot та pending overlay зберігаються окремо: екран читає **effective state**, а не неперевірену серверну копію.
- **Inbound sync transaction:** застосувати server changes до confirmed state, зберегти новий `SyncCursor`, переобчислити effective state з pending операцій у їх порядку; rejected/conflicting patches перенести у recoverable drafts із журналом, ніколи не стирати їх мовчки.
- Починаючи з Ф1a, локальні `ExternalIdentity` і `LocalWorkspace` зберігаються разом із backup для майбутньої Ф5/Ф5.1. Імпортне source ID не замінюється на UUID новоствореної задачі.
- Планувальник нагадувань і updater віджетів спостерігають за змінами Room/Domain events; мережі напряму не викликають.
- Не виконувати руйнівну міграцію бази за замовчуванням (`fallbackToDestructiveMigration` — заборонити у production).

### 3.3. Ранній кросплатформний spike (Ф0, без повного UI)

Мінімальний «одна задача — три таргети» тест: Android, Chromium-based browser через `WebWorkerSQLiteDriver` та JVM desktop. Статус технологій із документа — **candidate**, не `tested`. Перевірити create/read/update після закриття й повторного відкриття, схему та міграції, транзакцію з outbox, FTS5, часові зони й роботу в CI. Для Web — Chromium і Firefox; Safari (за доступності підтримуваного WasmGC), private mode, quota/eviction, очищення сховища. Кожен сценарій має відтворюваний command + CI artifact. Без проходження spike Web не можна оголошувати сумісним.

#### 3.3.1. Матриця технічної сумісності та fallback (V25-STACK)

| Критерій | Android | Desktop/JVM | Web/Wasm | Gate Ф0 |
| --- | --- | --- | --- | --- |
| Kotlin/Compose/Gradle/KSP/Room 3 сумісні на одному version catalog | compile + runtime | compile + runtime | compile + runtime | Усі 3 build jobs зелені, pinned versions/lockfiles |
| Room/SQLiteDriver: DAO, FTS5, transaction, `OutboxOperation`, migration v1→v2 | persistent device DB | persistent file DB | worker/SQLite WASM + OPFS | Persisted CRUD, індекси й rollback без втрат |
| Shared parser/timezone/recurrence | golden commonTest | ті самі fixtures | ті самі fixtures | Однакові serialized DTO, civil dates і nextOccurrence |
| Supabase client module matrix (Auth, PostgREST/RPC, Realtime) | відповідні API | відповідні API | відповідні API | Перевірити кожний модуль окремо; unsupported модуль → platform adapter (HTTP/WS) без зміни domain API |
| Browser persistence та isolation | не застосовується | не застосовується | Chromium + Firefox, WasmGC, COOP/COEP, OPFS, private/quota/reopen | Запис зберігається після reload; failure modes документовані; без підтримки — fallback |
| Release/CI | APK та migrate/upgrade | runnable JVM artifact | статичний hosted build | Підпис, тест міграції, заголовки hosting перевірені |

**Версії:** Ф0 обирає і фіксує конкретні Kotlin, Compose Multiplatform, AGP, Gradle, KSP, Room3, SQLite WASM, Ktor, supabase-kt та JDK у `gradle/libs.versions.toml`/lockfile; рішення ґрунтується на перевірці, не на припущенні «latest». Compose/Wasm має beta/браузерні обмеження; Android/Desktop не блокуються через нестабільний Web. `supabase-kt` 3.x може мати wasm-js **лише для підтримуваних модулів**; відсутність Realtime або Auth для конкретного target не є підставою відключити sync — ізолюємо у платформному adapter.

**Fallback (заздалегідь погоджений, два рівні):** (1) якщо `WebWorkerSQLiteDriver + OPFS` не проходить стабільність/підтримку браузерів, спершу замінити **сховище під SQLite, а не шар даних**: worker для `WebWorkerSQLiteDriver` на SQLite-WASM зі сховищем поверх IndexedDB (наприклад, wa-sqlite з IndexedDB VFS; сумісність із протоколом worker перевірити у Ф0). Room-схема, SQL, FTS5, міграції, Repository і SyncEngine лишаються без змін. (2) Лише якщо й це не працює — браузерний `LocalStore` на IndexedDB (versioned schema + atomic IDB transactions) за тим самим Repository/SyncEngine контрактом. В обох варіантах **offline-first і pending outbox зберігаються**; кожен fallback проходить ті самі тести, самовільне переключення на online-only web заборонене. Якщо жоден рівень не проходить, Web позначити blocked у `ADR-STACK-001`, не випускати його та не зупиняти Android. Web worker, ізоляція, browser storage можуть вимагати власного хостингу/headers. Runtime evidence у `docs/TECH_COMPATIBILITY.md` + CI reports: target × versions × verdict × failure/fallback × tested browser/device.

**Ф0 go/no-go (єдиний нормативний статусний контракт, FIX-06):** `pass` = збірка + runtime persistence + CI/log evidence; `blocked` = PoC не пройшов, є ADR/owner/план повторної перевірки; `not_tested` не дозволяє завершити Ф0 для відповідного target. **Android тільки `pass`; Web/Desktop `pass` або документований `blocked`, без заяви про runtime compatibility.** (a) Android APK із Room/DAO та безпечними міграціями — обов’язково; (b) Desktop JVM — compile+runtime persistence або документований блокер Ф8; (c) Web — успішний Room/OPFS spike, **або** Room на worker із SQLite-WASM поверх IndexedDB, **або** (лише якщо обидва не вдалися) PoC `LocalStore` на IndexedDB; якщо жоден не проходить — `Web = blocked`, відкрити ADR до початку Ф7. Не називати результат «повністю перевірено» до публікації тестових артефактів; повна browser matrix завершується у Ф7.


Окремо для Web у межах spike:

1. Зібрати власний worker для `WebWorkerSQLiteDriver` на основі прикладу з androidx (SQLite WASM + OPFS): драйвер не постачається з готовим worker.
2. З'ясувати, чи обраний варіант OPFS вимагає cross-origin isolation (заголовки COOP/COEP). Частина проєктів на Room 3 + SQLite-WASM з OPFS повідомляє, що так.
3. Відповідно обрати хостинг для Ф7, що дозволяє власні HTTP-заголовки: Cloudflare Pages, Netlify, Firebase Hosting або власна VM. GitHub Pages таких заголовків не ставить.
4. Врахувати, що на Web Room працює з єдиним з'єднанням до бази.

### 3.4. Розповсюдження APK і ключ підпису (`P0`)

- **Ключ підпису.** Release keystore створюється у Ф0 й одразу копіюється у два незалежні місця (наприклад, менеджер паролів і зашифрований офлайн-носій) разом із паролями та alias. Без цього ключа нову версію неможливо встановити поверх старої, а видалення застосунку знищує локальні дані. Ключ ніколи не потрапляє в git; у CI — лише як захищений secret для збірок із захищеної гілки або тегу.
- **Канал оновлень.** Підписаний release APK публікується в GitHub Releases за тегом `vX.Y.Z`. На телефоні — Obtainium, який відстежує релізи (для приватного репозиторію потрібен read-only токен — перевірити у Ф0), або вбудована перевірка нової версії з посиланням на завантаження. Вибір фіксується ADR у Ф0.
- **Версії.** `versionCode` монотонно зростає й виводиться з тегу або номера збірки CI; `versionName` = тег. Debug-збірки мають окремий суфікс `applicationId`, щоб не конфліктувати з робочою установкою.
- **Захист міграцій SQLite/Room (FIX-01, P0).** Перед upgrade схеми створити **консистентний** snapshot усієї БД. Просте `copy(main.db)` при активному SQLite WAL **заборонене**: потрібен підтримуваний SQLite online-backup механізм/`VACUUM INTO` **лише якщо він доступний на конкретному runtime**, або контрольована холодна копія після зупинки усіх writer/reader, коректного checkpoint/close та обліку `-wal`/`-shm`. Для Android minSdk 26 **не припускати** доступності `VACUUM INTO` чи Backup API — перевірити під час Ф0 та визначити platform adapter. Рівно одна операція upgrade одночасно; під час snapshot/restore жодна інша точка входу (WorkManager, widget, інший процес) не пише в Room. Backup у тимчасовій директорії/файлі, з атомарною публікацією, checksum, тестовим відкриттям, `PRAGMA integrity_check` і порівнянням кількості/ключових FK; якщо валідація не пройшла — **не починати** руйнівну міграцію. Після збою відновлювати **цілісний узгоджений набір файлів/стан БД**, не відкривати частково перенесену БД як нормальну. Перед upgrade перевірити достатній обсяг сховища; за браку місця зупинитися без втрат. Зберігати перевірений pre-upgrade snapshot до успішного відкриття нової схеми та контрольного semantic verification. На Web/Desktop механізм snapshot адаптується до відповідного storage API й проходить аналогічні тести.
- **Перевірка.** Оновлення з попередньої релізної версії поверх, без втрати даних, — пункт DoD кожної фази, що змінює схему БД. Gate: записи у WAL до upgrade, crash під час snapshot/міграції, нестача місця, невдала валідація й відновлення із контрольного snapshot; `integrity_check` та semantic equality зелені.

---

## 4. Модель даних

**ID:** UUID генерується клієнтом. Синхронізовані записи мають `id`, `user_id` на сервері, `created_at`, `updated_at`, `deleted_at`, `revision` (серверна версія); клієнтський timestamp не є авторитетом порядку конфліктних операцій. Локальна база має `account_scope`: до Ф5 це `local:<workspace_uuid>`, після контрольованого claim — ідентифікатор зв'язаного акаунта. `LocalWorkspace.workspace_id` і `ExternalIdentity` **не змінюються** від переходу на хмару; системний Inbox **один на власника**, при злитті зв'язки переназначаються транзакційно.

**Часова модель:** `due_date` = `LocalDate`; `due_time` = `LocalTime` або null; `due_tz` = IANA timezone або null (floating local time). Абсолютні моменти (`completed_at`, `created_at`, `remind_at`) — UTC instant. Ніякої прихованої конвертації календарної дати у UTC-північ. Нижче — нормативний контракт (не TODO) для всіх платформ, імпортера, віджетів та сповіщень.

### 4.0.1. Нормативний контракт часу й повторів (V25-TIME)

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



### 4.1. Task

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

### 4.2. Інші сутності

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

### 4.3. Поведінка складних переходів

- **Виконання неповторюваної задачі:** ставимо completion, у `TaskActivity` записуємо подію, зберігаємо Undo. Повторне натискання не створює другий completion.
- **Виконання повторюваної:** атомарно фіксуємо `TaskCompletion` для конкретного occurrence; визначаємо наступний occurrence з RRULE; старий залишається в історії.
- **Скасувати виконання:** не стираємо історію; ставимо `undone_at` і подію. Якщо наступної активної появи ще немає і правило дозволяє — повертаємо попередню як `active`. Якщо наступна **вже існує**, звичайний Undo коригує **лише історичне виконання**, зберігаючи поточну active та інваріант 0..1; явна дія «перепланувати» створює окрему операцію. Якщо після старого виконання є нові completion, підтверджуємо вплив на історію, але не відкочуємо часову послідовність автоматично.
- **Пропустити occurrence / `skip_to_next`:** фіксуємо `skipped` для поточної появи й обчислюємо наступну за recurrence policy; **без** `TaskCompletion`. Якщо це остання допустима поява, `recurrence_end_reason=exhausted_skipped`, задача залишається відкритою без дати й видимою у добірці «Розклад закінчився».
- **Ручне перенесення `reschedule_current(new_date_time)`:** зберігаємо `override` активної `TaskOccurrence`, оновлюємо кеш `Task.due_*`; основне RRULE не змінюємо. **`edit_recurrence_rule(new_rrule)`** — інша команда, що змінює всю серію та перераховує активну появу. `skip_to_next` не дорівнює ручному перенесенню. Для фіксованого/абсолютного правила наступні появи прив’язані до розкладу (з відсіканням уже минулих дат); для відносного інтервалу `every N days/weeks` — до актуальної бази перенесення/виконання відповідно до явно заданої політики. `recur_from_completion` є окремим completion-based режимом. Кожен розрахунок next має детерміновані `clock`, timezone, reference/anchor і політику missed occurrences.
- **Завершити повтор:** явно `stop` переводить active у `stopped` і ставить `manually_stopped`; вичерпання `COUNT`/`UNTIL` після Complete → `exhausted_completed`, після Skip → `exhausted_skipped`. Усі три результати мають 0 active, але різні статуси задачі й семантику історії.
- **Батьківська задача:** виконання батька не виконує підзадачі автоматично. Для повторюваного батька — дві явні дії (як у Todoist): «Виконати» — наступна поява батька, підзадачі лишаються як є; «Виконати й скинути підзадачі» — виконані дочірні задачі повертаються в невиконаний стан для наступної появи однією груповою операцією з Undo. Незавершені підзадачі не втрачаються в жодному варіанті; типову дію можна налаштувати.
- **Move між проєктами:** валідність `section_id`, належність підзадач і порядок оновлюються в одній транзакції.
- **Delete:** видалення батька/проєкту вимагає явного підтвердження наслідків; переміщення в Trash має бути зворотним, включно з необхідними пов'язаними сутностями.

**Таблиця переходів occurrence (контракт Ф2):**

| Початковий стан | Команда | Результат і правило |
| --- | --- | --- |
| `active` | `complete` | Стара поява → `completed` + 1 `TaskCompletion`; створити наступну `active` лише якщо правило ще діє; якщо правило вичерпано — `Task.completed_at` = момент виконання, `recurrence_end_reason=exhausted_completed` |
| `active` (батько з підзадачами) | `complete_and_reset_subtasks` | Як `complete`, плюс виконані підзадачі → невиконані в тій самій групі операцій; один Undo |
| `active` | `skip_to_next` | Стара → `skipped` без completion; наступна `active` за RRULE, інакше `exhausted_skipped`, задача відкрита без due |
| `active` | `reschedule_current(new_due)` | Та ж `active`, лише `override/effective_due` змінено; базове правило збережено |
| `active` | `edit_recurrence_rule(new_rule)` | Нове правило/якір; обчислити одну коректну active, історичні появи не переписувати |
| `active` | `stop` | `stopped`; наступна не створюється; due-cache очищено; задача лишається відкритою без дати |
| `completed` (немає наступної active) | `undo` | `TaskCompletion.undone_at`; без дубля, можливе повернення старої active, якщо правило дозволяє; якщо задачу було виконано через вичерпане правило — `Task.completed_at` знімається |
| `completed` (наступна active існує) | `undo` | Історичне `undone_at`, поточна active не змінюється; необхідне нове планування — окрема команда |
| будь-який | повторна команда з тим самим `operation_id` | Без повторного ефекту; сервер/локальна БД гарантують ідемпотентність |

**Правило конкурентності:** `complete`, `skip_to_next`, `reschedule_current` для однієї появи перевіряють її `occurrence_id` і стан. Сервер відхиляє неможливий перехід із recoverable conflict, а не мовчки генерує другий occurrence. Переходи завершення повтору, редагування правила та Undo фіксуються property-based тестами. До Ф2 імпортний `needs_review` **не** означає, що завдання назавжди втратило повтор; зберігати raw source і показувати попередження при виконанні.
**Політика імпортних повторів:** у Ф1a повторювані задачі з Todoist **не імпортуються, як і всі їхні транзитивні дочірні задачі, навіть якщо діти не мають власного повтору**. Увесь залежний підграф лишається в Todoist до Ф2, а звіт містить `root_recurring_source_id`, усі `source_id`/`parent_source_id`, кількість коренів і кількість відкладених нащадків. Зберігати `DeferredTodoistNode` в backup як метадані, але **не** створювати orphaned `Task` чи фальшивий `parent_id`. Коли батько є звичайним і вже імпортованим, його звичайні діти не відкладаються лише через наявність інших повторюваних родичів. У Ф2 скрипт доімпортовує **повторювані корені з усіма відкладеними нащадками** append-only режимом `--recurring-only` (§9.0): починає з батьків у topological order, перевіряє наявні parent/project/section source IDs, атомарно зв'язує UUID (для нащадків без власного повтору — звичайний Task), усуває дублікати через `ExternalIdentity`; розпізнане парсером стає повтором із `TaskOccurrence`, нерозпізнане отримує `ImportPending` у стані `needs_review`. Для таких задач звичайний `Complete` блокується з поясненням «Потрібно перевірити повтор» і кнопками «Редагувати правило» та «Підтвердити як одноразову» (остання — лише з явним підтвердженням; raw-правило лишається в історії, source identity зберігається). Ніякого мовчазного завершення.


---

## 5. Функціональний скоуп по модулях

### 5.1. Задачі — Ф1a/Ф1b (`P0`)

- CRUD з Undo (створення, редагування, видалення, відновлення).
- Підзадачі 4 рівнів, пріоритети p1–p4, перенесення між проєктами/секціями, drag-and-drop порядок.
- Due date + due time + timezone окремо від deadline; візуальний індикатор залишку до дедлайну.
- Множинний вибір: пріоритет, due, deadline, проєкт, секція, мітки, виконати, видалити, відновити; показати попередження про обсяг операції.
- Редагування описів, деталей, duration; виконане можна переглянути/повернути.
- Починаючи з Ф1b, `http/https` URL у content/description — клікабельні лише після безпечної нормалізації, зі зрозумілим підтвердженням переходу за зовнішнім посиланням, без виконання HTML/JavaScript; протоколи `javascript:`, `file:`, `intent:` не активуються автоматично. Порожній або небезпечний URL лишається текстом.
- Пошук і доступність з клавіатури — мінімальний функціонал у Ф1b, розширення у Ф6a.

### 5.2. Проєкти, секції, мітки — Ф1a/Ф1b (`P0`)

- Вкладені проєкти, кольори, обране, архів, системний Inbox (не перейменовується і не видаляється).
- Секції з reorder/collapse; однорядкове прев'ю опису, повний опис по натисканню, редагування на місці.
- Мітки з кольорами, пошук, злиття дублікатів і коректне оновлення посилань.
- Видалення/архівування проєкту із зрозумілим вибором долі задач.

### 5.3. Відображення — Ф1a, Ф1b та Ф6b (`P1`)

- Inbox, Today, Upcoming, Project, Label, Filter (деталі в §6).
- List у Ф1a (Inbox/Today/Upcoming) і Ф1b (Project/Label), Board і Calendar у Ф6b. Сортування, групування, показ завершених, персональні налаштування кожного виду.

### 5.4. Quick Add — базовий у Ф1a, повний у Ф2 (`P0` для ефективного MVP)

- Текстова форма з підсвічуванням, відображенням розпізнаних полів чипами та можливістю їх відредагувати перед збереженням.
- Токени: `#проєкт`, `/секція`, `%мітка`, `p1`–`p4`, `{дедлайн}`, `!нагадування`, тривалість, дата, час, повтор; `* ` на початку рядка — невиконувана задача (`is_completable = false`).
- `@мітка` — опція в налаштуваннях для звички з Todoist, типово вимкнена (сам Todoist прибирає `@` для міток до кінця 2026 року). Розпізнається лише після пробілу й ніколи всередині email чи нікнейма.
- **Граматика `!`:**
    - `!14:00`, `!9` — нагадування в цей час дня due-дати; без дати — сьогодні або завтра, якщо час уже минув (видно в прев'ю);
    - `!30хв до`, `!1год до`, `!30m before`, `!30 min przed` — відносно due date+time; якщо в задачі немає часу — помилка з підказкою, а не мовчазне планування;
    - `!через 2год`, `!in 2h`, `!za 2 godz` — від моменту створення;
    - `!завтра`, `!tomorrow`, `!jutro` — завтра о налаштованій ранковій годині;
    - голе `!30хв` двозначне — прев'ю пропонує «за 30 хв до терміну» або «через 30 хв»;
    - повторювані нагадування: базова граматика — Ф3 (`!щодня 9:00`, `!щосуботи 9:00`, `!every sat 9am`, `!co sobotę 9:00`) → `Reminder.repeat_rule`, діє до виконання задачі; розширена (інтервали, кінцеві дати) — Ф9.
- **Тривалість:** число + одиниця після `на`/`for`/`na` — `на 30хв`, `на 1,5 год`, `for 45m`, `na 30 min` → `duration_min`. Без числа (`на завтра`) це не тривалість.
- **Опис через `//` (опція Ф2):** `Купити фільтр завтра #Дім // Перевірити модель` → `content` + `description`. Роздільник лише `пробіл + // + пробіл` поза URL, email, quoted/escaped фрагментом; `https://` не перетворюється на опис. Підсвітка/preview; відміна розпізнавання одним тапом.
- **Багаторядкове створення (Ф2):** при вставленні тексту з `\n` запропонувати «Одна задача з кількома рядками» або «Окрема задача з кожного рядка»; dry-run preview з кількістю, розпізнаними полями, помилками; commit як контрольована група операцій з окремим `operation_id` на задачу та Undo. **Не** створювати N задач автоматично без вибору користувача.
- **Обсяг Ф1a:** основні дати й час трьома мовами, `#проєкт`, `p1`–`p4`. Решта токенів, повтори, `//`, batch create й неоднозначності — Ф2.
- Українська, англійська, польська: сьогодні, завтра, післязавтра, у пн, 15.11, 15 листопада, о 9, о 18:30, через 3 дні, наступного тижня, every Monday, co poniedziałek тощо.
- Неоднозначні `03.04`, `9`, час без дати, дати минулого року, відмінки й мішаний текст — парсер або явно обирає правило з прев'ю, або пропонує уточнення; **не змінює текст мовчки**.
- Введені фрази, які помилково розпізналися як дата, можна повернути в назву одним тапом.
- **Налаштування Quick Add (Ф2, не чекати загального Settings Ф6c):** увімкнути/вимкнути smart date recognition (у вимкненому режимі ввід дат лишається звичайним текстом, окремий date picker працює), конфігурований набір і порядок кнопок/чипів, проєкт за замовчуванням (`Inbox` або обраний), опційно остання мітка; поточний контекст Project/Widget/Share має пріоритет над глобальним default. Перевірити, що зміни опцій не переписують текст, введений у відкритому редакторі.
- **Share Target контракт (Ф2):** `ACTION_SEND` для `text/plain` та URL; `ACTION_SEND_MULTIPLE` — множина текстів/URL з preview кількості і вибором «одна задача/кілька задач», без автоматичного створення. Читати лише дозволений текстовий payload, санітизувати title/URI, не виконувати вкладені посилання/скрипти, не вимагати обов'язкового доступу до файлів/вкладень (вони Ф9). Зберігати canonical `source_url` та текстовий контекст; duplicate share не створює N задач без підтвердження.
- Точки входу: `+`, widget, App Shortcuts, Quick Settings tile, Share target; для desktop у Ф8 — global hotkey.
- Приклад: `Оплатити рахунок завтра о 10 #Фінанси p1 !30хв до {15.11} на 15хв` → назва + due + project + priority + reminder + deadline + duration.

### 5.5. Повтори — Ф2 (`P0`)

- RRULE subset: `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`, `INTERVAL`, `BYDAY`, `BYMONTHDAY`, `BYSETPOS`, `UNTIL`, `COUNT`.
- Режим `fixed_schedule` і `completion_interval` **повністю визначені контрактом §4.0.1**: first occurrence / DTSTART, локальні чи прив’язані до зони дати, DST gap/overlap, `COUNT/UNTIL`, late completion, `skip`, change of zone і pure nextOccurrence. Невизначеність «уточнити правила першої появи та timezone» закрита в v2.5.
- Робочі дні, останній день місяця, кожен N-й день тижня, every other week, 31-е число місяця, leap day.
- **Початок і кінець повтору:** `починаючи з 3 січня` / `starting Jan 3` / `od 3 stycznia` задає першу появу (DTSTART); `до 31.12` / `until` / `ending` / `do 31.12` — кінцева дата, **включна** (як у Todoist). Фрази комбінуються: `кожні 2 тижні починаючи з 3 січня до 30 червня`.
- Розмежувати **Complete / Skip / Postpone occurrence / Stop recurrence**.
- `TaskOccurrence` і `TaskCompletion` з Ф2 (таблиці можна створити в схемі раніше, але логіки повторів у Ф1a немає); історія не губиться після кожного виконання.
- **Ф1a без повторів:** у Ф1a немає recurrence engine; повторювані задачі живуть у Todoist до Ф2 (§4.3, §9.0).
- **Три окремі дії:** `skip_to_next` (фіксує пропуск), `reschedule_current` (редагує лише одну активну появу), `edit_recurrence_rule` (змінює серію). Для `fixed_schedule` зберігаємо DTSTART і пропускаємо минулі календарні кандидати за §4.0.1; для `completion_interval` базою стає UTC completion плюс зафіксована `completion_zone_id`. Не використовувати неформальне «relative/intervallike» як третій прихований режим. Golden tests у §12.
- **Останній Skip:** не створює completion, закриває розклад `exhausted_skipped` й залишає невиконану задачу без дати з дією «Відновити повтор».
- **Майбутні появи для календаря:** `RecurrenceProjectionService(task, from, to, timezone)` обчислює **віртуальні**, не persisted майбутні появи за RRULE та active override для видимого діапазону. Стабільний presentation key = `(task_id, scheduled_local_date/time, projection_version)`; вони **не** створюють `TaskOccurrence`, outbox чи десятки alarms. Запланована дія над віртуальною появою матеріалізується контрольованою доменною командою з перевіркою правила.
- Тестувати переходи DST, UTC/local, подорожі й offline виконання з двох пристроїв, завершення `COUNT/UNTIL` (0 active), Undo старої появи після створення нової, ідентичність віртуальних projections.

### 5.6. Нагадування — базові у Ф1a, повні у Ф3 (`P0`)

Детальна платформа поведінки — §7. Підтримати абсолютні/відносні/від дедлайну, декілька нагадувань на задачу, snooze без переносу due, actions Complete/1h/Tomorrow. Якщо час задачі не вказаний, відносне нагадування до `due_time` не планувати мовчки; дедлайнове нагадування використовує конфігурований час дня.

**Авто-нагадування:** задача з часом отримує нагадування автоматично — типово в момент `due_time`, зсув налаштовується, функцію можна вимкнути. Такі нагадування мають `is_auto = true` і перераховуються при зміні налаштування або часу задачі; додані вручну нагадування не зачіпаються. У Ф1a — одне авто-нагадування на задачу, у Ф3 — повний набір.

**Окремий повтор нагадування з Ф3:** `Reminder.repeat_rule` для «Нагадуй щодня о 09:00, поки задачу не виконано» незалежний від `Task.rrule`. Одне завдання може мати recurring reminder і не бути повторюваною задачею. **Обов’язковий `ReminderLifecyclePolicy`:** `one_shot` (один раз і припинити), `occurrence_bound` (до активної `occurrence_id`, при complete/skip/postpone/repeat перерахувати або припинити для старої появи) і `task_until_completed` (незалежний регулярний повтор доки задача як ціле не завершена). Для повторюваної задачі «нагадувати на кожну появу» — `occurrence_bound` з новим planned delivery для наступної active; **кастомне одноразове нагадування не переноситься автоматично** без явної опції. Snooze/delivery — локальні й прив’язані до `reminder_id + occurrence_id? + planned_at + device_id`; Complete/Skip поточної появи скасовує старий alarm, нова поява може створити новий; `stop recurrence` потребує явної поведінки, видимої в UI; остаточне completion задачі зупиняє `task_until_completed` якщо `stop_on_task_complete=true`. Наступний alarm планується ліниво; reboot/sync не дублює delivery.

### 5.7. Фільтри, пошук — Ф6a (`P1`)

- Конструктор: проєкти, мітки, пріоритети, due, deadline, без дати, прострочені, повторювані, із часом, статус, дата створення, підзадачі.
- `AND`, `OR`, `NOT`, групування умов дужками; внутрішній типізований AST як єдине джерело правди для UI, widgets і текстових фільтрів.
- Початкова мова запитів у Ф6a: `today & #Робота | p1`. Базові ключові слова сумісні з Todoist (`today`, `overdue`, `date`, `due`, `deadline`, `no date`, `no deadline`, `#проєкт`, `%`/`@мітка`, `p1`–`p4`, кома для груп), щоб імпортовані з Todoist фільтри працювали без переписування; повне покриття та автодоповнення — Ф9.
- FTS5 по назвах та описах; індексація змін, нормалізація Unicode, префіксний пошук, перевірка uk/pl/en.
- **Семантика дат (обов'язковий контракт, сумісний із Todoist):** `date` — лише запланована дата (`Task.due_date` / effective due активної появи); `deadline` — лише дедлайн; `due` — запланована дата, а якщо її немає — дедлайн, як у Todoist. У конструкторі ці три варіанти підписані явно, тож змішування не буває непомітним. `overdue` — невиконані із запланованою датою в минулому; прострочений дедлайн — окрема умова (`deadline before: today`). `no date` і `no deadline` — окремі умови.
- **Multi-group Smart Views (Ф6a):** одне збережене представлення містить впорядковані `FilterGroup[]` з назвами й незалежними AST, наприклад «Прострочені» / «P1 сьогодні» / «Без дати», із явно визначеною поведінкою дублів (показати один раз у першій групі або дозволити повтор за налаштуванням). Передбачити пресети «Без дати», «Цей тиждень», «Прострочені», «Майбутні дедлайни». У текстовій мові запитів кома розділяє групи, як у Todoist: `overdue, p1 & today, no date` → три групи; текст і конструктор дають той самий AST.
- Фільтр може бути джерелом віджета.
- **Двоступеневий імпорт фільтрів:** `FilterImportPending` із raw query/metadata створюється у Ф5.1 без виконання неіснуючого Filter AST. Ф6a версійно конвертує raw query в AST, показує preview/`needs_review` і тільки після успішного парсингу активує Smart View. Непідтримані фільтри залишаються видимими в «Потребує перевірки», не перетворюються мовчки на пустий/ширший фільтр.

### 5.8. Коментарі, історія, шаблони, кошик — Ф1a/Ф6a/Ф6c (`P1`)

- Шаблони задач/підзадач і проєктів із відносними датами — Ф6a.
- `TaskActivity` починає записуватися у Ф1a; повний екран історії — Ф6c. Типи: створення, перейменування, due/deadline, project, priority, completion/undo, delete/restore, `sync_conflict_overwritten` (перезаписане значення з дією «Повернути моє»). Для conflict із видаленою сутністю використовується окремий `ConflictRecovery`, щоб recovery не залежало від FK на живу Task.
- Текстові коментарі до задач у Ф6c.
- **Ф6c — форматування описів і коментарів:** зберігати Markdown-source, рендерити лише підтримувану підмножину (жирний/курсив, списки, посилання, inline code), санітизувати URL/HTML; без вимоги повного WYSIWYG. Дозволити plain-text editing і copy/export без втрати оригінальних символів; контент із Todoist невідомого формату зберігати як raw та позначати невідтворені елементи у звіті імпорту.
- Trash із попереднім переглядом і Restore; purge лише коли узгоджено безпечність для офлайн-пристроїв.

### 5.9. Todoist і резервні копії — Ф1a/Ф5a/Ф5.1/Ф6c (`P0/P1`)

- У Ф1a локальний JSON export/import зі schema version/checksum та restore на чистому пристрої, плюс одноразовий імпорт із Todoist локальним скриптом (§9.0).
- З Ф5a — щотижневий зашифрований серверний backup поза Supabase (§10.3).
- У Ф5.1 перехідний **тільки Todoist → застосунок** імпорт: проєкти, секції, мітки, активні задачі, дедлайни, описи, вкладені задачі, повтори, дати; додаткові поля — лише за підтвердженням фактичної підтримки API.
- У Ф6c — шифровані й автоматичні версіоновані backup за бажанням, restore з керуванням існуючим акаунтом.

### 5.10. Налаштування — Ф6c (`P1`; окремі пункти з'являються раніше разом зі своїми функціями)

- Стартовий екран, тема, перший день тижня (типово понеділок), глибина Upcoming, timezone/display, формат часу.
- Авто-нагадування для задач із часом (зсув або вимкнено); година дня для дедлайнових і `!завтра` нагадувань; exact alarms стан/підказка, snooze варіанти, privacy notification preview.
- Розпізнавання `@` для міток у Quick Add (типово вимкнено).
- Налаштування Quick Add (`smart_date_recognition`, набір/порядок action buttons, default project, optional last-label) доступні **вже у Ф2**; у Ф6c лише об'єднуються в загальний екран Settings без зміни семантики.
- Мова uk/en/pl, перемикач у налаштуваннях; системна мова за замовчуванням.
- Екран Sync Health: остання успішна синхронізація, активні пристрої, pending operations, конфлікти, помилки імпорту, backup status.
- Ф9: опційний PIN/біометрія, voice capture, AI, location reminders, календарні інтеграції, ранкове зведення.

---

## 6. Відображення, календар і віджети

Один доменний query engine `getTasks(source, options)` живить екрани та віджети й читає effective local projection. Результат залежить від локального стану БД, дати/часового поясу та `ViewSettings`. Не дублюємо логіку Today або Filter у Glance; один AST/одна семантика due, deadline, Smart Views у всіх клієнтах.

| Джерело | Правило | Типове групування |
| --- | --- | --- |
| Inbox | Невиконані задачі проєкту Inbox | Немає |
| Today | Невиконані із `due_date ≤ today` | Прострочені / Сьогодні |
| Upcoming | Майбутні задачі та обраний період | За днями |
| Project | Задачі проєкту, вкладені проєкти — опційно | За секціями |
| Label | Задачі з вибраною міткою | За проєктами |
| Filter / Smart View | Одна або декілька іменованих груп AST; порядок і dedup policy | Налаштовуване |

**ViewSettings:** `layout` = list (Ф1a), board або calendar (Ф6b); `group_by` = дата/пріоритет/проєкт/мітка/без; `sort_by` = вручну/дата/пріоритет/назва; `show_completed`; `show_deadlines`; `include_subprojects`; `show_future_occurrences` (типово false).

### 6.1. Calendar (Ф6b)

- Вид дня, тижня та місяця; відображення дедлайнів окремо від due date.
- Drag-and-drop задач по даті/часу; тривалість `duration_min` для time blocks.
- **Створення безпосередньо зі слота (Ф6b):** long-press/tap-and-hold на порожньому часовому слоті відкриває Quick Add з prefilled `due_date`, `due_time`, `duration_min` (за grid step/настройкою), з можливістю редагування та Cancel; створення лише після Save, Undo створеної задачі. На all-day області дата без фіктивного часу; на малому екрані доступна альтернативна кнопка для TalkBack без long-press.
- Візуальний конфлікт/перекриття інтервалів і підрахунок запланованих хвилин на день.
- **Unscheduled tray:** окрема панель задач без дати; drag з панелі на день/час встановлює due date/time, з підтримкою Undo, без зміни deadline.
- **Портретний Android:** адаптивний 3-day view поруч із day/week/month; інтервали та touch-targets працюють на малому екрані.
- **Future projections:** перемикач «Майбутні появи» у налаштуваннях вигляду, типово вимкнений (як у Todoist): без нього календар показує лише наступну появу. Увімкнений — `RecurrenceProjectionService` показує всі майбутні появи повторів у поточному вікні календаря без фізичних future-occurrence records; перегляд і перехід до задачі працюють, drag майбутньої віртуальної появи вимагає контрольованої команди/override.
- **Duration контракт Ф6b:** 5–1 440 хвилин у першому релізі (поза межами — валідація); блоки, що переходять через опівніч, малюються у двох днях, timezone/DST трактуються явно. Для задач без часу duration не створює вигаданий midnight start.
- **Колір (P2):** за проєктом, пріоритетом або міткою; не блокує Ф6b.
- Базове календарне відображення без сторонніх інтеграцій та без обов'язкової прив'язки до Google Calendar.
- Спеціальне правило для all-day (не створювати штучну 00:00 подію) та timezone.

### 6.2. Glance widget (Ф4)

- Ресайз, кілька екземплярів, незалежний `widget_id → view_key`.
- Джерело: стандартні Inbox/Today/Upcoming, Projects, Labels; Filters стають доступні після Ф6a без зміни контракту віджета.
- Checkbox Complete без відкриття застосунку; task tap → деталі; title tap → view; `+` → Quick Add з контекстом view/project.
- Оновлення після локальної зміни (debounce), після sync apply, опівночі, при зміні timezone та конфігурації.
- Під час locked/неавторизованої сесії — поважати privacy settings, не відображати дані іншого акаунта.
- **Налаштування вигляду з Ф4:** тема/колір, прозорість фону, масштаб тексту, compact mode, максимум видимих задач, показ due/priority; прев'ю перед збереженням. Доступні значення обмежити платформними можливостями Glance і перевірити на підтримуваних лаунчерах.
- **Швидке перемикання джерела:** заголовок віджета відкриває picker або налаштовану дію `quick_switch`, не змінює конфігурації іншого widget instance.
- Компактний віджет Quick Add — опційно після основного списку.

---

## 7. Android-нагадування і сповіщення

Нагадування плануються **локально**, сервер потрібен лише для синхронізації визначення нагадування між пристроями. `ReminderDelivery` за замовчуванням локальний: snooze на телефоні A не переносить due date і не скасовує сповіщення на телефоні B.

### 7.1. Типи та UX

- Absolute (`remind_at`), relative (`offset_min` від due date+time), deadline-relative (N днів до deadline о заданій годині), auto (relative зі зсувом із налаштувань, `is_auto = true`).
- Більше одного активного нагадування на задачу; підтримка окремого `repeat_rule` для повторного нагадування **до виконання** задачі, не тотожного повторюваній задачі (Ф3).
- `Complete`: через Repository виконує задачу / occurrence.
- `Snooze 1h`: змінює лише локальну доставку нагадування, не `due_date`, не `deadline`.
- **Lifecycle transitions:** `complete occurrence`, `skip occurrence`, `reschedule occurrence`, `stop recurrence`, `complete task`, `undo` та `delete/restore` повторно обчислюють право доставки згідно з `ReminderLifecyclePolicy`; старі alarms скасовуються за стабільним ключем. `Snooze` старої вже виконаної появи не воскресає після `undo` без явного нового планування.
- `Tomorrow`: **окрема дія перенесення задачі** із чітким текстом у UI; не плутати зі snooze.
- При видаленні/редагуванні задачі старі alarm request codes скасовуються, нові детерміновано створюються; для recurring reminder наступна доставка планується після firing/dismissal відповідно до його `repeat_rule`, без дублювання після reboot/Doze.

### 7.2. Дозволи й планування

- Android 13+: запитувати `POST_NOTIFICATIONS` з поясненням.
- Для функцій, що справді потребують точного часу, використовувати `SCHEDULE_EXACT_ALARM` (Android 12+), перевіряти `canScheduleExactAlarms()` перед `setExactAndAllowWhileIdle()` та обробляти `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
- **Не обирати `USE_EXACT_ALARM` автоматично лише через sideload APK:** цей дозвіл має обмежені випадки використання. Розглядати його лише після окремої перевірки відповідності призначенню застосунку.
- Якщо exact permission відсутній, використовувати допустимий inexact alarm/`setAndAllowWhileIdle()` із зрозумілим попередженням про можливу затримку. Не обіцяти секундної точності.
- Ререєструвати при `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, зміні дати/часу/timezone, зміні дозволу, sync apply.
- WorkManager — для некритичної фонової роботи, не заміна precise user reminder.
- Підказка щодо Samsung/інших battery restrictions — лише contextual, без агресивного прохання вимкнути всі оптимізації.
- Ранкове зведення «на сьогодні N задач» — опційно Ф9.

### 7.3. Тестова матриця

Android 8/12/13/14+ (доступні пристрої/емулятори); exact grant/deny/revoke; Doze; reboot; App Update; timezone/DST; планування повтору; дворазовий tap Complete; notification permission denied; Samsung battery optimization; відновлення після kill. У тестовому звіті відокремити точну доставку від fallback-доставки та зафіксувати реально спостережені затримки.

---

## 8. Синхронізація між пристроями (Ф5a–Ф5b)

Офлайн-клієнт пише в Room і `OutboxOperation` транзакційно. Supabase Postgres — арбітр підтвердженого стану, джерело монотонних версій і довготривалого журналу змін. Realtime лише запускає перевірку, **не** є гарантованою доставкою.

### 8.1. Основний протокол

1. **Підготовка:** клієнт читає `device_id`, `sync_epoch`, останній `change_seq`, локальний outbox; перевіряє сесію Auth. Offline — жодної блокування UI.
2. **Handshake:** сервер перевіряє account scope, epoch, допустимість курсора, client protocol version. Застарілому клієнту повертає `FULL_RECONCILIATION_REQUIRED`, а не приймає blind push.
3. **Push:** відправити bounded batch із `operation_id`, `device_id`, `entity_id`, `base_revision`, `field_patch`/typed operation. Одна server RPC транзакційно перевіряє RLS, idempotency, FK і валідність переходу; фіксує зміни в canonical rows та append-only `ServerChangeLog`.
4. **Ack:** сервер повертає для кожної операції `applied` / `already_applied` / `conflict` / `validation_error` / `retryable_error` з revision; клієнт стійко фіксує Ack, результат/receipt та, де потрібно, server revision **до** очищення outbox; успішна операція не повинна «відкатити» effective state, доки її server change не застосована.
5. **Pull + Rebase:** сторінками читати `ServerChangeLog` строго після `last_applied_change_seq` в порядку `change_seq`; у **кожній локальній транзакції** застосувати changes до `ConfirmedSnapshot`, оновити cursor, відтворити ще не Ack-нуті `OutboxOperation` як effective projection. Конфліктні локальні патчі — у `ConflictRecovery`, з recovery action; ніякого blind overwrite Room-рядка. Після crash повторне Pull/Replay ідемпотентне.
6. **Post-sync:** перепланувати нагадування, оновити віджети, індекси/фільтри, відобразити статус.
7. **Повторні спроби:** exponential backoff + jitter, обмеження мережевих пакетів, збереження pending outbox між перезапусками.

**Серверний `change_seq` (контракт v2.2):** усі **runtime canonical writes** одного акаунта (клієнтський Sync RPC, Edge Function імпортера, bulk restore, server scripts) проходять через єдиний контрольований транзакційний mutation gateway із `pg_advisory_xact_lock` за акаунтом, лічильником `account_seq` і записом до `ServerChangeLog` + operation receipt. Прямі `INSERT/UPDATE/DELETE` canonical tables для `anon`/`authenticated` заборонені через grants; RPC перевіряють `auth.uid()` і RLS-подібне володіння. Привілейовані адмінскрипти **зобов'язані** користуватися тим самим gateway; database restore/службові міграції, які обходять його, виконуються лише в maintenance mode з новим `sync_epoch` та контрольним snapshot/reconciliation. Послідовність номерів відповідає порядку комітів одного акаунта; курсор — останній повністю застосований суцільний seq, не max timestamp. Всі writer-paths є частиною інтеграційних тестів. **Входи в gateway:** (1) клієнтський `sync_push(ops)` — власника бере тільки з `auth.uid()`, `user_id` з payload ігнорує; (2) внутрішній `apply_ops(owner_id, ops)` — `EXECUTE` лише для серверної ролі імпортера й службових скриптів, недоступний `anon`/`authenticated`. Обидва входи викликають ту саму транзакційну логіку: lock, `account_seq`, change log, receipts.

### 8.1.0. Канонічне виконання повторів на сервері (FIX-03, Ф5b)

- **Межа довіри:** Android/Web/Desktop можуть обчислювати `nextOccurrence()` для optimistic offline UI, але **не є авторитетом** для server-accepted `complete`, `skip_to_next`, `reschedule_current`, `edit_recurrence_rule`, `stop` і Undo, які змінюють активну появу. У `sync_push` клієнт надсилає **типізовану команду**, `occurrence_id`, `base_revision`, `recurrence_policy_version`, `action_at` (UTC instant), `action_zone_id` (IANA), `operation_id`; **не** надсилає довірений `next_due` як факт. Звичайні field-patch мутації зберігають попередній gateway.
- **Обраний серверний шлях:** sync gateway для recurrence-команди повертає `requires_server_evaluation` без мутації; authenticated клієнт спрямовує цю команду до **trusted Supabase Edge Function** `evaluate_recurrence`. Edge перевіряє JWT/ownership, читає поточний стан і через власний **версійний TypeScript/Deno recurrence evaluator** (без client-provided result) готує typed transition plan: prior `occurrence_id`/state, expected server revision, expected policy version, old/new occurrence (або null), completion/skip та activity. Server-only роль викликає `apply_ops(owner_id, trusted_plan)`; прямий клієнтський виклик `apply_ops` заборонений.
- **Атомарність:** `apply_ops` під account advisory lock **повторно перевіряє** ownership, `operation_id` receipt, epoch/handshake, entity `revision`, `occurrence_id`, стан, `recurrence_policy_version`, `action_at/action_zone_id`, допустимість структурного transition; лише тоді одним commit записує canonical Task/Occurrence/Completion, activity, log, receipt. Якщо між читанням Edge та комітом ревізія змінилась — `stale_evaluation`, **без часткового запису**; Edge заново читає стан і повторно розраховує або повертає recoverable conflict. Непідтверджений Edge план із користувацьким довільним `next_due` не приймається.
- **Паритет алгоритмів:** Kotlin `nextOccurrence` та trusted TypeScript evaluator реалізують **одну нормативну §4.0.1 state machine**, з фіксованою версією політики та однаковим serialized fixtures (включно з DST, `COUNT`, `UNTIL`, floating/fixed, completion interval, Undo). Серверна зона часу/tzdb перевіряється за golden UTC+local fixtures; в разі різниці правила або `policy_version` — **відхилити мутацію**, а не мовчки підставити іншу дату. Gateway не робить власного недокументованого алгоритму календаря в SQL.
- **Офлайн та ідемпотентність:** offline-клієнт зберігає локальний optimistic state та immutable команду в outbox; при відновленні мережі надсилає ту саму `operation_id` на trusted path. Неоднозначна доставка/невідомий ACK: спочатку receipt lookup, без автоматичного подвоєння completion. `operation_id` і майбутній occurrence ID визначаються детерміновано/серверним receipt, а не залежать від повторної спроби.
- **Gate Ф5b:** два пристрої одночасно завершують/пропускають ту саму появу; одна операція прийнята, друга `already_applied` або recoverable conflict; жодної другої active чи дубля completion. Ті самі TIME-01..TIME-08 та повний recurrence corpus проходять Kotlin + Deno, а gateway тести перевіряють unauthorized direct proposal, stale plan, crash after commit before ack, policy/tzdb mismatch і idempotent retry.

### 8.1.1. Confirmed / Pending / Effective та rebase (обов'язковий контракт Ф5b)

- **Confirmed state** — серверно підтверджені сутності (`ConfirmedSnapshot` і `revision/change_seq`). **Pending state** — впорядковані незавершені локальні `OutboxOperation` і окремі recoverable drafts. **Effective state** — confirmed + валідний overlay з pending; лише його показують UI, віджети та планувальник. **Зберігання:** effective state — це звичайні таблиці Room (на них індекси, FTS і запити віджетів); `ConfirmedSnapshot` — тіньова копія серверної версії лише тих сутностей, що мають pending-операції. Rebase відтворює рядок зі snapshot + pending ops; після Ack + Pull snapshot видаляється. Повної копії всіх даних у двох місцях немає.
- **Локальна зміна:** Repository атомарно пише immutable typed op і перераховує effective projection; зміна видима миттєво офлайн. Кожна операція посилається на server `base_revision`, а не на поточний wall clock.
- **First-edit atomicity (вимога Ф5b):** для синхронізованої сутності з `pending_count=0` **до** зміни `Task` скопіювати confirmed payload/revision/seq у `ConfirmedSnapshot`, а в цій же Room транзакції створити op та перезаписати effective row. Якщо транзакція відхилилась, **немає** ані нового snapshot, ані op, ані зміненого row. При кількох pending edits повторно брати основу з існуючого snapshot, не з effective row. Для нових локальних сутностей snapshot не вигадувати; pending `create` має окремий стан та базову ревізію null. Snapshot очищати лише після відповідного Ack **і** Pull/receipt, перевіривши відсутність інших pending ops.
- **Push/Ack:** `operation_id` стабільний при retry. Підтверджену операцію позначають acked, але effective projection не перераховують так, щоб значення зникло до отримання server change. Ack і cursor після Pull узгоджуються по operation receipt/change seq.
- **Pull/Rebase:** приймаємо сторінку server changes до confirmed state; детерміновано перепрограємо невирішені pending ops у локальному порядку. Незалежні field patches залишаються; уже підтверджені не застосовуються вдруге; некоректні/конфліктні переходи переносяться до `ConflictRecovery`, доступного користувачу, і не губляться.
- **Recovery:** `ConflictRecovery` зберігає payload навіть при soft-deleted/відсутній Task, дозволяє перегляд, «Повернути моє» як нову операцію, відхилення з явним підтвердженням та експорт журналу. Розв'язання з новою server revision, не blind overwrite.
- **Crash gates:** тестувати interruption після local commit, push, server commit до ack, local ack до pull, під час сторінкового pull і rebase. Після відновлення жодна підтверджена зміна не губиться, дубль не виникає.

### 8.2. Вирішення конфліктів

- **Зміни різних полів** однієї задачі (A редагує `content`, B `priority`) зливаються. Механізм: `ServerChangeLog.change_payload` містить імена змінених полів. Для операції з `base_revision` меншою за поточну сервер збирає множину полів, змінених у записах журналу цієї сутності з ревізією після `base_revision`. Немає перетину з полями патча — патч застосовується; є перетин — це конфлікт одного поля.
- **Те саме поле з двох пристроїв (v1):** перемагає вже підтверджений серверний стан. Локальне значення зберігається в `ConflictRecovery` (і відображається в `TaskActivity` там, де задача доступна) з дією «Повернути моє значення» як новою операцією. Sync Health показує такі випадки; повний side-by-side UI конфліктів — Ф9 за потреби. Комутативні операції (мітки, порядок) розв’язуються типізовано.
- **Виконання та повтори:** повторний `complete` одного occurrence не створює два записи; concurrent complete/skip/postpone визначаються state machine, а нерозв'язні випадки фіксуються в історії так само, як конфлікт одного поля.
- **Мітки, порядок, підзадачі:** операції з множинами/позиціями визначені типізовано, не повний row overwrite.
- **Delete vs edit:** видалене не воскресає автоматично; локальна правка зберігається в історії з дією «Відновити задачу з моїми змінами».
- Годинник пристрою — лише інформація для інтерфейсу, не критерій того, хто перемагає.

### 8.3. Tombstones, retention та старі пристрої (V25-RECON)

**Конкретна початкова політика v1 (всі числа — мінімальні горизонти, а не SLA доступності сервера):** Регулярне обрізання журналу **не змінює `sync_epoch` усіх здорових пристроїв**; `sync_epoch` змінюється лише при несумісному перевиданню canonical baseline, disaster recovery, авторизованому account reset/rebinding. Звичайний GC просуває `min_available_seq`/`compaction_generation`; клієнти з актуальним курсором продовжують delta без full download.

| Дані | Мінімальний retention | Правило очищення |
| --- | --- | --- |
| `ServerChangeLog` | **180 днів** | Лише після durable checkpoint повного стану, опублікованого `min_available_seq`/`compaction_generation`; записи старші за горизонт можна обрізати лише за умови, що клієнти з курсором нижче floor отримають full reconciliation |
| Tombstones/soft-delete і deletion identity | **365 днів** | Видалення можливе лише після дотримання epoch barrier; не вважати очищення Trash достатнім |
| `ServerOperationReceipt` для accepted ops | **365 днів** | Старіше можна очищати лише після відсікання старого epoch від push; дубль із невідомим Ack **не** replay автоматично |
| Current full checkpoint/snapshot | **щонайменше 1 актуальний** | Створювати новий атомарний consistent snapshot перед prune; старий зберігати до верифікації нового |
| `TrashEntry` (UI) | **30 днів** типово | UI retention незалежний від 365-денного tombstone |

**GC-процедура (сервер, транзакційний/account-serialized):** (1) одержати узгоджений full snapshot усіх live сутностей + IDs видалених, `account_seq` і checksum; (2) опублікувати `snapshot_id`, `checkpoint_seq`, `min_available_seq`, `compaction_generation` та `snapshot_schema_version` атомарно, **не змінюючи `sync_epoch` при звичайному GC**; (3) тільки потім видаляти записи журналу за retention і перевіреним cutoff; (4) перевірити checksum/restore; (5) не видаляти ідентичність видаленого до проходження tombstone horizon/epoch barrier. `min_available_seq` — найменший безперервно доступний seq для pull; ніколи не використовувати `MAX(change_seq)` як доказ повного журналу. Якщо checkpoint недоступний або пошкоджений, GC **блокується**, існуючий log лишається.

**Handshake:** клієнт надсилає `{device_id, client_protocol_version, sync_epoch, last_applied_change_seq, pending_operation_ids}`. Якщо epoch поточний, протокол підтримуваний, `cursor >= min_available_seq - 1`, сервер повертає `DELTA_OK`; інакше `FULL_RECONCILIATION_REQUIRED` з `snapshot_id`, `checkpoint_seq`, `new_epoch` (для routine compaction може дорівнювати старому), `compaction_generation`, `schema_version`, `manifest_checksum`, `reason`. **Ні sync_push, ні apply_ops не приймають операції пристрою з курсором нижче актуального floor; gateway перевіряє актуальний handshake-token/cursor/epoch перед mutation**, незалежно від валідності Auth. Старий/відкликаний `device_id` та несумісний протокол не отримують права на `push` до авторизованого reenrollment/оновлення. Відповідь ніколи не приймає blind push зі старим epoch або зі stale cursor.

**Full reconciliation (локальна атомарність + recoverability):**

1. Зупинити звичайний Push; перевести pending outbox у `reconciliation_hold`, створити локальний зашифрований або явно підтверджений backup checkpoint із `ExternalIdentity`, незавершеними операціями й журналом; не втрачати локальні чернетки.
2. Завантажити snapshot сторінками в **окрему staging database/namespace**; перевірити scope/user, schema version, кількість об'єктів, фінальний checksum і `checkpoint_seq`. Якщо з'єднання обірвалося або checksum не збігається — відновити staging/retry, **не** торкатися поточного effective state.
3. На сервері зберегти barrier `{current_epoch, min_available_seq, compaction_generation}`; клієнт атомарно (на своїй платформі) перемикає canonical confirmed state + cursor до checkpoint, а pending операції зберігає в hold; якщо немає атомарного filesystem swap, використовувати транзакційний state-switch з `generation_id`. При crash відновлюється **одна** повна генерація, без змішування двох.
4. Кожну pending op зі старого epoch перевірити за доступними receipt/operation_id й зіставити зі станом snapshot. `already_applied` → закрити без replay; `not_applied` з доведеним фактом → rebase з новою `base_revision` і **новим** `operation_id` з `replayed_from`, через звичайний gateway; `unknown/ambiguous` → `ConflictRecovery`, **не** автоматичний повтор. Тільки після перевірки й видимого preview дозволити повторний Push.
5. Після повного catch-up (`DELTA_OK` від `checkpoint_seq`) відновити віджети/alarms; відобразити Sync Health `last_reconciled_at`, `recovered`, `held_conflicts`, `server_epoch`, `compaction_generation`. Стару локальну генерацію/backup не чистити до підтвердження відновлюваності й ручного або політично дозволеного retention.

**Доказ безпеки:** жодна stale create/update/delete не може воскресити раніше видалену сутність: **epoch + cursor-floor + device handshake guard** спрацьовують до мутації, а при невідомому результаті старої операції клієнт не replay автоматично. Окремий тест: пристрій не виходить онлайн **400 днів**, має pending create/edit/delete і пропущений Ack; після reconnect видалене не воскресає, confirmed записи не губляться, а невизначені op потрапляють у recoverable conflicts. `Device.revoked_at` також блокує push до нового handshake/авторизації. **Regression test GC-NORMAL:** активний пристрій із курсором вище floor робить звичайний delta pull після compaction без повного snapshot; старий пристрій із cursor нижче floor отримує FULL, навіть якщо його `sync_epoch` та Auth token ще актуальні.

### 8.3.1. Перший login і `ClaimLocalWorkspace` (Ф5a)

**Сценарій:** Ф1a–Ф4 існують повністю локально (`LocalWorkspace`, `account_scope = local:<workspace_uuid>`), а Supabase Auth з'являється у Ф5. Перший login **не** означає, що локальний workspace можна перезаписати серверним snapshot.

1. **Preflight:** створити перевірену локальну резервну копію; зафіксувати `workspace_id`, `inbox_id`, кількість задач, `ExternalIdentity`, outbox і обраний `user_id`. Показати вибір «Прив'язати локальні задачі» / «Залишити локально». Сервер повідомляє, чи хмара акаунта порожня.
2. **Claim request:** створити один стабільний `claim_operation_id`; сервер автентифікує `user_id`, перевіряє власність/ідемпотентність, позначає локальний workspace як claim-in-progress у власному ledger. Retry не створює нове місце/Inbox.
3. **Обсяг v1 — claim лише в порожню хмару.** Локальні UUID, Inbox і `ExternalIdentity` переходять у хмару без змін. Якщо хмара **не порожня** (уже прив'язаний інший пристрій), повного злиття у v1 немає; користувач обирає: (а) «Замінити локальне хмарним» — після backup, з можливістю відкату; (б) «Додати з backup» — **контрольований append-only import**, а не повний merge: dry-run зі stable UUID/external source ID mapping, FK preflight, перевіркою ownership, `ExternalIdentity` та відомих UUID; уже наявні source IDs лише зіставляються, не перезаписуються; задачі з локального Inbox переносяться в хмарний Inbox однією атомарною операцією (`inbox_merge_map`), а невідомі сутності додаються. **Жодного name-based merge.** При колізії UUID з іншим content/власником, неможливій FK або неоднозначному source mapping **commit блокується цілком**, показується журнал, користувач зберігає backup і може експортувати список конфліктів. Повне злиття двох непорожніх workspace із preview колізій — Ф9.
4. **Commit / bootstrap-outbox barrier (FIX-04):** у `ClaimLocalWorkspace` створити **один початковий канонічний snapshot** сутностей і `ExternalIdentity` (разом із Inbox remap), а не `snapshot + replay` старих CRUD. У `preflight` зафіксувати `local_cutoff_seq = max(local_seq)` у тій самій локальній транзакції, упорядкований список/хеш усіх `OutboxOperation` до нього, `workspace_id`, snapshot checksum і стабільний `claim_operation_id`; на час atomic local snapshot writes або блокуються, або нові отримують `local_seq > cutoff`. Сервер за цим claim ID у транзакції й через mutation gateway публікує bootstrap snapshot + `change_seq`, створює **`ClaimBootstrapReceipt`** (`claim_operation_id`, `workspace_id`, `cutoff_seq`, `source_outbox_digest`, `snapshot_checksum`, `committed_seq`, `result`) та посилання/receipt **для всіх pre-claim operation IDs** (або еквівалентний доведено ідемпотентний receipt range/digest). Жодна з них не застосовується вдруге як runtime patch. Клієнт перевіряє receipt/digest, підтверджений snapshot та counts, **локально в одній транзакції** позначає ops `absorbed_by_bootstrap` і записує cursor/epoch/account binding; історичні `TaskActivity` не знищує. Тільки `local_seq > cutoff` переходять у нормальний outbox Push із новими `base_revision` та FK-маппінгом після завершення claim. При невідомому ACK повторний claim **із тим самим ID** повертає receipt, але не створює дубль; mismatch digest/checksum або недоступний receipt → `needs_resolution`/pause Push, **жодного blind replay**. До повного Ack/звірки клієнт показує `preparing` і не втрачає локальний effective state.
5. **Rollback/retry:** при розриві мережі/краші claim залишається відновлюваним: повтор з тим самим `claim_operation_id` повертає попередній результат; локальний snapshot і копія БД зберігаються до валідації. Відновлення не «відв'язує» вже підтверджені серверні записи мовчки; помилки з явною дією продовжити або відкотити лише непідтверджені кроки.
6. **Switch/logout:** local scopes ізольовані; при новому акаунті не показувати чужий effective state, не змішувати widgets/alarms; offlining після вже успішного claim працює з кешем тільки свого акаунта.

**Acceptance:** claim у порожню хмару переносить усі дані з тими самими UUID і одним Inbox; **старі локальні `operation_id` поглинуто bootstrap receipt, але `TaskActivity` збережено**; варіанти (а) і (б) для непорожньої хмари не гублять задач і не створюють другого Inbox; два імпорти одного Todoist source ID → одна канонічна сутність; кілька retries та повторний login не створюють дублів.
**Тести bootstrap-outbox:** монотонність/унікальність `(workspace_id, local_seq)` навіть при concurrent writers; 1 000 локальних CRUD-операцій до claim (у т.ч. create→edit→delete однієї задачі), snapshot включає лише актуальний ефективний стан; crash до/після server commit і до local ACK; повтор із тим самим `claim_operation_id`; receipt/digest mismatch; нова правка під час snapshot отримує seq після cutoff; збережена історія та незмінна кількість задач; **жодного повторного replay** поглинутих ops. **Тести append-only:** (a) однакова `ExternalIdentity` → тільки link/skip, (b) нова UUID + valid FK → додати, (c) та сама UUID з іншим вмістом → abort без часткового commit, (d) неіснуючий parent/section → abort з помилкою, (e) два Inbox → зберегти один, переназначити links, (f) повторний backup/повтор RPC → ідемпотентно. Перед commit незмінна резервна копія, після — counts/checksums і receipt.

### 8.4. Автентифікація, RLS, секрети

- Supabase Auth: email/password, відновлення пароля. Після першого входу офлайн-доступ до кешованих особистих даних — без обов'язкового мережевого входу; logout/switch account ізолює локальні акаунтні сховища.
- Серверні таблиці з `user_id` та RLS на `auth.uid()`. Для RPC перевірити належність усіх `entity_id`, пов’язаних проєктів/секцій/міток; не довіряти `user_id` з клієнтського payload. `GRANT INSERT/UPDATE/DELETE` безпосередньо на canonical tables клієнтським ролям не видавати: будь-яка runtime-мутація — через контрольований gateway, який створює `change_seq` та receipt.
- Supabase **publishable/anon key** можна включати до клієнта (це не admin secret), але RLS обов'язкові. `service_role`, Todoist access token, signing private key — лише в захищених secret stores, **ніколи** в APK/git.
- Для `security definer` — мінімальний `EXECUTE` grant, фіксований `search_path`, перевірка auth, окремі тести bypass RLS.
- Шифрування трафіку TLS; локальне зберігання токенів — platform secure storage adapter.

### 8.5. Періодичність, Realtime, сервіси

- Android: на запуску, незабаром після мутації, в app foreground, WorkManager periodic не частіше мінімально дозволеного інтервалу (зазвичай 15 хв) — доставка best effort, не SLA «кожні рівно 15 хв».
- Web/Desktop: foreground/open-client loop, reconnect triggers, manual Sync; background sync при закритому браузері не гарантується.
- Realtime: сигнал «щось змінилося» → виклик Pull. Пропущені повідомлення лікує catch-up із журналу.
- UI Sync Health: last success, pending ops, retries, paused backend, sync conflicts, device last_seen.

### 8.6. Supabase Free і heartbeat

Free-проєкт може бути поставлений на паузу за недостатньої активності протягом 7 днів. Кілька реальних запитів на день **можуть допомогти, але не гарантують** відсутності паузи. Синхронізація і локальна робота не повинні залежати від того, що heartbeat завжди вдасться.

1. Android може викликати authenticated `heartbeat(device_id)` раз на день, не витрачаючи батарею на примусове пробудження.
2. **Опційний зовнішній heartbeat:** GitHub Actions може викликати **окрему Edge Function** через HTTPS із спеціальним секретом `HEARTBEAT_TOKEN` у GitHub Actions Secrets (мінімальний доступ, rotation); Edge Function перевіряє секрет, rate-limit і викликає вузьку серверну процедуру. **Publishable/anon key сам по собі не автентифікує GitHub**, тому публічний `heartbeat('github')` через `security definer` заборонений. `service_role`, паролі БД і refresh tokens у GitHub не зберігати. Якщо безпечно налаштувати це незручно — вимкнути зовнішній ping, не створювати open endpoint. Реальна доступність Supabase і відновлення після паузи важливіші за «зелений» heartbeat. Репозиторій приватний; невдалий scheduled run — повідомлення й діагностика, але не гарантія uptime.
3. При паузі: банер «Хмара тимчасово недоступна» + last sync + pending ops; після відновлення запускати повний catch-up.
4. Серверний backup поза Supabase — §10.3; на Free не слід розраховувати на керовані daily DB backups.

---

## 9. Перехід із Todoist (Ф5.1, окрема фаза)

**Напрям тільки один:** Todoist → особистий застосунок. Зміни із застосунку не відправляються в Todoist. Після cutover Todoist може бути вимкнений без впливу на роботу власного task manager.

### 9.0. Ранній одноразовий імпорт (Ф1a)

Мета — працювати з реальними задачами вже у Ф1a, задовго до хмарної синхронізації.

- Локальний скрипт `tools/todoist-oneshot/` на TypeScript (Node або Deno), який власник запускає на своєму комп'ютері. Токен Todoist — лише змінна оточення на час запуску; не зберігається й не комітиться.
- Скрипт читає Todoist API v1 (повна вибірка **та incremental catch-up**, якщо API повертає sync token, щоб закрити зміни під час повного імпорту) і пише JSON у форматі backup Ф1a з `BackupManifest`. Файл переноситься на телефон і відновлюється штатним restore у порожнє сховище.
- Для **кожної** імпортованої сутності (проєкт, секція, мітка, задача) JSON містить `ExternalIdentity(source='todoist', entity_type, source_id, local_id, source_hash, import_version)`; backup/restore зберігає ці поля й стабільні `local_id`. `TodoistMap` на сервері Ф5.1 формується з цих даних, а не відтворюється за назвами.
- **Залежний підграф (FIX-02):** у Ф1a повторюваний корінь і **всі його нащадки будь-якого типу** відкладаються; не створювати неповторювану дитину без батька. Скрипт проходить Todoist hierarchy після повного/incremental pull, будує DAG/tree з перевіркою циклів, зберігає `DeferredTodoistNode` і `source_parent_id` у metadata backup та окремий звіт `recurring_roots_count` / `deferred_descendants_count` / `deferred_source_ids`. Задачі, не залежні від відкладених коренів, імпортуються як звичайно. У Ф2 той самий скрипт запускається в режимі `--recurring-only` (append-only): **у topological order атомарно додає корені та всі їхні відкладені піддерева**, з дедуплікацією за `ExternalIdentity`; зв'язує `parent_id` тільки через підтверджений stable UUID mapping та перевіряє належність проєкту/секції. Для повторів зберігає сирі `due.string` + `lang` у `ImportPending`, розпізнане конвертує у RRULE, нерозпізнане отримує `needs_review` (§4.3). При відсутньому/видаленому батьку, циклі або незрозумілому mapping — **abort цілого піддерева без часткового імпорту**, `needs_review` у звіті. Відкладені метадані прибирати лише після верифікації counts, parent links і збереження receipt.
- Модуль маппінгу (пріоритети, проєкти, секції, мітки, дедлайни, описи) — окремий файл, який без змін переходить у Edge Function Ф5.1.
- Повторний запуск у непорожню базу заборонений (крім append-only режиму `--recurring-only` **разом із залежним підграфом** у Ф2) або виконується як dry-run із порівнянням `ExternalIdentity`; під час Ф5/Ф5.1 повторні джерельні ID прив’язуються до наявних local UUID, а не створюють нові записи. Ідентичні назви **не** є критерієм дедуплікації.

### 9.1. Архітектура імпортера

- Supabase Edge Function на TypeScript/Deno; плановий запуск через `pg_cron` орієнтовно раз на 15 хв, якщо інфраструктура і ліміти дозволяють. Окрема кнопка ручного імпорту.
- Офіційний Todoist API v1 Sync із `sync_token`: перший full pull, наступні incremental. Схему, доступні поля та ліміти API ще раз перевірити під час реалізації Ф5.1.
- Токен Todoist зберігається в Supabase secrets або захищеному server-side store, без попадання в клієнт або dump.
- `TodoistMap` (`entity`, `todoist_id`, `local_id`, version/hash) створюється на сервері **з локальних `ExternalIdentity` після claim**, має унікальність `(user_id, entity_type, todoist_id)`; identity з Ф1a переживає backup, перехід у хмару й повторні імпорти.
- Мапа пріоритетів: Todoist API 4 = UI p1, отже `local_priority = 5 - api_priority` для діапазону 1..4.
- Маппінг сутностей — спільний модуль із §9.0.
- **Повтори.** API v1 повертає термін як `string`, `date`, `datetime`, `timezone`, `lang` і `is_recurring` — без RRULE. Імпортер на TypeScript не дублює Kotlin-парсер: він записує задачу з найближчою датою та створює синхронізований запис `ImportPending` із сирим рядком і мовою. Конвертацію в RRULE виконує спільний KMP-парсер на пристрої **ідемпотентною, версійною операцією** (`source_hash`, `parser_version`, expected `ImportPending.status`, unique conversion receipt), щоб два пристрої не конвертували той самий рядок двічі. Парсер має розуміти фрази повторів Todoist щонайменше англійською та мовою джерела. Нерозпізнане отримує `needs_review` і видимий warning при completion, raw string лишається; жоден невідомий повтор не вважається підтвердженою одноразовою задачею. Альтернатива — збірка парсера в Kotlin/JS для Deno — лише через ADR.

### 9.2. Етапи перенесення

1. **Discovery/dry-run:** підрахунок проєктів/секцій/міток/задач, звіт про обсяг та непідтримані поля; нічого не записувати.
2. **Перший імпорт:** проєкти, описи, секції, мітки, активні задачі, due/deadline, пріоритети, підзадачі, recurrence (через `ImportPending`, §9.1); далі доступні нагадування/коментарі за можливостями й дозволами API. **Збережені фільтри** (API v1 Sync) у Ф5.1 зберігаються **без втрати** як `FilterImportPending` (`source_query`, description, color, favorite, view options, source ID, hash); preview показує «Очікує підтримки фільтрів». Конвертація, активація як Smart View і перевірка семантики — **Ф6a**, коли Filter AST уже існує. Непідтримані конструкції лишаються `needs_review`, а не зникають; при cutover вони чесно позначаються як відкладені.
3. **Валідація:** порівняння лічильників і вибіркових записів, інваріантів вкладеності, timezone і повторів; журнал помилок із retry. Зіставити всі `ExternalIdentity` із Ф1a та серверні `TodoistMap`; виконати incremental catch-up після full pull; виключити дублікати за source ID.
4. **Перехідна синхронізація:** нові зміни Todoist приймати **лише коли локальний відповідник не мав незалежної зміни**; при обох змінених версіях — source conflict, без тихого перезапису. За бажанням імпортовані дані можна зробити read-only до cutover, але це опційний режим.
5. **Cutover:** остаточний sync, звірка, повний backup, вимкнути cron і токен, зафіксувати checkpoint. `TodoistMap` зберігати read-only до окремого підтвердженого очищення, не видаляти відразу.

**Done (Ф5.1):** raw filter definitions + `FilterImportPending` повністю збережені, але **не** вимагається робочий Smart View до Ф6a; Import Coverage Matrix створена й перевірена; повторний імпорт і retry не створюють дублів, **включно із задачами, вже імпортованими у Ф1a**; контрольний піднабір повністю збігається із джерелом; усі unsupported поля задокументовані; локально редаговані задачі не перезаписуються; імпорт можна зупинити без втрати власних даних; кожен повтор із Todoist або перетворено в RRULE, або він має `needs_review` — жоден не зникає мовчки. Усі імпортовані recurrence (не лише вибірка 20) отримали фінальний статус `parsed/needs_review`, нерозпізнані винесено у звіт; мінімум 20 реальних прикладів **додатково** проходять порівняння наступних дат і boundary tests.

### 9.3. Import Coverage Matrix / Cutover evidence (обов'язково для Ф5.1)

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

## 10. Backup та відновлення

### 10.1. Ф1a: мінімальний обов'язковий backup (`P0`)

- Export усіх локальних сутностей у versioned JSON зі schema version, `created_at`, checksums і явною manifest schema. Не включати password/auth tokens, Todoist token, signing secrets.
- Atomic file write через тимчасовий файл + фінальний commit/rename (де підтримується).
- Import із перевіркою checksum/schema; dry-run counts; опція **відновити в порожнє локальне сховище**. Не імпортувати некоректний backup частково.
- На Ф1a/Ф1b restore не змішує два живі синхронізовані акаунти (сервер ще не запроваджено). Після Ф5 — окремий контрольований restore workflow, який не генерує тисячі безконтрольних дублів.
- Контрольний тест: експорт → скидання тестової БД → відновлення → semantic comparison основних таблиць.

### 10.2. Ф6c: розширення

- Версіоновані автоматичні backups із налаштованим retention; шифрування паролем/ключем за вибором користувача; тест відновлення.
- Зовнішнє місце зберігання (через системний Android file picker / локальну папку); не потребує обов'язкової інтеграції зі стороннім сервісом.
- Відображати час останнього успішного backup, а не лише факт запуску.
- Наявність даних у Supabase **не** вважається резервною копією.

### 10.3. Ф5a: серверний backup поза Supabase (`P0` з появою синхронізації)

- Раз на тиждень `pg_dump` **canonical application tables, sync logs/receipts, `account_seq`, external identities та необхідних метаданих** окремою read-only роллю Postgres; scope дампу документувати. Це **не** повна копія керованої Supabase Auth, конфігурації проєкту або файлів Storage.
- Запуск — cron на власній VM (переважно: рядок підключення не виходить за межі власної інфраструктури) або GitHub Actions у приватному репозиторії з рядком підключення в secrets.
- Дамп шифрується публічним ключем `age` ще до запису на диск; приватний ключ зберігається офлайн разом із копією keystore.
- Retention — щонайменше 8 тижневих копій; час останнього успішного backup видно в Sync Health.
- Перевірка: відновлення дампу в локальний Supabase (`supabase start`) — частина gate Ф5a, далі — раз на квартал; **додатково** перевірити повне disaster recovery на новому тестовому Supabase-проєкті за §10.4.

### 10.4. Відновлення після втрати Supabase-проєкту (P0 з Ф5b)

**Сценарій:** старий Supabase-проєкт, Auth identities, Edge Function config та cron недоступні. `pg_dump` **сам по собі не відновлює** всю керовану конфігурацію Supabase. Операційний runbook `docs/DISASTER_RECOVERY.md` включає:

1. Створити **новий** тестовий Supabase-проєкт, застосувати versioned SQL migrations/GRANT/RLS, RPC, Edge Functions, scheduled jobs, секрети з безпечного secret store; **не** класти токени у backup чи репозиторій.
2. Розшифрувати перевірений дамп, відновити application rows, operation receipts, seq/sync metadata; прогнати FK/invariant/count/checksum checks. Якщо старі `auth.users` не перенесені, заново створити користувача через стандартний Auth flow і **транзакційно переприв'язати** owner UUID для всіх таблиць, журналу, `ExternalIdentity`/`Device`/`TodoistMap` під обмеженим maintenance tool, із перевіркою RLS.
3. Створити новий `sync_epoch` і server snapshot/checkpoint; старі client cursors не приймати для blind push. Після повторної авторизації клієнти проходять handshake/full reconciliation, зберігають offline drafts і не дублюють дані з backup.
4. Повернути доступ до клієнтів, перевірити `Client A/B → sync`, Inbox uniqueness, Todoist mapping, recurring occurrences, Trash і нагадування. Звірити, що остання підтверджена серверна зміна **до часу backup** відновилась; усе, що після backup, відновлюється з локальних pending/backup за явною політикою — **без гарантій**, яких резервна копія не забезпечує.
5. Перевірити фонові завдання й нові backup після реконструкції, відкликати старі токени/ключі. Документувати RPO як вік **останньої фактично успішної та перевіреної копії** на момент збою (інтервал між запусками не гарантує RPO) і фактичний RTO тестового відновлення; не декларувати 0 втрат між копіями.

**Gate Ф5b (обов'язковий перед cutover у Ф5.1):** повний dry-run на **окремому чистому Supabase-проєкті**, а не лише локальний restore, із новим Auth UID, працездатною RLS, успішним sync-клієнтом і перевіркою дедуплікації. Процедура має бути доступною без автора коду.

---

## 11. Роадмап і критерії готовності

**Робоче правило:** кожна фаза — один epic/основний PR; всередині можливі менші PR, щоб зміни легко перевірялися. Перехід далі тільки після acceptance + QA gates; для двох паралельних агентів — різні незалежні гілки, без паралельного редагування однієї міграції.

### Ф0 — Архітектура, каркас, кросплатформний proof of concept

**Роботи:** KMP-проєкт, modules/core/ui/androidApp, Koin, navigation/theme, Room 3 schema v1 (з critical entities, `LocalWorkspace`, `ExternalIdentity` та outbox), migrations, локальний Repository, version catalog, feature flags; **заповнити матрицю V25-STACK (§3.3.1) Android/JVM/Web на зафіксованих версіях з доказами runtime persistence, обрати Web fallback ADR за потреби**; маленькі Web/Desktop PoC; CI lint/unit/build; **контракти** операцій і confirmed/pending/effective state SyncEngine (без робочої хмари); release keystore із двома резервними копіями; канал оновлень APK (ADR); власний web-worker і перевірка COOP/COEP; **`docs/UX_SPEC.md` (screens/navigation, Quick Add options/Smart Date, Share Target, recurrence choices, reminder lifecycle, rich links, calendar slot create, gesture/empty/error/offline flows, accessibility)**; фіксація версій QA-інструментів (§12.1).

**Приймання:**
- [ ] Android APK встановлюється й може створити/прочитати одну локальну задачу.
- [ ] **Android — обов'язковий `pass`:** persistent Room CRUD (після перезапуску), транзакційний outbox, WAL-safe snapshot, migration/restore. **Desktop/Web — статус `pass` або `blocked` з окремим ADR**, відтворюваними логами й резервним планом; `blocked` не означає перевірену сумісність і не зупиняє Android, але є явним blockers gate для Ф7/Ф8.
- [ ] Транзакція Entity + Outbox проходить rollback/crash тест.
- [ ] Android Kotlin/Compose/Room/KSP/AGP версії фактично сумісні й pinned; Desktop/Web версії зафіксовані з **чесним статусом** `pass` або `blocked/ADR` (не записувати `compatible` без runtime evidence).
- [ ] CI зелений; ключі підпису не доступні PR з недовірених гілок.
- [ ] Keystore збережено у двох незалежних місцях; збірка, підписана з резервної копії, встановлюється поверх попередньої.
- [ ] Оновлення v0.0.1 → v0.0.2 обраним каналом проходить без втрати локальної задачі.
- [ ] Для Web задокументовано worker, COOP/COEP, обраний хостинг **або причину blocked та дозволений fallback**, з owner і gate до Ф7.
- [ ] `docs/UX_SPEC.md` описує ключові Android-екрани та сценарії, включно з fallback/offline/empty states; для `LocalWorkspace`, `ExternalIdentity` та confirmed/pending визначені schema/operations contracts.

**Додаткові P0-gates Ф0 (уточнено v2.6.1):**
- [ ] `docs/TECH_COMPATIBILITY.md`: version catalog pinned, матриця (§3.3.1) заповнена фактичними `pass`/`fail`/`blocked`, кожний результат має command, OS/browser, build/log evidence.
- [ ] Android Room+outbox CRUD/rollback/restart і **WAL-safe** backup/recovery проходять. JVM/Desktop та Web мають **`pass` із доказами або `blocked` з ADR, owner, fallback і повторним gate у Ф7/Ф8**; статус `blocked` НЕ рахується успішним PoC, але допускає закриття Android-first Ф0.
- [ ] Shared `nextOccurrence` golden fixtures та timezone policy v2.6.1 зафіксовані як виконувані тестові контракти: Ф0 створює fixtures і запускає підтримуваний time-only subset; повний recurrence engine та Kotlin/Deno parity є gates Ф2/Ф5b, **не** удавана готова функція Ф1a.

### Ф1a — Щоденний мінімум (dogfooding)

**Мета:** почати користуватися застосунком щодня якомога раніше, паралельно з Todoist, не жертвуючи інваріантами даних.

**Роботи:** CRUD задач і проєктів; Inbox/Today/Upcoming у вигляді списку; пріоритети, due date/time, deadline; базовий Quick Add (основні дати й час uk/en/pl, `#проєкт`, `p1`–`p4`); одне авто-нагадування на задачу (exact із fallback, перереєстрація після reboot); **мінімальний JSON backup/restore**, що зберігає `ExternalIdentity`/`LocalWorkspace`; одноразовий імпорт із Todoist (§9.0) зі збереженням source IDs; повторювані задачі з Todoist **і всі їхні транзитивні нащадки** не імпортуються (лишаються в Todoist до Ф2, усі source IDs/parent IDs у metadata-звіті); outbox і `TaskActivity` пишуться з першого дня.

**Приймання:**
- [ ] Тиждень щоденного використання на основному телефоні без втрати задач.
- [ ] Реальні задачі з Todoist імпортовані; стабільні `ExternalIdentity` для задач/проєктів/секцій/міток збереглися після backup→restore; повторювані корені **разом із транзитивними дітьми** не імпортовані й обліковані в metadata-звіті — жодна не загубилась.
- [ ] Задачі без повтору, які **не є нащадками відкладеного повторюваного кореня**, імпортовано повністю; числа відкладених повторюваних коренів і **всіх залежних підзадач** збігаються з Todoist; жодного orphan `parent_id`.
- [ ] Нагадування для задачі з часом приходить і після перезавантаження телефона.
- [ ] Export → restore у чисте сховище повертає дані без пошкоджень.
- [ ] Невдале локальне збереження не залишає сутність без outbox або навпаки.

### Ф1b — Повне офлайн-ядро

**Роботи:** секції, мітки, підзадачі, вкладені проєкти; Project/Label list; duration, reorder, moves, bulk actions, Undo; Trash/Restore базово; базова accessibility; клікабельні безпечні http/https URLs у назві/описі з `source_url`; вимірювання й фіксація перф-бюджету.

**Приймання:**
- [ ] Тиждень реального користування в airplane mode без втрати задач.
- [ ] 10 000 тестових задач доступні для перегляду/пошуку без зависань; перф-бюджет виміряно й записано в `docs/QA_PLAN.md`.
- [ ] Видалити → Undo/Trash Restore працює для задачі й проєкту.
- [ ] URL `https://` коректно відкривається після підтвердження, `javascript:`/`intent:`/`file:` не запускаються; експорт/відновлення зберігає `source_url`.

### Ф2 — Повний Quick Add і повтори

**Роботи:** три мови, чипи і прев’ю токенів, повний синтаксис `#` `/` `%` `@` (опція) `p1`–`p4` `!` `{}` `* `, `//` description і тривалості, граматика `!` (§5.4), **багаторядкове створення з явним preview**, настроювані `smart_date_recognition`/кнопки/проєкт за замовчуванням; `ACTION_SEND` + `ACTION_SEND_MULTIPLE` із безпечним URL preview; recurrence engine, 0..1 active occurrence, завершення/skip/undo й projection contract; app shortcuts, share target, Quick Settings; append-only доімпорт **повторюваних коренів із повними відкладеними піддеревами** із Todoist (`--recurring-only`) та ідемпотентна конвертація `ImportPending` у RRULE; Complete guard для `needs_review`.

**Приймання:**
- [ ] **≥50 позитивних** еталонних рядків на кожну мову **плюс ≥50 негативних/неоднозначних загалом** і cross-language приклади.
- [ ] Date/DST/leap/month-end сценарії, skip/postpone/undo для повторів зелені в unit/property tests.
- [ ] Повтор виконання одного occurrence не створює дубль; сценарій `completed→next active→undo old` не створює другу active; завершення `UNTIL/COUNT` залишає 0 active.
- [ ] Batch Quick Add не розбиває URL `https://` на опис і не створює задачі без підтвердження; `//` працює як опціональний delimiter з Undo.
- [ ] `починаючи з` / `starting` / `od` і включна кінцева дата дають правильні першу й останню появи трьома мовами.
- [ ] Виконання останньої появи `COUNT`/`UNTIL` виконує задачу; «Зупинити повтор» лишає її відкритою без дати; «Виконати й скинути підзадачі» повертає підзадачі з одним Undo.
- [ ] Quick Add з Share Target/shortcut зберігає правильні поля й історію.
- [ ] Усі повторювані задачі з Todoist **та їхні відкладені діти** доімпортовано без дублів і orphan parent links (дедуплікація за `ExternalIdentity`, topological parent-first/atomic subtree): кожна має RRULE або `needs_review` із Complete guard; жодна не завершується мовчки.
- [ ] `skip_to_next`/`reschedule_current`/`edit_recurrence_rule` дають різні історичні та майбутні результати; fixed-schedule і completion-interval запізнілі й перенесені дати мають golden tests; `Skip` останньої появи зберігає задачу відкритою як `exhausted_skipped` без `TaskCompletion`. Тести охоплюють §4.0.1 (DST gap shifted forward, overlap first offset, DTSTART, floating travel, COUNT/UNTIL inclusive, completion zone).
- [ ] Вимкнений smart-date parsing зберігає природну фразу як текст; default project/контекст не конфліктують; Share Target `ACTION_SEND_MULTIPLE` і невідомий URI не створюють задач автоматично, source URL безпечний.

### Ф3 — Android-нагадування

**Роботи:** multiple reminders, exact/inexact fallback, permission UX, snooze і task postpone як окремі дії, **базові recurring reminders незалежно від Task.rrule**, notification actions, reboot/update/timezone rescheduling, device delivery records; `ReminderLifecyclePolicy` із прив’язкою one-shot / occurrence-bound / task-until-completed і ключем доставки.

**Приймання:**
- [ ] З grant exact alarm нагадування доставляється у запланований інтервал в умовах тесту (метрики зафіксовані).
- [ ] З deny/revoke застосунок не падає, показує статус і планує fallback.
- [ ] Snooze не змінює due/deadline; Complete обробляє повтор коректно.
- [ ] Незалежне щоденне нагадування для одиничної задачі повторюється до completion і перестає плануватися після completion; не створює повторюваних задач, не дублюється після reboot.
- [ ] Quick Add `!щодня 9:00` / `!every day 9am` / `!codziennie 9:00` створює таке нагадування трьома мовами.
- [ ] Таблиця переходів Complete/Skip/Reschedule/Stop recurrence/Undo/reboot перевіряє старі/нові alarms за ключем `reminder+occurrence+device+planned_at`; custom one-shot не починає повторюватися; незалежне до-completion нагадування зупиняється саме за policy.
- [ ] Тест на реальному Android після перезавантаження й Doze задокументований.

### Ф4 — Android-віджети

**Роботи:** Jetpack Glance collection widget, конфігурація виду й стилю (тема, прозорість, text scale, compact, count, due/priority), джерела й швидкого перемикання; task complete/Quick Add, schedule updates, midnight/timezone refresh, незалежні екземпляри.

**Приймання:**
- [ ] Два віджети різних джерел, різних тем і масштабу тексту одночасно незалежно працюють; вибір джерела в одному не змінює другий.
- [ ] Complete із віджета записує outbox і оновлює Today.
- [ ] Midnight/timezone refresh відображає потрібний список.
- [ ] Зміна/вихід з акаунта не витікає вміст у віджеті.
- [ ] Перф-бюджет, зафіксований у Ф1b, виконується на основному телефоні.

**Межі готовності (не плутати з релізом і повною заміною Todoist):**

- **Dogfood — після Ф1a:** щоденне використання на основному телефоні **паралельно** з Todoist, з локальним backup і реальними імпортованими задачами; основним інструментом лишається Todoist.
- **Personal Daily Driver — після Ф4:** стабільна щоденна робота на одному Android із локальним backup, Quick Add, повтореннями, нагадуваннями й віджетами; **не** обіцянка повної паритетності Todoist (фільтри, календар, хмара ще попереду).
- **Migration Ready — після Ф5.1:** захищена синхронізація між пристроями, звірений імпорт Todoist і контрольований cutover, але деякі зручні функції Todoist ще можуть бракувати.
- **Full Android Replacement — після Ф6c:** фільтри, шаблони, дошка, календар, історія, настройки та backup готові; рішення припинити Todoist ґрунтується на checklist власних сценаріїв і тестах, а не лише на номері фази.
- **Web + Android — після Ф7:** однакові core-функції і стан між Android та браузером; browser persistence/offline limitations задокументовані.
- **Cross-platform Personal Replacement — після Ф8:** Android + Web + підтримуваний Desktop installer, наскрізна синхронізація та порівняльний checklist власних Todoist-сценаріїв; це повна ціль для всіх погоджених платформ, не обов’язкова умова перестати використовувати Todoist на Android.

### Ф5a — Хмара для одного пристрою

**Роботи:** Supabase schema + migrations + RLS/grants; Auth; `ClaimBootstrapReceipt`/`local_cutoff_seq`/outbox absorption; `ClaimLocalWorkspace` у порожню хмару та варіанти (а)/(б) для непорожньої (§8.3.1); **єдиний canonical mutation gateway** з двома входами + ordered change log; ідемпотентний outbox; Push/Ack/Pull для одного пристрою; tombstones+epoch; базовий Sync Health; захищений/необов’язковий heartbeat (§8.6); щотижневий зашифрований серверний backup (§10.3).

**Приймання:**
- [ ] Перший login переносить повний локальний workspace у порожню хмару один раз: ті самі UUID, один Inbox, `ExternalIdentity` збережені; транзакційний `local_seq` утворює точний cutoff; `ClaimBootstrapReceipt` поглинає всі pre-claim outbox IDs без повторного застосування; retry/partial crash/digest mismatch і backup rollback протестовані.
- [ ] Для непорожньої хмари варіанти «Замінити локальне хмарним» і «Додати з backup» не гублять задач і не створюють другого Inbox.
- [ ] `Додати з backup` є append-only з dry-run і транзакційною перевіркою FK/UUID/source ID; суперечлива UUID/відсутній parent зупиняють commit без часткових записів; повтор імпорту ідемпотентний, дублікати за назвами не об’єднуються.
- [ ] Повторний push одного `operation_id` 100 разів не дублює ефект.
- [ ] Crash між push/ack/pull і network timeouts не гублять операцій.
- [ ] Прямі writes canonical tables без gateway заборонені (grants + integration tests); `apply_ops` недоступний клієнтським ролям.
- [ ] Крос-акаунтний доступ до чужих сутностей/Sync RPC заблокований RLS/валідацією.
- [ ] Пауза Supabase не призводить до втрати локального outbox; recovery працює.
- [ ] Зашифрований серверний backup відновлено в локальний Supabase.

### Ф5b — Кілька пристроїв і відновлення

**Роботи:** trusted server `evaluate_recurrence` (TS/Deno) + atomic `apply_ops` recurrence validation, golden parity із Kotlin; confirmed/pending/effective projection + deterministic rebase (§8.1.1); revisions/patch merge/`ConflictRecovery`; full reconciliation для старих курсорів; Realtime hint; Sync Health (конфлікти, пристрої); **disaster recovery на чистому проєкті** (§10.4).

**Приймання:**
- [ ] Два пристрої створюють різні задачі офлайн й отримують обидві після sync.
- [ ] Одночасне редагування різних полів зливається; однакових полів — перемагає сервер, а локальне значення збережене в `ConflictRecovery`/історії з дією «Повернути моє».
- [ ] Push/Ack/Pull з pending overlay і ребейзом не затирає локальну назву/пріоритет при concurrent server changes; rejected patch відновлюваний; `ConfirmedSnapshot` не лишається для сутностей без pending-операцій.
- [ ] Перше локальне редагування синхронізованої задачі створює `ConfirmedSnapshot` **до мутації** атомарно з outbox; rollback нічого не залишає; друга правка не перезаписує основу; new local create має null base та не вигадує server snapshot.
- [ ] Delete на A + offline B на 45+ днів не воскресає після повернення B. Також 180/365/400-денні cursors, нові epoch, blocked blind push, прерваний snapshot download, corrupted checksum, невідомий Ack та `ConflictRecovery` перевірені через штучний clock; GC із retention 180/365 днів (§8.3) працює тільки після checkpoint.
- [ ] Старий APK може безпечно відмовитися від несумісного sync, не пошкодивши дані.
- [ ] Зашифрований серверний backup відновлено **на окремому чистому Supabase-проєкті** з новим Auth UID, RLS/secrets/cron, новим sync epoch і працездатним клієнтом — **обов’язковий gate перед cutover у Ф5.1**; зовнішній heartbeat, якщо ввімкнений, авторизований через вузьку Edge Function без service_role у GitHub (не обов’язковий gate).

- [ ] Для `complete`/`skip` на двох пристроях trusted Deno evaluator + `apply_ops` виконують один canonical transition: Kotlin/Deno fixtures і DST тести збігаються; direct client future due заборонено; stale plan не застосовується.

### Ф5.1 — Міграція Todoist

**Роботи:** dry-run, full+incremental Todoist Sync API importer з catch-up, зв’язування `ExternalIdentity` Ф1a → server `TodoistMap`, preview/mapping validation, перехідний scheduler, conflict policy, версійна ідемпотентна конвертація повторів, імпорт збережених фільтрів (§9.2), звірка, cutover після DR-gate Ф5b.

**Приймання:**
- [ ] Dry-run відображає кількість імпортованих/пропущених сутностей до змін.
- [ ] Повторний повний/інкрементальний імпорт **і перехід із Ф1a** не створює дублів (source IDs збережено, не дедуп за назвою); full pull завершено incremental catch-up.
- [ ] Нова задача з Todoist з'являється після успішного планового імпорту (ціль — один 15-хв цикл за здорового сервера, не абсолютна гарантія).
- [ ] Локальна зміна не перезаписується імпортером тихо.
- [ ] Після фінальної звірки й backup імпортер можна вимкнути без наслідків для основної бази.
- [ ] Кожен повторюваний запис із Todoist має RRULE або `needs_review`, жоден не був тихо перетворений на звичайну одноразову задачу; на вибірці щонайменше 20 реальних повторів наступні 3 дати збігаються з Todoist; `parser_version`/source hash не допускають подвійної конвертації.
- [ ] Усі доступні збережені фільтри Todoist імпортовано як **raw `FilterImportPending`**, з source query/ID, description/view options і статусом; активний Smart View **не** є gate Ф5.1, ним стане gate Ф6a; несумісні конструкції не відкинуто мовчки.
- [ ] Cutover виконано лише після проходження DR-gate Ф5b.
- [ ] `Import Coverage Matrix` має `imported/partial/metadata_only/unsupported` для всіх типів даних і звірені counts; користувач бачить що залишилось raw/pending, а не помилкову обіцянку повної функціональної заміни.

### Ф6a — Фільтри, пошук, шаблони

**Роботи:** Filter AST + конструктор + базова мова запитів; конвертація/активація `FilterImportPending` із Ф5.1, preview та `needs_review`; **date/due/deadline семантика, multi-group Smart Views + пресети**; FTS5; group/sort; фільтр як джерело віджета; шаблони задач і проєктів.

**Приймання:**
- [ ] Фільтр працює в UI, у widget source і після синхронізації однаково; `date`, `due` і `deadline` поводяться як у Todoist (тест-набір запитів із довідки Todoist); кома дає окремі групи зі стабільним порядком і контролем дублів.
- [ ] Full-text search працює з українською/польською/англійською й 10 000 задач.
- [ ] Шаблон із відносними датами створює задачі з правильними датами від дня застосування.
- [ ] Raw фільтри з Ф5.1 перетворено в коректний AST/Smart Views або вони явно `needs_review`; unsupported query не запускає надто широкий/порожній фільтр; source query і IDs збережені, парсер версійний.

### Ф6b — Дошка й календар

**Роботи:** board; calendar day/3-day/week/month + time blocking, virtual recurrence projections, unscheduled tray і drag-to-schedule, overnight time blocks; long-press/tap у вільному часовому слоті → Quick Add з prefilled due/time/duration і Cancel/Undo; перенесення drag-and-drop.

**Приймання:**
- [ ] Calendar moves відображаються у Today/Upcoming, не змінюючи deadline помилково.
- [ ] All-day задачі не перетворюються на події о 00:00; тижні з переходом DST відображаються коректно.
- [ ] Calendar показує майбутні повтори без створення фізичних active occurrences/нагадувань; перетягування з unscheduled tray на конкретний слот відображається в Today/Upcoming; 3-day і overnight blocks коректні; перемикач «Майбутні появи» типово вимкнений, і тоді видно лише наступну появу.
- [ ] Створення з порожнього слота (включно з all-day) правильно підставляє дату/час/тривалість, Cancel не створює запис, Undo відкочує його; існує доступний альтернативний шлях для TalkBack.

### Ф6c — Історія, backup, налаштування, переклади

**Роботи:** full UI TaskActivity/Comments і безпечний Markdown у description/comments; розширений Trash; шифровані автоматичні backup; settings; UI translations en/pl; privacy options.

**Приймання:**
- [ ] Всі основні екрани й помилки локалізовані трьома мовами; немає обрізаних кнопок (screenshot-тести).
- [ ] Шифрований backup, якщо ввімкнений, реально відновлюється з паролем; невірний пароль не псує дані.
- [ ] Значення, перезаписані під час синхронізації, видно в історії, і їх можна повернути.
- [ ] Markdown source round-trips без HTML/script injection; URL безпечні; вихідні comments/опис імпорту з Todoist не втрачаються при plain-text редагуванні.

### Ф7 — Web

**Роботи:** повний browser UI для Ф1a–Ф6c, адаптивність, Room/WebWorkerSQLiteDriver або схвалений у Ф0 backend локального сховища, хостинг із власними заголовками (COOP/COEP за потреби, §3.3), sync, PWA (опційно), Playwright E2E.

**Приймання:**
- [ ] Web ↔ Android sync проходить усі основні сценарії, включно з offline edits і conflict.
- [ ] Відкриття/закриття вкладки не призводить до silent loss; браузерна очистка сховища пояснена.
- [ ] Chromium/Firefox/Safari smoke checks виконані або точні несумісності задокументовані.
- [ ] Playwright E2E проходить в CI, responsive 360px/desktop, keyboard accessibility.
- [ ] Gate **Web + Android**: основні сценарії виконано на обох платформах із перевіркою результатів after-sync; documented differences не приховані.

### Ф8 — Desktop

**Роботи:** Compose Desktop (спершу Windows, за потреби macOS/Linux), system tray, global Quick Add, системні сповіщення, локальний persist, sync та packaging.

**Приймання:**
- [ ] Desktop installer/build працює на затвердженій ОС.
- [ ] Global hotkey доступний з інших вікон (після дозволів ОС).
- [ ] Офлайн-зміни та sync з Android/Web не губляться.
- [ ] Дублікати системних нагадувань контрольовані налаштуваннями пристроїв.
- [ ] Gate **Cross-platform Personal Replacement**: персональна матриця сценаріїв виконана на Android, Web і затвердженій Desktop ОС, включно з офлайн→sync і backup/recovery; unresolved P0/P1 немає.

### Ф9 — Опційно після стабілізації

- Advanced filter language/autocomplete, повне злиття двох непорожніх workspace при першому login, повний UI конфліктів синхронізації (якщо статистика перезаписів у Sync Health покаже потребу), **розширена мова для повторюваних нагадувань (базові вже Ф3)**, urgent/full-screen reminders лише після перевірки Android-обмежень, ранкові зведення, voice capture, додатковий AI (локальний/хмарний за окремим рішенням), файли, location reminders, опційний календарний sync, PIN/біометрія, додаткові шаблони, розширена аналітика. Не включати в критичний шлях основного релізу.

---

## 12. QA-стратегія та тестовий каталог

### 12.1. Рівні

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

### 12.2. Тест-дизайн (обов'язково використовувати 5 технік)

| Техніка | Приклади |
| --- | --- |
| Equivalence Class Partitioning | Task без/з датою, recurring/non-recurring, account online/offline, exact allowed/denied |
| Boundary Value Analysis | Підзадача рівні 0/1/4/5, 31-ше число, 29 лютого, min/max duration, 10k задач |
| State Transition | Active → Completed/Skipped → Next active або `exhausted_completed`/`exhausted_skipped`; imported unknown → blocked Complete → review; claim local → Bound/Retry; reminder one-shot/occurrence/task lifecycle |
| Pairwise | platform × locale × timezone × online status × notification permission |
| Error Guessing | Crash після push до ack, duplicate sync requests, corrupt JSON, expired token, DST overlap |

### 12.3. Обов'язкова матриця регресії

- **Offline:** повний CRUD, reorder, bulk, Restore, повторне відкриття без мережі.
- **Sync:** 2 пристрої без мережі, concurrent field edits, confirmed/pending/effective rebase, Ack-before-Pull crash, `ConflictRecovery`, claim у порожню хмару та варіанти (а)/(б) для непорожньої, direct canonical write rejection, delete/edit, stale cursor 45/180/365/400 днів; checkpoint/min_available_seq, epoch barrier та cursor-floor guard, GC-NORMAL без зайвого full download, corrupt staging, unknown ACK (без auto replay), receipt GC, operation replay, split network, server pause.
- **Recurrence:** Ф1a без повторів (повторювані корені та транзитивні діти не імпортуються); Ф2 atomic append-only доімпорт із parent mapping і Complete guard для `needs_review`; детермінований `nextOccurrence` із injected clock/zone, floating travel vs fixed IANA zone, nonexistent DST hour shifted forward (02:30 → 03:30, рахується в COUNT), overlap first offset, RFC5545 UNTIL value type; daily/weekdays/nth weekday/last day/monthly 31/leap-year; complete-last → `exhausted_completed`, skip-last → `exhausted_skipped` без completion; `skip_to_next`/`reschedule_current`/`edit_recurrence_rule` з absolute/relative/completion-based anchors; starting/ending inclusive, reset subtasks, Undo older occurrence with next active, future virtual projections, timezone travel/DST.
- **Backup:** schema version, corrupt checksum, file truncation, clean restore, **SQLite WAL snapshot під навантаженням, зупинка writer/читачів, збій під час копіювання, нестача місця, `integrity_check`, повернення pre-upgrade DB**, restore after sync, `ExternalIdentity`/LocalWorkspace retained, secrets absent, дамп у локальний Supabase **та на чистий новий Supabase-проєкт** з owner remap/Auth/RLS/epoch.
- **Notifications:** exact grant/deny/revoke, snooze, one-shot/occurrence-bound/task-until-completed lifecycle, recurring task + recurring reminder combinations, Skip/Undo/cancel old alarm, post-reboot/Doze/update/permission denied.
- **Import:** one-shot (Ф1a) зі source IDs без повторюваних коренів **і залежного піддерева**, `DeferredTodoistNode` та звіт no-orphans; `--recurring-only` атомарний доімпорт subtree у Ф2 + Complete guard для `needs_review`, Todoist full + incremental catch-up, `ExternalIdentity`→`TodoistMap`, duplicate source IDs, source deletions, local edit conflict, parallel parser conversion, raw filter Ф5.1 → active AST Ф6a, Import Coverage Matrix, `needs_review` warning, cutover.
- **Security:** server-RPC RLS, another user's ID, session expiry, logout/switch account, tokens in logs, signing secrets in CI.
- **Release:** оновлення поверх попередньої версії, підпис із резервної копії keystore, монотонний `versionCode`, **консистентний SQLite/WAL snapshot до міграції + verify/restore**, а не копія одного `.db`.
- **UI/UX:** small screens/large font/TalkBack, en/pl/uk, search 10k tasks, long titles; Quick Add smart-date toggle/buttons/default project, multiline and `//` escaping, Share Target multiple URLs/unsafe URI, Markdown sanitization, widget independent style, calendar unscheduled tray/3-day/slot-create, Filter Smart Groups and deferred raw migration.

### 12.4. Definition of Done кожної фази

- [ ] Acceptance критерії фази перевірені, не лише «код скомпілювався».
- [ ] Тести додані до repository й реально запускалися; звіт із командою, версією інструментів та результатом.
- [ ] CI зелений, релевантні release artifacts збираються, перевірки секретів пройдені.
- [ ] Немає відкритих P0/P1 регресій; кожен відомий виняток задокументований окремо.
- [ ] Нова DB schema має forward migrations, **WAL-safe verified snapshot**, `integrity_check` і restore-test; destructive migration у production відсутня.
- [ ] Оновлення з попередньої релізної версії поверх (тим самим ключем) проходить без втрати даних.
- [ ] README, CHANGELOG, roadmap і decision log оновлено; конфлікти з іншими PR перевірено.
- [ ] Фактичні обмеження (OEM, free backend, браузер) не приховані.

---

## 13. Правила для агентів-розробників

### 13.1. Процес роботи

- Перед фазою: короткий план, залежності, очікувані файли, SQL migration, тести, ризики та API contracts.
- Для кожної фази створити epic/issue, branch/PR; великі фази розбивати на незалежні PR із чіткими інтерфейсами та integration gates.
- Два паралельних агенти працюють у різних гілках; не редагують одночасно ту саму SQL migration або ядро SyncEngine без зафіксованого ownership.
- Не переходити до наступної фази, доки acceptance й обов'язкові gate checks не закриті.
- Після змін: надавати **повні файли та реальні commits**, не лише фрагменти; PR містить тестовий протокол, не голослівне «все готово».
- Якщо технічне рішення не визначене, документувати ADR (Architecture Decision Record), варіанти і ризик; не вигадувати мовчки.
- README, **`CHANGELOG.md` релізів застосунку**, `docs/SPEC_CHANGELOG.md` рішень специфікації, схеми, API/DB contracts і тестові дані підтримувати разом із кодом.

### 13.2. Код, ресурси та стабільність

- Kotlin/Gradle dependency versions pinned у `libs.versions.toml` або еквіваленті.
- `commonMain` — максимальна частка доменної логіки; платформні API через DI/expect-actual.
- Спільна локалізація у `commonMain/composeResources/values/strings.xml`, `values-en`, `values-pl`, `values-uk` (де необхідно); Android platform resources — у `androidApp`.
- Репозиторій ніколи не зберігає production secret keys, auth tokens, keystore; `.gitignore` і secret-scanning у CI.
- Release APK має стабільний signing key; debug і release keys не плутати. Не підписувати недовірені PR production keystore. Резервні копії keystore й процедура відновлення — §3.4.
- Всі database migrations — без втрати даних із тестом від старих версій; кожен API контракт має версію.
- На платформі, де функція неможлива, показати зрозумілий unavailable стан, а не тихе ігнорування.

### 13.3. Артефакти фаз

- `README.md`: setup/build/run/platform limitations.
- `CHANGELOG.md`: **тільки історія збірок/релізів застосунку** (`Unreleased`, Android APK `versionName/versionCode`, Web/Desktop), зміни коду й фікси; до Ф0 має лише `Unreleased — реалізація не розпочата`.
- `docs/SPEC_CHANGELOG.md`: **лише історія редакцій специфікації** v2.0–v2.6.1, аудит v2.5 і ADR-посилання; історичні рішення не є чинними правилами.
- `docs/ARCHITECTURE.md`: модулі та межі відповідальності.
- `docs/UX_SPEC.md` (Ф0): навігація, екрани, Quick Add, states, empty/offline/error/recovery, gestures, accessibility, адаптивність.
- `docs/DATA_MODEL.md`: актуальні таблиці, інваріанти, міграції.
- `docs/SYNC_PROTOCOL.md`: confirmed/pending/effective, rebase, Ack/Pull, operations, claim, cursors, conflict rules, writer gateway, epoch/tombstones.
- `docs/QA_PLAN.md`: матриця, автоматизація, ручні результати.
- `docs/ADR/`: рішення та альтернатива, якщо відхилилися від плану.
- `docs/MIGRATION_TODOIST.md` (Ф1a, Ф5.1): one-shot імпорт, mappings, exclusions, фрази повторів, dry-run results, cutover checklist.
- `docs/IMPORT_COVERAGE.md` (Ф1a → Ф5.1 → Ф6a): матриця полів/доступності Todoist, dry-run counts, metadata-only статуси, невирішені фільтри й рішення про cutover.
- `docs/RECURRENCE_CONTRACT.md` (Ф2): таблиця `Complete/Skip/Reschedule/Edit rule/Undo`, absolute vs relative anchors, imported pending, ReminderLifecyclePolicy і golden next-date cases.
- `docs/RELEASE.md` (Ф0): канал оновлень, версіонування, процедура відновлення keystore.
- `docs/OPERATIONS.md` (Ф5a): auth-gated/optional heartbeat, серверний backup, перевірка відновлення.
- `docs/DISASTER_RECOVERY.md` (Ф5b): новий Supabase проєкт, auth remap, RLS/secrets/cron, epoch, client recovery, RPO/RTO.

### 13.4. Робота з цим документом

- **v2.6.1 — фінальний заморожений baseline після останнього рев'ю.** Нові теоретичні перевидання не плануються. Подальші зміни специфікації — точкові ADR у `docs/ADR/`, оновлення нормативного `docs/` і запис у `docs/SPEC_CHANGELOG.md`; реалізовані зміни — в `CHANGELOG.md`. Відхилення дозволяється з документованою причиною, тестами й власником рішення.
- Історія версій специфікації й аудити ведуться в `docs/SPEC_CHANGELOG.md`, а не в релізному `CHANGELOG.md` або цьому нормативному документі; тут — лише чинні правила й коротка дельта patch baseline (§0.1).
- У Ф0 агент переносить розділи у файли з §13.3: §4 → `docs/DATA_MODEL.md`, §8 → `docs/SYNC_PROTOCOL.md`, §12 → `docs/QA_PLAN.md`, §10.4 → `docs/DISASTER_RECOVERY.md`, §5–§7 → `docs/UX_SPEC.md` (функціональні вимоги), §4.3/§5.5/§7 → `docs/RECURRENCE_CONTRACT.md`, §9.3 → `docs/IMPORT_COVERAGE.md`. Цей документ лишається індексом: мета й рамки, роадмап, gate-критерії, рішення (§1, §11, §14) з посиланнями на файли.
- Агент кожної фази читає індекс і лише ті файли, на які посилається його фаза.
- Будь-яка зміна нормативного рішення — ADR у `docs/ADR/` + запис у `docs/SPEC_CHANGELOG.md` + оновлення відповідного файлу; кожна реалізована зміна — у релізному `CHANGELOG.md`. Номер версії індексу зростає лише при зміні фаз або gate-критеріїв.

---

## 14. Прийняті рішення та питання на перевірку

### Прийняті

- [x] Один користувач; Android-first, потім Web та Desktop.
- [x] Kotlin Multiplatform і Compose Multiplatform з Ф0.
- [x] Room 3 для локальної БД, Koin, Kotlin coroutines/Flow.
- [x] Supabase (Postgres/Auth/Realtime/Edge Functions) як бекенд.
- [x] Синхронізація тільки через typed outbox + server change log/versions, без row-LWW за timestamp.
- [x] Todoist тільки → застосунок, Ф5.1; без write-back у Todoist.
- [x] Мови українська, англійська, польська.
- [x] Android minSdk 26; side-loaded підписаний APK.
- [x] Мінімальний backup з Ф1a; recurrence history з Ф2.
- [x] Ф1a — щоденний мінімум для раннього використання; Ф1b — решта офлайн-ядра.
- [x] Фільтри, пошук і шаблони у Ф6a; дошка й календар у Ф6b; історія, backup, налаштування й переклади у Ф6c.
- [x] `change_seq`: `pg_advisory_xact_lock` на акаунт + лічильник через **єдиний runtime mutation gateway** для sync/import/bulk; прямі canonical writes клієнтським ролям заборонені.
- [x] Конфлікт одного поля у v1: перемагає сервер, локальне значення — у `ConflictRecovery` (та історії за можливості) з дією «Повернути моє»; підтверджений/pending/effective rebase.
- [x] Повтори з Todoist конвертує клієнтський KMP-парсер через `ImportPending`; нерозпізнане → `needs_review`.
- [x] APK розповсюджується через GitHub Releases; keystore має дві незалежні резервні копії.
- [x] Мітки в Quick Add: `%` за замовчуванням, `@` — опція.
- [x] Зовнішній heartbeat **опційний**, лише через захищену Edge Function із вузьким секретом або повністю вимкнений; publishable key **не** достатня автентифікація. Серверний backup і повний disaster recovery — з Ф5.
- [x] `LocalWorkspace` і `ExternalIdentity` з Ф0/Ф1a, прив’язка локальних даних до акаунта у Ф5a без втрати Inbox/ID; у v1 claim лише в порожню хмару, повне злиття двох непорожніх workspace — Ф9.
- [x] Повтори мають інваріант **0..1** active occurrence; виконання останньої появи виконує задачу; майбутні календарні появи — віртуальні й показуються за перемикачем, типово вимкненим.
- [x] Ф3 містить базові recurring reminders разом із граматикою `!` у Quick Add; Ф6b — 3-day/unscheduled calendar, Ф6a — багатогрупові Smart Views.
- [x] Готовність: Dogfood Ф1a / Android Daily Driver Ф4 / Migration Ready Ф5.1 / Full **Android** Replacement Ф6c / Web + Android Ф7 / Cross-platform Personal Replacement Ф8.
- [x] Мова фільтрів сумісна з Todoist (`date`/`due`/`deadline`, кома для груп); **raw фільтри Todoist зберігаються у Ф5.1, перетворюються в активні Smart Views у Ф6a**.
- [x] Ф5 розділена на Ф5a (один пристрій) і Ф5b (кілька пристроїв, DR); DR на чистому проєкті — gate перед cutover.
- [x] Effective state — звичайні таблиці Room; `ConfirmedSnapshot` лише для сутностей із pending-операціями.
- [x] Gateway має два входи: клієнтський `sync_push` і внутрішній `apply_ops` лише для серверної ролі.
- [x] v2.6.1 — заморожений передімплементаційний baseline після фінального рев'ю; зміни через ADR + `docs/SPEC_CHANGELOG.md` і пофазне `docs/` (§13.4); runtime-перевірки — лише з артефактами.
- [x] DST-розрив у повторах: неіснуючий час зсувається вперед і рахується в `COUNT`.
- [x] Історія специфікації — у `docs/SPEC_CHANGELOG.md`; `CHANGELOG.md` — лише релізи; документ містить лише чинні правила.
- [x] Web fallback: спершу SQLite-WASM поверх IndexedDB під Room, потім `LocalStore` на IndexedDB.
- [x] Командна робота/iOS/продуктивність/вкладення не входять до основного релізу.
- [x] Повтори: у Ф1a немає (повторювані задачі лишаються в Todoist до Ф2); Ф2 — append-only доімпорт і захищений Complete для `needs_review`, Ф2 `skip_to_next/reschedule_current/edit_recurrence_rule`, completion/skip останньої появи — різні статуси.
- [x] `ReminderLifecyclePolicy` відрізняє one-shot/occurrence-bound/task-until-completed; непідтверджений ImportPending не має права зникнути мовчки.
- [x] `ConfirmedSnapshot` зберігається **до першої pending правки** в одній транзакції; нові local creates не мають фальшивої server revision.
- [x] Backup у непорожню хмару — тільки контрольований append-only з FK/UUID/source ID dry-run і atomic abort при колізії.
- [x] Quick Add settings/Share Target/Rich URLs/Calendar slot create мають власні gates у Ф2/Ф1b/Ф6b.
- [x] Todoist `Import Coverage Matrix` є доказом повноти migration та явних виключень до cutover.

### Потрібно перевірити в межах конкретної фази (не блокують прийняту концепцію)

- [ ] Ф0 **runtime gate, не концептуальне питання:** зафіксувати перевірену комбінацію Kotlin/Compose/AGP/KSP/Room3/supabase-kt для Android/JVM/Web у `docs/TECH_COMPATIBILITY.md` із log evidence; для недоступної цілі прийняти ADR і перевірити adapter fallback (див. §3.3.1).
- [ ] Ф0 **runtime gate:** WebWorkerSQLiteDriver/OPFS на тестових браузерах, або worker на SQLite-WASM поверх IndexedDB, або (останній рівень) `LocalStore` на IndexedDB; browser matrix і full Web release gate завершити у Ф7 (§3.3.1).
- [ ] Ф0: Obtainium із приватним репозиторієм чи вбудована перевірка оновлень (ADR).
- [ ] Ф0 **runtime gate:** перевірити COOP/COEP і headers фактично обраного worker; хостинг/ADR зафіксувати до Ф7.
- [ ] Ф5b: навантажувальна перевірка advisory lock при одночасній синхронізації трьох пристроїв.
- [ ] Ф2: UX вибору неоднозначних дат; типова дія для повторюваного батька («Виконати» чи «Виконати й скинути підзадачі»).
- [ ] Ф3: точний manifest/permission strategy на підтримуваних Android-версіях та OEM.
- [ ] Ф2: перелік фраз повторів Todoist, які має розуміти парсер (на основі реальних записів `ImportPending`).
- [ ] Ф9: чи потрібен повний UI конфліктів — за статистикою перезаписів у Sync Health.
- [ ] Ф5b **implementation gate (політика вже прийнята):** реалізувати 180-day log / 365-day tombstone / 365-day receipts, checkpoint+epoch та детальний full reconciliation із §8.3; показати тести 400 days offline/unknown ACK. Чистий Disaster Recovery із новим Auth UID залишається обов’язковим.
- [ ] Ф5.1: фактично доступні Todoist Sync API поля, limit/rate/permissions, completed history та scope exclusions.
- [ ] Ф5.1: які конструкції фільтрів Todoist не мають відповідника в AST (звіт dry-run).
- [ ] Ф6c: retention/history/backup storage volume і шифрування архівів.
- [ ] Ф8: які саме Desktop OS підтримує перший installer (план — Windows спершу).

---

## 15. Офіційні технічні джерела для імплементації

*Це довідкові зовнішні джерела станом на 01.11.2026; перед оновленням залежностей і початком відповідної фази перевірити актуальність.*

- RFC 5545 iCalendar RRULE (`DTSTART`, `COUNT`, `UNTIL`, DST invalid candidates): https://www.rfc-editor.org/rfc/rfc5545
- JetBrains Compose Multiplatform compatibility/support matrix: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- JetBrains Web/Wasm beta status: https://kotlinlang.org/docs/multiplatform/faq.html
- supabase-kt target/module support (3.x Wasm supported modules only): https://github.com/supabase/supabase-kt
- Android Room 3 release notes: https://developer.android.com/jetpack/androidx/releases/room3
- Compose Multiplatform resources/localization: https://kotlinlang.org/docs/multiplatform/compose-localize-strings.html
- Android exact alarms: https://developer.android.com/develop/background-work/services/alarms
- Android 14 default denial for exact alarms: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Todoist API v1: https://developer.todoist.com/api/v1/
- Todoist Quick Add: https://www.todoist.com/help/todoist/features/use-task-quick-add-in-todoist-va4Lhpzz
- Todoist labels (`%` and legacy `@`): https://www.todoist.com/help/todoist/features/introduction-to-labels-dSo2eE
- Supabase RLS/Data API security: https://supabase.com/docs/guides/api/securing-your-api
- Supabase Free project pausing: https://supabase.com/docs/guides/platform/free-project-pausing
- Supabase backups: https://supabase.com/docs/guides/platform/backups
- Todoist: перехід міток на `%` і плани щодо `@`: https://todoist.com/help/articles/how-to-best-use-labels
- Todoist API, структура Due (без RRULE): https://doist.github.io/todoist-api-typescript/api/type-aliases/DueDate
- Приклад Room 3 + SQLite-WASM з OPFS і заголовками COOP/COEP: https://github.com/ohs-foundation/kotlin-fhir-engine/wiki
- GitHub: автовимкнення scheduled workflows у публічних репозиторіях: https://docs.github.com/en/enterprise-server@3.12/actions/managing-workflow-runs/disabling-and-enabling-a-workflow
- Obtainium (оновлення APK із GitHub Releases): https://github.com/ImranR98/Obtainium
- Todoist: фільтри (`date`/`due`/`deadline`, кома для окремих списків): https://todoist.com/help/articles/introduction-to-filters-V98wIH
- Todoist: календарний вигляд (No date, 3-day на Android, Future occurrences): https://www.todoist.com/help/articles/use-the-calendar-layout-in-todoist-lPHRQTu0o
- Todoist: повторювані дати (`starting`, включна кінцева дата, підзадачі): https://todoist.com/help/articles/introduction-to-recurring-due-dates-YUYVJJAV
- Todoist: кілька задач одразу: https://todoist.com/help/articles/6246468182684
- Todoist: recurring dates, absolute/relative schedule та поведінка після перенесення (оновлено 30.10.2026): https://www.todoist.com/help/todoist/features/introduction-to-recurring-dates-YUYVJJAV
- Todoist: custom/recurring reminders, one-shot on recurring task (оновлено 26.10.2026): https://www.todoist.com/help/todoist/features/introduction-to-reminders-9PezfU

## 16. Заключна матриця простежуваності та freeze gate v2.6.1

Ця матриця — **специфікаційна перевірка**, а не твердження про реалізовані тести. Агент не може вважати вимогу готовою тільки тому, що вона записана в roadmap; посилання на код/тест додається у відповідній фазі.

| ID | Обов'язковий контракт | Фаза-критерій | Доказ виконання |
| --- | --- | --- | --- |
| TR-01 | Ф1a відкладає повторювані **корені з усім залежним підграфом**, Ф2 — atomic subtree доімпорт і Complete guard | Ф1a/Ф2 | `DeferredTodoistNode` + no-orphan/source ID counts + import graph tests |
| TR-02 | `skip_to_next`, `reschedule_current`, `edit_recurrence_rule` та exhausted skip | Ф2 | Golden recurrence fixture / property tests |
| TR-03 | ReminderLifecyclePolicy для occurrence/task і скасування старих alarms | Ф3 | Device notification transition matrix |
| TR-04 | Quick Add settings + Share Target URL/multi + `source_url` | Ф1b/Ф2 | Compose/Android intent tests та security tests |
| TR-05 | `ConfirmedSnapshot` before-first-edit + rebase/rollback | Ф5b | SQLite transactions + crash/replay integration tests |
| TR-06 | Append-only backup import into nonempty cloud, no name-based merge | Ф5a | RPC conflict/FK/idempotency tests |
| TR-07 | Todoist raw filter import і import coverage | Ф5.1 | Raw payload/report counts, no orphaned source ID |
| TR-08 | Raw Todoist filter → activated Smart View | Ф6a | AST equivalence, unsupported remains `needs_review` |
| TR-09 | Calendar create from slot with Undo and accessibility | Ф6b | Compose UI + TalkBack alt-flow |
| TR-10 | Safe Markdown in description/comments | Ф6c | Renderer sanitization/round-trip tests |
| TR-11 | Full Android Replacement checklist | Ф6c | Personal scenarios, no unresolved P0/P1 |
| TR-12 | Cross-platform Personal Replacement | Ф8 | Android/Web/Desktop e2e + restore |
| TR-13 | Time/recurrence policy: DTSTART, gap/overlap, floating/fixed, COUNT/UNTIL і completion zone | Ф1a/Ф2 | Чисті golden fixtures + commonTest на Android/JVM/Web |
| TR-14 | Cross-platform compatibility matrix + offline Web fallback | Ф0/Ф7 | `docs/TECH_COMPATIBILITY.md`, CI logs, runtime restart/upgrade + ADR |
| TR-15 | Server GC/checkpoint/epoch/full reconciliation retention 180/365/365 | Ф5b | Clock-controlled 400-day offline + unknown ACK + interrupted snapshot integration tests |
| TR-16 | Консистентний SQLite/WAL snapshot і restore перед upgrade, без `copy(main.db)` на живій БД | Ф0 і кожна міграція | WAL writes + crash/disk-full + checksum + `integrity_check` + semantic restore |
| TR-17 | Серверна канонічна перевірка повторів (trusted Deno evaluator + atomic gateway) | Ф5b | Kotlin/Deno parity corpus; concurrent action/stale plan/unauthorized proposal tests |
| TR-18 | Первинний claim поглинає історичний outbox тільки через `ClaimBootstrapReceipt`, без double replay | Ф5a | 1 000 preclaim edits, unknown ACK/retry, source digest, one Inbox |
| TR-19 | Ф0: Android `pass`, Web/Desktop `pass` або `blocked+ADR` без фальшивої перевіреності | Ф0 | `TECH_COMPATIBILITY.md` + Android CI + platform ADR |
| TR-20 | Роздільні історії специфікації та застосунку | Ф0 | `docs/SPEC_CHANGELOG.md` і релізний `CHANGELOG.md` без історичних норм як поточних |

**Загальна передстартова перевірка у Ф0:** посилання `§`/фази, однозначність інваріантів, відсутність фічі у gate до появи її engine, збереження raw Todoist даних, відсутність втрачених confirmed/pending записів, рішення про безпечні URL/скрипти/секрети, і контроль включення всіх 12 пунктів цього аудиту. Виявлену помилку виправляють цільовим ADR/PR та тестом, а не новою повною редакцією roadmap.

**Stop condition:** після прийняття цього baseline не додавати нові необов'язкові можливості в Ф0–Ф6c без окремого погодження, доки не отримано реальний Android APK і результати dogfooding. Винятки — втрата даних, безпека, порушення офіційного API/ОС, блокування основного сценарію. Ф9 лишається опційним backlog.


---

**Ключове рішення (з v2.0, без змін):** ніяке покращення UI чи нова функція не може виправдовувати втрату, непомітне перезаписування або неможливість відновлення задач. Саме це перевіряють QA gates кожної фази.

**Версія 2.6.1:** документ описує **консолідований погоджений baseline-план**, а не вже розроблені фічі. Усі P0 контракти (`ClaimLocalWorkspace`, `ExternalIdentity`, rebase, recurrence state machine, writer gateway, disaster recovery) є обов’язковими gate-критеріями відповідних фаз, а не твердженням, що їх уже реалізовано.
