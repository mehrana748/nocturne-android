package com.nocturne.player.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Estimates tempo from a short, decoded section of a local audio file.
 * Decoding is bounded to the first 30 seconds and stays on the device.
 */
object LocalTempoAnalyzer {
    private const val MAX_ANALYSIS_US = 30_000_000L
    private const val MIN_ANALYSIS_SECONDS = 8.0
    private const val ENVELOPE_HZ = 100.0
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 180.0

    fun estimateBpm(context: Context, uri: Uri): Double? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var decoderStarted = false
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = index
                    inputFormat = format
                    break
                }
            }
            if (audioTrack < 0 || inputFormat == null) return null
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(audioTrack)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decoderStarted = true

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var idleIterations = 0
            var collector: EnvelopeCollector? = null
            while (!outputEnded && idleIterations < 1_500) {
                var madeProgress = false
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                        if (input == null) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            input.clear()
                            val sampleTime = extractor.sampleTime
                            if (sampleTime < 0L || sampleTime >= MAX_ANALYSIS_US) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, max(0L, sampleTime), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                val size = extractor.readSampleData(input, 0)
                                if (size < 0) {
                                    decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputEnded = true
                                } else {
                                    decoder.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        size,
                                        sampleTime,
                                        extractor.sampleFlags
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                        madeProgress = true
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        val sampleRate = outputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 0)
                        val channels = outputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 0)
                        val encoding = outputFormat.getIntegerOrDefault(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        if (sampleRate > 0 && channels > 0 && collector == null) {
                            collector = EnvelopeCollector(sampleRate, channels, encoding)
                        }
                        madeProgress = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = decoder.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            if (collector == null) {
                                val sampleRate = inputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 0)
                                val channels = inputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 0)
                                if (sampleRate > 0 && channels > 0) {
                                    collector = EnvelopeCollector(sampleRate, channels, AudioFormat.ENCODING_PCM_16BIT)
                                }
                            }
                            collector?.consume(output)
                        }
                        val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (endOfStream) outputEnded = true
                        madeProgress = true
                    }
                }
                idleIterations = if (madeProgress) 0 else idleIterations + 1
            }
            val activeCollector = collector ?: return null
            return estimateFromEnvelope(activeCollector.finish(), activeCollector.frameRate)
        } catch (_: Exception) {
            return null
        } finally {
            if (decoderStarted) runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun estimateFromEnvelope(envelope: List<Double>, frameRate: Double): Double? {
        if (frameRate <= 0.0 || envelope.size < (MIN_ANALYSIS_SECONDS * frameRate).toInt()) return null
        val onset = DoubleArray(envelope.size)
        for (index in 1 until envelope.size) {
            onset[index] = max(0.0, envelope[index] - envelope[index - 1])
        }
        val mean = onset.average()
        for (index in onset.indices) onset[index] -= mean
        val minimumLag = max(1, (60.0 * frameRate / MAX_BPM).toInt())
        val maximumLag = (60.0 * frameRate / MIN_BPM).toInt().coerceAtMost(onset.size / 2)
        if (maximumLag <= minimumLag) return null

        var bestLag = -1
        var bestScore = 0.0
        for (lag in minimumLag..maximumLag) {
            var cross = 0.0
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            for (index in lag until onset.size) {
                val left = onset[index]
                val right = onset[index - lag]
                cross += left * right
                leftEnergy += left * left
                rightEnergy += right * right
            }
            val denominator = sqrt(leftEnergy * rightEnergy)
            val score = if (denominator > 0.0) cross / denominator else 0.0
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestScore < 0.12) return null
        val bpm = 60.0 * frameRate / bestLag
        if (bpm !in MIN_BPM..MAX_BPM) return null
        return round(bpm * 10.0) / 10.0
    }

    private class EnvelopeCollector(
        private val sampleRate: Int,
        private val channels: Int,
        private val encoding: Int
    ) {
        private val framesPerWindow = max(1, sampleRate / ENVELOPE_HZ.toInt())
        private var framesInWindow = 0
        private var energyInWindow = 0.0
        private val envelope = ArrayList<Double>()
        val frameRate: Double = sampleRate.toDouble() / framesPerWindow

        fun consume(buffer: java.nio.ByteBuffer) {
            buffer.order(ByteOrder.nativeOrder())
            val bytesPerSample = when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                AudioFormat.ENCODING_PCM_8BIT -> 1
                else -> 2
            }
            while (buffer.remaining() >= bytesPerSample * channels) {
                var frameEnergy = 0.0
                repeat(channels) {
                    val value = when (encoding) {
                        AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.toDouble()
                        AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128.0
                        else -> buffer.short.toDouble() / Short.MAX_VALUE
                    }
                    frameEnergy += abs(value)
                }
                energyInWindow += frameEnergy / channels
                framesInWindow++
                if (framesInWindow >= framesPerWindow) {
                    envelope += energyInWindow / framesInWindow
                    framesInWindow = 0
                    energyInWindow = 0.0
                }
            }
        }

        fun finish(): List<Double> = envelope.toList()
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, defaultValue: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(defaultValue) else defaultValue
}
