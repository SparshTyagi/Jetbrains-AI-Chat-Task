package com.repolens.ai.llm

/**
 * Abstraction for LLM completion providers.
 *
 * Design decision: This is a single-method interface, not an abstract class
 * with configuration methods. Why?
 * - It's the simplest possible abstraction that enables testing (mock it)
 *   and future extensibility (add Anthropic, Gemini, local models).
 * - We DON'T add methods like `listModels()`, `validateKey()`, or `streamComplete()`
 *   because we don't need them yet. YAGNI.
 * - The suspend modifier enables non-blocking calls without callback hell,
 *   which is the modern IntelliJ pattern for background work.
 */
interface LlmProvider {

    /**
     * Sends a completion request to the LLM and returns the response text.
     *
     * @param systemPrompt Instructions for the LLM's behavior and output format.
     * @param userPrompt The actual content to analyze (code + context).
     * @return The LLM's response text.
     * @throws LlmException if the request fails for any reason.
     */
    suspend fun complete(systemPrompt: String, userPrompt: String): String
}

/**
 * Typed exception for LLM-related failures.
 * Using a dedicated exception type (rather than generic IOException) lets the UI
 * layer show specific, actionable error messages.
 */
class LlmException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
