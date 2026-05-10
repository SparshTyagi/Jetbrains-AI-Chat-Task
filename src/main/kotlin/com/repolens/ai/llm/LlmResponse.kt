package com.repolens.ai.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for the OpenAI-compatible chat completions API response.
 *
 * Design decision: We model only the fields we actually read, not the full API surface.
 * OpenAI's response has ~20 fields; we need exactly 3. Modeling the rest would be
 * dead code that creates a false sense of completeness.
 *
 * These models also work with any OpenAI-compatible API (Ollama, OpenRouter, vLLM,
 * LM Studio, etc.) since they all follow the same response schema.
 */

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val message: MessageContent,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class MessageContent(
    val role: String,
    val content: String? = null,
)
