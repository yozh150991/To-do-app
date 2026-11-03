# CHANGELOG — Task Manager (застосунок)

Історія **реальних релізів застосунку** (`versionName` / `versionCode`), а не редакцій технічної специфікації.

## [Unreleased]

Web/Desktop релізів і production-міграцій ще немає. Чинне технічне завдання: [`task-manager-scope-roadmap-v2.6.1.md`](task-manager-scope-roadmap-v2.6.1.md); історія рішень специфікації: [`docs/SPEC_CHANGELOG.md`](docs/SPEC_CHANGELOG.md).

### Ф0 — у роботі

- Каркас Kotlin Multiplatform: `core/model`, `core/database`, `core/domain`, `ui`, `androidApp`, `desktopApp`; версії зафіксовано в `gradle/libs.versions.toml` (Kotlin 2.4.21, AGP 9.5.0, Gradle 9.7.0, Compose Multiplatform 1.12.2, Room 3.0.3, SQLite bundled 2.7.1; Android compileSdk/targetSdk 37, minSdk 26). Статуси сумісності — `docs/TECH_COMPATIBILITY.md`.
- Room 3 schema v1: `local_workspace`, `project`, `task`, `task_activity`, `outbox_operation`, `external_identity` (`docs/DATA_MODEL.md`).
- `TaskRepository`: створення, перейменування, виконання/повернення, м'яке видалення/відновлення; кожна зміна пише сутність, activity й outbox в одній транзакції.
- Мінімальний екран Inbox (Android і Desktop).
- Schema v2: `section`, `label`, `task_label`, зовнішній ключ `task.section_id` (AutoMigration 1 → 2).
- Безпечне оновлення схеми (FIX-01): `VACUUM INTO` snapshot з перевіркою й sha256, маркер незавершеного оновлення, автоматичний відкат, перевірка вільного місця, відмова від downgrade. База відкривається один раз поза головним потоком; помилка відкриття показується в UI.
- CI на GitHub Actions: JVM-тести, debug APK, Desktop JAR, instrumented-тести на емуляторах API 26 і API 37, зведений звіт із runtime-доказами в PR.
- Release-підпис із змінних середовища (ключ ніколи не в git), release APK лише `arm64-v8a`, `versionCode`/`versionName` з тегу. Workflow `Release`: тег `vX.Y.Z` на `main` → підписаний APK і `.sha256` у GitHub Releases з перевіркою закріпленого сертифіката. На кожному PR: release-збірка з одноразовим ключем і тест оновлення поверх попередньої версії на емуляторі (`docs/RELEASE.md`).

- Web proof of concept (Kotlin/Wasm): той самий Compose-екран Inbox і Room 3 у браузері через `WebWorkerSQLiteDriver` і власний worker на SQLite WASM з OPFS (`opfs-sahpool`); працює без заголовків COOP/COEP. Тести Playwright у Chromium, Firefox і WebKit: збереження після перезавантаження, нової вкладки й перезапуску браузера, атомарність outbox, FTS5, міграція v1 → v2, `VACUUM INTO`, очищення сховища, задача з поля вводу UI (ADR-004).
- Desktop: CI запускає упакований застосунок (jpackage-образ) на віртуальному дисплеї, вводить задачу у вікні, жорстко завершує процес і перевіряє базу та повторний запуск (`tools/ci/desktop-ui-test.sh`).
- Часова модель: `CivilTime` — цивільна дата/час + IANA-зона → момент за правилами spec §4.0.1 (DST-розрив і перекриття, fixed/floating). Golden fixtures TIME-01..04, TIME-08 проходять на JVM, Android API 26/37 і в Chromium, Firefox, WebKit; на Web IANA-правила дає `@js-joda/timezone`.
- Локалізація інтерфейсу: рядки в Compose Multiplatform resources (англійська за замовчуванням, українська, польська), мова — з налаштувань системи; повідомлення про помилки — типізовані й перекладені.
- Матриця модулів supabase-kt 3.8.0: Auth, PostgREST/RPC і Realtime перевірено на Android (емулятор API 37), JVM і в Chromium, Firefox, WebKit проти локального Supabase у CI. Обхід KT-87116 для Web-бандла.
- CI: дії GitHub оновлено до версій на Node 24 (checkout 7, setup-java 6, setup-node 7, upload-artifact 7, download-artifact 8, setup-gradle 5); probe показує нові мажорні версії з їхніми release notes.
- Скрипти власника: `tools/release/create-keystore.sh` (ключ підпису) і `tools/release/build-release.sh` (основна збірка релізу на комп'ютері: перевірка `main`, версії й ключа, підписаний APK у `dist/`, тег, встановлення через USB). Запасна збірка — workflow `Release` на GitHub. CI проганяє обидва скрипти наскрізно на Linux і Windows (ADR-003).
- Навігація за `docs/UX_SPEC.md` §2.1–§2.2: вкладки «Вхідні · Сьогодні · Майбутні · Огляд», старт «Сьогодні», «Назад» повертає до стартової вкладки, нижня панель на телефоні й rail від 600 dp; власні іконки вкладок. Прапорці функцій: незавершені вкладки видно лише в development-збірках (debug APK, `:desktopApp:run`, `?features=all`), release показує лише «Вхідні».
- Версії QA-інструментів закріплено й перевірено в CI (spec §12.1): Kotest property, Turbine, Roborazzi (скріншоти Inbox en/uk/pl і «Про застосунок»), Maestro на емуляторах API 26/37, pgTAP через `supabase test db`, Toxiproxy перед локальним Supabase; Robolectric закріплено для Ф1a (`docs/TECH_COMPATIBILITY.md`).
- Діалог «Про застосунок» (кнопка у верхній панелі): версія і `versionCode`, пристрій, версія SQLite, наявність FTS5, схема, режим журналу, `quick_check`, кількість задач і операцій, що чекають синхронізації. Текст можна виділити й скопіювати; скріншот із телефона — доказ для gate Ф0 (ADR-001). Ті самі дані через Room перевіряються на JVM, Android API 26/37 і в браузерах.

> Перший запис про реальний реліз створюється лише після збірки, перевірки й публікації артефактів. Не переносити сюди історичні правила зі специфікації.

## [0.0.1] — 2026-11-02

Технічний реліз Ф0: перевірка шляху підпису й оновлення, а не продуктова версія.

- Android APK `0.0.1` (`versionCode` 1), лише `arm64-v8a`, підписаний ключем релізу; у GitHub Releases: `todo-v0.0.1.apk` і `todo-v0.0.1.apk.sha256`.
- Зібрано запасним шляхом — workflow `Release` на GitHub з тегу `v0.0.1` (коміт `07698b5`), сертифікат підпису перевірено проти закріпленого.
- Вміст: екран Inbox (додати й виконати задачу), локальна база Room 3 schema v2, outbox; без синхронізації.
- Встановлення на телефон власника й оновлення поверх нього до `0.0.2` — ще не підтверджено (gate Ф0).
