import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.InetAddress
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

object Str {
    const val tab = 9.toChar().toString()
    const val ctrlA = 1.toChar().toString()
    const val dblQt = 34.toChar().toString()
    const val lSqBrkt = 91.toChar().toString()
    const val bkSlash = 92.toChar().toString()
    const val rSqBrkt = 93.toChar().toString()
    const val empty = ""
}

object RecordNumber {
    //
    // manage the base 36 tag that gets
    // associated with each record so that
    // an external sort can convert sorted
    // output to original output to verify
    // the sort did not lose any records
    //
    private var count = 0L
    private var base36Tag = "00000"
    val update: String
        get() {
            var b36tag = count.toString(36)
            while (b36tag.length < 5) b36tag = "0$b36tag"
            base36Tag = b36tag
            count += 1
            return b36tag
        }
    val request: String
        get() {
            return base36Tag
        }
    val tally: Long
        get() {
            return count
        }
}

class Stack<T : Comparable<T>>(list: MutableList<T>) {
    var items: MutableList<T> = list
    private fun isEmpty(): Boolean = items.isEmpty()
    fun count(): Int = items.count()
    fun push(element: T) {
        val position = this.count()
        items.add(position, element)
    }

    override fun toString() = items.toString()
    fun pop(): T? {
        return if (this.isEmpty()) {
            null
        } else {
            val item = items.count() - 1
            items.removeAt(item)
        }
    }

}

object SubTree {
    val ok2Traverse: MutableMap<String, Boolean> = mutableMapOf()
}

object FTStamp {
    //
    // Timestamp for all generated files remains the same
    // for the entire run
    //
    private val stampLDT = LocalDateTime.now()
    val value: String
        get() {
            return with(stampLDT) {
                String.format("%04d%02d%02d.%02d%02d%02d.%03d",
                        year,
                        monthValue,
                        dayOfMonth,
                        hour,
                        minute,
                        second,
                        nano / 1000000
                )
            }
        }
}

object TStamp {
    //
    // Timestamp changes each time is is called
    // for the entire run
    //
    val value: String
        get() {
            val stampLDT = LocalDateTime.now()
            return with(stampLDT) {
                String.format("%04d%02d%02d.%02d%02d%02d.%03d",
                        year,
                        monthValue,
                        dayOfMonth,
                        hour,
                        minute,
                        second,
                        nano / 1000000
                )
            }
        }
}

object Bit {
    val map = mapOf(
            PosixFilePermission.valueOf("OWNER_READ") to 0b100000000L,
            PosixFilePermission.valueOf("OWNER_WRITE") to 0b010000000L,
            PosixFilePermission.valueOf("OWNER_EXECUTE") to 0b001000000L,
            PosixFilePermission.valueOf("GROUP_READ") to 0b000100000L,
            PosixFilePermission.valueOf("GROUP_WRITE") to 0b000010000L,
            PosixFilePermission.valueOf("GROUP_EXECUTE") to 0b000001000L,
            PosixFilePermission.valueOf("OTHERS_READ") to 0b000000100L,
            PosixFilePermission.valueOf("OTHERS_WRITE") to 0b000000010L,
            PosixFilePermission.valueOf("OTHERS_EXECUTE") to 0b000000001L
    )
}

