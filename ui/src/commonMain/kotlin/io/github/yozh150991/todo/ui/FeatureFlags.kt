package io.github.yozh150991.todo.ui

/**
 * Feature flags (spec §11, Ф0 work list): unfinished parts of the UI are visible only in
 * development builds — Android debug builds, `-Dtodo.features=all` on Desktop (set for
 * `./gradlew :desktopApp:run`), `?features=all` on Web. Release builds keep what is finished.
 */
data class FeatureFlags(
    val todayTab: Boolean = false,
    val upcomingTab: Boolean = false,
    val browseTab: Boolean = false,
) {
    companion object {
        val Release = FeatureFlags()
        val Development = FeatureFlags(todayTab = true, upcomingTab = true, browseTab = true)

        fun of(development: Boolean): FeatureFlags = if (development) Development else Release
    }
}
