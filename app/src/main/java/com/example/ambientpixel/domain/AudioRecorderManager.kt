package com.example.ambientpixel.domain

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.io.IOException

class AudioRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private val sampleRateHz = 16000

    @Synchronized
    @Throws(IOException::class)
    fun startRecording(filename: String) {
        if (recorder != null) {
            return
        }

        val file = File(context.filesDir, filename)
        outputFile = file

        val mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(96000)
        mediaRecorder.setAudioSamplingRate(sampleRateHz)
        mediaRecorder.setOutputFile(file.absolutePath)

        mediaRecorder.prepare()
        mediaRecorder.start()
        recorder = mediaRecorder
    }

    @Synchronized
    fun stopRecording() {
        val mediaRecorder = recorder ?: return
        try {
            mediaRecorder.stop()
        } catch (e: RuntimeException) {
            outputFile?.delete()
        } finally {
            mediaRecorder.reset()
            mediaRecorder.release()
            recorder = null
        }
    }

    fun getOutputFile(): File? = outputFile
}
