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

    /** Plurals: the same keys everywhere, an "other" form each, and the placeholder of the default. */
    @Test
    fun pluralsExistInEveryLanguage() {
        fun plurals(dir: String): Map<String, Map<String, String>> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(root, "$dir/strings.xml")).getElementsByTagName("plurals")
            return (0 until nodes.length).associate { i ->
                val element = nodes.item(i) as Element
                val items = element.getElementsByTagName("item")
                element.getAttribute("name") to (0 until items.length).associate { j ->
                    val item = items.item(j) as Element
                    item.getAttribute("quantity") to item.textContent
                }
            }
        }
        val base = plurals("values")
        (listOf("values") + languages).forEach { language ->
            val forms = plurals(language)
            assertEquals(base.keys, forms.keys, "plurals of $language")
            forms.forEach { (key, items) ->
                assertTrue("other" in items, "$key has no 'other' form in $language")
                val expected = placeholder.findAll(base.getValue(key).getValue("other")).map { it.value }.toSet()
                items.forEach { (quantity, text) ->
                    assertEquals(expected, placeholder.findAll(text).map { it.value }.toSet(), "$key/$quantity in $language")
                }
            }
        }
    }
}
