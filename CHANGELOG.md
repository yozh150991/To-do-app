# CHANGELOG — Task Manager (застосунок)

Історія **реальних релізів застосунку** (`versionName` / `versionCode`), а не редакцій технічної специфікації.

## [Unreleased]

Підписаних APK, Web/Desktop релізів і production-міграцій ще немає. Чинне технічне завдання: [`task-manager-scope-roadmap-v2.6.1.md`](task-manager-scope-roadmap-v2.6.1.md); історія рішень специфікації: [`docs/SPEC_CHANGELOG.md`](docs/SPEC_CHANGELOG.md).

### Ф0 — у роботі

- Каркас Kotlin Multiplatform: `core/model`, `core/database`, `core/domain`, `ui`, `androidApp`, `desktopApp`; версії зафіксовано в `gradle/libs.versions.toml` (Kotlin 2.4.21, AGP 9.5.0, Gradle 9.7.0, Compose Multiplatform 1.12.2, Room 3.0.3, SQLite bundled 2.7.1).
- Room 3 schema v1: `local_workspace`, `project`, `task`, `task_activity`, `outbox_operation`, `external_identity` (`docs/DATA_MODEL.md`).
- `TaskRepository`: створення, перейменування, виконання/повернення, м'яке видалення/відновлення; кожна зміна пише сутність, activity й outbox в одній транзакції.
- Мінімальний екран Inbox (Android і Desktop).
- CI на GitHub Actions: JVM-тести, debug APK, Desktop JAR, звіт із runtime-доказами в PR.

> Перший запис про реальний реліз створюється лише після збірки, перевірки й публікації артефактів. Не переносити сюди історичні правила зі специфікації.
