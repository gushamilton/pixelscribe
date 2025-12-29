package com.example.ambientpixel.domain

import android.content.Context
import java.io.File

object ModelAssets {
    // We now look for files in /sdcard/Download/ rather than assets for the large models.
    private val downloadsPath = "/sdcard/Download/"
    
    private val transcriptionFiles = listOf(
        "encoder.int8.onnx",
        "decoder.int8.onnx",
        "joiner.int8.onnx",
        "tokens.txt"
    )

    private val llmFiles = mapOf(
        ModelType.GEMMA_4B to "gemma-3n-E2B-it-int4.task",
        ModelType.GEMMA_2B to "gemma-3n-E2B-it-int2.task"
    )

    fun missingAssetsFor(modelType: ModelType): List<String> {
        val missing = mutableListOf<String>()

        transcriptionFiles.forEach { filename ->
            val file = File(downloadsPath, filename)
            if (!file.exists()) {
                missing.add(filename)
            }
        }

        val llmFile = llmFiles[modelType]
        if (llmFile != null) {
            val file = File(downloadsPath, llmFile)
            if (!file.exists()) {
                missing.add(llmFile)
            }
        }

        return missing
    }
}
