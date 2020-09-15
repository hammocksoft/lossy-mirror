package io.hammock.tools

import io.hammock.tools.LossyMirror.doMirror
import java.io.File
import kotlin.system.exitProcess

/**
 * iTunes has this nice functionality that it converts all lossless files to non lossless on the fly in order to save space on the phone.
 *
 * Mirrors all audio files found in the specified source folders as mp3 into the specified destination folder.
 * - mp3, ogg, aac, jpg and png files will be copied as is
 * - flac and alac files will be converted to mp3 (160kbit)
 * Requires ffmpeg.
 *
 * Mirror into a directory on the computer:
 * /home/mark/Music/Music /home/mark/Music/Audiobooks /home/mark/Music/Compilations /home/mark/Music/.mp3
 * Mirror directly to the android phone:
 * /home/mark/Music/Music /home/mark/Music/Audiobooks /home/mark/Music/Compilations /run/user/1000/gvfs/mtp:host=SAMSUNG_SAMSUNG_Android_RF8N31XCVVJ/Phone/Music
 *
 * Sync to android phone (trailing / on source is important) in an additional step using rsync:
 * (Add -n, eg. "rsync -rvn" for dry run)
 * rsync -rv --ignore-existing /home/mark/Music/.mp3/ '/run/user/1000/gvfs/mtp:host=SAMSUNG_SAMSUNG_Android_RF8N31XCVVJ/Phone/Music'
 *
 * TODO:
 * - create an index to avoid rechecking all files on every run.
 */
fun main(args: Array<String>?) {
    if (args == null || args.size < 2) {
        printUsage()
        exitProcess(-1)
    }
    val srcDirs = args.toList().subList(0, args.size - 1).map { File(it) }
    val targetDir = File(args.last())
    print("This will mirror and convert all files from $srcDirs to $targetDir  - are you sure (y/n)? ")
    val stringInput = readLine()!!
    if (stringInput != "y") {
        exitProcess(0)
    }

    doMirror(srcDirs, targetDir)
}

private fun printUsage() {
    println("usage:")
    println("./gradlew run --args='[source-folder]+ target-folder'")
//    println("lossy-mirror <source dir> [<source dir>]+ <target dir>")
//    println("example: lossy-mirror /home/mark/Music/Music /home/mark/Music/Audiobooks /home/mark/Music/Compilations /home/mark/Music/.mp3")
    println("example: ./gradlew run --args='/home/mark/Music/Music /home/mark/Music/Audiobooks /home/mark/Music/Compilations /home/mark/Music/.mp3'")
    println("NOTE: requires ffmpeg")
}