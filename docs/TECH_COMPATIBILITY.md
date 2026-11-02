# Технічна сумісність — Ф0

Матриця V25-STACK (spec v2.6.1 §3.3.1, ADR-001). Статуси за контрактом FIX-06:

- `pass` — збірка + runtime-перевірка + доказ у CI;
- `compile_only` — збирається, runtime ще не перевірено (проміжний стан, Ф0 з ним не закривається);
- `blocked` — PoC не пройшов, є ADR, власник і план повторної перевірки;
- `not_tested` — ще не запускалось.

Доказом вважається звіт CI у PR (коментар `CI report` з SHA коміту) та артефакти workflow `CI`. Нічого не позначається `pass` на підставі документації бібліотек.

## Зафіксовані версії

Джерело: `gradle/libs.versions.toml`, вибрані 01.11.2026 за `tools/deps/probe-versions.sh` (останні стабільні на ту дату).

| Компонент | Версія |
| --- | --- |
| Gradle (wrapper, перевірка sha256) | 9.7.0 |
| Kotlin / Compose compiler plugin | 2.4.21 |
| KSP | 2.3.13 |
| Android Gradle Plugin | 9.5.0 (KMP: `com.android.kotlin.multiplatform.library`, застосунок — built-in Kotlin) |
| Compose Multiplatform | 1.12.2 (Material3 1.9.0) |
| Room 3 | 3.0.3 |
| androidx.sqlite (bundled) | 2.7.1 → SQLite 3.50.1 |
| Koin | 4.2.2 |
| supabase-kt (Auth, PostgREST, Realtime) / Ktor | 3.8.0 / 3.5.1 (OkHttp на Android і JVM, fetch + WebSocket у браузері); локальний стек — Supabase CLI 2.122.0 |
| kotlinx-coroutines / datetime / serialization | 1.11.0 / 0.8.0 / 1.12.0 |
| JDK у CI | Temurin 21 (bytecode target 17) |
| Android | compileSdk 37, targetSdk 37, minSdk 26 |
| Web: Kotlin/Wasm (`wasmJs`, browser) | Kotlin 2.4.21; `androidx.sqlite:sqlite-web` 2.7.1 (`WebWorkerSQLiteDriver`) |
| Часові зони на Web | `@js-joda/timezone` 2.23.0 — IANA-правила для kotlinx-datetime у браузері (новіші версії вимагають `@js-joda/core` ≥ 5.7, а kotlinx-datetime 0.8.0 тягне 3.2.0) |
| SQLite WASM у worker | `@sqlite.org/sqlite-wasm` 3.54.1-build1 → SQLite 3.54.1, VFS `opfs-sahpool` (`kotlin-js-store/wasm/yarn.lock`) |
| Браузерні тести | Playwright 1.64.1: Chromium 156, Firefox 157, WebKit 27.2; Node.js 22 |

**Поправка під час Ф0:** compileSdk піднято з 36 до 37, бо androidx Compose 1.12.1 (транзитивно через Compose Multiplatform 1.12.2) вимагає компіляції проти API 37 (`checkDebugAarMetadata`). `minSdk` 26 не змінився.

## Матриця

