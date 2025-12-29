package com.example.ambientpixel.domain

import android.content.Context
import java.io.File

object ModelAssets {
    // We now look for files in /sdcard/Download/ rather than assets for the large models.
    private val downloadsPath = "/sdcard/Download/"
    
    // Map of Friendly Name -> Filename
    private val requiredFiles = mapOf(
        "Transcription Encoder" to "encoder.int8.onnx",
        "Transcription Decoder" to "decoder.int8.onnx",
        "Transcription Joiner" to "joiner.int8.onnx",
        "Transcription Tokens" to "tokens.txt",
        "LLM Model" to "gemma-3n-E4B-it-int4.task"
    )

    fun missingAssets(context: Context): List<String> {
        // We check the Download folder directly
        val missing = mutableListOf<String>()
        
        requiredFiles.forEach { (name, filename) ->
            val file = File(downloadsPath, filename)
            if (!file.exists()) {
                missing.add(filename)
            }
        }
        
        return missing
    }
}
