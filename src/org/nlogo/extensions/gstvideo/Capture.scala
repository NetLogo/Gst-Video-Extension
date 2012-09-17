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
import org.gstreamer.{ Bus, Caps, Element, ElementFactory, Fraction, GstObject, Pipeline, State, TagList }
import org.gstreamer.elements.{ AppSink, RGBDataFileSink }
import org.nlogo.api.{ Argument, Context, DefaultCommand, DefaultReporter, ExtensionException, Syntax }

object Capture {
  def unload() {
    if (cameraPipeline != null) {
      cameraPipeline.setState(State.NULL)
      cameraPipeline.dispose()
      cameraPipeline = null
    }
    if (scale != null) scale.dispose()
    if (balance != null) balance.dispose()
    scale = ({
      balance = null; balance
    })
    if (appSink != null) appSink.dispose()
    appSink = null
    fpsCountOverlay = null
  }

  private var cameraPipeline: Pipeline = null
  private var scale: Element = null
  private var balance: Element = null
  private var fpsCountOverlay: Element = null
  private var appSink: AppSink = null
  private var recorder: RGBDataFileSink = null
  private var recording = false
  private var framerate: Fraction = null
  private final val WORST = 0 //@ ENUM!
  private final val LOW = 1
  private final val MEDIUM = 2
  private final val HIGH = 3
  private final val BEST = 4

  object StartRecording {
    final val THEORA = 0
    final val XVID = 1
    final val X264 = 2
    final val DIRAC = 3
    final val MJPEG = 4
    final val MJPEG2K = 5
  }

