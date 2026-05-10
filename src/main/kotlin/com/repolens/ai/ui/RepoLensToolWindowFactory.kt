package com.repolens.ai.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for the RepoLens AI tool window.
 *
 * Design decisions:
 * - Implements [DumbAware] so the tool window is available during indexing.
 *   Code explanations don't require full index — PSI is available immediately.
 * - The tool window is created lazily by IntelliJ (only when first activated),
 *   so there's no startup cost.
 * - The actual UI lives in [ResultPanel] — this factory is just the bridge
 *   between IntelliJ's extension system and our panel.
 */
class RepoLensToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val resultPanel = ResultPanel(project)
        val content = ContentFactory.getInstance().createContent(
            resultPanel,
            "",     // No tab title needed — single tab
            false   // Not lockable
        )
        toolWindow.contentManager.addContent(content)
    }
}
