# ADR-004: Локальна база у браузері — Room 3 + SQLite WASM на OPFS (SAHPool)

- **Статус:** Proposed (чекає погодження власника)
- **Дата:** 2 листопада 2026 року
- **Власник рішення:** власник проєкту
- **Стосується v2.6.1:** §3.3 (Web у spike), §3.3.1 (рядки Room/SQLiteDriver, «Browser persistence та isolation», «Release/CI» для Web; пункти 1–4 «Окремо для Web у межах spike»), gate Ф0 (c), §14 «Перевірити COOP/COEP і headers фактично обраного worker»; ADR-001 п. 3 (FTS5 у WASM-збірці)

## Контекст

- Специфікація обирає для Web `WebWorkerSQLiteDriver` + SQLite WASM у worker з OPFS і дає два рівні fallback: SQLite WASM поверх IndexedDB під тією ж Room, потім `LocalStore` на IndexedDB.
- `androidx.sqlite:sqlite-web` постачає драйвер без worker: worker із протоколом `open/prepare/step/close` пише проєкт.
- Офіційна збірка SQLite WASM (`@sqlite.org/sqlite-wasm`) має два VFS для OPFS:
  - `opfs` — потребує `SharedArrayBuffer` і `Atomics.wait`, отже cross-origin isolation (заголовки COOP/COEP); саме про нього пишуть як про вимогу заголовків;
  - `opfs-sahpool` — синхронні access handles OPFS без `SharedArrayBuffer`, працює без COOP/COEP і швидший, але тримає файли ексклюзивно: базу відкриває лише одна вкладка.
- Специфікація вимагала з'ясувати, чи потрібні COOP/COEP, і від цього залежав вибір хостингу для Ф7 (GitHub Pages заголовків не ставить).
- API `androidx.sqlite` у браузері асинхронне (`suspend prepare/step`), на Android і JVM — блокуюче, і в `commonMain` його немає.

## Рішення

