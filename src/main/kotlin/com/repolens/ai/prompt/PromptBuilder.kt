package com.repolens.ai.prompt

import com.repolens.ai.context.CodeContext

/**
 * Builds structured LLM prompts from extracted [CodeContext].
 *
 * Design decisions:
 * - Stateless object with pure functions — easy to test by asserting on string output.
 * - Separates system prompt (role/format instructions) from user prompt (actual content).
 *   This follows the OpenAI best practice of using the system message for behavioral
 *   instructions and the user message for the specific task.
 * - Token-conscious: selectively includes context sections based on availability,
 *   with character budgets to prevent exceeding model context windows.
 * - The structured output format (4 sections) is enforced in the system prompt,
 *   making the response predictable and parseable by the UI layer.
 */
object PromptBuilder {

    /** Maximum total characters for the user prompt to stay within token limits. */
    private const val MAX_USER_PROMPT_CHARS = 6000

    /**
     * System prompt — instructs the LLM on its role and required output format.
     *
     * Key design choice: We ask for 4 specific sections rather than free-form text.
     * This makes the response predictable and allows the UI to render each section
     * in its own collapsible panel. The "keep it concise" instruction prevents
     * the LLM from generating walls of text.
     */
    fun buildSystemPrompt(): String = """
        |You are RepoLens AI, a code analysis assistant embedded in a developer's IDE.
        |You help developers understand code by providing clear, actionable explanations
        |enriched with repository context.
        |
        |When analyzing code, respond with EXACTLY these four sections using markdown headers:
        |
        |## Explanation
        |A clear, concise explanation of what the code does and why it exists.
        |Focus on intent, not just mechanics.
        |
        |## Dependencies
        |What this code depends on and what depends on it.
        |Include imports, method calls, inherited behavior, and injected services.
        |
        |## Side Effects
        |Any observable side effects: state mutations, I/O operations, network calls,
        |database writes, UI updates, or exception throwing. If none, say "No observable side effects."
        |
        |## Related Components
        |Other files, classes, or modules in the project that are likely related.
        |Explain the relationship briefly.
        |
        |Guidelines:
        |- Be concise. Developers read this in a tool window, not a blog post.
        |- Use code formatting for identifiers (e.g., `ClassName`, `methodName()`).
        |- If context is insufficient, say what you'd need to give a better answer.
        |- Do NOT repeat the source code back to the user.
    """.trimMargin()

    /**
     * Builds the user prompt containing the actual code and context.
     *
     * Sections are included conditionally — no empty sections are sent.
     * This avoids confusing the LLM with "Imports: (none)" placeholders and
     * saves tokens for context that actually matters.
     */
    fun buildUserPrompt(context: CodeContext): String {
        val sections = buildList {
            add(buildCodeSection(context))
            addIfNotEmpty(buildLocationSection(context))
            addIfNotEmpty(buildImportsSection(context))
            addIfNotEmpty(buildContainingElementSection(context))
            addIfNotEmpty(buildSiblingsSection(context))
        }

        return sections
            .joinToString("\n\n")
            .take(MAX_USER_PROMPT_CHARS)
    }

    private fun buildCodeSection(context: CodeContext): String {
        return """
            |Analyze the following ${context.language} code from project "${context.projectName}":
            |
            |```${context.language.lowercase()}
            |${context.selectedText}
            |```
        """.trimMargin()
    }

    private fun buildLocationSection(context: CodeContext): String {
        val parts = mutableListOf("File: `${context.filePath}`")
        context.containingElementName?.let {
            parts.add("Inside: `$it`")
        }
        return parts.joinToString("\n")
    }

    private fun buildImportsSection(context: CodeContext): String {
        if (context.imports.isEmpty()) return ""
        val importList = context.imports.joinToString("\n") { "- `$it`" }
        return "Imports in this file:\n$importList"
    }

    private fun buildContainingElementSection(context: CodeContext): String {
        val text = context.containingElementText ?: return ""
        if (text == context.selectedText) return "" // Avoid duplication

        return """
            |Full containing element:
            |```${context.language.lowercase()}
            |$text
            |```
        """.trimMargin()
    }

    private fun buildSiblingsSection(context: CodeContext): String {
        if (context.siblingSignatures.isEmpty()) return ""
        val siblingList = context.siblingSignatures.joinToString("\n") { "- `$it`" }
        return "Other declarations in the same scope:\n$siblingList"
    }

    /** Extension to conditionally add non-empty strings to a list builder. */
    private fun MutableList<String>.addIfNotEmpty(value: String) {
        if (value.isNotBlank()) add(value)
    }
}
