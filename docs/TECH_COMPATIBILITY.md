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

Дата перевірки: 01.11.2026. Коміт: `9bd1c420b6` (PR #1).

| Критерій | Android | Desktop / JVM | Web / Wasm |
| --- | --- | --- | --- |
| Компіляція на одному version catalog | `pass` — debug APK зібрано (17.75 MiB, усі ABI) | `pass` — `desktopApp-jvm.jar` | `not_tested` — таргет ще не додано |
| Room 3 + `BundledSQLiteDriver`: CRUD, транзакція, outbox, persist після reopen | `compile_only` — потрібен тест на емуляторі | `pass` — 6 тестів `core:database` + 12 `core:domain` на файловій БД | `not_tested` |
| SQLite: версія, `ENABLE_FTS5`, `VACUUM INTO` (ADR-001) | `not_tested` — API 26 і актуальний емулятор | `pass` — SQLite 3.50.1, FTS5 є, `VACUUM INTO` створює копію | `not_tested` (WASM-збірка worker) |
| FTS5 uk/pl/en, `unicode61 remove_diacritics 2` | `not_tested` | `pass` — 8/8 перевірок (точний збіг, регістр кирилиці, префікс, `prad` → `prąd`) | `not_tested` |
| WAL-журнал Room | `not_tested` | `pass` — `journal_mode = wal` | — |
| Міграція v1 → v2 + WAL-safe snapshot (FIX-01) | `not_tested` | `not_tested` | `not_tested` |
| Спільні parser/timezone/recurrence fixtures | `not_tested` | `not_tested` | `not_tested` |
| supabase-kt (Auth, PostgREST/RPC, Realtime) | `not_tested` | `not_tested` | `not_tested` |
| Release/CI | Debug APK у CI; release-підпис `not_tested` | Runnable JAR; запуск вікна в CI не перевіряється (headless) | `not_tested` |

## Висновки на зараз

- **Desktop/JVM:** шар даних пройшов runtime-перевірку (persist після закриття й повторного відкриття, FK, унікальність Inbox/workspace, атомарність entity + activity + outbox, безперервний `local_seq` при 100 паралельних записах). UI-вікно не перевірене автоматично.
- **Android:** збірка є, runtime-доказу немає. Ф0 для Android закривається лише після instrumented-тестів на емуляторі API 26 і актуальному API в CI.
- **Web:** ще не почато; окремий spike за §3.3.1 (worker + OPFS або IndexedDB fallback).

## Що залишилось для gate Ф0

1. Android instrumented-тести на емуляторах API 26 і 37: CRUD, перезапуск, транзакція з outbox, SQLite/FTS5 evidence.
2. Міграція v1 → v2 і WAL-safe snapshot перед міграцією з `integrity_check`, checksum і сценаріями збою (FIX-01, ADR-001).
3. Release keystore у двох місцях, підписана збірка в CI лише з захищеної гілки/тегу, перевірка оновлення v0.0.1 → v0.0.2 (§3.4).
4. ADR каналу оновлень (Obtainium або вбудована перевірка).
5. Web spike і рішення pass/blocked з ADR; Desktop runtime UI-перевірка.
6. supabase-kt module matrix.
7. `docs/UX_SPEC.md`, рядки в Compose resources (uk/en/pl), розбиття специфікації на `docs/*` за §13.4.
8. Golden fixtures часу TIME-01..TIME-08 як виконувані тести (time-only subset).
