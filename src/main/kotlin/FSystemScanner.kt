import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.stream.Stream
import kotlin.collections.*

private const val tabChr = 9.toChar().toString()
private const val ctrlA = 1.toChar().toString()
private const val dblQt = 34.toChar().toString()
private const val nullStr = ""

object Status {
    //
    // manage the base 36 tag that gets
    // associated with each record so that
    // an external sort can convert sorted
    // output to original output to verify
    // the sort did not lose any records
    //
    private var count = 0L
    private var pTag = "00000"
    val update: String
        get() {
            var tag = count.toString(36)
            while (tag.length < 5) tag = "0$tag"
            pTag = tag
            count += 1
            return tag
        }
    val request: String
        get() {
            return pTag
        }
    val tally: Long
        get() {
            return count
        }
}

class Stack<T : Comparable<T>>(list: MutableList<T>) {
    var items: MutableList<T> = list
    private fun isEmpty(): Boolean = this.items.isEmpty()
    fun count(): Int = this.items.count()
    fun push(element: T) {
        val position = this.count()
        this.items.add(position, element)
    }

    override fun toString() = this.items.toString()
    fun pop(): T? {
        return if (this.isEmpty()) {
            null
        } else {
            val item = this.items.count() - 1
            this.items.removeAt(item)
        }
    }

}

object SubTree {
    val ok2Traverse: MutableMap<String, Boolean> = mutableMapOf()
}

data class FSysRecorder(private val logDirectoryString: String = "/tmp") {
    object Cupboard {
        data class BoxTop(
                var absolutePath: String,
                val fH: Scanner,
                val fN: String,
                var csv: String
        )

        var shelf: TreeMap<String, BoxTop> = TreeMap()
    }

    private val stamp: String
        get() {
            val stampLDT = LocalDateTime.now()
            return with(stampLDT) {
                String.format("%04d%02d%02d.%02d%02d%02d.%04d",
                        year,
                        monthValue,
                        dayOfMonth,
                        hour,
                        minute,
                        second,
                        nano / 100000
                )
            }
        }

    private val tStamp: String = stamp
    private val raw = BufferedWriter(FileWriter("$logDirectoryString/$tStamp.tox.raw", true))
    private val cartonLimit = 4096
    private var cartonCount = 1

    private val carton: TreeMap<String, String> = TreeMap()

    private fun dumpCarton() {
        //
        // The 'Carton' files are smaller fixed length csv files
        // that will be merged is the last faze of the filesystem scan
        //
        val sortedCartonFileName = String.format("$logDirectoryString/%s.%04d.tox.srt", tStamp, cartonCount++)
        val sortedCartonOutFileHandle = BufferedWriter(FileWriter(sortedCartonFileName, true))
        for (k in carton.keys) {
            sortedCartonOutFileHandle.write(carton[k])
        }
        carton.clear()
        sortedCartonOutFileHandle.close()
        val sortedCartonInFileHandle = Scanner(FileReader(sortedCartonFileName))
        val firstLine: String = sortedCartonInFileHandle.nextLine()
        val absPathString: String = firstLine.split(tabChr).last()
        Cupboard.shelf[absPathString] = Cupboard.BoxTop(
                absolutePath = absPathString,
                fH = sortedCartonInFileHandle,
                fN = sortedCartonFileName,
                csv = firstLine
        )
    }

    fun write(dataOut: FSysNode) {
        val csvStr = "${dataOut.flatView(Status.update)}\n"
        raw.write(csvStr)
        carton[dataOut.absolutePath] = csvStr
        if (0 == carton.size % cartonLimit) {
            dumpCarton()
        }
    }

    fun finalize(): String {
        raw.close()
        if (carton.isNotEmpty()) {
            dumpCarton()
        }
        val retVal = mergeTheCartons()
        return retVal
    }

