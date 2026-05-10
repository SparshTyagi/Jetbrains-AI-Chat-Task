package com.repolens.ai.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

/**
 * Settings UI for RepoLens AI, accessible via Settings → Tools → RepoLens AI.
 *
 * Design decisions:
 * - Uses [BoundConfigurable] with Kotlin UI DSL — the modern, recommended approach
 *   for IntelliJ settings pages. Handles apply/reset/isModified automatically
 *   through property bindings.
 * - API key field uses a password input (JBPasswordField equivalent via `passwordField()`).
 * - The API key is bound manually (not via `bindText`) because it's stored in
 *   PasswordSafe rather than the regular state object.
 * - Base URL and model name are bound to mutable buffers and applied manually,
 *   since BaseState delegates use nullable types internally.
 */
class RepoLensConfigurable : BoundConfigurable("RepoLens AI") {

    private val settings = RepoLensSettings.getInstance()

    // Mutable copies for the settings form lifecycle.
    // We buffer these rather than binding directly to BaseState properties,
    // because BaseState delegates use nullable String? internally while
    // UI DSL `bindText` expects non-null KMutableProperty0<String>.
    private var apiKeyBuffer = settings.getApiKey()
    private var baseUrlBuffer = settings.state.baseUrl ?: "https://api.openai.com/v1"
    private var modelNameBuffer = settings.state.modelName ?: "gpt-4o-mini"

    override fun createPanel(): DialogPanel = panel {
        group("API Configuration") {
            row("API Key:") {
                passwordField()
                    .columns(COLUMNS_LARGE)
                    .applyToComponent {
                        text = apiKeyBuffer
                    }
                    .onChanged {
                        apiKeyBuffer = String(it.password)
                    }
                    .comment("Your OpenAI API key. Stored securely in the OS credential manager.")
            }

            row("Base URL:") {
                textField()
                    .columns(COLUMNS_LARGE)
                    .applyToComponent {
                        text = baseUrlBuffer
                    }
                    .onChanged {
                        baseUrlBuffer = it.text
                    }
                    .comment("Change this for Ollama (http://localhost:11434/v1), OpenRouter, etc.")
            }

            row("Model:") {
                textField()
                    .columns(COLUMNS_LARGE)
                    .applyToComponent {
                        text = modelNameBuffer
                    }
                    .onChanged {
                        modelNameBuffer = it.text
                    }
                    .comment("e.g., gpt-4o-mini, gpt-4o, llama3, mistral")
            }
        }

        group("About") {
            row {
                text(
                    "RepoLens AI provides repository-aware code explanations. " +
                    "Select code in the editor and use <b>Right-click → Explain with RepoLens AI</b> " +
                    "or press <b>Ctrl+Shift+L</b>."
                )
            }
        }
    }

    override fun apply() {
        super.apply()
        settings.setApiKey(apiKeyBuffer)
        settings.state.baseUrl = baseUrlBuffer
        settings.state.modelName = modelNameBuffer
    }

    override fun reset() {
        super.reset()
        apiKeyBuffer = settings.getApiKey()
        baseUrlBuffer = settings.state.baseUrl ?: "https://api.openai.com/v1"
        modelNameBuffer = settings.state.modelName ?: "gpt-4o-mini"
    }

    override fun isModified(): Boolean {
        return super.isModified()
            || apiKeyBuffer != settings.getApiKey()
            || baseUrlBuffer != (settings.state.baseUrl ?: "https://api.openai.com/v1")
            || modelNameBuffer != (settings.state.modelName ?: "gpt-4o-mini")
    }
}
