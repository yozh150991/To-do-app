package io.github.yozh150991.todo.ui

import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.fail

/**
 * Screenshot baselines (spec §12.1, Roborazzi; plan Ф1a PR 1). Roborazzi records each screenshot
 * into `build/outputs/roborazzi/`; [verify] compares it with the committed baseline in
 * `ui/screenshots/` and fails when they differ beyond antialiasing noise.
 *
 * A screenshot without a baseline passes and is written base64-encoded to
 * `build/reports/new-baselines/` — CI prints those into the job log so the baseline can be
 * committed from the CI renderer (fonts differ between machines, so baselines are always taken in
 * CI). To renew a baseline after an intended UI change, delete it and push.
 */
object Baselines {
    private val output = File(System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi")
    private val baselines = File(System.getProperty("todo.baselineDir") ?: "screenshots")
    private val fresh = File(System.getProperty("todo.newBaselineDir") ?: "build/reports/new-baselines")
    private val evidence = File(System.getProperty("todo.reportDir") ?: "build/reports/runtime-evidence")

    /** A pixel counts as changed when a channel moves by more than this (0..255). */
    private const val CHANNEL_TOLERANCE = 32

    /** At most this share of pixels may change (text antialiasing, caret blink). */
    private const val CHANGED_SHARE = 0.002

    /** Where Roborazzi writes the screenshot [name] (pass to `captureRoboImage`). */
    fun path(name: String): String = File(output, "$name.png").path

    fun verify(name: String) {
        val actualFile = File(output, "$name.png")
        check(actualFile.exists()) { "Roborazzi did not write $actualFile" }
        val baselineFile = File(baselines, "$name.png")
        if (!baselineFile.exists()) {
            fresh.mkdirs()
            File(fresh, "$name.png.b64").writeText(Base64.getEncoder().encodeToString(actualFile.readBytes()))
            report("$name: NEW (no baseline yet; printed to the CI log)")
            return
        }
        val actual = ImageIO.read(actualFile)
        val expected = ImageIO.read(baselineFile)
        if (actual.width != expected.width || actual.height != expected.height) {
            report("$name: DIFFERS in size ${actual.width}x${actual.height}, baseline ${expected.width}x${expected.height}")
            fail("Screenshot $name is ${actual.width}x${actual.height}, the baseline ${expected.width}x${expected.height}")
        }
        val diff = BufferedImage(actual.width, actual.height, BufferedImage.TYPE_INT_ARGB)
        var changed = 0
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val a = actual.getRGB(x, y)
                val e = expected.getRGB(x, y)
                if (channelDistance(a, e) > CHANNEL_TOLERANCE) {
                    changed++
                    diff.setRGB(x, y, 0xFFFF0000.toInt())
                } else {
                    diff.setRGB(x, y, (e and 0x00FFFFFF) or 0x30000000)
                }
            }
        }
        val share = changed.toDouble() / (actual.width * actual.height)
        if (share > CHANGED_SHARE) {
            File(output, "diff").mkdirs()
            ImageIO.write(diff, "png", File(output, "diff/$name.png"))
            report("$name: DIFFERS in $changed px (${"%.3f".format(share * 100)} %), diff in build/outputs/roborazzi/diff/")
            fail("Screenshot $name differs from its baseline in $changed pixels; see build/outputs/roborazzi/diff/$name.png")
        }
        report("$name: matches the baseline ($changed px within tolerance)")
    }

    private fun channelDistance(a: Int, b: Int): Int =
        (0..24 step 8).maxOf { shift -> abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)) }

    @Synchronized
    private fun report(line: String) {
        evidence.mkdirs()
        File(evidence, "roborazzi-verify.txt").appendText(line + "\n")
    }
}