data class FSysRecorder(
        private val resultsDirectoryString: String = "/tmp",
        private val topNodeString: String = "/") {

    object Cupboard {
        data class BoxTop(
                var absolutePath: String,
                val fH: Scanner,
                val fN: String,
                var csv: String
        )
        var shelf: TreeMap<String, BoxTop> = TreeMap()
    }

    private val topNodeTag = transformTopNodeString(topNodeString)
    private val rawFileName = checkPath(String.format(
            "%s/%s.%s.%s.tox.raw",
            resultsDirectoryString,
            FTStamp.value,
            InetAddress.getLocalHost().hostName,
            topNodeTag
    ))
    private val rawWrtOK = vfyWrtAccess(rawFileName, "4C288E0E") // we`re pretty much dead if this fails

    private val finalSortedFileName = checkPath(String.format(
            "%s/%s.%s.%s.tox.srt",
            resultsDirectoryString,
            FTStamp.value,
            InetAddress.getLocalHost().hostName,
            topNodeTag
    ))
    private val srtWrtOK = vfyWrtAccess(finalSortedFileName, "ADE1AEDC")  // we`re pretty much dead if this fails


    private val raw = BufferedWriter(FileWriter(rawFileName, true))
    private val cartonLimit = 4096
    private var cartonCount = 1
    private val carton: TreeMap<String, String> = TreeMap()

    init {
        Log.toTxtFile.trace("""$rawWrtOK == vfyWrtAccess($rawFileName, 0A26ED76)""")
        Log.toTxtFile.trace("""$srtWrtOK == vfyWrtAccess($finalSortedFileName, 927E8146)""")
    }
    //
    // ONLY FUNCTIONS AND INNER CLASSES SHOULD
    // BE PLACED IN FSysRecorder AFTER THIS POINT
    //
    private fun dumpCarton() {
        //
        // A carton treemap (sorted by absolute path name) is filled as the
        // scan proceeds. The number of csv records (lines) in a carton is
        // cartonLimit. Once the treemap reaches that limit it is written
        // out to a file. All the files will be merged in the last faze of
        // the filesystem scan
        //
        val sortedCartonFileName = String.format(
                "%s/%s.%04d.tox.srt",
                resultsDirectoryString,
                FTStamp.value,
                cartonCount++
        )
        vfyWrtAccess(sortedCartonFileName, "244331B3") // we`re pretty much dead if this fails
        val sortedCartonOutFileHandle = BufferedWriter(FileWriter(sortedCartonFileName, true))
        for (k in carton.keys) {
            sortedCartonOutFileHandle.write(carton[k])
        }
        carton.clear()
        sortedCartonOutFileHandle.close()
        val sortedCartonInFileHandle = Scanner(FileReader(sortedCartonFileName))
        val firstLine: String = sortedCartonInFileHandle.nextLine()
        val absPathString: String = firstLine.split(Str.tab).last()
        Cupboard.shelf[absPathString] = Cupboard.BoxTop(
                absolutePath = absPathString,
                fH = sortedCartonInFileHandle,
                fN = sortedCartonFileName,
                csv = firstLine
        )
    }

    fun add2carton(dataOut: FSysNode) {
        val csvStr = "${dataOut.flatView(RecordNumber.update)}\n"
        //
        // also write to raw (unsorted) file to facilitate sort
        // verification/validation
        //
        raw.write(csvStr)
        carton[dataOut.absolutePath] = csvStr // entries placed in sorted map
        if (0 == carton.size % cartonLimit) {
            dumpCarton()
        }
    }

    fun finalize(): String {
        raw.close()
        if (carton.isNotEmpty()) {
            Log.toTxtFile.trace("""fun finalize(): carton.isNotEmpty() == true""")
            dumpCarton()
        }
        val retVal = mergeTheCartons()
        Log.toTxtFile.trace("""fun finalize(): returning $retVal""")
        return retVal
    }

    private fun closeAndDeleteCartonFile(boxTop: Cupboard.BoxTop) {
        boxTop.fH.close()
        val checkFile: File? = Paths.get(boxTop.fN).toFile()
        val file2Nuke: File = if (checkFile != null) {
            checkFile
        } else {
            val error = """ERROR: ${RecordNumber.request} >>>>> **
                            | Paths.get(${boxTop.fN}).toFile()
                            | RETURNED A NULL FILE PATH NOT FOUND **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        file2Nuke.delete()
    }

    private fun mergeTheCartons(): String {
        vfyWrtAccess(finalSortedFileName, "BBB9583C")  // we`re pretty much dead if this fails
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
            Log.toTxtFile.fatal(error)
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
        //outFile.flush() // delete this line to improve performance
        Cupboard.shelf.remove(minKey)
        if (boxTop.fH.hasNextLine()) {
            boxTop.csv = boxTop.fH.nextLine()
            boxTop.absolutePath = boxTop.csv.split(Str.tab).last()
            Cupboard.shelf[boxTop.absolutePath] = boxTop
        } else {
            Log.toTxtFile.info("Cupboard.shelf.size = ${Cupboard.shelf.size} keys == ${Cupboard.shelf.keys}")
            closeAndDeleteCartonFile(boxTop)
        }
    }
}

data class FSysNode(var f: File) {
    private val checkPath: Path? = f.toPath()
    val path = if (checkPath != null) {
        checkPath
    } else {
        val error = """ERROR: ${RecordNumber.request} >>>>> ** COULD NOT EXTRACT `path` FROM
                           | `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
        Log.toTxtFile.warn(error)
        throw Exception(error)
    }

    private val checkAbsolutePath: String? = f.absolutePath
    val absolutePath = if (checkAbsolutePath != null) {
        checkAbsolutePath
    } else {
        val error = """ERROR: ${RecordNumber.request} >>>>> ** COULD NOT EXTRACT `absolutePath` FROM
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
        val error = """ERROR: ${RecordNumber.request} >>>>> ** COULD NOT EXTRACT `PosixFileAttributes`
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
        val error = """ERROR: ${RecordNumber.request} >>>>> ** COULD NOT EXTRACT `BasicFileAttributes`
                           | FROM `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
        Log.toTxtFile.warn(error)
        throw Exception(error)
    }

    private val size
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
                val error = """ERROR: ${RecordNumber.request} >>>>> ** COULD NOT EXTRACT `BasicFileAttributes`
                           | FROM `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
                Log.toTxtFile.warn(error)
                throw Exception(error)
            }
            attr
        } else {
            bAttr
        }

    private val bAttrL: BasicFileAttributes = attr

    private val realPath: String
        get() = if ("L" == typeFDLU(bAttr)) {
            " -> ${f.toPath().toRealPath()}"
        } else {
            Str.empty
        }

    private val checkINode: Any? = bAttr.fileKey()
    private val iNode: Any = if (null != checkINode) {
        checkINode
    } else {
        val error = """ERROR: ${RecordNumber.request} >>>>> ** COULD NOT EXTRACT `fileKey`
                           | FROM `f` IN FSysNode(var f: File) CONSTRUCTOR""".trimMargin()
        Log.toTxtFile.warn(error)
        throw Exception(error)
    }
    val iNodeStr = iNode.toString()
    var permBits: Long = 0b1000000000L // 10 bits

    init {
        pAttr.permissions().forEach {
            permBits = permBits or bitMask(Bit.map[it])
        }
    }

    private val permBitString = permBits.toString(2).substring(1) // 9 bits
    private val permOctString = java.lang.Long.toOctalString(
            java.lang.Long.parseLong(permBitString, 2)
    )

    private fun bitMask(p: Long?): Long {
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

    private val typeCode: String
        get() {
            val firstLetter = typeFDLU(bAttr)
            return if ("L" == firstLetter) {
                "L${typeFDLU(bAttrL)}"
            } else {
                "X$firstLetter"
            }
        }

    private val modTime: String
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
                Str.ctrlA,
                typeCode,
                Str.ctrlA,
                permOctString,
                Str.ctrlA,
                modTime,
                Str.ctrlA,
                size,
                Str.tab,
                absolutePath,
                realPath)
    }
}

data class FSystemScan(var topNodeString: String = "/",
                       var resultsDirString: String = "/tmp") {

    init {
        val skipList = mapOf(
                "/proc" to true ,
                "/media" to true
        )

        val checkResultsDirectory: File? = Paths.get(resultsDirString).toFile()
        val resultsDirectory: File = if (checkResultsDirectory != null) {
            checkResultsDirectory
        } else {
            val error = """ERROR: ${RecordNumber.request} >>>>> **
                            | Paths.get($resultsDirString).toFile()
                            | RETURNED A NULL RESULTS DIRECTORY PATH NOT FOUND **""".trimMargin()
            Log.toConsole.fatal(error)
            throw Exception(error)
        }

        val resultsDirectoryInfo = FSysNode(resultsDirectory)
        if (!resultsDirectoryInfo.bAttr.isDirectory) {
            val error = """ERROR: ${RecordNumber.request} >>>>> **
                            | Paths.get($resultsDirString).toFile()
                            | RETURNED AN ENTRY THAT IS NOT A DIRECTORY **""".trimMargin()
            Log.toConsole.fatal(error)
            throw Exception(error)
        }

        val directoriesToScan = Stack<File>(mutableListOf())
        val checkTopNode: File? = Paths.get(topNodeString).toFile()
        val topNode: File = if (checkTopNode != null) {
            checkTopNode
        } else {
            val error = """ERROR: ${RecordNumber.request} >>>>> **
                            | Paths.get($topNodeString).toFile()
                            | RETURNED A NULL TOP NODE PATH NOT FOUND **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        val topNodeInfo = FSysNode(topNode)

        if (!topNodeInfo.bAttr.isDirectory) {
            val error = """ERROR: ${RecordNumber.request} >>>>> **
                            | Paths.get($topNodeString).toFile()
                            | RETURNED AN ENTRY THAT IS NOT A DIRECTORY **""".trimMargin()
            Log.toTxtFile.warn(error)
            throw Exception(error)
        }
        directoriesToScan.push(topNode)
        SubTree.ok2Traverse[topNodeInfo.iNodeStr] = true
        val fSysRecorder = FSysRecorder(resultsDirString, topNodeString)
        fSysRecorder.add2carton(topNodeInfo)
        Log.toTxtFile.info("TOP NODE == $topNodeString")

        mainloop@ while (0 != directoriesToScan.count()) {
            val checkCurrentDirectory = directoriesToScan.pop()
            val currentDirectory = if (checkCurrentDirectory != null) {
                checkCurrentDirectory
            } else {
                val error = """ERROR: ${RecordNumber.request} >>>>> ** directoriesToScan.pop() RETURNED
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
                // directory. This causes an infinite recurse. That`s bad.
                // so I try to keep track of all the directories I descend
                // making sure I travers each directory exactly once.
                //
                try {
                    val checkPath: Path? = currentDirectory.toPath()
                    val currentDirectoryPath: Path
                    currentDirectoryPath = if (checkPath != null) {
                        checkPath
                    } else {
                        val error = """ERROR: ${RecordNumber.request} >>>>> ** currentDirectory.toPath() RETURNED
                                       | A NULL -- UNDERLYING PROGRAM LOGIC ERROR **""".trimMargin()
                        Log.toTxtFile.warn(error)
                        throw Exception(error)
                    }

                    val checkList: Stream<Path>? = Files.list(currentDirectoryPath)
                    val fList: Stream<Path>
                    fList = if (checkList != null) {
                        checkList
                    } else {
                        val error = """ERROR: ${RecordNumber.request} >>>>> ** Files.list(currentDirectoryPath) RETURNED
                                       | A NULL -- UNDERLYING PROGRAM LOGIC ERROR **""".trimMargin()
                        Log.toTxtFile.warn(error)
                        throw Exception(error)
                    }

                    val checkIterator: Iterator<Path>? = fList.iterator()
                    val currentItem = if (checkIterator != null) {
                        checkIterator
                    } else {
                        val error = """ERROR: ${RecordNumber.request} >>>>> ** fList.iterator() RETURNED
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
                                val error = """ERROR: ${RecordNumber.request} >>>>> ** directoryItem.toFile()
                                               | RETURNED A NULL UNDERLYING PROGRAM LOGIC ERROR **""".trimMargin()
                                Log.toTxtFile.warn(error)
                                throw Exception(error)
                            }
                            try {
                                val nodeInfo = FSysNode(directoryEntry)
                                fSysRecorder.add2carton(nodeInfo)
                                if (0L == RecordNumber.tally % 0x3FFFF) {
                                    Log.toConsole.info("FILE SYSTEM ENTRIES CAPTURED == ${RecordNumber.tally}")
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
                                Log.toTxtFile.warn("ERROR: ${RecordNumber.request} >>>>> $error")
                            }
                        }
                    } catch (error: Throwable) {
                        when (error) {
                            is java.io.UncheckedIOException,
                            is java.nio.file.FileSystemException,
                            is java.nio.file.AccessDeniedException -> {
                                Log.toTxtFile.warn("ERROR: ${RecordNumber.request} >>>>> $error")
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
                            Log.toTxtFile.warn("ERROR: ${RecordNumber.request} >>>>> ${Str.lSqBrkt} $error ${Str.rSqBrkt}")
                            continue@mainloop
                        }
                        else -> throw error
                    }

                }
            }
            SubTree.ok2Traverse[branchNodeInfo.iNodeStr] = false
        }
        val finalOutputFile = fSysRecorder.finalize()
        Log.toConsole.info("FILE SYSTEM ENTRIES RECURSIVELY CAPTURED FROM ${Str.dblQt}$topNodeString${Str.dblQt} == ${RecordNumber.tally}")
        Log.toConsole.info("SEE RESULTS IN $finalOutputFile")
    }

}

