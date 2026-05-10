package com.repolens.ai.context

/**
 * Represents the extracted repository context around a user's code selection.
 *
 * Design decision: This is a simple data class rather than a sealed hierarchy.
 * We don't need polymorphism here — every analysis request has the same shape
 * of context, just with varying completeness. Nullable fields handle the cases
 * where context isn't available (e.g., no enclosing method in a top-level script).
 */
data class CodeContext(
    /** The text the user explicitly selected, or the element under the caret. */
    val selectedText: String,

    /** Name of the enclosing method/function/class, if identifiable. */
    val containingElementName: String?,

    /** Full source of the enclosing element — provides structural context to the LLM. */
    val containingElementText: String?,

    /** Relative path of the file within the project (e.g., "src/main/kotlin/Foo.kt"). */
    val filePath: String,

    /** Programming language ID as reported by IntelliJ (e.g., "Kotlin", "JAVA", "Python"). */
    val language: String,

    /** Import statements in the file — reveals dependencies without sending the whole file. */
    val imports: List<String>,

    /** Signatures of sibling declarations (other methods/fields in the same class).
     *  Gives the LLM awareness of the broader class contract without excessive tokens. */
    val siblingSignatures: List<String>,

    /** Project name — useful for the LLM to understand the codebase identity. */
    val projectName: String,
)
