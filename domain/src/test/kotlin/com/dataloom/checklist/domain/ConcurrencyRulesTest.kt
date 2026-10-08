package com.dataloom.checklist.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level guards for the coroutine rules in docs/performance-and-state.md. They scan the
 * production sources of every module (tests are free to use test dispatchers), so a violation fails
 * `:domain:test` on a developer machine without any Android tooling.
 *
 * Why scan instead of a lint rule: the project has no custom lint module yet, and these three rules
 * are plain text patterns.
 */
class ConcurrencyRulesTest {

    private val moduleDirs = listOf("domain", "data", "ai", "app")

    /** Production Kotlin files of all modules, located relative to this module's working directory. */
    private fun productionSources(): List<File> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: error("settings.gradle.kts not found above ${File("").absolutePath}")
        val files = moduleDirs
            .map { File(root, "$it/src/main") }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        assertTrue("no sources found under $root", files.isNotEmpty())
        return files
    }

    private fun File.codeLines(): List<Pair<Int, String>> = readLines()
        .mapIndexed { index, line -> index + 1 to line }
        .filterNot { (_, line) -> line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") } }

    private fun violations(pattern: Regex, allowed: Set<String> = emptySet()): List<String> =
        productionSources()
            .filterNot { it.name in allowed }
            .flatMap { file ->
                file.codeLines().filter { (_, line) -> pattern.containsMatchIn(line) }
                    .map { (number, line) -> "${file.name}:$number: ${line.trim()}" }
            }

    @Test
    fun `no class names a concrete dispatcher, only CoroutinesModule does`() {
        val found = violations(Regex("""Dispatchers\.(IO|Default|Unconfined)\b"""), allowed = setOf("CoroutinesModule.kt"))
        assertTrue("Inject @IoDispatcher or @DefaultDispatcher instead:\n${found.joinToString("\n")}", found.isEmpty())
    }

    @Test
    fun `no GlobalScope and no runBlocking in production code`() {
        val found = violations(Regex("""\b(GlobalScope|runBlocking)\b"""))
        assertTrue("Use a structured scope (viewModelScope or the injected application scope):\n${found.joinToString("\n")}", found.isEmpty())
    }

    @Test
    fun `scopes are created only by the application module`() {
        val found = violations(Regex("""\bCoroutineScope\("""), allowed = setOf("AppModule.kt"))
        assertTrue("Inject the @ApplicationScope scope instead of creating one:\n${found.joinToString("\n")}", found.isEmpty())
    }
}
