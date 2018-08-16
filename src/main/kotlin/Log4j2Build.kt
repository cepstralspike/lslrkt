import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.*
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Paths
import java.util.*


enum class LTyp(val i: Int) {
    Console(0),
    Txt(1),
    Xml(2)
}

fun checkPath(s: String): String {
    try {
        return File(s).canonicalPath.toString()
    } catch (err: Exception) {
        throw IOException("** FATAL: PATH [ $s ] CANNOT BE RECONCILED. **")
    }
}
//
class Log(logDirectoryString: String = "/tmp") {
    private val lgAppender: MutableMap<LTyp, AppenderComponentBuilder> = mutableMapOf()
    private val lgAppenderRef: MutableMap<LTyp, AppenderRefComponentBuilder> = mutableMapOf()
    private val lgBuilder: MutableMap<LTyp, LoggerComponentBuilder> = mutableMapOf()
    private val lgAppenderName: MutableMap<LTyp, String> = mutableMapOf()
    private val lgLoggerName: MutableMap<LTyp, String> = mutableMapOf()
    private val lgLayout: MutableMap<LTyp, LayoutComponentBuilder> = mutableMapOf()

    private var rootLogger: RootLoggerComponentBuilder
    private var cfg: Configuration
    private var cfgName: String
    private var cfgBuilder: ConfigurationBuilder<BuiltConfiguration>

    companion object {
        lateinit var toConsole: Logger
        lateinit var toTxtFile: Logger
        lateinit var toXmlFile: Logger
    }

