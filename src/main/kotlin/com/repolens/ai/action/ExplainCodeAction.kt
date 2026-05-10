package com.repolens.ai.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.repolens.ai.service.RepoLensService

/**
 * Editor action that triggers RepoLens AI code analysis.
 *
 * Available via:
 * - Right-click → "Explain with RepoLens AI" (EditorPopupMenu)
 * - Code menu → "Explain with RepoLens AI" (CodeMenu)
 * - Keyboard shortcut: Ctrl+Shift+L
 *
 * Design decisions:
 * - This class is intentionally thin. It only handles:
 *   (1) Determining if the action should be visible/enabled (update)
 *   (2) Delegating to the service layer (actionPerformed)
 *   All business logic lives in [RepoLensService].
 *
 * - getActionUpdateThread() returns BGT (Background Thread) — required for modern
 *   IntelliJ plugins. The update() method runs on a background thread to avoid
 *   blocking the UI when checking action availability.
 */
class ExplainCodeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        // Delegate entirely to the service — keep the action thin
        RepoLensService.getInstance(project).analyzeCode(editor, psiFile)
    }

    /**
     * Controls when the action is visible and enabled.
     *
     * Visible: when an editor and PSI file are available (i.e., a code file is open).
     * Enabled: same conditions. We don't require an active selection — the action
     * can also analyze the element under the caret.
     */
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }

    /**
     * Specifies that update() should run on a background thread.
     * This is required for IntelliJ 2022.3+ and prevents UI freezes
     * when the platform evaluates action availability.
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
