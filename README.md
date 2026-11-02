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
| `core/database` | Room 3 (schema v2), DAO, `LocalStore`, безпечне відкриття й оновлення схеми (`DatabaseOpener`) |
| `core/domain` | `TaskRepository`: усі локальні мутації (сутність + activity + outbox в одній транзакції) |
| `core/testing` | Спільні сценарії шару даних: запускаються і на JVM, і на Android-емуляторах |
| `ui` | Спільний Compose-інтерфейс (екран Inbox для Ф0), Koin-модуль ViewModel |
| `androidApp` | Android entry point (AGP 9, built-in Kotlin) |
| `desktopApp` | JVM Desktop proof of concept |
| `tools/ci` | Звіт CI для PR, запуск Android-емулятора |
| `tools/deps` | Перевірка актуальних версій залежностей |

## Вимоги

- JDK 17+ (CI використовує Temurin 21).
- Android SDK з API 37 для збірки APK.
- Gradle не потрібен окремо: використовуйте `./gradlew` (Gradle 9.7.0, перевірка checksum дистрибутиву).

## Збірка й тести

```bash
# Тести домену й бази на JVM (реальний SQLite через BundledSQLiteDriver)
./gradlew :core:model:jvmTest :core:database:jvmTest :core:domain:jvmTest :core:testing:jvmTest

# Ті самі сценарії бази на підключеному пристрої або емуляторі
./gradlew :androidApp:connectedDebugAndroidTest

# Debug APK (applicationId з суфіксом .debug, не конфліктує з робочою установкою)
./gradlew :androidApp:assembleDebug

# Desktop proof of concept
./gradlew :desktopApp:run
```

Desktop зберігає базу в `~/.todo-app/todo.db`; інше місце — `-Dtodo.dataDir=/шлях`.

## Як вносяться зміни

- `main` змінюється лише через pull request.
- CI (`.github/workflows/ci.yml`) на кожен PR збирає APK і JAR, запускає JVM-тести й instrumented-тести на емуляторах Android 8.0 (API 26) і Android 17 (API 37) та публікує зведений звіт коментарем у PR, включно з доказами runtime.
- Нова схема БД = нова версія Room + міграція + тест; деструктивна міграція заборонена (spec §3.2, §12.4). Відкриття бази в застосунку — тільки через `openAppDatabase` (snapshot перед оновленням і відкат при збої, `docs/DATA_MODEL.md`).
- Рішення, що відхиляються від специфікації, оформлюються ADR (`docs/ADR/`).

## Обмеження поточного стану

- Немає release-підпису, хмарної синхронізації, імпорту з Todoist і нагадувань: це наступні кроки Ф0 і фази Ф1a+.
- Web-таргет ще не доданий (окремий spike у Ф0).
- Рядки інтерфейсу поки українською в коді; перенесення в Compose resources (uk/en/pl) заплановане в Ф0.
