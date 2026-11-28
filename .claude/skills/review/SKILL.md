---
name: review
description: Незалежне рев'ю поточної гілки To-do app проти main (Fable).
argument-hint: [на що звернути увагу]
disable-model-invocation: true
---

Запусти субагента `reviewer` для поточної гілки (`git diff main...HEAD`). Додаткові побажання власника: $ARGUMENTS

Якщо для гілки є `.claude/work/<slug>/plan.md` — передай його шлях. Нічого не виправляй сам. Покажи власнику вердикт і список «Блокує» повністю, «Варто поправити» — стисло, і спитай, чи передати правки `coder`.
