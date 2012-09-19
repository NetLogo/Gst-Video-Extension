package org.nlogo.extensions.gstvideo

import java.awt.{ BorderLayout, Dimension }
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.JFrame

import org.gstreamer.{ Bin, Buffer, Bus, Caps, ClockTime, Element, elements, ElementFactory, GhostPad }, elements.PlayBin2
import org.gstreamer.{ GstObject, Pad, State, swing }, swing.VideoComponent

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

    // It actually kind of makes sense to have this bus listener here
    playbin.getBus.connect(new Bus.EOS {
      override def endOfStream(source: GstObject) {
        if (isLooping) playbin.seek(ClockTime.fromSeconds(0))
        else           playbin.setState(State.PAUSED)
      }
    })

    super.initBusListeners(playbin)

    val padAddedElem = new Element.PAD_ADDED {
      def padAdded(e: Element, p: Pad) {
        println("PAD ADDED: " + p)
      }
    }

    sinkBin.connect(padAddedElem)
    playbin.connect(padAddedElem)

    playbin

  }

  object OpenMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.StringType, Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {

      val patchSize = context.getAgent.world.patchSize
      val width     = args(1).getDoubleValue * patchSize //@ Why pass these in as arguments?
      val height    = args(2).getDoubleValue * patchSize
      val filename  =
        try context.attachCurrentDirectory(args(0).getString)
        catch {
          case e: IOException => throw new ExtensionException(e.getMessage)
        }

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
        throw new ExtensionException("Failed to link video sinks")

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

  val Image = Util.Image {
    val buff = if (player.getState == State.PLAYING) appSink.pullBuffer() else appSink.pullPreroll()
    Option(buff) orElse lastBufferOpt getOrElse (throw new ExtensionException("No buffer available to pull!"))
  } {
    buffer =>
      // If a buffer was cached and is not currently being relied on, dispose it now and cache current buffer
      if (!lastBufferOpt.isEmpty && !lastBufferOpt.exists(_ == buffer)) lastBufferOpt foreach (_.dispose())
      else                                                              lastBufferOpt = Option(buffer)
  }

  object IsPlaying extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.BooleanType)
    override def report(args: Array[Argument], context: Context) = Boolean.box(player.isPlaying)
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

  object CurrentTimeSeconds extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) = Double.box(player.queryPosition(TimeUnit.SECONDS))
  }

  object CurrentTimeMilliseconds extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) = Double.box(player.queryPosition(TimeUnit.MILLISECONDS))
  }

  object MovieDurationSeconds extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) = Double.box(player.queryDuration(TimeUnit.SECONDS))
  }

  object MovieDurationMilliseconds extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) = Double.box(player.queryDuration(TimeUnit.MILLISECONDS))
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

}