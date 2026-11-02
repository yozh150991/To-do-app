package io.github.yozh150991.todo.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Shared localization (spec §13.2): every string exists in English (default), Ukrainian and Polish,
 * with the same placeholders, so no language silently falls back or crashes on formatting.
 */
class StringResourcesTest {
    private val root = File("src/commonMain/composeResources")
    private val languages = listOf("values-uk", "values-pl")
    private val placeholder = Regex("""%(\d+)\$([sd])""")

    private fun load(dir: String): Map<String, String> {
        val file = File(root, "$dir/strings.xml")
        assertTrue(file.isFile, "missing $file")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    @Test
    fun everyLanguageHasEveryString() {
        val base = load("values")
        languages.forEach { language -> assertEquals(base.keys, load(language).keys, "keys of $language") }
    }

    @Test
    fun placeholdersMatchTheDefault() {
        val base = load("values")
        languages.forEach { language ->
            load(language).forEach { (key, text) ->
                val expected = placeholder.findAll(base.getValue(key)).map { it.value }.toSet()
                val actual = placeholder.findAll(text).map { it.value }.toSet()
                assertEquals(expected, actual, "placeholders of $key in $language")
            }
        }
    }

    @Test
    fun noEmptyStrings() {
        (listOf("values") + languages).forEach { language ->
            load(language).forEach { (key, text) -> assertTrue(text.isNotBlank(), "$key is empty in $language") }
        }
    }
}
