package com.example.ambientpixel.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ambientpixel.data.NoteEntity
import com.example.ambientpixel.data.NoteRepository
import com.example.ambientpixel.domain.AudioRecorderManager
import com.example.ambientpixel.domain.LlmManager
import com.example.ambientpixel.domain.ModelAssets
import com.example.ambientpixel.domain.TranscriptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConsultationViewModel(
    private val appContext: Context,
    private val noteRepository: NoteRepository
) : ViewModel() {
    private val recorder = AudioRecorderManager(appContext)
    private val transcriber = TranscriptionService(appContext)
    private val llmManager = LlmManager(appContext)

    private var timerJob: Job? = null
    private var currentAudioPath: String? = null
    private var currentNoteId: Long? = null

    var uiState by mutableStateOf(ConsultationUiState())
        private set

    init {
        val missing = ModelAssets.missingAssets(appContext)
        if (missing.isNotEmpty()) {
            uiState = uiState.copy(
                lastError = "Missing model files: ${missing.joinToString(", ")}."
            )
        }
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            noteRepository.observeNotes().collect { notes ->
                uiState = uiState.copy(history = notes)
            }
        }
    }

    fun startRecording() {
        if (uiState.screen == Screen.Recording) return

        val filename = "recording_${System.currentTimeMillis()}.m4a"
        recorder.startRecording(filename)
        currentAudioPath = recorder.getOutputFile()?.absolutePath
        currentNoteId = null

        uiState = uiState.copy(screen = Screen.Recording)
        startTimer()
    }

    fun stopRecording() {
        recorder.stopRecording()
        stopTimer()

        val audioPath = recorder.getOutputFile()?.absolutePath ?: currentAudioPath
        if (audioPath.isNullOrBlank()) {
            uiState = uiState.copy(
                screen = Screen.Result,
                lastError = "Recording failed: missing audio file."
            )
            return
        }

        uiState = uiState.copy(screen = Screen.Processing, processingMessage = "Transcribing...")

        viewModelScope.launch {
            try {
                Log.d("PixelScribe", "Starting transcription")
                val raw = transcriber.transcribeAudioFile(audioPath)
                Log.d("PixelScribe", "Transcription done. Length: ${raw.length}")
                
                uiState = uiState.copy(
                    rawTranscript = raw,
                    processingMessage = "Refining..."
                )

                // Pass the current prompt from UI state to LLM manager before generation
                llmManager.updateSystemPrompt(uiState.systemPrompt)

                Log.d("PixelScribe", "Starting LLM refinement")
                val cleaned = try {
                    llmManager.formatNote(raw).first()
                } finally {
                    // Unload to avoid memory pressure on device.
                    llmManager.close()
                }
                Log.d("PixelScribe", "LLM refinement done")

                // Save files to history folder
                val timestamp = System.currentTimeMillis()
                val savedAudioPath = saveAudioToHistory(audioPath, timestamp)
                val savedTranscriptPath = saveTextToHistory(raw, cleaned, timestamp)

                uiState = uiState.copy(
                    cleanedNote = cleaned,
                    screen = Screen.Result,
                    processingMessage = ""
                )

                val noteId = noteRepository.saveNote(
                    NoteEntity(
                        createdAt = timestamp,
                        audioPath = savedAudioPath,
                        rawTranscript = raw,
                        cleanedNote = cleaned
                    )
                )
                currentNoteId = noteId
            } catch (e: Exception) {
                Log.e("PixelScribe", "Error during processing", e)
                uiState = uiState.copy(
                    screen = Screen.Result,
                    processingMessage = "",
                    lastError = "Error: ${e.localizedMessage}"
                )
            }
        }
    }

    private suspend fun saveAudioToHistory(tempAudioPath: String, timestamp: Long): String {
        return withContext(Dispatchers.IO) {
            val historyDir = File(appContext.getExternalFilesDir(null), "history")
            if (!historyDir.exists()) historyDir.mkdirs()
            
            val fileName = "consultation_${formatDate(timestamp)}.m4a"
            val destFile = File(historyDir, fileName)
            
            File(tempAudioPath).copyTo(destFile, overwrite = true)
            // Optionally delete the temp file here if AudioRecorderManager doesn't handle it
            // File(tempAudioPath).delete() 
            
            destFile.absolutePath
        }
    }

    private suspend fun saveTextToHistory(raw: String, cleaned: String, timestamp: Long): String {
        return withContext(Dispatchers.IO) {
            val historyDir = File(appContext.getExternalFilesDir(null), "history")
            if (!historyDir.exists()) historyDir.mkdirs()

            val baseName = "consultation_${formatDate(timestamp)}"
            
            val rawFile = File(historyDir, "$baseName.txt")
            rawFile.writeText("RAW TRANSCRIPT:\n\n$raw")
            
            val noteFile = File(historyDir, "$baseName.md")
            noteFile.writeText(cleaned)
            
            noteFile.absolutePath
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
    }

    fun updateCleanedNote(text: String) {
        uiState = uiState.copy(cleanedNote = text)
    }

    fun updateSystemPrompt(text: String) {
        uiState = uiState.copy(systemPrompt = text)
    }

    fun reset() {
        // Keep the system prompt, but reset other session data
        uiState = uiState.copy(
            screen = Screen.Idle,
            elapsedSeconds = 0,
            rawTranscript = "",
            cleanedNote = "",
            processingMessage = "",
            lastError = null
        )
        currentAudioPath = null
        currentNoteId = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        llmManager.close()
        transcriber.close()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var elapsed = 0L
            while (true) {
                delay(1000)
                elapsed += 1
                uiState = uiState.copy(elapsedSeconds = elapsed)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}

enum class Screen {
    Idle,
    Recording,
    Processing,
    Result
}

data class ConsultationUiState(
    val screen: Screen = Screen.Idle,
    val elapsedSeconds: Long = 0,
    val rawTranscript: String = "",
    val cleanedNote: String = "",
    val processingMessage: String = "",
    val lastError: String? = null,
    val systemPrompt: String = """You are an expert clinical scribe. 
I will provide a raw, unlabelled transcript of a conversation between a Doctor and a Patient. 
Your task is to convert this text into a professional SOAP note.

CRITICAL INSTRUCTIONS:
1. **Identify Speakers:** You must infer who is speaking. The Doctor asks questions and gives medical advice. The Patient answers and describes symptoms.
2. **No Hallucinations:** Only include information explicitly stated in the transcript. If a vital sign or diagnosis is not mentioned, do not invent it.
3. **Format:** Output ONLY the SOAP note. Do not add introductory text like "Here is the note."

STRUCTURE:
**S (Subjective):** What the patient feels (Symptoms, History of Present Illness). Use quotes if relevant.
**O (Objective):** What the doctor observes (Physical Exam, Vitals, Labs).
**A (Assessment):** Diagnosis or Differential Diagnosis.
**P (Plan):** Treatment, Medications (include dosage if said), and Follow-up instructions.

EXAMPLE INPUT:
"Hi so what brings you in today well my throat really hurts and I have a fever ok lets take a look open wide ahh yes it looks very red I am going to prescribe amoxicillin 500mg"

EXAMPLE OUTPUT:
**S:** Patient reports sore throat and fever.
**O:** Throat is erythematous (red).
**A:** Pharyngitis.
**P:** Start Amoxicillin 500mg.

REAL TRANSCRIPT TO PROCESS:""",
    val history: List<NoteEntity> = emptyList()
)