    private fun closeAndDeleteCartonFile(boxTop: Cupboard.BoxTop) {
        boxTop.fH.close()
        val checkFile: File? = Paths.get(boxTop.fN).toFile()
        val file2Nuke: File = if (checkFile != null) {
            checkFile
        } else {
            val error = """ERROR: ${Status.request} >>>>> **
                            | Paths.get(${boxTop.fN}).toFile()
                            | RETURNED A NULL FILE PATH NOT FOUND **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        file2Nuke.delete()
    }

    private fun mergeTheCartons(): String {
        val finalSortedFileName = "$logDirectoryString/$tStamp.tox.srt"
        val finalSortedFileHandle = BufferedWriter(FileWriter(finalSortedFileName, true))
        while (Cupboard.shelf.size > 1) {
            processNextEntry(finalSortedFileHandle)
        }
        val lastKey = Cupboard.shelf.keys.last()
        val checkBoxTop: Cupboard.BoxTop? = Cupboard.shelf[lastKey]
        val boxTop: Cupboard.BoxTop = if (checkBoxTop != null) {
            checkBoxTop
        } else {
            val error = """ERROR: mergeTheCartons() >>>>> **
                            | Cupboard.shelf.keys = ${Cupboard.shelf.keys}
                            | Cupboard.BoxTop is NULL **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)

        }
        finalSortedFileHandle.write("${boxTop.csv}\n")
        finalSortedFileHandle.flush()
        while (boxTop.fH.hasNextLine()) {
            finalSortedFileHandle.write(boxTop.fH.nextLine())
            finalSortedFileHandle.newLine()
        }
        closeAndDeleteCartonFile(boxTop)
        finalSortedFileHandle.close()
        return finalSortedFileName
    }

    private fun processNextEntry(outFile: BufferedWriter) {
        val checkMinKey: String? = Cupboard.shelf.keys.min()
        val minKey: String = if (checkMinKey != null) {
            checkMinKey
        } else {
            val error = """ERROR: processNextEntry(outFile: BufferedWriter) >>>>> **
                                | Cupboard.shelf.keys = ${Cupboard.shelf.keys}
                                | Cupboard.shelf.keys.checkMinKey() is NULL **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        val checkBoxTop: Cupboard.BoxTop? = Cupboard.shelf[minKey]
        val boxTop: Cupboard.BoxTop = if (checkBoxTop != null) {
            checkBoxTop
        } else {
            val error = """ERROR: processNextEntry(outFile: BufferedWriter) >>>>> **
                                | Cupboard.shelf.keys = ${Cupboard.shelf.keys}
                                | Cupboard.BoxTop is NULL **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)

        }
        outFile.write("${boxTop.csv}\n")
        outFile.flush() // delete this line to improve performance
        Cupboard.shelf.remove(minKey)
        if (boxTop.fH.hasNextLine()) {
            boxTop.csv = boxTop.fH.nextLine()
            boxTop.absolutePath = boxTop.csv.split(tabChr).last()
            Cupboard.shelf[boxTop.absolutePath] = boxTop
        } else {
            Log.toTxtFile.warn("Cupboard.shelf.size = ${Cupboard.shelf.size} keys == ${Cupboard.shelf.keys}")
            closeAndDeleteCartonFile(boxTop)
        }
    }
}

data class FSysNode(var f: File) {
    private val bitMap = FSysNode.bitMap()
    private val checkPath: Path? = f.toPath()
    val path = if (checkPath != null) {
        checkPath
    } else {
        val error = """ERROR: ${Status.request} >>>>> ** COULD NOT EXTRACT `path` FROM
                           | `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
        Log.toTxtFile.warn(error)
        throw Exception(error)
    }

    private val checkAbsolutePath: String? = f.absolutePath
    val absolutePath = if (checkAbsolutePath != null) {
        checkAbsolutePath
    } else {
        val error = """ERROR: ${Status.request} >>>>> ** COULD NOT EXTRACT `absolutePath` FROM
                           | `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
        Log.toTxtFile.warn(error)
        throw Exception(error)
    }

    private val checkPAttr: PosixFileAttributes? = Files.readAttributes(
            path,
            PosixFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
    )
    private val pAttr: PosixFileAttributes = if (checkPAttr != null) {
        checkPAttr
    } else {
        val error = """ERROR: ${Status.request} >>>>> ** COULD NOT EXTRACT `PosixFileAttributes`
                           | FROM `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
        Log.toTxtFile.warn(error)
        throw Exception(error)
    }

