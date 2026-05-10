package com.repolens.ai.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persistent settings for the RepoLens AI plugin.
 *
 * Design decisions:
 * - API key is stored in IntelliJ's [PasswordSafe] (encrypted credential store),
 *   NOT in the XML state file. This is the correct pattern for secrets in IntelliJ
 *   plugins — it uses the OS keychain on macOS, Windows Credential Manager on Windows,
 *   and libsecret on Linux.
 * - Non-sensitive settings (model name, base URL) use [SimplePersistentStateComponent]
 *   with [BaseState] property delegates for automatic persistence.
 * - Default model is "gpt-4o-mini" — cheap, fast, and good enough for code explanations.
 *   Users can change to gpt-4o, Claude, or any compatible model.
 */
@Service(Service.Level.APP)
@State(
    name = "RepoLensAISettings",
    storages = [Storage("repolens-ai.xml")]
)
class RepoLensSettings : SimplePersistentStateComponent<RepoLensSettings.State>(State()) {

    class State : BaseState() {
        /** The LLM model to use for completions. */
        var modelName by string("gpt-4o-mini")

        /** Base URL for the OpenAI-compatible API. Changeable for Ollama, OpenRouter, etc. */
        var baseUrl by string("https://api.openai.com/v1")
    }

    /**
     * Retrieves the API key from the encrypted credential store.
     */
    fun getApiKey(): String {
        val attributes = createCredentialAttributes()
        return PasswordSafe.instance.getPassword(attributes) ?: ""
    }

    /**
     * Stores the API key in the encrypted credential store.
     */
    fun setApiKey(apiKey: String) {
        val attributes = createCredentialAttributes()
        PasswordSafe.instance.setPassword(attributes, apiKey)
    }

    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("RepoLensAI", "apiKey")
        )
    }

    companion object {
        fun getInstance(): RepoLensSettings {
            return ApplicationManager.getApplication().getService(RepoLensSettings::class.java)
        }
    }
}
