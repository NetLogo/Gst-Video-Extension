package org.nlogo.extensions.gstvideo

import org.gstreamer.{ Bus, Element, ElementFactory, elements, GstObject, State, TagList }, elements.AppSink
import org.nlogo.api.{ Argument, Context, ExtensionException, Syntax}

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/18/12
 * Time: 1:17 PM
 */

trait VideoPrimitiveManager {

  protected lazy val appSink = initSink()
  protected lazy val balance = ElementFactory.make("videobalance", "balance")
  protected lazy val scale   = ElementFactory.make("videoscale",   "scale")

  private val isDebugging = false

  def unload() {
    appSink.dispose()
    balance.dispose()
    scale.dispose()
  }

  protected def mainBusOwner: Element            // Marks the `Element` onto which the bus listeners will be placed
  protected def initExtraBusListeners = () => () // Override this is you have extra listeners you want initialized
  protected def initBusListeners() {             // You should probably never override this --JAB (9/18/12)

    if (isDebugging) {

      val bus = mainBusOwner.getBus

      bus.connect(new Bus.INFO {
        override def infoMessage(source: GstObject, code: Int, message: String) {
          println("Code: " + code + " | Message: " + message)
        }
      })

      bus.connect(new Bus.TAG {
        override def tagsFound(source: GstObject, tagList: TagList) {
          import scala.collection.JavaConversions._
          for {
            tagName <- tagList.getTagNames
            tagData <- tagList.getValues(tagName)
          } { println("[%s]=%s".format(tagName, tagData)) }
        }
      })

      bus.connect(new Bus.ERROR {
        override def errorMessage(source: GstObject, code: Int, message: String) {
          println("Error occurred: " + message + "(" + code + ")")
        }
      })

      mainBusOwner.getBus.connect(new Bus.STATE_CHANGED {
        override def stateChanged(source: GstObject, old: State, current: State, pending: State) {
          if (source == mainBusOwner) {
            println("Pipeline state changed from %s to %s".format(old, current))
          }
        }
      })

      initExtraBusListeners()

    }

  }

  protected def initSink() : AppSink = {
    val sink = ElementFactory.make("appsink", "sink") match {
      case appSink: AppSink => appSink
      case other            => throw new ExtensionException("Invalid sink type created: class == " + other.getClass.getName)
    }
    sink.set("max-buffers", 1)
    sink.set("drop", true)
    sink
  }

  def generateColorspaceConverter : Element = ElementFactory.make("ffmpegcolorspace", "colorspace-converter")
  def generateVideoFilter         : Element = ElementFactory.make("capsfilter",       "video-filter")

  object StartFullscreen extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      setFullscreen(true)
    }
  }

  object StopFullscreen extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      setFullscreen(false)
    }
  }

  protected def setFullscreen(isStretching: Boolean) {
    scale.set("add-borders", !isStretching)
  }

  object SetContrast extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      validateAndSet("contrast", args(0).getDoubleValue, 0, 2)
    }
  }

  object SetBrightness extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      validateAndSet("brightness", args(0).getDoubleValue, -1, 1)
    }
  }

  object SetHue extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      validateAndSet("hue", args(0).getDoubleValue, -1, 1)
    }
  }

  object SetSaturation extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      validateAndSet("saturation", args(0).getDoubleValue, 0, 2)
    }
  }

  private def validateAndSet(settingName: String, value: Double, min: Double, max: Double) {
    if (value >= min && value <= max)
      balance.set(settingName, value)
    else
      throw new ExtensionException("invalid %s value: %f (must be within [%f, %f])".format(settingName, value, min, max))
  }

}