  class StartRecording extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int](Syntax.StringType, Syntax.NumberType, Syntax.NumberType))
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      import StartRecording._
      val patchSize = context.getAgent.world.patchSize
      val width = args(1).getDoubleValue * patchSize
      val height = args(2).getDoubleValue * patchSize
      println("recording-width: " + width.toInt)
      println("recording-height: " + height.toInt)
      val filename = args(0).getString
      val codecQuality = MEDIUM
      val codecType = THEORA
      var propNames: Array[String] = null
      var propValues: Array[AnyRef] = null
      var encoder = ""
      var muxer = ""
      val fn = filename.toLowerCase
      if (fn.endsWith(".ogg")) {
        muxer = "oggmux"
      }
      else if (fn.endsWith(".avi")) {
        muxer = "avimux"
      }
      else if (fn.endsWith(".mov")) {
        muxer = "qtmux"
      }
      else if (fn.endsWith(".flv")) {
        muxer = "flvmux"
      }
      else if (fn.endsWith(".mkv")) {
        muxer = "matroskamux"
      }
      else if (fn.endsWith(".mp4")) {
        muxer = "mp4mux"
      }
      else if (fn.endsWith(".3gp")) {
        muxer = "gppmux"
      }
      else if (fn.endsWith(".mpg")) {
        muxer = "ffmux_mpeg"
      }
      else if (fn.endsWith(".mj2")) {
        muxer = "mj2mux"
      }
      else {
        throw new ExtensionException("Unrecognized video container")
      }
      // Configuring encoder.
      if (codecType == THEORA) {
        encoder = "theoraenc"
        propNames = new Array[String](1)
        propValues = new Array[AnyRef](1)
        propNames(0) = "quality"
        var q = 31
        if (codecQuality == WORST) {
          q = 0
        }
        else if (codecQuality == LOW) {
          q = 15
        }
        else if (codecQuality == MEDIUM) {
          q = 31
        }
        else if (codecQuality == HIGH) {
          q = 47
        }
        else if (codecQuality == BEST) {
          q = 63
        }
        propValues(0) = Int.box(q)
      }
      else if (codecType == DIRAC) {
        encoder = "schroenc"
        propNames = new Array[String](1)
        propValues = new Array[AnyRef](1)
        propNames(0) = "quality"
        var q = 5.0d
        if (codecQuality == WORST) {
          q = 0.0d
        }
        else if (codecQuality == LOW) {
          q = 2.5d
        }
        else if (codecQuality == MEDIUM) {
          q = 5.0d
        }
        else if (codecQuality == HIGH) {
          q = 7.5d
        }
        else if (codecQuality == BEST) {
          q = 10.0d
        }
        propValues(0) = Double.box(q)
      }
      else if (codecType == XVID) {
        encoder = "xvidenc"
      }
      // TODO: set Properties of xvidenc.
      else if (codecType == X264) {
        encoder = "x264enc"
        propNames = new Array[String](2)
        propValues = new Array[AnyRef](2)

        // The pass property can take the following values:
				// (0): cbr              - Constant Bitrate Encoding (default)
				// (4): quant            - Constant Quantizer
				// (5): qual             - Constant Quality
				// (17): pass1            - VBR Encoding - Pass 1
				// (18): pass2            - VBR Encoding - Pass 2
				// (19): pass3            - VBR Encoding - Pass 3
        propNames(0) = "pass"
        propValues(0) = Int.box(5)
        propNames(1) = "quantizer"
        var q = 21
        if (codecQuality == WORST) {
          q = 50
        }
        else if (codecQuality == LOW) {
          q = 35
        }
        else if (codecQuality == MEDIUM) {
          q = 21
        }
        else if (codecQuality == HIGH) {
          q = 15
        }
        else if (codecQuality == BEST) {
          q = 1
        }
        propValues(1) = Int.box(q)
      }
      else if (codecType == MJPEG) {
        encoder = "jpegenc"
        propNames = new Array[String](1)
        propValues = new Array[AnyRef](1)
        propNames(0) = "quality"
        var q = 85
        if (codecQuality == WORST) {
          q = 0
        }
        else if (codecQuality == LOW) {
          q = 30
        }
        else if (codecQuality == MEDIUM) {
          q = 50
        }
        else if (codecQuality == HIGH) {
          q = 85
        }
        else if (codecQuality == BEST) {
          q = 100
        }
        propValues(0) = Int.box(q)
      }
      else if (codecType == MJPEG2K) {
        encoder = "jp2kenc"
      }
      else {
        throw new ExtensionException("Unrecognized video container")
      }
      var fps = 30
      if (framerate != null) fps = framerate.getNumerator / framerate.getDenominator
      val file = new File(filename)
      recorder = new RGBDataFileSink("Recorder", width.toInt, height.toInt, fps, encoder, propNames, propValues, muxer, file)
      recorder.start
      recorder.setPreQueueSize(0)
      recorder.setSrcQueueSize(60)
      recording = true
    }
  }

  class StopRecording extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int]())
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      recorder.stop
      recording = false
    }
  }

  class SetStrechToFillScreen extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int](Syntax.BooleanType))
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      val shouldAddBorders = !(args(0).getBooleanValue)
      scale.set("add-borders", shouldAddBorders)
    }
  }

  class SetContrast extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      val contrast = args(0).getDoubleValue
      if (contrast >= 0 && contrast <= 2) balance.set("contrast", contrast)
      else throw new ExtensionException("Invalid contrast value: [0, 2] (default is 1)")
    }
  }

  class SetBrightness extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      val contrast = args(0).getDoubleValue
      if (contrast >= -1 && contrast <= 1) balance.set("brightness", contrast)
      else throw new ExtensionException("Invalid brightness value: [-1, 1] (default is 0)")
    }
  }

  class SetHue extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      val contrast = args(0).getDoubleValue
      if (contrast >= -1 && contrast <= 1) balance.set("hue", contrast)
      else throw new ExtensionException("Invalid hue value: [-1, 1] (default is 0)")
    }
  }

  class SetSaturation extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      val contrast = args(0).getDoubleValue
      if (contrast >= 0 && contrast <= 2) balance.set("saturation", contrast)
      else throw new ExtensionException("Invalid saturation value: [0, 2] (default is 1)")
    }
  }

  class StartCamera extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      cameraPipeline.setState(State.PLAYING)
    }
  }

  class IsRolling extends DefaultReporter {
    override def getSyntax           = Syntax.reporterSyntax(Syntax.BooleanType)
    override def getAgentClassString = "O"
    override def report(args: Array[Argument], context: Context): AnyRef = {
      Boolean.box(cameraPipeline != null && cameraPipeline.getState == State.PLAYING)
    }
  }

  class SelectCamera extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      val capturePlugin = "qtkitvideosrc"
      val patchSize = context.getAgent.world.patchSize
      val width = args(0).getDoubleValue * patchSize
      val height = args(1).getDoubleValue * patchSize
      println("======== World Information ========")
      println("width:  " + width)
      println("height: " + height)
      println("===================================")
      // Pipeline construction based on Processing
			// http://code.google.com/p/processing/source/browse/trunk/processing/java/libraries/video/src/processing/video/Capture.java
      cameraPipeline = new Pipeline("camera-capture")
      cameraPipeline.getBus.connect(new Bus.TAG {
        def tagsFound(source: GstObject, tagList: TagList) {
          import scala.collection.JavaConversions._
          for (tagName <- tagList.getTagNames) {
            for (tagData <- tagList.getValues(tagName)) {
              printf("[%s]=%s\n", tagName, tagData)
            }
          }
        }
      })
      cameraPipeline.getBus.connect(new Bus.STATE_CHANGED {
        def stateChanged(source: GstObject, old: State, current: State, pending: State) {
          println("Pipeline state changed from " + old + " to " + current)
          if (old == State.READY && current == State.PAUSED) {
            val sinkPads = appSink.getSinkPads
            val sinkPad = sinkPads.get(0)
            val sinkCaps = sinkPad.getNegotiatedCaps
            println(sinkCaps)
            val structure = sinkCaps.getStructure(0)
            framerate = structure.getFraction("framerate")
            println("Camera FPS: " + framerate.getNumerator + " / " + framerate.getDenominator)
          }
        }
      })
      val webcamSource = ElementFactory.make(capturePlugin, null)
      val conv = ElementFactory.make("ffmpegcolorspace", null)
      val videofilter = ElementFactory.make("capsfilter", null)
      videofilter.setCaps(Caps.fromString("video/x-raw-rgb, endianness=4321" + ", bpp=32, depth=24, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216"))
      scale = ElementFactory.make("videoscale", null)
      balance = ElementFactory.make("videobalance", null)
      appSink = ElementFactory.make("appsink", null).asInstanceOf[AppSink] //@ Pattern match!
      appSink.set("max-buffers", 1)
      appSink.set("drop", true)
      val capsString = "video/x-raw-rgb, width=%d, height=%d, bpp=32, depth=24, pixel-aspect-ratio=480/640".format(width.toInt, height.toInt)
      val filterCaps = Caps.fromString(capsString)
      appSink.setCaps(filterCaps)
      cameraPipeline.addMany(webcamSource, conv, videofilter, scale, balance, appSink)
      Element.linkMany(webcamSource, conv, videofilter, scale, balance, appSink)
      cameraPipeline.getBus.connect(new Bus.ERROR {
        def errorMessage(source: GstObject, code: Int, message: String) {
          println("Error occurred: " + message)
        }
      })
      cameraPipeline.getBus.connect(new Bus.STATE_CHANGED {
        def stateChanged(source: GstObject, old: State, current: State, pending: State) {
          if (source == cameraPipeline) {
            println("Pipeline state changed from " + old + " to " + current)
          }
        }
      })
    }
  }

  class StopCamera extends DefaultCommand {
    override def getSyntax           = Syntax.commandSyntax(Array[Int]())
    override def getAgentClassString = "O"
    override def perform(args: Array[Argument], context: Context) {
      try {
        cameraPipeline.setState(State.NULL)
      }
      catch {
        case e: Exception =>
          throw new ExtensionException(e.getMessage)
      }
    }
  }

  class Image extends DefaultReporter {
    override def getSyntax           = Syntax.reporterSyntax(Array[Int](), Syntax.WildcardType)
    override def getAgentClassString = "O"
    override def report(args: Array[Argument], context: Context): AnyRef = {
      try {
        val buffer = appSink.pullBuffer
        val structure = buffer.getCaps.getStructure(0)
        val height = structure.getInteger("height")
        val width = structure.getInteger("width")
        val intBuf = buffer.getByteBuffer.asIntBuffer
        val imageData = new Array[Int](intBuf.capacity)
        intBuf.get(imageData, 0, imageData.length)
        if (recording) {
          recorder.pushRGBFrame(buffer)
        }
        if (!recording) buffer.dispose()
        Util.getBufferedImage(imageData, width, height)
      }
      catch {
        case e: Exception =>
          throw new ExtensionException(e.getMessage)
      }
    }
  }

}