package com.softyorch.stroopoverload.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `CancellationException` is an `Exception`, so a bare `catch (e: Exception)` around a
 * suspending call swallows coroutine cancellation: the function logs the "failure" and
 * carries on writing state after the scope that cancelled it is gone.
 *
 * The data layer is where it bites hardest (those catches wrap Firebase `await()` calls),
 * but the mistake is invisible at review time anywhere, so it is checked mechanically
 * across the app's sources: every block-form `} catch (e: Exception) {` must be preceded
 * by a CancellationException rethrow.
 * Inline `catch (e: Exception) { ... }` one-liners in property initializers are not
 * coroutine code and are not matched.
 */
class NoSwallowedCancellationTest {

    private val sourceRoot = File("src/main/kotlin/com/softyorch/stroopoverload")

    @Test
    fun `the app source is where it is expected to be`() {
        assertTrue("Expected ${sourceRoot.absolutePath} to exist", sourceRoot.isDirectory)
    }

    @Test
    fun `every catch of Exception rethrows CancellationException first`() {
        val offenders = mutableListOf<String>()

        sourceRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (!line.trim().startsWith("} catch (e: Exception) {")) return@forEachIndexed
                val preceding = lines.subList(maxOf(0, index - 3), index).joinToString("\n")
                val rethrows = preceding.contains("CancellationException") && preceding.contains("throw e")
                if (!rethrows) offenders += "${file.name}:${index + 1}"
            }
        }

        assertTrue(
            "These catches swallow CancellationException -- add `catch (e: CancellationException) { throw e }` " +
                "before them, or use runCatchingCancellable: $offenders",
            offenders.isEmpty(),
        )
    }
}
