package io.hammock.tools

import io.hammock.tools.LossyMirror.Stats.brokenAlbums
import io.hammock.tools.LossyMirror.Stats.brokenFiles
import io.hammock.tools.LossyMirror.Stats.convertedCount
import io.hammock.tools.LossyMirror.Stats.copyCount
import io.hammock.tools.LossyMirror.Stats.existingCount
import io.hammock.tools.LossyMirror.Stats.printStats
import io.hammock.tools.LossyMirror.Stats.skippedCount
import io.hammock.tools.LossyMirror.Stats.totalCount
import java.io.BufferedReader
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// /home/mark/Music/Music /home/mark/Music/Audiobooks /home/mark/Music/Compilations /home/mark/Music/.lossy
object LossyMirror {
    /**
     * Copy artwork and existing lossy audio files as is.
     */
    private val COPY_AS_IS_EXTENSIONS = listOf("mp3", "ogg", "aac", "jpg", "jpeg", "png", "gif")
    private val CONVERT_EXTENSIONS = listOf("flac", "alac", "aiff")
    private val workingDir = File("/tmp")

    fun doMirror(srcDirs: List<File>, targetDir: File) {
        val indexFile = File(targetDir, ".lossy.idx")
        val index = if (indexFile.exists()) {
            FileIndex(indexFile.readText().split('\n').toSortedSet())
        } else {
            // index does not exist, build it based on the current contents of the target directory
            rebuildIndex(indexFile, targetDir)
        }
        try {
            srcDirs.forEach { srcDir ->
                val rootDir = srcDir.parentFile.absolutePath
                srcDir.walkTopDown().onFail { f, e ->
                    println("could not read directory $f")
                    e.printStackTrace()
                    exitProcess(-1)
                }.forEach srcFiles@{ srcFile ->
//            println(srcFile.absolutePath)
                    convertOrCopy(srcFile, targetDir, rootDir, index)
                }
                // update the stored index after each source directory
                writeIndex(index, indexFile)
            }
        } finally {
            writeIndex(index, indexFile)
            println("\nDone :)")
            printStats()
        }
    }

    private fun convertOrCopy(srcFile: File, targetDir: File, rootDir: String, index: FileIndex) {
        val relativePath = srcFile.absolutePath.removePrefix(rootDir)
        val isDirectory = srcFile.isDirectory
        if (!isDirectory) {
            totalCount++
        }
        if (index.contains(relativePath)) {
//            println("$relativePath found in index, skipping")
            if (!isDirectory) {
                existingCount++
            }
            return
        }
        if (isDirectory) {
            index.add(relativePath)
            handleDirectory(targetDir, relativePath)
            return
        } else {
            if (srcFile.length() == 0L) {
                skippedCount++
                brokenFiles.add(srcFile.absolutePath)
                brokenAlbums.add(srcFile.parentFile.absolutePath)
                return
            }
        }
        val extension = relativePath.substringAfterLast(".").toLowerCase()
        val isAac = isAac(srcFile, extension)

        if (isAac || COPY_AS_IS_EXTENSIONS.contains(extension)) {
            copyFile(srcFile, targetDir, relativePath, index)
            index.add(relativePath)
        } else {
            if (!(CONVERT_EXTENSIONS.contains(extension) || extension == "m4a")) {
                println("Don't know how to handle $relativePath, skipping")
                skippedCount++
            } else {
                convertFile(srcFile, targetDir, relativePath, index)
            }
        }
    }

    private fun isAac(srcFile: File, extension: String): Boolean {
        return if (extension == "m4a") {
            runCommand(
                    "ffmpeg",
                    "-loglevel",
                    "quiet",
                    "-i",
                    srcFile.absolutePath
            ).contains("Audio: aac")
        } else false
    }

    private fun convertFile(srcFile: File, targetDir: File, relativePath: String, index: FileIndex) {
        val convertedRelativePath = relativePath.replaceAfterLast('.', "ogg")
        if (index.contains(convertedRelativePath)) {
//            println("$convertedRelativePath found in index, skipping")
            existingCount++
        } else {
            val targetFile = File(targetDir, convertedRelativePath)
            doConvertFile(srcFile, targetFile, convertedRelativePath, index)
            index.add(convertedRelativePath)
        }
    }

