package org.nlogo.extensions.gstvideo.prim

import java.awt.image.{ BufferedImage, DataBufferInt, DirectColorModel, Raster, SampleModel, WritableRaster }
import org.gstreamer.{ Buffer, Bus, Element, elements, GstObject, State, TagList }, elements.AppSink
import org.nlogo.api.{ Argument, Context, DefaultReporter, ExtensionException, Syntax}
import model.ElementManager

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/18/12
 * Time: 1:17 PM
 */

trait VideoPrimitiveManager {

  /*
  I had explored the possibility of moving these shared elements and their prims into a separate object entirely
  (i.e. so that there wouldn't be separate prims for both `movie-set-balance` and `camera-set-balance`), but my excursion
  showed me only that such a thing is an impracticality.  The crux of the problem is that an element can only have one
  one owner/parent at a time.  As such, a single element would need to remove itself from its parent whenever being added
  somewhere else, and also unlink itself from its neighbor elements--and make sure that that _always_ happens when it gets
  added somewhere.  In the end, I couldn't think of a way of doing it that wasn't unwieldy or architecturally nonsensical.
  As such, I've opted to stick with the existing sufficiently-good badism.  --JAB (9/25/12)
   */
  protected lazy val appSink = initSink()
  protected lazy val balance = ElementManager.generateBalancer
  protected lazy val scale   = ElementManager.generateScaler

  private val isDebugging = false

  def unload() {
    appSink.dispose()
    balance.dispose()
    scale.dispose()
  }

  // You should probably never override this --JAB (9/18/12)
  protected def initBusListeners(mainBusOwner: Element, initExtras: () => Unit = () => ()) {

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
          if (source ne mainBusOwner) {
            println("Pipeline state changed from %s to %s".format(old, current))
          }
        }
      })

      initExtras()

    }

  }

  protected def initSink() : AppSink = {
    val sink = ElementManager.generateAppSink
    sink.set("max-buffers", 1)
    sink.set("drop", true)
    sink
  }

  protected def determineWorldDimensions(context: Context) : (Double, Double) = {
    val world     = context.getAgent.world
    val patchSize = world.patchSize
    val width     = world.worldWidth  * patchSize
    val height    = world.worldHeight * patchSize
    (width, height)
  }

  object KeepAspect extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      setKeepAspect(true)
    }
  }

  object IgnoreAspect extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      setKeepAspect(false)
    }
  }

  protected def setKeepAspect(isKeeping: Boolean) {
    scale.set("add-borders", isKeeping)
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
      throw new ExtensionException("Invalid %s value: %f (must be within [%f, %f])".format(settingName, value, min, max))
  }

  object Image extends DefaultReporter {
    override def getSyntax           = Syntax.reporterSyntax(Array[Int](), Syntax.WildcardType)
    override def getAgentClassString = "O"
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      try {

        val buffer          = generateBuffer
        val structure       = buffer.getCaps.getStructure(0)
        val (width, height) = (structure.getInteger("width"), structure.getInteger("height"))

        val intBuf    = buffer.getByteBuffer.asIntBuffer
        val imageData = new Array[Int](intBuf.capacity)
        intBuf.get(imageData, 0, imageData.length)

        handleImageBuffer(buffer)

        getBufferedImage(imageData, width, height)

      }
      catch {
        case e: Exception => throw new ExtensionException(e.getMessage, e)
      }
    }
  }

  protected def generateBuffer : Buffer
  protected def handleImageBuffer(buffer: Buffer)

  protected def getBufferedImage(data: Array[Int], width: Int, height: Int) : BufferedImage = {

    def colorModel =
      new DirectColorModel(32, 0xff0000, 0xff00, 0xff)
    def getRGBSampleModel(width: Int, height: Int) : SampleModel =
      colorModel.createCompatibleWritableRaster(1, 1).getSampleModel.createCompatibleSampleModel(width, height)
    def getRaster(model: SampleModel, data: Array[Int]) : WritableRaster =
      Raster.createWritableRaster(model, new DataBufferInt(data, data.length, 0), null)

    new BufferedImage(colorModel, getRaster(getRGBSampleModel(width, height), data), false, null)

  }

}
