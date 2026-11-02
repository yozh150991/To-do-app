package io.github.yozh150991.todo.core.model

/**
 * kotlinx-datetime reads IANA rules on the Web from js-joda; without @js-joda/timezone only UTC and
 * a fixed-offset system zone exist and TimeZone.of("Europe/Warsaw") throws. Importing the package
 * registers the rules (kotlinx-datetime README, "Note about time zones in Wasm/JS").
 */
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule : JsAny

private var timeZoneRules: JsAny? = null

internal actual fun ensureTimeZoneRules() {
    if (timeZoneRules == null) timeZoneRules = JsJodaTimeZoneModule
}
