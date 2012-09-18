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
import org.gstreamer.elements.{ AppSink, RGBDataFileSink }
import org.nlogo.api.{ Argument, Context, ExtensionException, Syntax }

object Capture {

  //@ Option!
  //@ How many of these _really_ need to be globals?
  private var cameraPipeline: Pipeline = null
  private var scale: Element = null
  private var balance: Element = null
  private var fpsCountOverlay: Element = null
  private var appSink: AppSink = null
  private var recorder: RGBDataFileSink = null
  private var recording = false

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

  class StartRecording extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.StringType, Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {

      val fps       = 30
      val filename  = args(0).getString
      val file      = new File(filename)
      val patchSize = context.getAgent.world.patchSize
      val width     = args(1).getDoubleValue * patchSize
      val height    = args(2).getDoubleValue * patchSize
      //@ println("recording-width: " + width.toInt)
      //  println("recording-height: " + height.toInt)

      import Codec.Theora, Quality.Medium
      val codec = new Theora(Medium)
      val (propNames, propValues, encoder) = codec.getProps
      val muxer     = Util.determineMuxer(filename) getOrElse (throw new ExtensionException("Unrecognized video container"))

      recorder = new RGBDataFileSink("Recorder", width.toInt, height.toInt, fps, encoder, propNames, propValues, muxer, file)
      recorder.start
      recorder.setPreQueueSize(0)
      recorder.setSrcQueueSize(60)
      recording = true

    }
  }

  class StopRecording extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      recorder.stop
      recording = false
    }
  }

  class SetStrechToFillScreen extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.BooleanType))
    override def perform(args: Array[Argument], context: Context) {
      val shouldAddBorders = !(args(0).getBooleanValue)
      scale.set("add-borders", shouldAddBorders)
    }
  }

  class SetContrast extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val contrast = args(0).getDoubleValue
      if (contrast >= 0 && contrast <= 2) balance.set("contrast", contrast)
      else                                throw new ExtensionException("Invalid contrast value: [0, 2] (Default is 1)")
    }
  }

  class SetBrightness extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val brightness = args(0).getDoubleValue
      if (brightness >= -1 && brightness <= 1) balance.set("brightness", brightness)
      else                                     throw new ExtensionException("Invalid brightness value: [-1, 1] (Default is 0)")
    }
  }

  class SetHue extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val hue = args(0).getDoubleValue
      if (hue >= -1 && hue <= 1) balance.set("hue", hue)
      else                       throw new ExtensionException("Invalid hue value: [-1, 1] (Default is 0)")
    }
  }

  class SetSaturation extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val saturation = args(0).getDoubleValue
      if (saturation >= 0 && saturation <= 2) balance.set("saturation", saturation)
      else                                    throw new ExtensionException("Invalid saturation value: [0, 2] (Default is 1)")
    }
  }

  class StartCamera extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      cameraPipeline.setState(State.PLAYING)
    }
  }

  class IsRolling extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.BooleanType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      Boolean.box(cameraPipeline != null && cameraPipeline.getState == State.PLAYING)
    }
  }

  //@ Fix up
  class SelectCamera extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {

      val capturePlugin = "qtkitvideosrc"
      val patchSize     = context.getAgent.world.patchSize
      val width         = args(0).getDoubleValue * patchSize
      val height        = args(1).getDoubleValue * patchSize

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

          if (old == State.READY && current == State.PAUSED)
            println(appSink.getSinkPads.get(0).getNegotiatedCaps)

        }
      })

      val webcamSource = ElementFactory.make(capturePlugin, null)
      val conv         = ElementFactory.make("ffmpegcolorspace", null)
      val videofilter  = ElementFactory.make("capsfilter", null)
      videofilter.setCaps(Caps.fromString("video/x-raw-rgb, endianness=4321, bpp=32, depth=24, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216"))

      scale   = ElementFactory.make("videoscale", null)
      balance = ElementFactory.make("videobalance", null)
      appSink = ElementFactory.make("appsink", null).asInstanceOf[AppSink] //@ Pattern match!
      appSink.set("max-buffers", 1)
      appSink.set("drop", true)
      appSink.setCaps(Caps.fromString("video/x-raw-rgb, width=%d, height=%d, bpp=32, depth=24, pixel-aspect-ratio=480/640".format(width.toInt, height.toInt)))

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

  class StopCamera extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      try cameraPipeline.setState(State.NULL)
      catch {
        case e: Exception => throw new ExtensionException(e.getMessage)
      }
    }
  }

  val image = Util.Image {
    appSink.pullBuffer
  } {
    buffer =>
      if (recording) recorder.pushRGBFrame(buffer)
      else           buffer.dispose()
  }

}