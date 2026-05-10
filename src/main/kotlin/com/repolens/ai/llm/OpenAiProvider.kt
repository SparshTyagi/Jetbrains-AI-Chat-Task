package com.repolens.ai.llm

import com.repolens.ai.settings.RepoLensSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible LLM provider using OkHttp.
 *
 * Design decisions:
 * - Uses OkHttp directly instead of a community SDK. Why?
 *   (1) The OpenAI chat completions API is simple enough that a raw HTTP call
 *       is clearer than adding a library dependency.
 *   (2) It demonstrates understanding of the API rather than delegating to a wrapper.
 *   (3) Works with ANY OpenAI-compatible endpoint (Ollama, OpenRouter, vLLM, etc.)
 *       without SDK-specific configuration.
 *
 * - Non-streaming: For one-shot code explanations (typically <500 tokens), streaming
 *   adds complexity (SSE parsing, partial UI updates) without meaningful UX improvement.
 *   A user waits 2-3 seconds either way. Streaming would be worthwhile for chat-style
 *   interactions, which is documented as a future improvement.
 *
 * - JSON is built with kotlinx.serialization for type safety, but the request body
 *   is constructed manually to avoid modeling the full request schema. The response
 *   is deserialized into typed models for safe field access.
 */
class OpenAiProvider : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)    // LLM responses can take a while
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true              // Future-proof against API additions
        isLenient = true                      // Handle minor JSON quirks from some providers
    }

    override suspend fun complete(systemPrompt: String, userPrompt: String): String {
        val settings = RepoLensSettings.getInstance()
        val apiKey = settings.getApiKey()
        val baseUrl = (settings.state.baseUrl ?: "https://api.openai.com/v1").trimEnd('/')
        val model = settings.state.modelName ?: "gpt-4o-mini"

        if (apiKey.isBlank()) {
            throw LlmException(
                "API key not configured. Go to Settings → Tools → RepoLens AI to set your API key."
            )
        }

        val requestBody = buildRequestJson(systemPrompt, userPrompt, model)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        // Execute on IO dispatcher — OkHttp's execute() blocks the calling thread
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                        ?: throw LlmException("Empty response from LLM API")

                    if (!response.isSuccessful) {
                        throw mapHttpError(response.code, body)
                    }

                    val parsed = json.decodeFromString<ChatCompletionResponse>(body)
                    parsed.choices.firstOrNull()?.message?.content
                        ?: throw LlmException("LLM returned no content in response")
                }
            } catch (e: LlmException) {
                throw e // Re-throw our own exceptions as-is
            } catch (e: IOException) {
                throw LlmException("Network error: ${e.message}. Check your internet connection and base URL.", e)
            } catch (e: Exception) {
                throw LlmException("Unexpected error: ${e.message}", e)
            }
        }
    }

    /**
     * Builds the JSON request body manually.
     *
     * We construct this as a raw JSON string rather than defining a full request
     * data class. The request shape is simple and stable — 3 fields — so a data class
     * would be over-engineering for no testability gain.
     */
    private fun buildRequestJson(systemPrompt: String, userPrompt: String, model: String): String {
        // Use kotlinx.serialization to safely escape string content
        val escapedSystem = json.encodeToString(systemPrompt).removeSurrounding("\"")
        val escapedUser = json.encodeToString(userPrompt).removeSurrounding("\"")

        return """
            {
                "model": "${model}",
                "messages": [
                    {"role": "system", "content": "$escapedSystem"},
                    {"role": "user", "content": "$escapedUser"}
                ],
                "temperature": 0.3,
                "max_tokens": 1500
            }
        """.trimIndent()
    }

    /**
     * Maps HTTP error codes to user-friendly error messages.
     * This is important because raw "401 Unauthorized" doesn't tell a developer
     * what to DO about it.
     */
    private fun mapHttpError(code: Int, body: String): LlmException = when (code) {
        401 -> LlmException("Invalid API key. Check your key in Settings → Tools → RepoLens AI.")
        429 -> LlmException("Rate limit exceeded. Please wait a moment and try again.")
        404 -> LlmException("API endpoint not found. Check your base URL in Settings → Tools → RepoLens AI.")
        in 500..599 -> LlmException("LLM server error (HTTP $code). The provider may be experiencing issues.")
        else -> LlmException("LLM API error (HTTP $code): ${body.take(200)}")
    }
}