1. **Web використовує ту саму Room 3:** `AppDatabase`, схему, DAO, міграції й `TaskRepository`, що й Android/Desktop. Драйвер — `WebWorkerSQLiteDriver` (`androidx.sqlite:sqlite-web` 2.7.1).
2. **Власний worker** `webApp/worker/worker.js` на офіційному `@sqlite.org/sqlite-wasm` (зафіксовано `3.54.1-build1`, `kotlin-js-store/wasm/yarn.lock`) з VFS **`opfs-sahpool`** (ім'я `todo-sahpool`, каталог OPFS `.todo-db`, ємність пулу 10 файлів з дозаповненням старих пулів).
3. **Cross-origin isolation не потрібна.** Збірка роздається звичайним статичним сервером без COOP/COEP; e2e-тести саме так і працюють (`python3 -m http.server`). Пункт §3.3.1 «обрати хостинг із власними заголовками» перестає бути обов'язковим; вибір хостингу лишається у Ф7, GitHub Pages допустимий.
4. **Журнал — `TRUNCATE`** (rollback journal): WAL на цьому VFS без спільної пам'яті не працює. Room у браузері має одне з'єднання з базою.
5. **Одна вкладка.** Друга вкладка, поки перша відкрита, отримує зрозумілу помилку від браузера, дані першої не страждають; після закриття першої друга працює. Повідомлення для користувача («застосунок уже відкрито в іншій вкладці») і, можливо, передача бази між вкладками через Web Locks — Ф7.
6. **Код, що звертається до SQLite напряму** (`DatabaseOpener`, snapshot FIX-01), живе в source set `nonWebMain` (Android + JVM). Web має власну фабрику `createWebAppDatabase`.
7. **FIX-01 у браузері:** `VACUUM INTO` у другий файл того ж пулу працює і проходить `integrity_check` (перевірено в Ф0). Повний механізм snapshot/rollback для Web (адаптер `DatabaseOpener`) реалізується до першої зміни схеми, яка вийде на Web, тобто до релізу Web у Ф7.
8. **Fallback рівнів 1–2 не задіюється**, поки цей варіант проходить тести. Якщо браузер не дає OPFS SAHPool, застосунок показує помилку відкриття бази (не падає й не переходить у online-only).

## Перевірка в Ф0

Команда: `./gradlew :webApp:wasmJsBrowserDistribution`, далі `cd webApp/e2e && npm ci && npx playwright test` (job `Web in browsers` у CI). Обов'язкові браузери — Chromium і Firefox (§3.3); WebKit записується, але Ф0 не блокує.

| Сценарій | Chromium 156 | Firefox 157 | WebKit 27.2 (Playwright, Linux) |
| --- | --- | --- | --- |
| Без COOP/COEP (`crossOriginIsolated = false`) | pass | pass | pass |
| SQLite у worker: 3.54.1, `journal_mode = truncate` | pass | pass | pass (постійний профіль) |
| Транзакція entity + activity + outbox, відкат при збої outbox, `local_seq` 1..5 | pass | pass | pass (постійний профіль) |
| Дані після перезавантаження сторінки і в новій вкладці | pass | pass | — (див. нижче) |
| Дані після перезапуску браузера (постійний профіль) | pass | pass | pass |
| Друга вкладка, поки перша відкрита | зрозуміла помилка, дані цілі | зрозуміла помилка, дані цілі | — |
| FTS5 uk/pl/en, `unicode61 remove_diacritics 2` (6 запитів) | pass | pass | pass |
| `VACUUM INTO` + `integrity_check` + кількість задач у копії | pass | pass | pass |
| Міграція v1 → v2 (Room AutoMigration на справжньому файлі v1) | перевіряється | перевіряється | — |
| Очищення сховища → чистий старт з новим workspace | pass | pass | — |
| UI: старт, задача з поля вводу збережена в базі | pass | перевіряється | помилка відкриття бази (див. нижче) |

**WebKit.** У тимчасовому контексті Playwright (аналог приватного вікна) WebKit не дає OPFS access handles: «The operation failed for an unknown transient reason». У постійному профілі все проходить. Тобто Safari у звичайному режимі має працювати, у приватному — ні; застосунок у такому разі показує помилку відкриття бази, а не падає. Перевірка на справжньому Safari — Ф7.

**Помилка «друга вкладка»:** Chromium — «Access Handles cannot be created if there is another open Access Handle…», Firefox — «No modification allowed».

**Квота:** у Chromium і Firefox сховище `persisted = false` (best-effort), квота ≈ 10 GiB на тестових раннерах.

## Наслідки

- Будь-який статичний хостинг підходить; заголовки не налаштовуються.
- Лише одна відкрита вкладка застосунку. Для одного користувача прийнятно; UX другої вкладки — у Ф7.
- Сховище браузера за замовчуванням best-effort: браузер може його витерти під тиском на диск, а користувач — очистити разом із даними сайту. До синхронізації (Ф5) це втрата локальних даних. Мітігація у Ф7: `navigator.storage.persist()` і попередження, якщо дозволу немає; після Ф5 — дані в хмарі.
- Приватний режим (Firefox private browsing, Safari) і квоти перевіряються в повній browser matrix Ф7.
- Розмір worker-збірки SQLite WASM додається до Web-бандла.

## Розглянуті альтернативи

- **VFS `opfs`** (через `SharedArrayBuffer`): дозволяє кілька вкладок, але вимагає COOP/COEP на хостингу і забороняє вбудовувати сторонні ресурси без CORP; повільніший. Не потрібен для одного користувача.
- **Рівень 1 fallback — SQLite WASM поверх IndexedDB** (наприклад, wa-sqlite з IndexedDB VFS): лишається запасним, якщо SAHPool підведе в якомусь із обов'язкових браузерів.
- **Рівень 2 — `LocalStore` на IndexedDB:** окрема реалізація шару даних; лише якщо обидва варіанти з SQLite не працюють.
