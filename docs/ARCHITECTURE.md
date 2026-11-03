# ARCHITECTURE — модулі й межі відповідальності

- **Статус:** нормативний текст специфікації v2.6.1 (§3), перенесений з [`task-manager-scope-roadmap-v2.6.1.md`](../task-manager-scope-roadmap-v2.6.1.md) без змін змісту (3 листопада 2026 року, §13.4). Змінюється лише через ADR + [`docs/SPEC_CHANGELOG.md`](SPEC_CHANGELOG.md).
- **Фази:** усі; модулі, правила доступу до даних, матриця сумісності, розповсюдження APK.
- Заголовки перенесених розділів мають знак «§» і номер зі специфікації; посилання «§N» у тексті ведуть на розділи специфікації (де лежить кожен розділ — у таблиці §0.3 індексу).
- Фактичні результати перевірок сумісності — [`docs/TECH_COMPATIBILITY.md`](TECH_COMPATIBILITY.md); рішення, що замінюють частини §3, — у [`docs/ADR/`](ADR/README.md) (ADR-001 драйвер SQLite, ADR-003 канал оновлень, ADR-004 сховище браузера).

## Фактична структура (Ф0)

Залежності йдуть в один бік: застосунки → `ui` → `core:domain` → `core:database` → `core:model`. `core:sync` поки незалежний (Ф0-проба supabase-kt); `core:testing` — фікстури для тестів і self-test.

| Модуль | Source sets | Що містить |
| --- | --- | --- |
| `core/model` | `commonMain`, платформні `actual` | Доменні типи (`Task`, `Project`, `OutboxOperation`…), `IdGenerator`, ключі порядку, `CivilTime` і IANA-зони (на Web — `@js-joda/timezone`) |
| `core/database` | `commonMain`; `nonWebMain` (Android + JVM); `wasmJsMain` | Room 3: `AppDatabase`, entities, DAO, `LocalStore` (entity + activity + outbox в одній транзакції), діагностика; `nonWebMain` — `DatabaseOpener` (FIX-01: snapshot, відкат), `BundledSQLiteDriver`; `wasmJsMain` — `createWebAppDatabase` (ADR-004) |
| `core/domain` | `commonMain` | `TaskRepository`: усі мутації, створення workspace й Inbox, діагностика |
| `core/sync` | `commonMain` | Ф0: `SupabaseProbe` (Auth, PostgREST/RPC, Realtime). `SyncEngine` — Ф5a |
| `core/testing` | `commonMain`, `nonWebMain` | `DatabaseScenarios`, `SchemaV1Fixture`, `V1Database`, `TimeGoldenFixtures` — спільні для JVM, Android і браузерів |
| `ui` | `commonMain` | Compose-екрани (Inbox, «Про застосунок»), ViewModels, тема, рядки en/uk/pl (`composeResources`), `uiModule` (Koin) |
| `androidApp` | `main`, `debug`, `androidTest` | `Application`/`Activity`, Koin-зв'язування, instrumented-тести |
| `desktopApp` | `jvmMain` | Вікно Compose Desktop (proof of concept; продукт — Ф8) |
| `webApp` | `wasmJsMain`, `worker/`, `e2e/` | Compose/Wasm, worker SQLite WASM (`opfs-sahpool`), self-test (`?selftest=…`), тести Playwright |
| `supabase/` | — | Локальний стек для CI (`config.toml`) і міграція Ф0-проби |
| `tools/ci`, `tools/release`, `tools/deps` | — | Скрипти CI (емулятори, Xvfb, звіт PR), релізу власника (ADR-003), перевірки версій |
| `.github/workflows` | — | `ci.yml` (кожен PR), `release.yml` (тег `vX.Y.Z`), `deps-probe.yml` |

Платформи підключають базу, `TaskRepository` і `AppInfo` у своїх точках входу; спільний `uiModule` дає ViewModels.

---

## §3. Стек та архітектура

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

### §3.1. Модулі

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

### §3.2. Правила доступу до даних

