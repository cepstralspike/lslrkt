import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option


class lslrkt: CliktCommand(help="""Recursively descend a filesystem directory. Save filesystem info
                                  | one entry on each line of a CSV file sorted by path name""".trimMargin()) {

    private val topNodeString: String by option("-d", "--directory",help="Top Directory").default("/")
    private val logDirectoryString:  String by option("-l", "--logdir",help="Log Directory").default("/tmp")
    private val resultsDirectoryString: String by option("-r", "--resultdir", help="Results Directory").default("/tmp")

    override fun run() {
        println("${TStamp.value} INFO : Scan Started")
        Log(logDirectoryString)
        Log.toConsole.info("Log4j2 Activated -- Starting Scan")
        FSystemScan(topNodeString, resultsDirectoryString)
        Log.toConsole.info("Scan Complete")
        println("${TStamp.value} INFO : Scan Complete")
    }
}

fun main(args: Array<String>) {
    lslrkt().main(args)
}