fun vfyWrtAccess(pathString: String, tag: String) : Boolean {
    try {
        //
        // if no error is thrown, then write access is verified
        //
        val verify = BufferedWriter(FileWriter(pathString, true))
        verify.write(Str.empty)
        verify.close()
        return true
    } catch (error: Throwable) {
        val errMsg = """ERROR: $tag >>>>> **
                            | ATTEMPTED::
                            | BufferedWriter(FileWriter($pathString, append = true))
                            | FAILED. $pathString IS NOT WRITEABLE ** <<<<<""".trimMargin()
        Log.toConsole.fatal(errMsg)
        Log.toTxtFile.fatal(errMsg)
        throw Exception(error)
    }
}

fun transformTopNodeString(s: String): String {
    Log.toTxtFile.trace("""transformTopNodeString: received $s""")
    val retVal = ttnsFixOtherSlashes(ttnsFixLeadingSlash(s))
    Log.toTxtFile.trace("""transformTopNodeString produced $retVal""")
    return retVal
}

fun ttnsFixLeadingSlash(s: String): String {
    // replace leading slash with "slash."
    Log.toTxtFile.trace("""ttnsFixLeadingSlash received $s""")
    return if ("/" == s) {
        val retVal = "slash"
        Log.toTxtFile.trace("""ttnsFixLeadingSlash produced $retVal""")
        retVal
    } else {
        val retVal = s.replace("""^[${Str.bkSlash}/]""".toRegex(), "slash.")
        Log.toTxtFile.trace("""ttnsFixLeadingSlash produced $retVal""")
        retVal
    }
}

fun ttnsFixOtherSlashes(s: String): String {
    // replace any and all `[\/]` with `.`
    Log.toTxtFile.trace("""ttnsFixOtherSlashes received $s""")
    val retVal = s.replace("""[${Str.bkSlash}/]""".toRegex(), ".")
    Log.toTxtFile.trace("""ttnsFixOtherSlashes produced $retVal""")
    return retVal
}
