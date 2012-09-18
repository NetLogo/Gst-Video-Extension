package org.nlogo.extensions.gstvideo

import java.awt.{ BorderLayout, Dimension }
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.JFrame

import org.gstreamer.{ Bin, Buffer, Bus, Caps, ClockTime, Element, elements, ElementFactory, GhostPad }
import org.gstreamer.{ GstObject, Pad, State, swing, TagList }
import elements.PlayBin2
import swing.VideoComponent

import org.nlogo.api.{ Argument, Context, ExtensionException, Syntax }

object Movie extends VideoPrimitiveManager {

  private lazy val player      = initPlayer()
  private lazy val playerFrame = new JFrame("NetLogo: GstVideo Extension - External Video Frame")
  private lazy val frameVideo  = new VideoComponent
  private lazy val sinkBin     = new Bin

  private var lastBufferOpt: Option[Buffer] = None
  private var isLooping                     = false //@ Surely, there's some way to encapsulate this away somewhere

  override def unload() {
    player.setState(State.NULL)
    sinkBin.dispose()
  }

  override protected def setFullscreen(isStretching: Boolean) {
    super.setFullscreen(isStretching)
    frameVideo.setKeepAspect(!isStretching)
  }

  private def initPlayer() : PlayBin2 = {

    val playbin = new PlayBin2("player")

    playbin.getBus.connect(new Bus.EOS {
      override def endOfStream(source: GstObject) {
        if (isLooping) playbin.seek(ClockTime.fromSeconds(0))
        else           playbin.setState(State.PAUSED)
      }
    })

    playbin

  }

  object StartLooping extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      isLooping = true
    }
  }

  object StopLooping extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      isLooping = false
    }
  }

  object OpenMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.StringType, Syntax.NumberType, Syntax.NumberType))
    //@ Should have a way to turn these on/off
    private def installCallbacks() { // Watch for errors and log them

      val bus = player.getBus

      val padAddedElem = new Element.PAD_ADDED {
        def padAdded(e: Element, p: Pad) {
          println("PAD ADDED: " + p)
        }
      }

      sinkBin.connect(padAddedElem)
      player.connect(padAddedElem)

      bus.connect(new Bus.INFO {
        override def infoMessage(source: GstObject, code: Int, message: String) {
          println("Code: " + code + " | Message: " + message)
        }
      })

      bus.connect(new Bus.TAG {
        //@ Oh, how I love code redundancy
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

      bus.connect(new Bus.STATE_CHANGED {
        override def stateChanged(source: GstObject, old: State, current: State, pending: State) {
          if (source == player) {
            println("Pipeline state changed from %s to %s".format(old, current))
          }
        }
      })

    }

    override def perform(args: Array[Argument], context: Context) {

      val patchSize = context.getAgent.world.patchSize
      val width = args(1).getDoubleValue * patchSize
      val height = args(2).getDoubleValue * patchSize
      val filename =
        try context.attachCurrentDirectory(args(0).getString)
        catch {
          case e: IOException => throw new ExtensionException(e.getMessage)
        }

      installCallbacks()

      val colorConverter = generateColorspaceConverter
      val sizeFilter     = generateVideoFilter
      val rate           = ElementFactory.make("videorate", "rate")
      val capsString     = "video/x-raw-rgb, width=%d, height=%d".format(width.toInt, height.toInt)
      sizeFilter.setCaps(Caps.fromString(capsString))

      sinkBin.addMany(scale, sizeFilter, balance, colorConverter, rate, appSink)
      Element.linkMany(scale, sizeFilter, balance, colorConverter, rate, appSink)

      sinkBin.addPad(new GhostPad("sink", scale.getSinkPads.get(0)))

      // Snippet from http://opencast.jira.com/svn/MH/trunk/modules/matterhorn-composer-gstreamer/src/main/java/org/opencastproject/composer/gstreamer/engine/GStreamerEncoderEngine.java
      val some_caps = new Caps("video/x-raw-rgb, bpp=32, depth=24, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216, alpha_mask=(int)255")

      if (!Element.linkPadsFiltered(rate, "src", appSink, "sink", some_caps))
        throw new ExtensionException("Failed linking ffmpegcolorspace with appsink")

      player.setVideoSink(sinkBin)
      player.setState(State.NULL)
      player.set("uri", "file://" + filename)

    }
  }

  object StartMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      player.setState(State.PLAYING)
    }
  }

  object StopMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      player.setState(State.PAUSED)
    }
  }

  object CloseMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      playerFrame.setVisible(false)
      setPlayerSink(sinkBin)
    }
  }

  object IsPlaying extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.BooleanType)
    override def report(args: Array[Argument], context: Context) = Boolean.box(player.isPlaying)
  }

  object MovieDurationSeconds extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) = Double.box(player.queryDuration(TimeUnit.SECONDS))
  }

  object MovieDurationMilliseconds extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) = Double.box(player.queryDuration(TimeUnit.MILLISECONDS))
  }

  object CurrentTimeSeconds extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) = Double.box(player.queryPosition(TimeUnit.SECONDS))
  }

  object CurrentTimeMilliseconds extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) = Double.box(player.queryPosition(TimeUnit.MILLISECONDS))
  }

  object SetTimeSeconds extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      player.seek(ClockTime.fromSeconds(args(0).getDoubleValue.longValue))
    }
  }

  object SetTimeMilliseconds extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      player.seek(ClockTime.fromMillis(args(0).getDoubleValue.longValue))
    }
  }

  object OpenPlayer extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {

      val patchSize    = context.getAgent.world.patchSize
      val width        = args(0).getDoubleValue * patchSize
      val height       = args(1).getDoubleValue * patchSize
      val videoSink    = frameVideo.getElement

      setPlayerSink(videoSink)
      frameVideo.setPreferredSize(new Dimension(width.toInt, height.toInt))
      playerFrame.add(frameVideo, BorderLayout.CENTER)
      playerFrame.pack()
      playerFrame.setVisible(true)

    }
  }

  // It seems to switch video sinks the pipeline needs to be reconfigured.  Set to NULL and rebuild.
  //@ Weird hack; must investigate
  private def setPlayerSink(sink: Element) {
    val currentState = player.getState
    player.setState(State.NULL)
    player.setVideoSink(sink)
    player.setState(currentState)
  }

  val image = Util.Image {
    Option(appSink.pullBuffer) orElse lastBufferOpt getOrElse (throw new ExtensionException("No buffer available to pull!"))
  } {
    buffer =>
      // If a buffer was cached and is not currently being relied on, dispose it now and cache current buffer
      if (!lastBufferOpt.isEmpty && buffer != lastBufferOpt) lastBufferOpt foreach (_.dispose())
      else                                                   lastBufferOpt = Option(buffer)
  }

}