Дата перевірки: 01.11.2026 (Android, Desktop), 02.11.2026 (Web). Коміти: `dbca137361` (PR #2), `9cf79f7372` (PR #3: release-підпис і оновлення поверх), `5f9fe2edf4` (PR #7: Web, Playwright 1.64.1). Емулятори: Android 8.0 (API 26, `default` x86_64) і Android 17 (API 37, `google_atd` x86_64), запуск скриптом `tools/ci/run-emulator.sh`.

| Критерій | Android | Desktop / JVM | Web / Wasm |
| --- | --- | --- | --- |
| Компіляція на одному version catalog | `pass` — debug APK (17.80 MiB, усі ABI) | `pass` — `desktopApp-jvm.jar` | `pass` — `wasmJsBrowserDistribution`, статична збірка 17.2 MiB разом із source maps (без стиснення: Skiko 8.4 MiB, застосунок 3.9 MiB, SQLite WASM 0.9 MiB) |
| Room 3 + драйвер: CRUD, транзакція, outbox, persist після reopen | `pass` — API 26 і API 37, 11/11 сценаріїв | `pass` — ті самі 11 сценаріїв + 17 тестів `core:database`/`core:domain` | `pass` — `WebWorkerSQLiteDriver` + SQLite WASM на OPFS: Chromium і Firefox (перезавантаження, нова вкладка, перезапуск браузера, відкат outbox, безперервний `local_seq`); WebKit — у постійному профілі (ADR-004) |
| SQLite: версія, `ENABLE_FTS5`, `VACUUM INTO` (ADR-001) | `pass` — SQLite 3.50.1 на API 26 і 37, FTS5 є, `VACUUM INTO` працює | `pass` — SQLite 3.50.1 | `pass` — SQLite 3.54.1, FTS5 є, `VACUUM INTO` у другий файл пулу OPFS + `integrity_check` (Chromium, Firefox, WebKit) |
| FTS5 uk/pl/en, `unicode61 remove_diacritics 2` | `pass` — 8/8 на API 26 і 37 | `pass` — 8/8 | `pass` — 6/6 у Chromium, Firefox, WebKit |
| Міграція v1 → v2 зі snapshot (FIX-01): дані, нова FK, WAL-фрейми, відкат, збій на кожному кроці, нестача місця, відмова від downgrade | `pass` — API 26 і 37 | `pass` | Міграція v1 → v2 (Room AutoMigration на справжньому файлі v1, ті самі фікстури): `pass` у Chromium і Firefox — рядки, `integrity_check`, нова FK `section_id`, workspace, `local_seq`. Snapshot-захист FIX-01 на Web — `not_tested`, до релізу Web у Ф7 (ADR-004 п. 7) |
| Спільні parser/timezone/recurrence fixtures | Timezone (TIME-01..04, TIME-08, ALL-DAY): `pass` на API 26 (tzdb 2017b) і API 37 (tzdb 2025c). Parser — Ф1a, recurrence (TIME-05..07) — Ф2 | Timezone: `pass`, JDK 21 (tzdb 2026e) | Timezone: `pass` у Chromium, Firefox, WebKit (`?selftest=time`, правила з `@js-joda/timezone`) |
| supabase-kt (Auth, PostgREST/RPC, Realtime) | `pass` — емулятор API 37 проти локального Supabase (порт через `adb reverse`): усі 8 перевірок | `pass` — JDK 21: усі 8 перевірок | `pass` — Chromium, Firefox, WebKit: усі 8 перевірок; Web-бандл +0.8 MiB; обхід KT-87116 у webpack (див. нижче) |
| Браузерне сховище та ізоляція (§3.3.1) | — | — | `pass` — без COOP/COEP (`crossOriginIsolated = false`, звичайний статичний сервер); друга вкладка — зрозуміла помилка без втрати даних; очищення сховища → чистий старт; квота best-effort (`persisted = false`). WebKit у тимчасовому контексті (≈ приватне вікно) OPFS не дає; приватні режими й eviction — повна матриця Ф7 |
| UI у браузері / вікно Desktop | — | `pass` — упакований застосунок (`createDistributable`, jpackage-образ із власним runtime, 149 MiB без стиснення) під Xvfb + openbox: вікно відкривається, задача з поля вводу записана в `todo.db`; після жорсткого `kill -9` база цілісна (`integrity_check ok`, WAL), повторний запуск — той самий workspace, задача й один Inbox (`tools/ci/desktop-ui-test.sh`, коміт `0999ed949a`) | `pass` — Chromium і Firefox: Compose стартує, задача з поля вводу збережена в `todo.db`. Потрібен WebGL 2: без нього застосунок показує текстове повідомлення (у CI Firefox працює під Xvfb із програмним GL, бо headless Firefox без GPU WebGL не має) |
| Release-підпис і ABI | `pass` (механізм) — release APK з одноразовим ключем у CI: підпис APK Signature Scheme v2, сертифікат збігається, лише `arm64-v8a`, 10.7 MiB. Реальний ключ — `not_tested` (крок власника, `docs/RELEASE.md`) | Runnable JAR і jpackage-образ із runtime (`createDistributable`); вікно перевіряється в CI під Xvfb. Інсталятори й підпис — Ф8 | Статична збірка — артефакт CI (`webApp/build/dist/wasmJs/productionExecutable`); хостинг — Ф7, власні заголовки не потрібні (ADR-004) |
| Оновлення поверх попередньої версії (`install -r`) | `pass` — емулятор API 37: база PR → поточна гілка, workspace, Inbox і всі рядки збережені (PR #3: v2 → v2; міграція v1 → v2 перевірена сценаріями) | — | — |

**supabase-kt, що саме перевірено** (`core/sync/SupabaseProbe`, однаковий код на всіх платформах, локальний стек `supabase start` з `supabase/`): реєстрація email/паролем і повторний вхід (Auth); вставка з `owner_id = auth.uid()`, вибірка з фільтром, RLS показує лише свої рядки, RPC `f0_echo` (PostgREST); подія `postgres_changes` INSERT через WebSocket (Realtime); анонімний користувач рядків не бачить. Realtime іноді пропускає першу вставку одразу після `subscribed` — проба повторює вставку до трьох разів (на JVM і Android подія прийшла з другої). Це врахувати в SyncEngine Ф5b: Realtime — лише підказка, джерело правди — Pull.

**KT-87116 (Kotlin/Wasm 2.4.20+).** З supabase-kt у Web-бандл потрапляє kotlinx-io, у Node-гілці якого лишається голий `import.meta`; `webApp.js` як класичний скрипт тоді не парситься в жодному браузері. Обхід — `webApp/webpack.config.d/strip-bare-import-meta.js` (замінює лише голий вираз у Node-only коді). Прибрати після виправлення в Kotlin; `tools/ci/web-bundle-check.sh` у кожному прогоні показує, чи лишився `import.meta` у бандлі.

**Примітка про емулятор API 37.** Пакети системних образів Android 17 мають мінорний рівень у назві (`system-images;android-37.0;…`). Повний образ `google_apis` не дійшов до `boot_completed` на GitHub-hosted runner, тому для API 37 використовується ATD-образ (`google_atd`), призначений для автоматизованих тестів. Для тестів шару даних різниці немає; UI-тести на повному образі — окрема перевірка пізніше.

## Висновки на зараз

- **Android:** шар даних пройшов runtime-перевірку на мінімальному (API 26) і актуальному (API 37) Android: CRUD і збереження після перезапуску, атомарність entity + activity + outbox, безперервний `local_seq` при 50 паралельних записах, безпечне оновлення схеми з відкатом. Не перевірено на реальному телефоні.
- **Desktop/JVM:** ті самі сценарії `pass`; упакований застосунок відкриває вікно, зберігає задачу, введену в інтерфейсі, і переживає жорстке завершення процесу (перевірка в CI на віртуальному дисплеї). Реальний робочий стіл Windows — перевірка власника пізніше (Ф8).
- **Web:** Room/OPFS spike пройшов у обов'язкових браузерах (Chromium, Firefox): та сама схема, DAO і `TaskRepository` через `WebWorkerSQLiteDriver` і власний worker на SQLite WASM з VFS `opfs-sahpool`. COOP/COEP не потрібні, отже підходить будь-який статичний хостинг. Обмеження: одна вкладка; WebKit/Safari лише поза приватним режимом; сховище best-effort до синхронізації Ф5; Compose потребує WebGL 2. Fallback на IndexedDB не знадобився. Рішення — ADR-004 (Accepted).

## Стан gate ADR-001

| Пункт | Стан |
| --- | --- |
| `sqlite_version()` і `compile_options` на Android API 26 і JVM | Виконано в CI; на реальному телефоні — ні |
| FTS5 uk/pl/en на API 26 і актуальному Android | Виконано (API 26, API 37) |
| `VACUUM INTO` під записами у WAL, `integrity_check`, semantic comparison, crash і нестача місця | Виконано (сценарії `walFramesAreCapturedBySnapshot`, `interruptedUpgradeIsRecovered`, `insufficientSpaceLeavesDatabaseUntouched`) |
| Приріст розміру APK і рішення щодо ABI split | Рішення: release лише `arm64-v8a` (`-PreleaseAbis` для інших). Release APK — 10.7 MiB; debug з усіма ABI — 17.8 MiB |

## Що залишилось для gate Ф0

1. **Власник:** ключ створено 02.11.2026, відбиток закріплено в `.github/release-signing-cert.sha256`. Збірка — на комп'ютері власника скриптом `build-release.sh`, GitHub — запасна; без застосунку-оновлювача (ADR-003). Лишилось: дві резервні копії, Android Studio на комп'ютері, зібрати `v0.0.1` і `v0.0.2` (друга — з ключем із резервної копії) й оновити поверх на телефоні (`docs/RELEASE.md`, чекліст gate).
2. ~~Web spike~~ — `pass`, ADR-004 прийнято. ~~Desktop runtime UI-перевірка~~ — `pass` (job «Desktop window (Xvfb)»).
3. ~~supabase-kt module matrix~~ — `pass` на всіх трьох платформах (`core/sync/SupabaseProbe`, jobs «Supabase modules»).
4. `docs/UX_SPEC.md`, ~~рядки в Compose resources (uk/en/pl)~~, розбиття специфікації на `docs/*` за §13.4.
5. ~~Golden fixtures часу (time-only subset)~~ — TIME-01..04, TIME-08 виконуються на JVM, Android і в браузерах (`core/testing/TimeGoldenFixtures`); TIME-05..07 — разом із recurrence engine у Ф2.
6. Перевірка на реальному телефоні (sqlite evidence + встановлення APK).
