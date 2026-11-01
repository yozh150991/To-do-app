# To-do app

Особистий офлайн-перший менеджер задач: Android спочатку, далі Web і Desktop на Kotlin Multiplatform.

- Чинна специфікація: [`task-manager-scope-roadmap-v2.6.1.md`](task-manager-scope-roadmap-v2.6.1.md) і рішення в [`docs/ADR/`](docs/ADR/README.md).
- Стан сумісності платформ і версій: [`docs/TECH_COMPATIBILITY.md`](docs/TECH_COMPATIBILITY.md).
- Модель даних: [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md).
- Поточна фаза: **Ф0** (каркас і кросплатформний proof of concept). Робочого релізу ще немає.

## Структура

| Модуль | Що містить |
| --- | --- |
| `core/model` | Доменні типи, правила валідації, імена операцій, fractional order keys |
| `core/database` | Room 3 (schema v1), DAO, `LocalStore` з транзакційним outbox, мапери |
| `core/domain` | `TaskRepository`: усі локальні мутації (сутність + activity + outbox в одній транзакції) |
| `ui` | Спільний Compose-інтерфейс (екран Inbox для Ф0), Koin-модуль ViewModel |
| `androidApp` | Android entry point (AGP 9, built-in Kotlin) |
| `desktopApp` | JVM Desktop proof of concept |
| `tools/ci` | Звіт CI для PR |
| `tools/deps` | Перевірка актуальних версій залежностей |

## Вимоги

- JDK 17+ (CI використовує Temurin 21).
- Android SDK з API 36 для збірки APK.
- Gradle не потрібен окремо: використовуйте `./gradlew` (Gradle 9.7.0, перевірка checksum дистрибутиву).

## Збірка й тести

```bash
# Тести домену й бази на JVM (реальний SQLite через BundledSQLiteDriver)
./gradlew :core:model:jvmTest :core:database:jvmTest :core:domain:jvmTest

# Debug APK (applicationId з суфіксом .debug, не конфліктує з робочою установкою)
./gradlew :androidApp:assembleDebug

# Desktop proof of concept
./gradlew :desktopApp:run
```

Desktop зберігає базу в `~/.todo-app/todo.db`; інше місце — `-Dtodo.dataDir=/шлях`.

## Як вносяться зміни

- `main` змінюється лише через pull request.
- CI (`.github/workflows/ci.yml`) на кожен PR збирає APK і JAR, запускає JVM-тести та публікує звіт коментарем у PR, включно з доказами runtime (версія SQLite, FTS5, `VACUUM INTO`).
- Нова схема БД = нова версія Room + міграція + тест; деструктивна міграція заборонена (spec §3.2, §12.4).
- Рішення, що відхиляються від специфікації, оформлюються ADR (`docs/ADR/`).

## Обмеження поточного стану

- Немає release-підпису, хмарної синхронізації, імпорту з Todoist і нагадувань: це наступні кроки Ф0 і фази Ф1a+.
- Web-таргет ще не доданий (окремий spike у Ф0).
- Рядки інтерфейсу поки українською в коді; перенесення в Compose resources (uk/en/pl) заплановане в Ф0.
