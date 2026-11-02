# To-do app

Особистий офлайн-перший менеджер задач: Android спочатку, далі Web і Desktop на Kotlin Multiplatform.

- Чинна специфікація: [`task-manager-scope-roadmap-v2.6.1.md`](task-manager-scope-roadmap-v2.6.1.md) і рішення в [`docs/ADR/`](docs/ADR/README.md).
- Стан сумісності платформ і версій: [`docs/TECH_COMPATIBILITY.md`](docs/TECH_COMPATIBILITY.md).
- Модель даних: [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md).
- Інтерфейс і поведінка (навігація, екрани, стани, жести, доступність): [`docs/UX_SPEC.md`](docs/UX_SPEC.md).
- Релізи, ключ підпису, встановлення й оновлення на телефоні: [`docs/RELEASE.md`](docs/RELEASE.md).
- Поточна фаза: **Ф0** (каркас і кросплатформний proof of concept). Робочого релізу ще немає.

## Структура

| Модуль | Що містить |
| --- | --- |
| `core/model` | Доменні типи, правила валідації, імена операцій, fractional order keys |
| `core/database` | Room 3 (schema v2), DAO, `LocalStore`, безпечне відкриття й оновлення схеми (`DatabaseOpener`) |
| `core/domain` | `TaskRepository`: усі локальні мутації (сутність + activity + outbox в одній транзакції) |
| `core/testing` | Спільні сценарії шару даних: запускаються і на JVM, і на Android-емуляторах; схема v1 і seed як SQL (їх використовує й Web self-test) |
| `ui` | Спільний Compose-інтерфейс (екран Inbox для Ф0), Koin-модуль ViewModel |
| `androidApp` | Android entry point (AGP 9, built-in Kotlin) |
| `desktopApp` | JVM Desktop proof of concept |
| `webApp` | Web proof of concept (Kotlin/Wasm): той самий Compose-інтерфейс і Room через SQLite WASM на OPFS (ADR-004); `worker/` — worker бази, `e2e/` — тести в браузерах (Playwright) |
| `tools/ci` | Звіт CI для PR, запуск Android-емулятора, тест оновлення поверх, наскрізний тест скриптів релізу |
| `tools/release` | Скрипти власника: ключ підпису (`create-keystore.sh`) і збірка релізу на комп'ютері (`build-release.sh`) |
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

# Web: статична production-збірка у webApp/build/dist/wasmJs/productionExecutable
./gradlew :webApp:wasmJsBrowserDistribution
# Web у браузері з перезбиранням при змінах
./gradlew :webApp:wasmJsBrowserDevelopmentRun
# Тести в Chromium, Firefox і WebKit (Node.js 22; звичайний статичний сервер без COOP/COEP)
cd webApp/e2e && npm ci && npx playwright install chromium firefox webkit && npx playwright test
```

Desktop зберігає базу в `~/.todo-app/todo.db`; інше місце — `-Dtodo.dataDir=/шлях`.

## Як вносяться зміни

- `main` змінюється лише через pull request.
- CI (`.github/workflows/ci.yml`) на кожен PR збирає APK, JAR і Web-збірку, запускає JVM-тести, instrumented-тести на емуляторах Android 8.0 (API 26) і Android 17 (API 37), тести Web у Chromium, Firefox і WebKit, тест оновлення поверх і скрипти релізу на Linux і Windows, та публікує зведений звіт коментарем у PR, включно з доказами runtime.
- Нова схема БД = нова версія Room + міграція + тест; деструктивна міграція заборонена (spec §3.2, §12.4). Відкриття бази в застосунку — тільки через `openAppDatabase` (snapshot перед оновленням і відкат при збої, `docs/DATA_MODEL.md`).
- Рішення, що відхиляються від специфікації, оформлюються ADR (`docs/ADR/`).

## Обмеження поточного стану

- Ключ підпису створено, відбиток закріплено; перший реліз збирається `tools/release/build-release.sh` (потрібна Android Studio на комп'ютері, `docs/RELEASE.md`).
- Немає хмарної синхронізації, імпорту з Todoist і нагадувань: це фази Ф1a+.
- Web — proof of concept, не реліз (реліз Web — Ф7): працює в Chromium і Firefox, у WebKit/Safari — поза приватним режимом; одночасно відкрита лише одна вкладка; до синхронізації (Ф5) дані живуть тільки в цьому браузері (ADR-004).
- Рядки інтерфейсу поки українською в коді; перенесення в Compose resources (uk/en/pl) заплановане в Ф0.