    private val checkBAttr: BasicFileAttributes? = Files.readAttributes(
            path,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
    )
    val bAttr: BasicFileAttributes = if (checkBAttr != null) {
        checkBAttr
    } else {
        val error = """ERROR: ${Status.request} >>>>> ** COULD NOT EXTRACT `BasicFileAttributes`
                           | FROM `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
        Log.toTxtFile.warn(error)
        throw Exception(error)
    }

    val size
        get() = bAttr.size()

    private val attr: BasicFileAttributes
        get() = if ("L" == typeFDLU(bAttr)) {
            //
            // This will get the attributes by following the link
            val checkAttr: BasicFileAttributes? = Files.readAttributes(
                    path,
                    BasicFileAttributes::class.java
            )
            val attr: BasicFileAttributes = if (checkAttr != null) {
                checkAttr
            } else {
                val error = """ERROR: ${Status.request} >>>>> ** COULD NOT EXTRACT `BasicFileAttributes`
                           | FROM `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
                Log.toTxtFile.warn(error)
                throw Exception(error)
            }
            attr
        } else {
            bAttr
        }

    private val bAttrL: BasicFileAttributes = attr

    val realPath: String
        get() = if ("L" == typeFDLU(bAttr)) {
            " -> ${f.toPath().toRealPath()}"
        } else {
            nullStr
        }

    private val checkINode: Any? = bAttr.fileKey()
    private val iNode: Any = if (null != checkINode) {
        checkINode
    } else {
        val error = """ERROR: ${Status.request} >>>>> ** COULD NOT EXTRACT `fileKey`
                           | FROM `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
        Log.toTxtFile.warn(error)
        throw Exception(error)
    }
    val iNodeStr = iNode.toString()
    var permBits = 0b1000000000 // 10 bits

    init {
        pAttr.permissions().forEach {
            permBits = permBits or bitMask(bitMap[it])
        }
    }

    val permBitString = permBits.toString(2).substring(1) // 9 bits

    companion object {
        fun bitMap(): Map<Enum<PosixFilePermission>, Int> {
            return mapOf(
                    PosixFilePermission.valueOf("OWNER_READ") to 0b100000000,
                    PosixFilePermission.valueOf("OWNER_WRITE") to 0b010000000,
                    PosixFilePermission.valueOf("OWNER_EXECUTE") to 0b001000000,
                    PosixFilePermission.valueOf("GROUP_READ") to 0b000100000,
                    PosixFilePermission.valueOf("GROUP_WRITE") to 0b000010000,
                    PosixFilePermission.valueOf("GROUP_EXECUTE") to 0b000001000,
                    PosixFilePermission.valueOf("OTHERS_READ") to 0b000000100,
                    PosixFilePermission.valueOf("OTHERS_WRITE") to 0b000000010,
                    PosixFilePermission.valueOf("OTHERS_EXECUTE") to 0b000000001
            )
        }
    }

    private fun bitMask(p: Int?): Int {
        return p ?: 0
    }

    private fun typeFDLU(attrVariant: BasicFileAttributes): String {
        //
        // return one of D, F, L, O, U
        // result depends upon weather
        // attrVariant == bAttrL or bAttr
        // this enables the "-> realPath" link
        // target next to the fileName
        //
        with(attrVariant) {
            return when {
                isDirectory -> "D"
                isRegularFile -> "F"
                isSymbolicLink -> "L"
                isOther -> "O"
                else -> "U"
            }
        }
    }

    val typeCode: String
        get() {
            val firstLetter = typeFDLU(bAttr)
            return if ("L" == firstLetter) {
                "L${typeFDLU(bAttrL)}"
            } else {
                "X$firstLetter"
            }
        }

    val modTime: String
        get() {
            val fModTimeI = bAttr.lastModifiedTime().toInstant()
            val fModTimeLDT = LocalDateTime.ofInstant(fModTimeI, ZoneId.systemDefault())
            return with(fModTimeLDT) {
                String.format("%04d%02d%02d.%02d%02d%02d",
                        year,
                        monthValue,
                        dayOfMonth,
                        hour,
                        minute,
                        second
                )
            }
        }

