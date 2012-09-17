package org.nlogo.extensions.gstvideo

import java.awt.image.{ BufferedImage, DataBufferInt, DirectColorModel, Raster, SampleModel, WritableRaster }
import org.gstreamer.Buffer
import org.nlogo.api.{ Argument, Context, DefaultReporter, ExtensionException, Syntax }


object Util {

  private val colorModel = new DirectColorModel(32, 0xff0000, 0xff00, 0xff)

  private[gstvideo] def getBufferedImage(data: Array[Int], width: Int, height: Int) : BufferedImage = {

    def getRGBSampleModel(width: Int, height: Int) : SampleModel =
      colorModel.createCompatibleWritableRaster(1, 1).getSampleModel.createCompatibleSampleModel(width, height)

    def getRaster(model: SampleModel, data: Array[Int]) : WritableRaster =
      Raster.createWritableRaster(model, new DataBufferInt(data, data.length, 0), null)

    new BufferedImage(colorModel, getRaster(getRGBSampleModel(width, height), data), false, null)

  }

  class Image(generateBuffer: => Buffer)(cleanup: Buffer => Unit) extends DefaultReporter {
    override def getSyntax           = Syntax.reporterSyntax(Array[Int](), Syntax.WildcardType)
    override def getAgentClassString = "O"
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      try {

        val buffer          = generateBuffer
        val structure       = buffer.getCaps.getStructure(0)
        val (width, height) = (structure.getInteger("width"), structure.getInteger("height"))

        val intBuf          = buffer.getByteBuffer.asIntBuffer
        val imageData       = new Array[Int](intBuf.capacity)
        intBuf.get(imageData, 0, imageData.length)

        cleanup(buffer)

        getBufferedImage(imageData, width, height)

      }
      catch {
        case e: Exception =>
          throw new ExtensionException(e.getMessage)
      }
    }
  }

  object Image {
    def apply(generateBuffer: => Buffer)(cleanup: Buffer => Unit) : Image = new Image(generateBuffer)(cleanup)
  }

}