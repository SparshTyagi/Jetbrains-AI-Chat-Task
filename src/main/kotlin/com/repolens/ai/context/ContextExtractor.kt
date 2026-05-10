package com.repolens.ai.context

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Extracts repository-aware context from the IDE's editor and PSI tree.
 *
 * Design decisions:
 * - Stateless object: no mutable state, all inputs are parameters. This makes it
 *   trivially testable and safe to call from any thread.
 * - Language-agnostic: Uses generic PSI types (PsiNamedElement) rather than
 *   language-specific PSI classes (PsiMethod, KtFunction). This means the plugin
 *   works with ANY language IntelliJ supports, not just Java/Kotlin.
 * - Token-conscious: Caps extracted text lengths to prevent prompt explosion.
 *   The LLM doesn't need 500-line methods to understand a 10-line selection.
 */
object ContextExtractor {

    /** Maximum characters for the enclosing element to prevent token explosion. */
    private const val MAX_CONTAINING_ELEMENT_CHARS = 3000

    /** Maximum characters for each sibling signature. */
    private const val MAX_SIBLING_CHARS = 200

    /** Maximum number of sibling declarations to include. */
    private const val MAX_SIBLINGS = 15

    /**
     * Extracts [CodeContext] from the current editor state.
     *
     * Must be called within a read action (the caller is responsible for this).
     * The [RepoLensService] wraps this call in `readAction { }`.
     */
    fun extract(editor: Editor, psiFile: PsiFile, project: Project): CodeContext {
        val selectedText = getSelectedText(editor, psiFile)
        val offset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(offset)

        // Walk up the PSI tree to find the nearest meaningful named element
        // (method, function, class, etc.)
        val containingElement = elementAtCaret?.let { findContainingDeclaration(it) }

        return CodeContext(
            selectedText = selectedText,
            containingElementName = containingElement?.name,
            containingElementText = containingElement?.text?.take(MAX_CONTAINING_ELEMENT_CHARS),
            filePath = getRelativePath(psiFile, project),
            language = psiFile.language.displayName,
            imports = extractImports(psiFile),
            siblingSignatures = extractSiblingSignatures(containingElement),
            projectName = project.name,
        )
    }

    /**
     * Gets the user's selected text, or falls back to the element under the caret.
     * This ensures the action works both when text is highlighted and when the
     * user simply right-clicks on a symbol.
     */
    private fun getSelectedText(editor: Editor, psiFile: PsiFile): String {
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            return selectionModel.selectedText ?: ""
        }

        // No selection: use the element at the caret position
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
        val namedParent = element?.let { findContainingDeclaration(it) }
        return namedParent?.text?.take(MAX_CONTAINING_ELEMENT_CHARS) ?: element?.text ?: ""
    }

    /**
     * Walks up the PSI tree to find the nearest PsiNamedElement.
     * This is intentionally generic — it finds methods, functions, classes,
     * properties, etc. across all languages without importing language-specific PSI.
     */
    private fun findContainingDeclaration(element: PsiElement): PsiNamedElement? {
        return PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java, false)
    }

    /**
     * Extracts import statements from the file.
     * Uses a simple heuristic: lines starting with "import " in the file text.
     * This avoids depending on language-specific PSI import classes while
     * capturing the dependency information the LLM needs.
     */
    private fun extractImports(psiFile: PsiFile): List<String> {
        return psiFile.text
            .lines()
            .filter { it.trimStart().startsWith("import ") }
            .map { it.trim() }
            .take(30) // Cap to prevent excessive token usage
    }

    /**
     * Extracts short signatures of sibling declarations in the same parent scope.
     * This gives the LLM awareness of the class/file structure without sending
     * all the implementation details.
     */
    private fun extractSiblingSignatures(element: PsiNamedElement?): List<String> {
        val parent = element?.parent ?: return emptyList()

        return PsiTreeUtil.getChildrenOfType(parent, PsiNamedElement::class.java)
            ?.filter { it !== element } // Exclude the element itself
            ?.take(MAX_SIBLINGS)
            ?.mapNotNull { sibling ->
                // Take just the first line (signature) of each sibling
                val firstLine = sibling.text?.lines()?.firstOrNull()?.take(MAX_SIBLING_CHARS)
                sibling.name?.let { name -> "$name: $firstLine" }
            }
            ?: emptyList()
    }

    /**
     * Computes the file's path relative to the project root.
     * Falls back to the absolute path if the file isn't under the project base dir.
     */
    private fun getRelativePath(psiFile: PsiFile, project: Project): String {
        val projectDir = project.basePath ?: return psiFile.virtualFile?.path ?: "unknown"
        val filePath = psiFile.virtualFile?.path ?: return "unknown"

        return if (filePath.startsWith(projectDir)) {
            filePath.removePrefix(projectDir).removePrefix("/")
        } else {
            filePath
        }
    }
}
