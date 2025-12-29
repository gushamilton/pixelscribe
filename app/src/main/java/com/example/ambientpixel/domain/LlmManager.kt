package com.example.ambientpixel.domain

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class LlmManager(private val context: Context) {
    private var llmHandle: LlmInference? = null
    
    // Default prompt that can be overridden
    private var currentSystemPrompt = SYSTEM_PROMPT

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
            .setModelPath(MODEL_PATH)
            .setMaxTokens(DEFAULT_MAX_TOKENS)
            // .setTopK(DEFAULT_TOP_K) // This method does not exist in this library version.
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
        private const val MODEL_PATH = "/sdcard/Download/gemma-3n-E2B-it-int4.task"
        // Setting to a high value as requested to avoid truncation.
        private const val DEFAULT_MAX_TOKENS = 32000
        private const val DEFAULT_TOP_K = 40

        private const val SYSTEM_PROMPT =
            "You are an expert medical scribe for an infectious diseases specialist. " +
            "Review the following raw transcript and generate a concise clinical note. " +
            "Remove all extraneous or irrelevant information, but retain all core medical details. " +
            "Format the note with the following sections exactly: " +
            "'PC' (Presenting Complaint), " +
            "'PMH' (Past Medical History), " +
            "'DH' (Drug History), " +
            "'SH' (Social History), " +
            "'Travel' (Travel History), " +
            "'Vaccines', " +
            "'Exam' (Physical Examination, if present in the transcript), " +
            "and 'Plan'. " +
            "Output ONLY the structured clinical note."
    }
}
