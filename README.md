# Lossy Mirror

Simple Kotlin class to "mirror" directories containing lossless audio to an Android phone as lossy but smaller Ogg Vorbis files.

iTunes has this nice functionality that it converts all lossless files to lossy aac on the fly in order to save space on the phone.
Since I couldn't find anything similar for my new Linux / Android setup, I implemented this little App. 

This App is for people who'd like to keep their audio files in high quality on their computers but don't have enough 
space on their mobile devices to store their music library as lossless files.

## Features
 
Mirrors all audio files found in the specified source folders as Ogg Vorbis into the specified destination folder.

Supports:
* mp3, aac, ogg and jpg files will be copied as is
* flac, aiff and alac files will be converted to Ogg Vorbis with variable bitrate (maximum 192 kbit)

Creates an index file ``.lossy.idx`` to speed up mirroring to an existing mirror directory.

The following command is used for the conversion:
```
ffmpeg -i <input path> -vb 192k -map_metadata 0 -id3v2_version 3 <output path>
```
## Requirements

A *nix environment with JDK 1.8+ and ffmpeg

## Usage

Clone this repo, change into the new directory and run:

```
./gradlew run --args='[source-folder]+ target-folder'
```

## Examples

### Mirror into a directory on the local machine

 ```
   ./gradlew run --args='/home/mark/Music/Music /home/mark/Music/Audiobooks /home/mark/Music/Compilations /home/mark/Music/.lossy'
 ```  

I'm using a hidden (".lossy") folder here as the target so that it doesn't get picked up by Rhythmbox and I end up with duplicates in my library.

### Mirror directly to the Android phone

```
 ./gradlew run --args='/home/mark/Music/Music /home/mark/Music/Audiobooks /home/mark/Music/Compilations /run/user/1000/gvfs/mtp:host=SAMSUNG_SAMSUNG_Android_RF8N31XCVVJ/Phone/Music'
``` 

### Sync to the Android phone in an additional step using rsync

```
 rsync -rv --ignore-existing /home/mark/Music/.lossy/ '/run/user/1000/gvfs/mtp:host=SAMSUNG_SAMSUNG_Android_RF8N31XCVVJ/Phone/Music'
```

Note:
* The trailing / on source is important
* Add -n, eg. "rsync -rvn" for dry run)

## TODO
