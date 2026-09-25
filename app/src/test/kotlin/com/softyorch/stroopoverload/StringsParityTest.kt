package com.softyorch.stroopoverload

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CLAUDE.md requires every user-facing string to ship in all six languages in the
 * same commit. A key missing from one locale is invisible until someone runs the
 * app in that language, so it is checked here instead.
 */
class StringsParityTest {

    private val res = File("src/main/res")
    private val locales = listOf("values-es", "values-ja", "values-fr", "values-de", "values-pt-rBR")

    private fun keysOf(dir: String): Set<String> {
        val file = File(res, "$dir/strings.xml")
        assertTrue("Missing ${file.path}", file.isFile)
        return Regex("""<string name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `every locale defines exactly the same keys as the default one`() {
        val defaultKeys = keysOf("values")

        locales.forEach { locale ->
            val keys = keysOf(locale)
            assertEquals("$locale is missing keys", emptySet<String>(), defaultKeys - keys)
            assertEquals("$locale defines keys the default locale doesn't", emptySet<String>(), keys - defaultKeys)
        }
    }

    @Test
    fun `no string resource is left unused`() {
        val declared = keysOf("values")
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val referencedInXml = File(res, "values").walkTopDown()
            .plus(File(res, "xml").walkTopDown())
            .filter { it.extension == "xml" }
            .joinToString("\n") { it.readText() }
        val manifest = File("src/main/AndroidManifest.xml").takeIf { it.isFile }?.readText().orEmpty()

        val unused = declared.filterNot { key ->
            sources.contains("R.string.$key") ||
                referencedInXml.contains("@string/$key") ||
                manifest.contains("@string/$key")
        }

        assertEquals(
            "Unused string keys (each one is 6 translations to maintain) -- delete them from every locale",
            emptyList<String>(),
            unused,
        )
    }

    @Test
    fun `an escaped percent only appears in strings that are formatted`() {
        // `%%` becomes `%` only when the string goes through String.format. A string read
        // with plain stringResource(id) shows it literally -- the game-over screen said
        // "FLAWLESS 100%%" in every language.
        val formatSpecifier = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXeEgGcboh]""")
        val offenders = (listOf("values") + locales).flatMap { dir ->
            Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(File(res, "$dir/strings.xml").readText())
                .filter { m -> "%%" in m.groupValues[2] && !formatSpecifier.containsMatchIn(m.groupValues[2].replace("%%", "")) }
                .map { m -> "$dir/${m.groupValues[1]}" }
                .toList()
        }

        assertEquals("Unformatted strings showing a literal %%", emptyList<String>(), offenders)
    }
}
