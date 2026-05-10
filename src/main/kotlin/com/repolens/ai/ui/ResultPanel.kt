package com.repolens.ai.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * The main results panel for the RepoLens AI tool window.
 *
 * Design decisions:
 * - Uses standard Swing with IntelliJ UI utilities (JBColor, JBUI, UIUtil) for native
 *   look-and-feel across all themes (light, dark, high contrast).
 * - Three states: empty (instructions), loading (spinner text), result (structured sections).
 * - Results are displayed as collapsible-like sections with headers matching the 4
 *   sections from our system prompt (Explanation, Dependencies, Side Effects, Related).
 * - Uses JTextArea for content — supports text wrapping and selection/copy, which
 *   developers expect. A JLabel wouldn't allow copying text.
 * - Thread safety: all UI updates go through SwingUtilities.invokeLater to ensure
 *   they run on the EDT. The service layer calls these methods from coroutines.
 */
class ResultPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
    }

    private val scrollPane = JBScrollPane(contentPanel).apply {
        border = BorderFactory.createEmptyBorder()
        verticalScrollBar.unitIncrement = 16
    }

    init {
        add(scrollPane, BorderLayout.CENTER)
        showEmptyState()
    }

    /**
     * Shows usage instructions when no analysis has been performed yet.
     */
    fun showEmptyState() {
        SwingUtilities.invokeLater {
            contentPanel.removeAll()

            val label = JBLabel(
                "<html><center>" +
                "<p style='font-size:13pt; color:gray;'>RepoLens AI</p>" +
                "<br/>" +
                "<p style='color:gray;'>Select code in the editor and choose</p>" +
                "<p style='color:gray;'><b>Right-click → Explain with RepoLens AI</b></p>" +
                "<p style='color:gray;'>or press <b>Ctrl+Shift+L</b></p>" +
                "</center></html>"
            ).apply {
                horizontalAlignment = SwingConstants.CENTER
                border = JBUI.Borders.empty(40, 20)
            }

            contentPanel.add(label)
            contentPanel.revalidate()
            contentPanel.repaint()
        }
    }

    /**
     * Shows a loading indicator while waiting for the LLM response.
     */
    fun showLoading(elementName: String?) {
        SwingUtilities.invokeLater {
            contentPanel.removeAll()

            val message = if (elementName != null) {
                "Analyzing `$elementName`…"
            } else {
                "Analyzing selected code…"
            }

            val label = JBLabel(
                "<html><center>" +
                "<p style='font-size:12pt;'>⏳ $message</p>" +
                "<br/>" +
                "<p style='color:gray; font-size:10pt;'>Gathering context and querying LLM…</p>" +
                "</center></html>"
            ).apply {
                horizontalAlignment = SwingConstants.CENTER
                border = JBUI.Borders.empty(40, 20)
            }

            contentPanel.add(label)
            contentPanel.revalidate()
            contentPanel.repaint()
        }
    }

    /**
     * Displays the structured LLM analysis result.
     *
     * Parses the response into sections based on markdown H2 headers (## Explanation, etc.)
     * and renders each as a visually distinct panel. This parsing is deliberately simple —
     * we instructed the LLM to use specific headers, so we match on those.
     */
    fun showResult(filePath: String, elementName: String?, response: String) {
        SwingUtilities.invokeLater {
            contentPanel.removeAll()

            // Header with file path and element name
            addHeader(filePath, elementName)

            // Parse the LLM response into sections and render each one
            val sections = parseResponseSections(response)
            if (sections.isEmpty()) {
                // If parsing fails, show the raw response as a single section
                addSection("Analysis", response)
            } else {
                sections.forEach { (title, content) ->
                    addSection(title, content)
                }
            }

            contentPanel.revalidate()
            contentPanel.repaint()

            // Scroll to top
            SwingUtilities.invokeLater {
                scrollPane.verticalScrollBar.value = 0
            }
        }
    }

    /**
     * Displays an error message with actionable guidance.
     */
    fun showError(message: String) {
        SwingUtilities.invokeLater {
            contentPanel.removeAll()

            val label = JBLabel(
                "<html><center>" +
                "<p style='font-size:12pt; color:${getErrorColorHex()};'>⚠ Analysis Failed</p>" +
                "<br/>" +
                "<p style='font-size:10pt;'>${escapeHtml(message)}</p>" +
                "</center></html>"
            ).apply {
                horizontalAlignment = SwingConstants.CENTER
                border = JBUI.Borders.empty(40, 20)
            }

            contentPanel.add(label)
            contentPanel.revalidate()
            contentPanel.repaint()
        }
    }

    // --- UI Building Helpers ---

    private fun addHeader(filePath: String, elementName: String?) {
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 0, 12, 0)

            val title = elementName ?: filePath.substringAfterLast('/')
            val titleLabel = JBLabel(title).apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }
            add(titleLabel, BorderLayout.NORTH)

            val pathLabel = JBLabel(filePath).apply {
                font = font.deriveFont(10f)
                foreground = JBColor.GRAY
            }
            add(pathLabel, BorderLayout.SOUTH)
        }

        // Make the panel take full width
        headerPanel.maximumSize = java.awt.Dimension(Int.MAX_VALUE, headerPanel.preferredSize.height)
        headerPanel.alignmentX = LEFT_ALIGNMENT
        contentPanel.add(headerPanel)
    }

    private fun addSection(title: String, content: String) {
        val sectionPanel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = getSectionBackground()
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.empty(4),
                JBUI.Borders.empty(10)
            )

            // Section title
            val titleLabel = JBLabel(getSectionIcon(title) + " " + title).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                border = JBUI.Borders.empty(0, 0, 6, 0)
            }
            add(titleLabel, BorderLayout.NORTH)

            // Section content — JTextArea for selection/copy support
            val contentArea = JTextArea(content.trim()).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                isOpaque = false
                font = UIUtil.getLabelFont().deriveFont(12f)
                foreground = UIUtil.getLabelForeground()
                border = JBUI.Borders.empty()
                // Match the panel background
                background = getSectionBackground()
            }
            add(contentArea, BorderLayout.CENTER)
        }

        sectionPanel.maximumSize = java.awt.Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        sectionPanel.alignmentX = LEFT_ALIGNMENT
        contentPanel.add(sectionPanel)
    }

    // --- Response Parsing ---

    /**
     * Parses the LLM response into named sections.
     *
     * Expects markdown H2 headers matching our system prompt format:
     * ## Explanation, ## Dependencies, ## Side Effects, ## Related Components
     *
     * This is intentionally simple regex-free parsing. The LLM was instructed to use
     * these exact headers, so we split on them. If the format doesn't match (e.g.,
     * the user switched to a less capable model), the raw response is shown as-is.
     */
    private fun parseResponseSections(response: String): List<Pair<String, String>> {
        val sections = mutableListOf<Pair<String, String>>()
        val lines = response.lines()

        var currentTitle: String? = null
        val currentContent = StringBuilder()

        for (line in lines) {
            if (line.startsWith("## ")) {
                // Save the previous section
                if (currentTitle != null) {
                    sections.add(currentTitle to currentContent.toString())
                }
                currentTitle = line.removePrefix("## ").trim()
                currentContent.clear()
            } else {
                if (currentTitle != null) {
                    currentContent.appendLine(line)
                }
            }
        }

        // Save the last section
        if (currentTitle != null) {
            sections.add(currentTitle to currentContent.toString())
        }

        return sections
    }

    // --- Theme-Aware Utilities ---

    private fun getSectionBackground(): java.awt.Color {
        return JBColor(
            java.awt.Color(245, 245, 250),  // Light theme: subtle blue-gray
            java.awt.Color(45, 45, 50)       // Dark theme: slightly lighter than background
        )
    }

    private fun getErrorColorHex(): String {
        return if (JBColor.isBright()) "#C62828" else "#EF5350"
    }

    private fun getSectionIcon(title: String): String = when {
        title.contains("Explanation", ignoreCase = true) -> "💡"
        title.contains("Dependencies", ignoreCase = true) -> "🔗"
        title.contains("Side Effect", ignoreCase = true) -> "⚡"
        title.contains("Related", ignoreCase = true) -> "📁"
        else -> "📋"
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    companion object {
        /**
         * Finds the ResultPanel instance for the given project.
         * Returns null if the tool window hasn't been created yet.
         */
        fun getInstance(project: Project): ResultPanel? {
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("RepoLens AI") ?: return null
            val content = toolWindow.contentManager.getContent(0) ?: return null
            return content.component as? ResultPanel
        }
    }
}
