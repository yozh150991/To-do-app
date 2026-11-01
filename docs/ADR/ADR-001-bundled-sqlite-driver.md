# ADR-001: `BundledSQLiteDriver` для Android і Desktop

- **Статус:** Accepted
- **Дата:** 1 листопада 2026 року
- **Власник рішення:** власник проєкту
- **Доповнює / замінює у v2.6.1:** §3 (рядок «Локальна БД»), §3.3.1 (рядок Room/SQLiteDriver), §3.4 (FIX-01, механізм snapshot), Ф0 (gate-критерії), §12.3 (Backup/Release), §16 (TR-16)

## Контекст

- Специфікація з v2.0 спирається на FTS5: пошук (§5.7, Ф6a), таблиця стеку (§3), матриця сумісності Ф0 (§3.3.1). При цьому конкретний драйвер SQLite для Room 3 ніде не зафіксовано.
- Вбудований в Android (framework) SQLite не вмикає модуль FTS5. Користувачі Room 3 повідомляють про помилку `no such module: FTS5` при створенні FTS5-таблиць на Android.
- Збірка `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`) компілює SQLite з вихідного коду з прапорцями `SQLITE_ENABLE_FTS5`, `FTS3`, `FTS4`, `JSON1`, `RTREE`.
- FIX-01 вимагає консистентного snapshot БД перед міграцією і прямо забороняє припускати наявність `VACUUM INTO` на minSdk 26: версія системного SQLite залежить від версії Android і виробника.

## Рішення

1. **Android і Desktop (JVM)** використовують Room 3 з `BundledSQLiteDriver`. Системний `AndroidSQLiteDriver` у production не використовується.
2. Версії `androidx.sqlite:sqlite-bundled` і Room 3 фіксуються разом у `gradle/libs.versions.toml`; оновлюються однією зміною з прогоном міграційних тестів.
3. **Web** не змінюється: `WebWorkerSQLiteDriver` + SQLite-WASM у worker (§3.3.1). Наявність FTS5 у WASM-збірці worker перевіряється у Ф0. Якщо FTS5 немає, пошук на Web тимчасово працює через `LIKE`, а обмеження фіксується в `docs/TECH_COMPATIBILITY.md`.
4. **Snapshot перед міграцією (уточнення FIX-01):**
   - основний механізм — `VACUUM INTO '<тимчасовий файл>'` на тому ж bundled SQLite, якщо фактична `sqlite_version()` його підтримує (SQLite ≥ 3.27);
   - далі незмінно за FIX-01: `PRAGMA integrity_check` на копії, checksum, атомарне перейменування, перевірка вільного місця, лише одна операція upgrade, жодних інших writer-ів, snapshot зберігається до semantic verification нової схеми;
   - резервний механізм, якщо `VACUUM INTO` недоступний: закрити всі з'єднання Room, `PRAGMA wal_checkpoint(TRUNCATE)`, холодна копія основного файлу з урахуванням `-wal`/`-shm`.
5. **FTS5-токенайзер** обирається у Ф0 за тестами на українських, польських і англійських назвах (стартовий кандидат — `unicode61`); вибір фіксується в `docs/DATA_MODEL.md`.

## Наслідки

**Плюси**
- FTS5 гарантовано доступний на всіх версіях Android від minSdk 26 і на Desktop.
- Однакова версія й поведінка SQLite на Android і Desktop, незалежно від прошивки виробника.
- FIX-01 спрощується: консистентний snapshot однією командою замість окремих платформних адаптерів.

**Мінуси та як їх компенсувати**
- APK більшає на нативні бібліотеки SQLite для кожної ABI. Для власного телефона release можна збирати лише під `arm64-v8a` (ABI split); розмір фіксується в `docs/TECH_COMPATIBILITY.md`.
- Оновлення безпеки SQLite приходять лише з оновленням застосунку, а не з оновленням Android. Залежність перевіряється при кожному релізі.
- Bundled SQLite скомпільовано в режимі multi-thread; з'єднаннями керує Room, ручні з'єднання поза Room заборонені.

## Перевірка (додається до gate Ф0)

- [ ] `select sqlite_version()` і `PRAGMA compile_options` залоговано на Android (емулятор API 26 і реальний телефон) та JVM; `ENABLE_FTS5` присутній.
- [ ] FTS5-таблиця створюється й шукає по українських, польських і англійських рядках на API 26 і на актуальній версії Android.
- [ ] `VACUUM INTO` під навантаженням записів у WAL дає копію, що проходить `integrity_check` і semantic comparison; сценарії crash і нестачі місця — за FIX-01.
- [ ] Приріст розміру APK записано; рішення щодо ABI split зафіксовано.

## Розглянуті альтернативи

- **Системний `AndroidSQLiteDriver`.** Немає FTS5; версія SQLite різна на різних пристроях; FIX-01 потребує складних адаптерів. Відхилено.
- **FTS4 замість FTS5 на системному SQLite.** Слабші можливості ранжування й токенізації, різна поведінка на Android і Desktop, а `VACUUM INTO` однаково не гарантований. Відхилено.
- **Власна збірка SQLite через NDK.** Те саме, що дає bundled-драйвер, але з власною підтримкою збірки. Відхилено.

## Джерела

- Kotlin Slack, «Room 3 with FTS5»: https://slack-chats.kotlinlang.org/t/34195444/room-3-with-fts5
- androidx `sqlite-bundled`, прапорці компіляції: https://android.googlesource.com/platform/frameworks/support/+/f66fa79d19e5874f0afaa6868363c69ecf851cf2/sqlite/sqlite-bundled/build.gradle
- Room 3 release notes: https://developer.android.com/jetpack/androidx/releases/room3
