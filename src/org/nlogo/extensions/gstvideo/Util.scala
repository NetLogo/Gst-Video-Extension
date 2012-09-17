package org.nlogo.extensions.gstvideo

import java.awt.image.{ BufferedImage, DataBufferInt, DirectColorModel, Raster, SampleModel, WritableRaster }

object Util {

  private val colorModel = new DirectColorModel(32, 0xff0000, 0xff00, 0xff)

  private[gstvideo] def getBufferedImage(data: Array[Int], width: Int, height: Int) : BufferedImage = {

    def getRGBSampleModel(width: Int, height: Int) : SampleModel =
      colorModel.createCompatibleWritableRaster(1, 1).getSampleModel.createCompatibleSampleModel(width, height)

    def getRaster(model: SampleModel, data: Array[Int]) : WritableRaster =
      Raster.createWritableRaster(model, new DataBufferInt(data, data.length, 0), null)

    new BufferedImage(colorModel, getRaster(getRGBSampleModel(width, height), data), false, null)

  }

}