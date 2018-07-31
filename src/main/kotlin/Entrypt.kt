import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option



class Primary: CliktCommand(help="""Recursively descend a filesystem directory. Save filesystem info
                                  | one entry on each line of a CSV file sorted by path name""".trimMargin()) {
    init {
        Log4j2Build()
        Log.toConsole.info("Starting Scan")
    }

    private val topNodeString: String by option("-d", "--directory",help="Top Directory").default("/")
    private val logDirectoryString:  String by option("-l", "--logdir",help="Log Directory").default("/tmp")

    override fun run() {
        FSystemScan(topNodeString, logDirectoryString)
    }
}

fun main(args: Array<String>) {
    Primary().main(args)
}
