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
| kotlinx-coroutines / datetime / serialization | 1.11.0 / 0.8.0 / 1.12.0 |
| JDK у CI | Temurin 21 (bytecode target 17) |
| Android | compileSdk 37, targetSdk 37, minSdk 26 |

**Поправка під час Ф0:** compileSdk піднято з 36 до 37, бо androidx Compose 1.12.1 (транзитивно через Compose Multiplatform 1.12.2) вимагає компіляції проти API 37 (`checkDebugAarMetadata`). `minSdk` 26 не змінився.

## Матриця

Дата перевірки: 01.11.2026. Коміт: `dbca137361` (PR #2). Емулятори: Android 8.0 (API 26, `default` x86_64) і Android 17 (API 37, `google_atd` x86_64), запуск скриптом `tools/ci/run-emulator.sh`.

| Критерій | Android | Desktop / JVM | Web / Wasm |
| --- | --- | --- | --- |
| Компіляція на одному version catalog | `pass` — debug APK (17.80 MiB, усі ABI) | `pass` — `desktopApp-jvm.jar` | `not_tested` — таргет ще не додано |
| Room 3 + `BundledSQLiteDriver`: CRUD, транзакція, outbox, persist після reopen | `pass` — API 26 і API 37, 11/11 сценаріїв | `pass` — ті самі 11 сценаріїв + 17 тестів `core:database`/`core:domain` | `not_tested` |
| SQLite: версія, `ENABLE_FTS5`, `VACUUM INTO` (ADR-001) | `pass` — SQLite 3.50.1 на API 26 і 37, FTS5 є, `VACUUM INTO` працює | `pass` — SQLite 3.50.1 | `not_tested` (WASM-збірка worker) |
| FTS5 uk/pl/en, `unicode61 remove_diacritics 2` | `pass` — 8/8 на API 26 і 37 | `pass` — 8/8 | `not_tested` |
| Міграція v1 → v2 зі snapshot (FIX-01): дані, нова FK, WAL-фрейми, відкат, збій на кожному кроці, нестача місця, відмова від downgrade | `pass` — API 26 і 37 | `pass` | `not_tested` |
| Спільні parser/timezone/recurrence fixtures | `not_tested` | `not_tested` | `not_tested` |
| supabase-kt (Auth, PostgREST/RPC, Realtime) | `not_tested` | `not_tested` | `not_tested` |
| Release/CI | Debug APK у CI; release-підпис `not_tested` | Runnable JAR; запуск вікна в CI не перевіряється (headless) | `not_tested` |

**Примітка про емулятор API 37.** Пакети системних образів Android 17 мають мінорний рівень у назві (`system-images;android-37.0;…`). Повний образ `google_apis` не дійшов до `boot_completed` на GitHub-hosted runner, тому для API 37 використовується ATD-образ (`google_atd`), призначений для автоматизованих тестів. Для тестів шару даних різниці немає; UI-тести на повному образі — окрема перевірка пізніше.

## Висновки на зараз

- **Android:** шар даних пройшов runtime-перевірку на мінімальному (API 26) і актуальному (API 37) Android: CRUD і збереження після перезапуску, атомарність entity + activity + outbox, безперервний `local_seq` при 50 паралельних записах, безпечне оновлення схеми з відкатом. Не перевірено на реальному телефоні.
- **Desktop/JVM:** ті самі сценарії `pass`. UI-вікно не перевіряється автоматично.
- **Web:** ще не почато; окремий spike за §3.3.1 (worker + OPFS або IndexedDB fallback).

## Стан gate ADR-001

| Пункт | Стан |
| --- | --- |
| `sqlite_version()` і `compile_options` на Android API 26 і JVM | Виконано в CI; на реальному телефоні — ні |
| FTS5 uk/pl/en на API 26 і актуальному Android | Виконано (API 26, API 37) |
| `VACUUM INTO` під записами у WAL, `integrity_check`, semantic comparison, crash і нестача місця | Виконано (сценарії `walFramesAreCapturedBySnapshot`, `interruptedUpgradeIsRecovered`, `insufficientSpaceLeavesDatabaseUntouched`) |
| Приріст розміру APK і рішення щодо ABI split | Debug APK з усіма ABI — 17.80 MiB; рішення приймається разом із release-збіркою |

## Що залишилось для gate Ф0

1. Release keystore у двох місцях, підписана збірка в CI лише з захищеної гілки/тегу, перевірка оновлення v0.0.1 → v0.0.2 поверх (§3.4); розмір release APK і ABI split.
2. ADR каналу оновлень (Obtainium або вбудована перевірка).
3. Web spike і рішення pass/blocked з ADR; Desktop runtime UI-перевірка.
4. supabase-kt module matrix.
5. `docs/UX_SPEC.md`, рядки в Compose resources (uk/en/pl), розбиття специфікації на `docs/*` за §13.4.
6. Golden fixtures часу TIME-01..TIME-08 як виконувані тести (time-only subset).
7. Перевірка на реальному телефоні (sqlite evidence + встановлення APK).
