package com.example.ambientpixel.domain

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

// Enum to define the available model types and their paths
enum class ModelType(val path: String) {
    GEMMA_4B("/sdcard/Download/gemma-3n-E2B-it-int4.task"),
    GEMMA_2B("/sdcard/Download/gemma-3n-E2B-it-int2.task") // Assumed filename for the 2-bit model
}

class LlmManager(private val context: Context) {
    private var llmHandle: LlmInference? = null
    private var currentModelPath = ModelType.GEMMA_4B.path // Default to 4B model

    // Default prompt that can be overridden
    private var currentSystemPrompt = SYSTEM_PROMPT

    /**
     * Sets the model to be used for inference. This will cause the current LlmInference instance
     * to be invalidated and a new one to be created on the next call to formatNote.
     */
    fun setModel(modelType: ModelType) {
        if (currentModelPath != modelType.path) {
            currentModelPath = modelType.path
            close() // Invalidate the handle to force recreation
        }
    }

    fun updateSystemPrompt(newPrompt: String) {
        currentSystemPrompt = newPrompt
    }

    fun formatNote(rawText: String): Flow<String> = flow {
        val prompt = buildPrompt(rawText)
        val result = generateResponse(prompt)
        emit(result)
    }.flowOn(Dispatchers.IO)

    fun close() {
        llmHandle?.close()
        llmHandle = null
    }

    private fun generateResponse(prompt: String): String {
        return getOrCreateLlm().generateResponse(prompt)
    }

    private fun getOrCreateLlm(): LlmInference {
        val existing = llmHandle
        if (existing != null) {
            return existing
        }

        val options = LlmInferenceOptions.builder()
            .setModelPath(currentModelPath)
            .setMaxTokens(DEFAULT_MAX_TOKENS)
            .build()

        val llm = LlmInference.createFromOptions(context, options)
        llmHandle = llm
        return llm
    }

    private fun buildPrompt(rawText: String): String {
        return """
            $currentSystemPrompt

            $rawText
        """.trimIndent()
    }

    private companion object {
        // Setting a high value to avoid truncation, as requested.
        private const val DEFAULT_MAX_TOKENS = 32000

        private const val SYSTEM_PROMPT =
            "You are a clinical documentation assistant. " +
            "Convert the raw transcript into a clear, structured clinical note. " +
            "HARD RULES: " +
            "(1) NO HALLUCINATION: Do not add facts, diagnoses, vitals, or interpretations not explicitly stated. " +
            "(2) COMPLETENESS: Include every medically relevant detail that is stated (symptoms, timeline, meds, doses, tests, advice, plans, follow-up, negatives, uncertainties). " +
            "(3) REMOVE ONLY IRRELEVANT CHATTER: Omit greetings, small talk, and unrelated conversation. " +
            "(4) UNCERTAINTY: If a detail is unclear, label it as 'Unclear' rather than guessing. " +
            "(5) OUTPUT ONLY THE NOTE. " +
            "FORMAT: Use SOAP headings (S, O, A, P). Include a section only if there is content for it."
    }
}