    //fun fSysNodeFields(recordTag: String): FSysNodeFields = FSysNodeFields(recordTag, this)
    fun flatView(recordTag: String): String {
        return String.format("%s%s%s%s%s%s%s%s%010d%s%s%s",
                recordTag,
                ctrlA,
                typeCode,
                ctrlA,
                permBitString,
                ctrlA,
                modTime,
                ctrlA,
                size,
                tabChr,
                absolutePath,
                realPath)
    }
}

//data class FSysNodeFields(val recordTag: String,
//                          val typeCode: String,
//                          val permBitString: String,
//                          val modTime: String,
//                          val size: Long,
//                          val absolutePath: String,
//                          val realPath: String
//) {
//    constructor(tag: String, ancestor: FSysNode) : this(
//            tag,
//            ancestor.typeCode,
//            ancestor.permBitString,
//            ancestor.modTime,
//            ancestor.size,
//            ancestor.absolutePath,
//            ancestor.realPath)
//
//}

class FSystemScan(topNodeString: String = "/", logDirectoryString: String = "/tmp") {
    init {
        val skipList = mapOf(
                "/proc" to true,
                "/media" to true
        )

        val checkLogDirectory: File? = Paths.get(logDirectoryString).toFile()
        val logDirectory: File = if (checkLogDirectory != null) {
            checkLogDirectory
        } else {
            val error = """ERROR: ${Status.request} >>>>> **
                            | Paths.get($logDirectoryString).toFile()
                            | RETURNED A NULL LOG DIRECTORY PATH NOT FOUND **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        val logDirectoryInfo = FSysNode(logDirectory)
        if (!logDirectoryInfo.bAttr.isDirectory) {
            val error = """ERROR: ${Status.request} >>>>> **
                            | Paths.get($logDirectoryString).toFile()
                            | RETURNED AN ENTRY THAT IS NOT A DIRECTORY **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        val directoriesToScan = Stack<File>(mutableListOf())
        val checkTopNode: File? = Paths.get(topNodeString).toFile()
        val topNode: File = if (checkTopNode != null) {
            checkTopNode
        } else {
            val error = """ERROR: ${Status.request} >>>>> **
                            | Paths.get($topNodeString).toFile()
                            | RETURNED A NULL TOP NODE PATH NOT FOUND **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        val topNodeInfo = FSysNode(topNode)


        if (!topNodeInfo.bAttr.isDirectory) {
            val error = """ERROR: ${Status.request} >>>>> **
                            | Paths.get($topNodeString).toFile()
                            | RETURNED AN ENTRY THAT IS NOT A DIRECTORY **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        directoriesToScan.push(topNode)
        SubTree.ok2Traverse[topNodeInfo.iNodeStr] = true
        val fSysRecorder = FSysRecorder(logDirectoryString)
        fSysRecorder.write(topNodeInfo)
        Log.toTxtFile.warn("TOP NODE == $topNodeString")

        mainloop@ while (0 != directoriesToScan.count()) {
            val checkCurrentDirectory = directoriesToScan.pop()
            val currentDirectory = if (checkCurrentDirectory != null) {
                checkCurrentDirectory
            } else {
                val error = """ERROR: ${Status.request} >>>>> ** directoriesToScan.pop() RETURNED
                               | A NULL -- UNDERLYING PROGRAM LOGIC ERROR **""".trimMargin()
                Log.toTxtFile.warn(error)
                throw Exception(error)
            }
            val branchNodeInfo = FSysNode(currentDirectory)
            val ok2Traverse = SubTree.ok2Traverse[branchNodeInfo.iNodeStr] ?: false
            if (ok2Traverse) {
                //
                // I have seen cases on Windows when an API cannot
                // distinguish a link to a directory from an actual
                // directory. This causes an infinite recurse. That's bad.
                // so I keep track of all the directories I descend
                // making sure I travers each directory exactly once.
                //
                try {
                    val checkPath: Path? = currentDirectory.toPath()
                    val currentDirectoryPath: Path
                    currentDirectoryPath = if (checkPath != null) {
                        checkPath
                    } else {
                        val error = """ERROR: ${Status.request} >>>>> ** currentDirectory.toPath() RETURNED
                                       | A NULL -- UNDERLYING PROGRAM LOGIC ERROR **""".trimMargin()
                        Log.toTxtFile.warn(error)
                        throw Exception(error)
                    }

                    val checkList: Stream<Path>? = Files.list(currentDirectoryPath)
                    val fList: Stream<Path>
                    fList = if (checkList != null) {
                        checkList
                    } else {
                        val error = """ERROR: ${Status.request} >>>>> ** Files.list(currentDirectoryPath) RETURNED
                                       | A NULL -- UNDERLYING PROGRAM LOGIC ERROR **""".trimMargin()
                        Log.toTxtFile.warn(error)
                        throw Exception(error)
                    }

                    val checkIterator: Iterator<Path>? = fList.iterator()
                    val currentItem = if (checkIterator != null) {
                        checkIterator
                    } else {
                        val error = """ERROR: ${Status.request} >>>>> ** fList.iterator() RETURNED
                                       | A NULL UNDERLYING PROGRAM LOGIC ERROR **""".trimMargin()
                        Log.toTxtFile.warn(error)
                        throw Exception(error)
                    }

                    try {
                        //
                        // currentItem.hasNext() can generate a
                        // java.io.UncheckedIOException
                        //      or
                        // java.nio.file.FileSystemException:
                        // This loop wil not die under that circumstance.
                        // It will log the error and keep running.
                        //
                        while (currentItem.hasNext()) {
                            val directoryItem = currentItem.next()
                            val checkDirectoryEntry: File? = directoryItem.toFile()
                            val directoryEntry: File = if (checkDirectoryEntry != null) {
                                checkDirectoryEntry
                            } else {
                                val error = """ERROR: ${Status.request} >>>>> ** directoryItem.toFile()
                                               | RETURNED A NULL UNDERLYING PROGRAM LOGIC ERROR **""".trimMargin()
                                Log.toTxtFile.warn(error)
                                throw Exception(error)
                            }
                            try {
                                val nodeInfo = FSysNode(directoryEntry)
                                fSysRecorder.write(nodeInfo)
                                if (0L == Status.tally % 0x3FFFF) {
                                    Log.toConsole.info("FILE SYSTEM ENTRIES CAPTURED == ${Status.tally}")
                                }
                                if (nodeInfo.bAttr.isDirectory && !nodeInfo.bAttr.isSymbolicLink) {
                                    if (nodeInfo.iNodeStr !in SubTree.ok2Traverse.keys) {
                                        if (nodeInfo.path.toString() !in skipList.keys) {
                                            SubTree.ok2Traverse[nodeInfo.iNodeStr] = true
                                            directoriesToScan.push(nodeInfo.f)
                                        }
                                    }
                                }
                            } catch (error: java.nio.file.FileSystemException) {
                                Log.toTxtFile.warn("ERROR: ${Status.request} >>>>> $error")
                            }
                        }
                    } catch (error: Throwable) {
                        when (error) {
                            is java.io.UncheckedIOException,
                            is java.nio.file.FileSystemException,
                            is java.nio.file.AccessDeniedException -> {
                                Log.toTxtFile.warn("ERROR: ${Status.request} >>>>> $error")
                                continue@mainloop
                            }
                            else -> throw error
                        }
                    }
                } catch (error: Throwable) {
                    when (error) {
                        is java.io.UncheckedIOException,
                        is java.nio.file.FileSystemException,
                        is java.nio.file.AccessDeniedException -> {
                            Log.toTxtFile.warn("ERROR: ${Status.request} >>>>> $error")
                            continue@mainloop
                        }
                        else -> throw error
                    }

                }
            }
            SubTree.ok2Traverse[branchNodeInfo.iNodeStr] = false
        }
        val finalOutputFile = fSysRecorder.finalize()
        Log.toConsole.info("FILE SYSTEM ENTRIES RECURSIVELY CAPTURED FROM $dblQt$topNodeString$dblQt == ${Status.tally}")
        Log.toConsole.info("SEE RESULTS IN $finalOutputFile")
    }
}