    init {
        val r = Random()
        val tagMap: MutableMap<LTyp, Int> = mutableMapOf(
                LTyp.Console to 0,
                LTyp.Txt to 0,
                LTyp.Xml to 0
        )
        val rndTagSet: MutableSet<Int> = mutableSetOf()

        while (rndTagSet.size != tagMap.size) {
            //
            // Fill set with random integers
            //
            val tag = r.nextInt(65535)
            rndTagSet.add(tag)
        }

        val tagSetElement = rndTagSet.iterator()
        for (mapKey in tagMap.keys) {
            //
            // Associate random integer with each log type tag
            //
            tagMap[mapKey] = tagSetElement.next()

        }

        val checkLogDirectory: File? = Paths.get(logDirectoryString).toFile()
        if (null == checkLogDirectory) {
            val errMsg = """ERROR: 800979A6 >>>>> **
                            | Paths.get($logDirectoryString).toFile()
                            | RETURNED A NULL LOG DIRECTORY PATH NOT FOUND **""".trimMargin()
            println(errMsg)
            throw Exception(IllegalAccessException(errMsg))
        }


        val fileNameStamp = String.format(
                "%s.%s",
                FTStamp.value,
                InetAddress.getLocalHost().hostName
        )
        val lgFileName: MutableMap<LTyp, String> = mutableMapOf()
        val patternDateSpec = "%replace{%replace{%d{ISO8601_BASIC}}{T}{.}}{,}{.}"
        val patternSpec = "$patternDateSpec %-5level: [%t:%F:%L] %msg %n%throwable"

        lgFileName[LTyp.Txt] = checkPath(String.format(
                "%s/%s.%04X.log4j.log",
                logDirectoryString,
                fileNameStamp,
                tagMap[LTyp.Txt]
        ))


        lgFileName[LTyp.Xml] = checkPath(String.format(
                "%s/%s.%04X.log4j.Xml",
                logDirectoryString,
                fileNameStamp,
                tagMap[LTyp.Xml]
        ))


        val lfNames = listOf(lgFileName[LTyp.Txt], lgFileName[LTyp.Xml])
        for (lfName in lfNames) {
            //
            // cannot call vfyWrtAccess here because log
            // is not set up yet
            //
            try {
                val verify = BufferedWriter(FileWriter(lfName, true))
                verify.write("")
                verify.close()
            } catch (error: Throwable) {
                val errMsg = """ERROR: A2E2390A >>>>> **
                            | verify = BufferedWriter(FileWriter($lfName, true)) FAILED
                            | $lfName IS NOT WRITEABLE **""".trimMargin()
                println(errMsg)
                error(message = errMsg)
                throw Exception(error)
            }
        }

        cfgName = String.format(
                "Log4j2Cfg%04X",
                tagMap[LTyp.Console]
        )

        lgLoggerName[LTyp.Console] = LogManager.ROOT_LOGGER_NAME
        lgAppenderName[LTyp.Console] = String.format(
                "Console%04X.Appender",
                tagMap[LTyp.Console]
        )

        lgLoggerName[LTyp.Txt] = String.format(
                "TxtFile%04X.Logger",
                tagMap[LTyp.Txt]
        )
        lgAppenderName[LTyp.Txt] = String.format(
                "TxtFile%04X.Appender",
                tagMap[LTyp.Txt]
        )

        lgLoggerName[LTyp.Xml] = String.format(
                "XmlFile%04X.Logger",
                tagMap[LTyp.Xml]
        )
        lgAppenderName[LTyp.Xml] = String.format(
                "XmlFile%04X.Appender",
                tagMap[LTyp.Xml]
        )

        cfgBuilder = ConfigurationBuilderFactory.newConfigurationBuilder()
        cfgBuilder.setStatusLevel(Level.WARN)
        cfgBuilder.setConfigurationName(cfgName)

        lgLayout[LTyp.Xml] = cfgBuilder.newLayout("XmlLayout")
        lgLayout[LTyp.Txt] = cfgBuilder.newLayout("PatternLayout")
        lgLayout[LTyp.Txt]?.addAttribute("pattern", patternSpec)

        lgAppender[LTyp.Console] = cfgBuilder.newAppender(
                lgAppenderName[LTyp.Console],
                "Console"
        )
        lgAppender[LTyp.Console]?.addAttribute(
                "target",
                ConsoleAppender.Target.SYSTEM_OUT
        )
        lgAppender[LTyp.Console]?.add(lgLayout[LTyp.Txt])
        cfgBuilder.add(lgAppender[LTyp.Console])

        lgAppender[LTyp.Txt] = cfgBuilder.newAppender(
                lgAppenderName[LTyp.Txt],
                "File"
        )
        lgAppender[LTyp.Txt]?.addAttribute("fileName", lgFileName[LTyp.Txt])
        lgAppender[LTyp.Txt]?.add(lgLayout[LTyp.Txt])
        cfgBuilder.add(lgAppender[LTyp.Txt])

        lgAppender[LTyp.Xml] = cfgBuilder.newAppender(
                lgAppenderName[LTyp.Xml],
                "File"
        )
        lgAppender[LTyp.Xml]?.addAttribute("fileName", lgFileName[LTyp.Xml])
        lgAppender[LTyp.Xml]?.add(lgLayout[LTyp.Xml])
        cfgBuilder.add(lgAppender[LTyp.Xml])

        rootLogger = cfgBuilder.newRootLogger(Level.INFO)
        rootLogger.add(cfgBuilder.newAppenderRef(lgAppenderName[LTyp.Console]))
        rootLogger.addAttribute("additivity", false) // Not needed but can't hurt
        cfgBuilder.add(rootLogger)

        lgBuilder[LTyp.Txt] = cfgBuilder.newLogger(
                lgLoggerName[LTyp.Txt],
                Level.TRACE,
                true
        )
        lgAppenderRef[LTyp.Txt] = cfgBuilder.newAppenderRef(lgAppenderName[LTyp.Txt])
        lgBuilder[LTyp.Txt]?.add(lgAppenderRef[LTyp.Txt])
        lgBuilder[LTyp.Txt]?.addAttribute("additivity", false)
        cfgBuilder.add(lgBuilder[LTyp.Txt])

        lgBuilder[LTyp.Xml] = cfgBuilder.newLogger(
                lgLoggerName[LTyp.Xml],
                Level.TRACE,
                true
        )
        lgAppenderRef[LTyp.Xml] = cfgBuilder.newAppenderRef(lgAppenderName[LTyp.Xml])
        lgBuilder[LTyp.Xml]?.add(lgAppenderRef[LTyp.Xml])
        lgBuilder[LTyp.Xml]?.addAttribute("additivity", false)
        cfgBuilder.add(lgBuilder[LTyp.Xml])

        cfg = cfgBuilder.build()

        Configurator.initialize(cfg)
        Configurator.setRootLevel(Level.INFO)
        Configurator.setLevel(lgLoggerName[LTyp.Txt], Level.TRACE)
        Configurator.setLevel(lgLoggerName[LTyp.Xml], Level.TRACE)

        toConsole = LogManager.getLogger(lgLoggerName[LTyp.Console])
        toTxtFile = LogManager.getLogger(lgLoggerName[LTyp.Txt])
        toXmlFile = LogManager.getLogger(lgLoggerName[LTyp.Xml])
    }
}