    private fun doConvertFile(srcFile: File, targetFile: File, relativePath: String, index: FileIndex) {
        val inputPath = srcFile.absolutePath
        val output = targetFile.absolutePath
        if (!targetFile.exists()) {
            println("converting ${srcFile.absolutePath} to  ${targetFile.absolutePath}")
            runCommand(
                    "ffmpeg",
                    "-loglevel",
                    "warning",
                    "-i",
                    inputPath,
                    "-vb",
                    "192k",
                    "-map_metadata",
                    "0",
                    "-id3v2_version",
                    "3",
                    output,
            )
            convertedCount++
        } else {
            // indexed files are already counted
            if (!index.contains(relativePath)) {
                existingCount++
            }
        }
        // if some error occurred the file length will be zero
        if (targetFile.length() == 0L) {
            brokenFiles.add(targetFile.absolutePath)
            brokenAlbums.add(targetFile.parentFile.absolutePath)
        }
    }

    private fun writeIndex(index: FileIndex, indexFile: File) {
        indexFile.writeText(index.build())
    }

    private fun handleDirectory(targetDir: File, relativePath: String) {
        val dir = File(targetDir, relativePath)
        println(dir.absolutePath)
        if (!dir.exists()) {
            println("creating directory: ${dir.absolutePath}")
            dir.mkdirs()
        }
    }


    private fun copyFile(srcFile: File, targetDir: File, relativePath: String, index: FileIndex) {
        val targetFile = File(targetDir, relativePath)
        println("copying ${srcFile.absolutePath} to  ${targetFile.absolutePath}")
        // File.copy has issues when copying files directly to the phone, so we use cp instead.
        //                    srcFile.copyTo(targetFile)
        if (!targetFile.exists()) {
            runCommand(
                    "cp",
                    "-v",
                    srcFile.absolutePath,
                    targetFile.absolutePath
            )
            copyCount++
        } else {
            // indexed files are already counted
            if (!index.contains(relativePath)) {
                existingCount++
            }
        }
    }

    private fun runCommand(vararg cmd: String): String {
//    println("executing: ${cmd.joinToString()}")
        val process = ProcessBuilder(*cmd)
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        process.waitFor(60, TimeUnit.MINUTES)

        if (process.exitValue() < 0) {
            val error = BufferedReader(process.errorStream.reader()).use {
                it.readText()
            }
            throw RuntimeException("ERROR running command: ${cmd.joinToString(" ")}: $error")
        }

        val reader = BufferedReader(process.inputStream.reader())
        return reader.use {
            it.readText()
        }
    }

    private fun rebuildIndex(indexFile: File, targetDir: File): FileIndex {
        val rootDir = targetDir.absolutePath
        val idx = FileIndex()
        println(".lossy.idx not found, rebuilding from existing files (if any). This might take a while...")
        targetDir.walkTopDown().forEach {
            if (it.absolutePath != targetDir.absolutePath) {
                val relativePath = it.absolutePath.removePrefix(rootDir)
                idx.add(relativePath)
            }
        }
        writeIndex(idx, indexFile)
        return idx
    }

    private object Stats {
        var copyCount = 0
        var existingCount = 0
        var convertedCount = 0
        var skippedCount = 0
        var totalCount = 0
        val brokenFiles = mutableListOf<String>()
        val brokenAlbums = mutableSetOf<String>()

        fun printStats() {
            println(" Files:     $totalCount")
            println(" Existing:  $existingCount")
            println(" Copied:    $copyCount")
            println(" Converted: $convertedCount")
            println(" Skipped:   $skippedCount")
            if (brokenFiles.isNotEmpty()) {
                println("Directories with broken files:")
                brokenAlbums.map {
                    println(it)
                }
            }
        }
    }


    data class FileIndex(private val files: SortedSet<String> = TreeSet()) {
        fun contains(file: File, rootDir: String) = contains(file.absolutePath.removePrefix(rootDir))
        fun contains(name: String) = files.contains(name)
        fun add(name: String) = files.add(name)
        fun build(): String = files.joinToString("\n")
    }
}