- UI → ViewModel → Repository → DAO/Room; мережевий запис/читання здійснює тільки SyncEngine та явно виділені adapters.
- Усі локальні мутації, включно з віджетом, notification action, імпортом і відновленням, проходять через однакову валідацію. Винятки (bulk sync apply/restore) — окремі контрольовані транзакційні сценарії, без циклу локальних outbox-операцій.
- В одній транзакції: **якщо перша pending-операція над уже синхронізованою сутністю, спочатку зберегти immutable `ConfirmedSnapshot` зі стану ДО правки**; після цього змінити сутність/effective projection, збільшити локальну версію, записати `TaskActivity` (за потреби), додати типізовану `OutboxOperation`. Новостворена локальна сутність до першого серверного Ack має `base_revision = null` і явний стан `locally_created`, без фальшивого server snapshot. Після Ф5 підтверджений серверний snapshot та pending overlay зберігаються окремо: екран читає **effective state**, а не неперевірену серверну копію.
- **Inbound sync transaction:** застосувати server changes до confirmed state, зберегти новий `SyncCursor`, переобчислити effective state з pending операцій у їх порядку; rejected/conflicting patches перенести у recoverable drafts із журналом, ніколи не стирати їх мовчки.
- Починаючи з Ф1a, локальні `ExternalIdentity` і `LocalWorkspace` зберігаються разом із backup для майбутньої Ф5/Ф5.1. Імпортне source ID не замінюється на UUID новоствореної задачі.
- Планувальник нагадувань і updater віджетів спостерігають за змінами Room/Domain events; мережі напряму не викликають.
- Не виконувати руйнівну міграцію бази за замовчуванням (`fallbackToDestructiveMigration` — заборонити у production).

### §3.3. Ранній кросплатформний spike (Ф0, без повного UI)

Мінімальний «одна задача — три таргети» тест: Android, Chromium-based browser через `WebWorkerSQLiteDriver` та JVM desktop. Статус технологій із документа — **candidate**, не `tested`. Перевірити create/read/update після закриття й повторного відкриття, схему та міграції, транзакцію з outbox, FTS5, часові зони й роботу в CI. Для Web — Chromium і Firefox; Safari (за доступності підтримуваного WasmGC), private mode, quota/eviction, очищення сховища. Кожен сценарій має відтворюваний command + CI artifact. Без проходження spike Web не можна оголошувати сумісним.

#### §3.3.1. Матриця технічної сумісності та fallback (V25-STACK)

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

### §3.4. Розповсюдження APK і ключ підпису (`P0`)

- **Ключ підпису.** Release keystore створюється у Ф0 й одразу копіюється у два незалежні місця (наприклад, менеджер паролів і зашифрований офлайн-носій) разом із паролями та alias. Без цього ключа нову версію неможливо встановити поверх старої, а видалення застосунку знищує локальні дані. Ключ ніколи не потрапляє в git; у CI — лише як захищений secret для збірок із захищеної гілки або тегу.
- **Канал оновлень.** Підписаний release APK публікується в GitHub Releases за тегом `vX.Y.Z`. На телефоні — Obtainium, який відстежує релізи (для приватного репозиторію потрібен read-only токен — перевірити у Ф0), або вбудована перевірка нової версії з посиланням на завантаження. Вибір фіксується ADR у Ф0.
- **Версії.** `versionCode` монотонно зростає й виводиться з тегу або номера збірки CI; `versionName` = тег. Debug-збірки мають окремий суфікс `applicationId`, щоб не конфліктувати з робочою установкою.
- **Захист міграцій SQLite/Room (FIX-01, P0).** Перед upgrade схеми створити **консистентний** snapshot усієї БД. Просте `copy(main.db)` при активному SQLite WAL **заборонене**: потрібен підтримуваний SQLite online-backup механізм/`VACUUM INTO` **лише якщо він доступний на конкретному runtime**, або контрольована холодна копія після зупинки усіх writer/reader, коректного checkpoint/close та обліку `-wal`/`-shm`. Для Android minSdk 26 **не припускати** доступності `VACUUM INTO` чи Backup API — перевірити під час Ф0 та визначити platform adapter. Рівно одна операція upgrade одночасно; під час snapshot/restore жодна інша точка входу (WorkManager, widget, інший процес) не пише в Room. Backup у тимчасовій директорії/файлі, з атомарною публікацією, checksum, тестовим відкриттям, `PRAGMA integrity_check` і порівнянням кількості/ключових FK; якщо валідація не пройшла — **не починати** руйнівну міграцію. Після збою відновлювати **цілісний узгоджений набір файлів/стан БД**, не відкривати частково перенесену БД як нормальну. Перед upgrade перевірити достатній обсяг сховища; за браку місця зупинитися без втрат. Зберігати перевірений pre-upgrade snapshot до успішного відкриття нової схеми та контрольного semantic verification. На Web/Desktop механізм snapshot адаптується до відповідного storage API й проходить аналогічні тести.
- **Перевірка.** Оновлення з попередньої релізної версії поверх, без втрати даних, — пункт DoD кожної фази, що змінює схему БД. Gate: записи у WAL до upgrade, crash під час snapshot/міграції, нестача місця, невдала валідація й відновлення із контрольного snapshot; `integrity_check` та semantic equality зелені.
