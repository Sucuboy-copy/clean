package com.sucu.spotdlapp

import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.Config
import java.io.File

object FFmpegHelper {

    fun executeFFmpegCommand(command: String): Int {
        return FFmpeg.execute(command)
    }

    fun getFFmpegVersion(): String {
        return Config.getVersion()
    }

    fun isFFmpegAvailable(): Boolean {
        return try {
            Config.getVersion().isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun convertToMp3(inputPath: String, outputPath: String): Int {
        val command = "-i \"$inputPath\" -codec:a libmp3lame -qscale:a 2 \"$outputPath\""
        return executeFFmpegCommand(command)
    }

    fun extractAudio(inputPath: String, outputPath: String, format: String = "mp3"): Int {
        val command = when (format) {
            "mp3" -> "-i \"$inputPath\" -q:a 0 -map a \"$outputPath\""
            "flac" -> "-i \"$inputPath\" -c:a flac \"$outputPath\""
            "wav" -> "-i \"$inputPath\" -c:a pcm_s16le \"$outputPath\""
            else -> "-i \"$inputPath\" -c:a copy \"$outputPath\""
        }
        return executeFFmpegCommand(command)
    }

    fun getMediaInfo(filePath: String): String {
        val command = "-i \"$filePath\" -hide_banner"
        val result = executeFFmpegCommand(command)
        // Esta ejecución fallará pero mostrará la info en stderr
        // Necesitarías capturar la salida de error, pero MobileFFmpeg no lo permite directamente
        return "Media info execution completed with code: $result"
    }
}