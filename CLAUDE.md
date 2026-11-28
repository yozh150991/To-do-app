# To-do app — правила для Claude Code

Особистий офлайн-перший менеджер задач на Kotlin Multiplatform (Android → Web → Desktop). Власник один; він же затверджує плани й зливає PR.

## Джерела правди

Перед будь-якою зміною читати релевантне, а не вгадувати:

- Специфікація: `task-manager-scope-roadmap-v2.6.1.md` (індекс, §0.3 — де лежить кожен розділ).
- `docs/ARCHITECTURE.md` (модулі, правила доступу до даних), `docs/DATA_MODEL.md`, `docs/SYNC_PROTOCOL.md`, `docs/RECURRENCE_CONTRACT.md`, `docs/UX_SPEC.md`, `docs/QA_PLAN.md`, `docs/RELEASE.md`.
- Рішення: `docs/ADR/`. План поточної фази: `docs/plans/`.

Якщо задача суперечить специфікації чи ADR — зупинитися й сказати власнику. Відхилення оформлюється новим ADR + записом у `docs/SPEC_CHANGELOG.md`, а не тихою зміною коду.

## Незмінні правила

1. `main` змінюється лише через pull request. Ніколи не комітити й не пушити в `main`, не робити force-push, не зливати PR (`gh pr merge`) — це робить власник.
2. Одна задача = одна гілка `<тип>/<короткий-slug>` (`feat/`, `fix/`, `docs/`, `chore/`, `test/`) = один PR.
3. Залежності модулів ідуть в один бік: застосунки → `ui` → `core:domain` → `core:database` → `core:model`. UI → ViewModel → Repository → DAO; мережа — тільки через `core:sync`.
4. Кожна локальна мутація — через `TaskRepository`: сутність + `TaskActivity` + `OutboxOperation` в одній транзакції.
5. Нова схема БД = нова версія Room + міграція + тест + upgrade-тест. Деструктивна міграція заборонена. Базу відкривати лише через `openAppDatabase`.
6. Новий рядок інтерфейсу додається в усі три мови (en, uk, pl) у `ui/src/commonMain/composeResources`.
7. Версії залежностей — тільки в `gradle/libs.versions.toml`, після перевірки, не «latest».
8. Ключ підпису, паролі, `.env`, токени — ніколи не читати, не друкувати, не комітити. Не чіпати `.github/release-signing-cert.sha256`.
9. Реліз (тег `vX.Y.Z`, `tools/release/build-release.sh`) запускає лише власник.
10. Не послаблювати й не вимикати тести та CI, щоб «позеленіло». Якщо тест падає — з'ясувати причину.

## Команди

```bash
# JVM-тести (основна швидка перевірка)
./gradlew :core:model:jvmTest :core:database:jvmTest :core:domain:jvmTest :core:testing:jvmTest :ui:jvmTest
# Збірки
./gradlew :androidApp:assembleDebug :desktopApp:jvmJar
./gradlew :webApp:wasmJsBrowserDistribution
# Instrumented-тести (потрібен емулятор або пристрій)
./gradlew :androidApp:connectedDebugAndroidTest
# Web E2E
cd webApp/e2e && npm ci && npx playwright install chromium firefox webkit && npx playwright test
```

Повний набір (емулятори API 26/37, браузери, Desktop, upgrade, скрипти релізу) проганяє CI на PR. Чого не вдалося запустити локально — чесно написати в PR, не видавати за пройдене.

## Розподіл ролей і моделей

Головна сесія — оркестратор (Sonnet): сама не проєктує й не пише продуктовий код, а передає роботу субагентам із `.claude/agents/`:

| Роль | Модель | Що робить |
| --- | --- | --- |
| `planner` | Fable | Аналіз задачі, технічне рішення, дизайн коду, план |
| `coder` | Opus | Пише код за затвердженим планом |
| `reviewer` | Fable | Перевіряє diff проти плану й специфікації |
| `tester` | Sonnet | Пише й запускає тести за `docs/QA_PLAN.md` |
| `docs-writer` | Sonnet | CHANGELOG, docs, ADR-чернетки |
| `release-prep` | Sonnet | Перевіряє готовність до релізу (сам реліз не запускає) |

Якщо Fable недоступна, спрацьовує `fallbackModel` із `.claude/settings.json` (Opus). Якщо субагент на Fable все одно завершився помилкою моделі — перезапустити його з параметром `model: opus` і сказати про заміну власнику.

Дрібниці (пошук по коду, відповідь на питання, правка одруківки) оркестратор робить сам.

## Команди власника

- `/task <опис>` — повний цикл: план → **зупинка на затвердження** → код → тести → рев'ю → документація → PR.
- `/review` — рев'ю поточної гілки проти `main`.
- `/release-check` — чи готовий `main` до релізу.

Робочі файли циклу (план, звіт рев'ю) лежать у `.claude/work/<slug>/` і не комітяться.

## Мова

Документація, PR, коміти й спілкування з власником — українською. Код, ідентифікатори, назви кроків CI — англійською.
