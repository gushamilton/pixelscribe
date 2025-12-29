package com.example.ambientpixel.domain

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TranscriptionService(private val context: Context) {
    private val recognizer: OfflineRecognizer by lazy { initRecognizer() }

    suspend fun transcribeAudioFile(filePath: String): String = withContext(Dispatchers.IO) {
        val decoded = AudioDecoder().decodeToPcm(filePath)
        val samples = if (decoded.sampleRate == TARGET_SAMPLE_RATE_HZ) {
            decoded.samples
        } else {
            resampleLinear(decoded.samples, decoded.sampleRate, TARGET_SAMPLE_RATE_HZ)
        }

        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, TARGET_SAMPLE_RATE_HZ)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text
        stream.release()
        text
    }

    fun close() {
        recognizer.release()
    }

    private fun initRecognizer(): OfflineRecognizer {
        // Direct absolute paths as requested, no asset copying logic needed
        val modelDir = "/sdcard/Download"
        val encoder = "$modelDir/encoder.int8.onnx"
        val decoder = "$modelDir/decoder.int8.onnx"
        val joiner = "$modelDir/joiner.int8.onnx"
        val tokens = "$modelDir/tokens.txt"

        val transducer = OfflineTransducerModelConfig(
            encoder = encoder,
            decoder = decoder,
            joiner = joiner
        )

        val modelConfig = OfflineModelConfig(
            transducer = transducer,
            tokens = tokens,
            numThreads = 2,
            debug = true,
            modelType = "zipformer"
        )

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        // Pass null for assetManager to ensure it reads from disk
        return OfflineRecognizer(assetManager = null, config = config)
    }

    private data class DecodedAudio(
        val samples: FloatArray,
        val sampleRate: Int
    )

    private class AudioDecoder {
        fun decodeToPcm(filePath: String): DecodedAudio {
            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) {
                extractor.release()
                throw IllegalStateException("No audio track found in $filePath")
            }

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing mime type for $filePath")

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val outputStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> {
                        if (outputIndex >= 0) {
                            val outBuffer = codec.getOutputBuffer(outputIndex)
                            if (bufferInfo.size > 0 && outBuffer != null) {
                                val bytes = ByteArray(bufferInfo.size)
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outBuffer.get(bytes)
                                outputStream.write(bytes)
                            }
                            codec.releaseOutputBuffer(outputIndex, false)

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEOS = true
                            }
                        }
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val rawBytes = outputStream.toByteArray()
            val samples = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    decodeFloatPcm(rawBytes, channelCount)
                }
                AudioFormat.ENCODING_PCM_16BIT -> {
                    decode16BitPcm(rawBytes, channelCount)
                }
                else -> {
                    throw IllegalStateException("Unsupported PCM encoding: $pcmEncoding")
                }
            }

            return DecodedAudio(samples = samples, sampleRate = sampleRate)
        }

        private fun selectAudioTrack(extractor: MediaExtractor): Int {
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    return i
                }
            }
            return -1
        }

        private fun decode16BitPcm(bytes: ByteArray, channelCount: Int): FloatArray {
            val shortBuffer = ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
            val shorts = ShortArray(shortBuffer.remaining())
            shortBuffer.get(shorts)
            return downmix(shorts, channelCount)
        }

        private fun decodeFloatPcm(bytes: ByteArray, channelCount: Int): FloatArray {
            val floatBuffer = ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
            val floats = FloatArray(floatBuffer.remaining())
            floatBuffer.get(floats)
            return if (channelCount == 1) {
                floats
            } else {
                val frames = floats.size / channelCount
                val output = FloatArray(frames)
                var outIndex = 0
                var index = 0
                while (index + channelCount <= floats.size) {
                    var sum = 0f
                    repeat(channelCount) {
                        sum += floats[index + it]
                    }
                    output[outIndex] = sum / channelCount
                    outIndex += 1
                    index += channelCount
                }
                output
            }
        }

        private fun downmix(shorts: ShortArray, channelCount: Int): FloatArray {
            if (channelCount <= 1) {
                return FloatArray(shorts.size) { index -> shorts[index] / 32768.0f }
            }

            val frames = shorts.size / channelCount
            val output = FloatArray(frames)
            var outIndex = 0
            var index = 0
            while (index + channelCount <= shorts.size) {
                var sum = 0
                repeat(channelCount) {
                    sum += shorts[index + it].toInt()
                }
                output[outIndex] = (sum / channelCount) / 32768.0f
                outIndex += 1
                index += channelCount
            }
            return output
        }
    }

    private fun resampleLinear(
        input: FloatArray,
        sourceRate: Int,
        targetRate: Int
    ): FloatArray {
        if (input.isEmpty() || sourceRate == targetRate) {
            return input
        }

        val ratio = targetRate.toDouble() / sourceRate.toDouble()
        val outputSize = max(1, floor(input.size * ratio).toInt())
        val output = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            val srcIndex = i / ratio
            val index = srcIndex.toInt()
            val frac = srcIndex - index
            val nextIndex = min(index + 1, input.size - 1)
            output[i] = (input[index] * (1.0 - frac) + input[nextIndex] * frac).toFloat()
        }
        return output
    }

    private companion object {
        private const val TIMEOUT_US = 10000L
        private const val TARGET_SAMPLE_RATE_HZ = 16000
    }
}
