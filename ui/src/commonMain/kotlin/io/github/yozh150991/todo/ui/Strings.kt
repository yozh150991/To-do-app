package io.github.yozh150991.todo.ui

/**
 * Temporary Ukrainian strings for the Ф0 proof screen.
 * Moves to Compose Multiplatform resources (values / values-en / values-pl) together with
 * docs/UX_SPEC.md later in Ф0; spec §13.2 requires composeResources for shared localization.
 */
object Strings {
    const val inboxTitle = "Вхідні"
    const val newTaskHint = "Нова задача"
    const val add = "Додати"
    const val emptyInbox = "Задач поки немає"

    fun pendingSync(count: Int): String = "Очікують синхронізації: $count"
}
