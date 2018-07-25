import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.*
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Random

import kotlin.collections.MutableMap

enum class LTyp(val i: Int) {
    console(0),
    txt(1),
    xml(2)
}

class TripNotez internal constructor() {
    private val lgAppender: MutableMap<LTyp, AppenderComponentBuilder> = mutableMapOf()
    private val lgAppenderRef: MutableMap<LTyp, AppenderRefComponentBuilder> = mutableMapOf()
    private val lgBuilder: MutableMap<LTyp, LoggerComponentBuilder> = mutableMapOf()
    private val lgAppenderName: MutableMap<LTyp, String> = mutableMapOf()
    private val lgLoggerName: MutableMap<LTyp, String> = mutableMapOf()
    private val lgLayout: MutableMap<LTyp, LayoutComponentBuilder> = mutableMapOf()

    private var rootLogger: RootLoggerComponentBuilder
    private var cfg: Configuration
    private var cfgName: String

    var scribeCONSOLE: Logger
    var scribeTXT: Logger
    var scribeXML: Logger

    var cfgBuilder: ConfigurationBuilder<BuiltConfiguration>

    init {
        val now = Date()
        val r = Random()
        val uniqTag: MutableMap<LTyp, Int> = mutableMapOf(
                LTyp.console to 0,
                LTyp.txt to 0,
                LTyp.xml to 0
        )
        val tagValues: MutableSet<Int> = mutableSetOf()

        while ( tagValues.size != uniqTag.size){
            //
            // Fill my set with unique random numbers
            //
            val tag = r.nextInt(65535)
            tagValues.add(tag)
            //System.out.printf("%04X\n", tag.toLong())
        }

        val tagValuesIter = tagValues.iterator()
        for(kVal in uniqTag.keys){
            //
            // Associate unique random integer with
            // each log type tag
            //
            if ( tagValuesIter.hasNext()) {
                uniqTag[kVal] = tagValuesIter.next()
            }

        }

        val fileNameStamp = SimpleDateFormat("yyyyMMdd.HHmmss.SSSSSS").format(now)
        val lgFileName: MutableMap<LTyp, String> = mutableMapOf()
        val patternDateSpec = "%replace{%replace{%d{ISO8601_BASIC}}{T}{.}}{,}{.}"
        val patternSpec = "$patternDateSpec %-5level: [%t:%F:%L] %msg %n%throwable"

        lgFileName[LTyp.txt] = String.format("/tmp/%s.%04X.log4j.log", fileNameStamp, uniqTag[LTyp.txt])
        lgFileName[LTyp.xml] = String.format("/tmp/%s.%04X.log4j.xml", fileNameStamp, uniqTag[LTyp.xml])

        cfgName = String.format("TripNotezCfg%04X", uniqTag[LTyp.console])

        lgLoggerName[LTyp.console] = LogManager.ROOT_LOGGER_NAME
        lgAppenderName[LTyp.console] = String.format("LTyp.console%04X.Appender", uniqTag[LTyp.console])

        lgLoggerName[LTyp.txt] = String.format("txtFile%04X.Logger", uniqTag[LTyp.txt])
        lgAppenderName[LTyp.txt] = String.format("txtFile%04X.Appender", uniqTag[LTyp.txt])

        lgLoggerName[LTyp.xml] = String.format("xmlFile%04X.Logger", uniqTag[LTyp.xml])
        lgAppenderName[LTyp.xml] = String.format("xmlFile%04X.Appender", uniqTag[LTyp.xml])

        cfgBuilder = ConfigurationBuilderFactory.newConfigurationBuilder()
        cfgBuilder.setStatusLevel(Level.WARN)
        cfgBuilder.setConfigurationName(cfgName)

        lgLayout[LTyp.xml] = cfgBuilder.newLayout("XmlLayout")
        lgLayout[LTyp.txt] = cfgBuilder.newLayout("PatternLayout")
        lgLayout[LTyp.txt]?.addAttribute("pattern", patternSpec)

        lgAppender[LTyp.console] = cfgBuilder.newAppender(lgAppenderName[LTyp.console], "console")
        lgAppender[LTyp.console]?.addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
        lgAppender[LTyp.console]?.add(lgLayout[LTyp.txt])
        cfgBuilder.add(lgAppender[LTyp.console])

        lgAppender[LTyp.txt] = cfgBuilder.newAppender(lgAppenderName[LTyp.txt], "File")
        lgAppender[LTyp.txt]?.addAttribute("fileName", lgFileName[LTyp.txt])
        lgAppenderRef[LTyp.txt] = cfgBuilder.newAppenderRef(lgAppenderName[LTyp.txt])
        lgAppender[LTyp.txt]?.add(lgLayout[LTyp.txt])
        cfgBuilder.add(lgAppender[LTyp.txt])

        lgAppender[LTyp.xml] = cfgBuilder.newAppender(lgAppenderName[LTyp.xml], "File")
        lgAppender[LTyp.xml]?.addAttribute("fileName", lgFileName[LTyp.xml])
        lgAppenderRef[LTyp.xml] = cfgBuilder.newAppenderRef(lgAppenderName[LTyp.xml])
        lgAppender[LTyp.xml]?.add(lgLayout[LTyp.xml])
        cfgBuilder.add(lgAppender[LTyp.xml])

        rootLogger = cfgBuilder.newRootLogger(Level.INFO)
        rootLogger.add(cfgBuilder.newAppenderRef(lgAppenderName[LTyp.console]))
        rootLogger.addAttribute("additivity", false)
        cfgBuilder.add(rootLogger)

        lgBuilder[LTyp.txt] = cfgBuilder.newLogger(lgLoggerName[LTyp.txt], Level.TRACE, true)
        lgBuilder[LTyp.txt]?.add(lgAppenderRef[LTyp.txt])
        lgBuilder[LTyp.txt]?.addAttribute("additivity", false)
        cfgBuilder.add(lgBuilder[LTyp.txt])

        lgBuilder[LTyp.xml] = cfgBuilder.newLogger(lgLoggerName[LTyp.xml], Level.TRACE, true)
        lgBuilder[LTyp.xml]?.add(lgAppenderRef[LTyp.xml])
        lgBuilder[LTyp.xml]?.addAttribute("additivity", false)
        cfgBuilder.add(lgBuilder[LTyp.xml])

        cfg = cfgBuilder.build()

        Configurator.initialize(cfg)
        Configurator.setRootLevel(Level.TRACE)
        Configurator.setLevel(lgLoggerName[LTyp.txt], Level.TRACE)
        Configurator.setLevel(lgLoggerName[LTyp.xml], Level.TRACE)

        scribeCONSOLE = LogManager.getLogger(lgLoggerName[LTyp.console])
        scribeTXT = LogManager.getLogger(lgLoggerName[LTyp.txt])
        scribeXML = LogManager.getLogger(lgLoggerName[LTyp.xml])


    }
}


fun main(args: Array<String>) {
    val log = TripNotez()
    log.scribeCONSOLE.warn("w: The love of money is the root of all evil")
    log.scribeCONSOLE.info(log.cfgBuilder.toXmlConfiguration())
    log.scribeTXT.warn("w: do not attempt to adjust your television")
    log.scribeTXT.warn("w: we control the horizontal")
    log.scribeTXT.warn(log.cfgBuilder.toXmlConfiguration())
    log.scribeXML.warn("w: do not attempt to adjust your radio")
    log.scribeXML.warn("w: we control the station")
    log.scribeXML.warn(log.cfgBuilder.toXmlConfiguration())
}