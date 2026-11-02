# CHANGELOG — Task Manager (застосунок)

Історія **реальних релізів застосунку** (`versionName` / `versionCode`), а не редакцій технічної специфікації.

## [Unreleased]

Підписаних APK, Web/Desktop релізів і production-міграцій ще немає. Чинне технічне завдання: [`task-manager-scope-roadmap-v2.6.1.md`](task-manager-scope-roadmap-v2.6.1.md); історія рішень специфікації: [`docs/SPEC_CHANGELOG.md`](docs/SPEC_CHANGELOG.md).

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
- Матриця модулів supabase-kt 3.8.0: Auth, PostgREST/RPC і Realtime перевірено на Android (емулятор API 37), JVM і в Chromium, Firefox, WebKit проти локального Supabase у CI. Обхід KT-87116 для Web-бандла.
- Скрипти власника: `tools/release/create-keystore.sh` (ключ підпису) і `tools/release/build-release.sh` (основна збірка релізу на комп'ютері: перевірка `main`, версії й ключа, підписаний APK у `dist/`, тег, встановлення через USB). Запасна збірка — workflow `Release` на GitHub. CI проганяє обидва скрипти наскрізно на Linux і Windows (ADR-003).

> Перший запис про реальний реліз створюється лише після збірки, перевірки й публікації артефактів. Не переносити сюди історичні правила зі специфікації.
