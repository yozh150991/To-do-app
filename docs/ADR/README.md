# Architecture Decision Records

Ухвалені ADR **мають пріоритет** над розділами `task-manager-scope-roadmap-v2.6.1.md`, які вони явно замінюють. Агент перед початком фази читає специфікацію, цей перелік і всі ADR, що стосуються його фази.

| ADR | Назва | Статус | Фази | Що замінює у v2.6.1 |
| --- | --- | --- | --- | --- |
| [ADR-001](ADR-001-bundled-sqlite-driver.md) | `BundledSQLiteDriver` для Android і Desktop | Accepted | Ф0, усі міграції, Ф6a | §3 «Локальна БД», §3.3.1, §3.4 (механізм FIX-01), gate Ф0, TR-16 |
| [ADR-002](ADR-002-client-computed-recurrence.md) | Переходи повторів рахує KMP-ядро, сервер перевіряє інваріанти | Accepted | Ф2, Ф5b | §8.1.0 (FIX-03), Ф5b, §12.1, TR-17 |
| [ADR-003](ADR-003-update-channel.md) | Збірка APK на комп'ютері власника (GitHub — запасна), ручне встановлення без оновлювача | Accepted | Ф0 і всі релізи | §3.4 «Канал оновлень», відкрите питання §14 |
| [ADR-004](ADR-004-web-storage.md) | Web: Room 3 + SQLite WASM на OPFS (`opfs-sahpool`) у власному worker, без COOP/COEP, одна вкладка | Proposed | Ф0, Ф7 | §3.3.1 (Web-рядки, пункти 1–4 про worker, COOP/COEP і хостинг), §14 «COOP/COEP і headers», ADR-001 п. 3 (FTS5 у WASM) |

## Правила

- Новий ADR отримує наступний номер і статус `Proposed`; після погодження власником — `Accepted`.
- Скасоване рішення не видаляється: статус `Superseded by ADR-NNN`.
- Кожен ADR перелічує розділи специфікації, які він замінює, і власні критерії перевірки.
- Запис про ADR додається в `docs/SPEC_CHANGELOG.md`; реалізовані зміни — у релізний `CHANGELOG.md`.
