// Video recording code from GSVideo (GPL license, below)
/**
 * Part of the GSVideo library: http://gsvideo.sourceforge.net/
 * Copyright (c) 2008-11 Andres Colubri
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, version 2.1.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * Public License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307  USA
 */
package org.nlogo.extensions.gstvideo

import java.io.File
import org.gstreamer.{ Bus, Caps, Element, ElementFactory, GstObject, Pipeline, State, TagList }
import org.gstreamer.elements.AppSink
import org.nlogo.api.{ Argument, Context, ExtensionException, Syntax }

object Capture {

  private lazy val appSink        = initSink()
  private lazy val cameraPipeline = initPipeline()
  private lazy val balance        = ElementFactory.make("videobalance", "balance")
  private lazy val scale          = ElementFactory.make("videoscale", "scale")

  private var recorder: Option[Recorder] = None //@ Is there something I can do about the `var`iness?  Recycling of recorders?

  val image = Util.Image{ appSink.pullBuffer }{ buffer => recorder foreach (_.push(buffer)) }

  def unload() {
    appSink.dispose()
    cameraPipeline.setState(State.NULL)
    cameraPipeline.dispose()
    balance.dispose()
    scale.dispose()
    recorder foreach (_.dispose())
  }

  private def initPipeline() : Pipeline = {

    // Pipeline construction based on Processing
    // http://code.google.com/p/processing/source/browse/trunk/processing/java/libraries/video/src/processing/video/Capture.java
    val pipeline = new Pipeline("camera-capture")

    pipeline.getBus.connect(new Bus.TAG {
      def tagsFound(source: GstObject, tagList: TagList) {
        import scala.collection.JavaConversions._
        for {
          tagName <- tagList.getTagNames
          tagData <- tagList.getValues(tagName)
        } { println("[%s]=%s".format(tagName, tagData)) }
      }
    })

    pipeline.getBus.connect(new Bus.ERROR {
      def errorMessage(source: GstObject, code: Int, message: String) {
        println("Error occurred: " + message)
      }
    })

    pipeline.getBus.connect(new Bus.STATE_CHANGED {
      def stateChanged(source: GstObject, old: State, current: State, pending: State) {
        if (source == pipeline) {
          println("Pipeline state changed from %s to %s".format(old, current))
        }
      }
    })

    pipeline

  }

  private def initSink() : AppSink = {
    val sink = ElementFactory.make("appsink", "sink") match {
      case appSink: AppSink => appSink
      case other            => throw new ExtensionException("Invalid sink type created: class == " + other.getClass.getName)
    }
    sink.set("max-buffers", 1)
    sink.set("drop", true)
    sink
  }

  object StartRecording extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.StringType, Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {

      import Codec.Theora, Quality.Medium
      val codec = new Theora(Medium)
      val (propNames, propValues, encoder) = codec.getProps

      val fps       = 30
      val filename  = args(0).getString
      val file      = new File(filename)
      val patchSize = context.getAgent.world.patchSize
      val width     = args(1).getDoubleValue * patchSize
      val height    = args(2).getDoubleValue * patchSize
      val muxer     = Util.determineMuxer(filename) getOrElse (throw new ExtensionException("Unrecognized video container"))

      recorder = Option(new Recorder("Recorder", width.toInt, height.toInt, fps, encoder, propNames, propValues, muxer, file))
      recorder foreach (_.start())

    }
  }

  object StopRecording extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      recorder foreach (_.stop())
    }
  }

  object SetStrechToFillScreen extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.BooleanType))
    override def perform(args: Array[Argument], context: Context) {
      val shouldAddBorders = !(args(0).getBooleanValue)
      scale.set("add-borders", shouldAddBorders)
    }
  }

  object SetContrast extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val contrast = args(0).getDoubleValue
      if (contrast >= 0 && contrast <= 2) balance.set("contrast", contrast)
      else                                throw new ExtensionException("Invalid contrast value: [0, 2] (Default is 1)")
    }
  }

  object SetBrightness extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val brightness = args(0).getDoubleValue
      if (brightness >= -1 && brightness <= 1) balance.set("brightness", brightness)
      else                                     throw new ExtensionException("Invalid brightness value: [-1, 1] (Default is 0)")
    }
  }

  object SetHue extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val hue = args(0).getDoubleValue
      if (hue >= -1 && hue <= 1) balance.set("hue", hue)
      else                       throw new ExtensionException("Invalid hue value: [-1, 1] (Default is 0)")
    }
  }

  object SetSaturation extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val saturation = args(0).getDoubleValue
      if (saturation >= 0 && saturation <= 2) balance.set("saturation", saturation)
      else                                    throw new ExtensionException("Invalid saturation value: [0, 2] (Default is 1)")
    }
  }

  object StartCamera extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      cameraPipeline.setState(State.PLAYING)
    }
  }

  object IsRolling extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.BooleanType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      Boolean.box(cameraPipeline.getState == State.PLAYING)
    }
  }

  object SelectCamera extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {

      val capturePlugin = "qtkitvideosrc"
      val patchSize     = context.getAgent.world.patchSize
      val width         = args(0).getDoubleValue * patchSize
      val height        = args(1).getDoubleValue * patchSize

      val webcamSource = ElementFactory.make(capturePlugin,      "capture")
      val conv         = ElementFactory.make("ffmpegcolorspace", "conv")
      val videofilter  = ElementFactory.make("capsfilter",       "filter")

      videofilter.setCaps(Caps.fromString("video/x-raw-rgb, endianness=4321, bpp=32, depth=24, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216"))
      appSink.setCaps(Caps.fromString("video/x-raw-rgb, width=%d, height=%d, bpp=32, depth=24, pixel-aspect-ratio=480/640".format(width.toInt, height.toInt)))

      cameraPipeline.addMany(webcamSource, conv, videofilter, scale, balance, appSink)
      Element.linkMany(webcamSource, conv, videofilter, scale, balance, appSink)

    }
  }

  object StopCamera extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      try cameraPipeline.setState(State.NULL)
      catch {
        case e: Exception => throw new ExtensionException(e.getMessage)
      }
    }
  }

}