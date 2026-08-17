package com.amqhi.services

import io.vertx.core.Future
import io.vertx.core.WorkerExecutor
import io.vertx.core.file.FileSystem
import io.vertx.core.json.JsonObject
import java.util.concurrent.Semaphore

class FileProcessingService(maximumQueueSize: Int = 3, private val fileSystem: FileSystem, private val workerExecutor: WorkerExecutor) {

    private val semaphore = Semaphore(maximumQueueSize)

    private fun isVideoExtension(fileExtension: String) : Boolean {
        val videoExtensions = setOf(
            "mp4", "mov", "avi", "wmv", "mkv", "flv", "webm",
            "mpeg", "mpg", "m4v", "3gp", "3g2", "f4v", "swf",
            "vob", "ts"
        )
        return fileExtension.lowercase() in videoExtensions
    }

    private fun isImageExtension(fileExtension: String) : Boolean {
        val imageExtensions = setOf(
            "bmp", "gif", "jpeg", "jpg", "png", "webp"
        )
        return fileExtension.lowercase() in imageExtensions
    }

    fun deleteTemporaryFolder(path: String) : Future<Void> {
        // TODO: Validate that path is strictly within the designated temp folder
        return fileSystem.deleteRecursive(path)
    }

    fun generateThumbnail(inputPath: String, outputPath: String, imageThumbnailWidth: Int = 300, videoThumbnailHeight: Int = 720) : Future<Void> {
        val fileExtension = inputPath.split(".").last()
        if(isVideoExtension(fileExtension)) {
            return generateVideoThumbnail(
                inputPath = inputPath,
                outputPath = outputPath,
                height = videoThumbnailHeight,
            )
        }
        else if(isImageExtension(fileExtension)) {
            return generateImageThumbnail(
                inputPath = inputPath,
                outputPath = outputPath,
                width = imageThumbnailWidth,
            )
        }
        else {
            return Future.failedFuture(IllegalArgumentException("Invalid extension: $fileExtension"))
        }
    }

    fun prepareTempDirectory(path: String): Future<Void> {
        return fileSystem.exists(path).compose { exists ->
            if(exists) {
                return@compose Future.succeededFuture()
            }
            fileSystem.mkdirs(path)
        }
    }

    fun getVideoResolution(videoPath: String): Future<Pair<Int, Int>?> {
        return workerExecutor.executeBlocking {
            semaphore.acquire()
            try {
                val probeProcess = ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=width,height",
                    "-of", "json",
                    videoPath
                ).start()
                val probeOutput = probeProcess.inputStream.bufferedReader().use { it.readText() }
                probeProcess.waitFor()

                val jsonObj = JsonObject(probeOutput)
                val streams = jsonObj.getJsonArray("streams")
                val stream = streams.getJsonObject(0)
                val width = stream.getInteger("width")
                val height = stream.getInteger("height")

                width to height
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun convertVideo(input: String, output: String, width: Int, height: Int) : Future<Void> {
        return workerExecutor.executeBlocking {
            semaphore.acquire()
            val cmd = listOf(
                "ffmpeg", "-y", "-i", input,
                "-vf", "scale=w=$width:h=$height:force_original_aspect_ratio=decrease",
                "-c:a", "copy",
                output
            )
            ProcessBuilder(cmd).redirectErrorStream(true).start()
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val code = proc.waitFor()
            //if (code != 0) throw RuntimeException("ffmpeg failed for $output")
        }.mapEmpty()
    }

    fun generateVideoThumbnail(inputPath: String, outputPath: String, height: Int = 720) : Future<Void> {
        return workerExecutor.executeBlocking {
            //semaphore.acquire()
            val ffmpegCommand = listOf(
                "ffmpeg",
                "-y",
                "-i", inputPath,
                "-ss", "00:00:05",
                "-vframes", "1",
                "-vf", "scale=-2:$height",
                outputPath
            )

            try {
                val process = ProcessBuilder(ffmpegCommand)
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    println("FFmpeg error: exit code $exitCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.mapEmpty()
    }

    fun generateImageThumbnail(inputPath: String, outputPath: String, width: Int = 300) : Future<Void> {
        return workerExecutor.executeBlocking {
            try {
                //semaphore.acquire()
                val ffmpegCommand = listOf(
                    "ffmpeg",
                    "-i", inputPath,
                    "-vf", "scale=${width}:-1",
                    outputPath
                )
                val process = ProcessBuilder(ffmpegCommand)
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    println("FFmpeg error: exit code $exitCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.mapEmpty()
    }
}