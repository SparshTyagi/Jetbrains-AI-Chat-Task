package com.repolens.ai.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import com.repolens.ai.context.ContextExtractor
import com.repolens.ai.llm.LlmException
import com.repolens.ai.llm.OpenAiProvider
import com.repolens.ai.prompt.PromptBuilder
import com.repolens.ai.ui.ResultPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Project-level service that orchestrates the full analysis flow.
 *
 * Design decisions:
 * - @Service(Service.Level.PROJECT) with injected CoroutineScope: This is the modern
 *   IntelliJ pattern for project-scoped background work. The scope is automatically
 *   cancelled when the project closes, preventing leaked coroutines.
 *
 * - Single public method [analyzeCode]: The action layer calls this one method,
 *   and the service handles all coordination. This keeps the action class thin
 *   (just UI event handling) and the service testable (inject mock LLM provider).
 *
 * - LLM provider is instantiated here, not injected. In a larger plugin we'd use
 *   dependency injection, but for 1 provider this is pragmatic and avoids boilerplate.
 *
 * - Error handling: All exceptions are caught and shown in the UI. The user never
 *   sees a raw stack trace — they see an actionable message.
 */
@Service(Service.Level.PROJECT)
class RepoLensService(
    private val project: Project,
    private val scope: CoroutineScope,
) {

    private val llmProvider = OpenAiProvider()

    private val log = Logger.getInstance(RepoLensService::class.java)

    /**
     * Entry point for code analysis. Called by [ExplainCodeAction].
     *
     * Flow:
     * 1. Extract context from PSI (read action)
     * 2. Build prompt from context
     * 3. Show loading UI
     * 4. Call LLM in background
     * 5. Show result or error in tool window
     */
    fun analyzeCode(editor: Editor, psiFile: PsiFile) {
        scope.launch {
            try {
                // Step 1: Extract context (must be in a read action)
                val context = com.intellij.openapi.application.readAction {
                    ContextExtractor.extract(editor, psiFile, project)
                }

                log.info("Extracted context for: ${context.containingElementName ?: context.filePath}")

                // Step 2: Build prompts
                val systemPrompt = PromptBuilder.buildSystemPrompt()
                val userPrompt = PromptBuilder.buildUserPrompt(context)

                // Step 3: Show loading state and activate tool window
                activateToolWindow()
                getResultPanel()?.showLoading(context.containingElementName)

                // Step 4: Call LLM with background progress indicator
                val response = withBackgroundProgress(project, "RepoLens AI: Analyzing code…") {
                    llmProvider.complete(systemPrompt, userPrompt)
                }

                // Step 5: Show result
                getResultPanel()?.showResult(
                    filePath = context.filePath,
                    elementName = context.containingElementName,
                    response = response,
                )

                log.info("Analysis complete for: ${context.containingElementName ?: context.filePath}")

            } catch (e: LlmException) {
                log.warn("LLM error: ${e.message}")
                getResultPanel()?.showError(e.message ?: "Unknown error")
            } catch (e: Exception) {
                log.error("Unexpected error during analysis", e)
                getResultPanel()?.showError("Unexpected error: ${e.message}")
            }
        }
    }

    /**
     * Activates the tool window and brings it to front.
     * Called on the coroutine thread — ToolWindowManager handles thread safety.
     */
    private fun activateToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("RepoLens AI")
        toolWindow?.show()
    }

    private fun getResultPanel(): ResultPanel? {
        return ResultPanel.getInstance(project)
    }

    companion object {
        fun getInstance(project: Project): RepoLensService {
            return project.getService(RepoLensService::class.java)
        }
    }
}
