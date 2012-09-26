package org.nlogo.extensions.gstvideo

import java.awt.{ BorderLayout, Dimension }
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.JFrame

import org.gstreamer.{ Buffer, Bus, Caps, ClockTime, Element, elements }, elements.PlayBin2
import org.gstreamer.{ GstObject, Pad, swing }, swing.VideoComponent

import org.nlogo.api.{ Argument, Context, ExtensionException, Syntax }

object Movie extends VideoPrimitiveManager {

  private lazy val player      = initPlayer()
  private lazy val playerFrame = new JFrame("NetLogo: GstVideo Extension - External Video Frame")
  private lazy val frameVideo  = new VideoComponent
  private lazy val sizeFilter  = ElementManager.generateSizeFilter(1, 1) // Height/width gets overwritten right away, but can't be < 1
  private lazy val binManager  = new SinkBinManager(List(scale, sizeFilter, balance, ElementManager.generateColorspaceConverter,
                                                         ElementManager.generateVideoRate, appSink))

  // These `var`s smell like onions... --JAB
  private var lastBufferOpt: Option[Buffer]  = None
  private var videoSinkOpt:  Option[Element] = None
  private var isLooping                      = false

  // These caps are necessary to get video hue flipped
  appSink.setCaps(new Caps("video/x-raw-rgb, bpp=32, depth=24, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216, alpha_mask=(int)255"))

  override def unload() {
    player.stop()
    binManager.dispose()
  }

  override protected def generateBuffer : Buffer = {
    val buff = if (player.isPlaying) appSink.pullBuffer() else appSink.pullPreroll()
    Option(buff) orElse lastBufferOpt getOrElse (throw new ExtensionException("No buffer available to pull!"))
  }

  override protected def handleImageBuffer(buffer: Buffer) {
    lastBufferOpt foreach { case lastBuffer => if (lastBuffer ne buffer) lastBuffer.dispose() }
    lastBufferOpt = Option(buffer)
  }

  override protected def setKeepAspect(isStretching: Boolean) {
    super.setKeepAspect(isStretching)
    frameVideo.setKeepAspect(!isStretching)
  }

  private def initPlayer() : PlayBin2 = {

    val playbin = ElementManager.generatePlayBin("playbin")

    // It actually kind of makes sense to have this bus listener here
    playbin.getBus.connect(new Bus.EOS {
      override def endOfStream(source: GstObject) {
        if (isLooping) playbin.seek(ClockTime.fromSeconds(0))
        else           playbin.pause()
      }
    })

    super.initBusListeners(playbin, () => {

      val padAddedElem = new Element.PAD_ADDED {
        def padAdded(e: Element, p: Pad) {
          println("PAD ADDED: " + p)
        }
      }

      playbin.connect(padAddedElem)

    })

    playbin

  }

  /*
  Right now, this is only done on movie LOAD, because it's seeming difficult/impossible to do on buffer pull
  Doing it while the movie is playing seems to break things entirely (doesn't resize & frames stop getting pulled)
  Pausing the movie has erratic, crash-causing behavior
  Doing it after stopping/readying the movie resets the seek bar, and seeking back to the old position doesn't won't work as expected
  Likely linked to the issues with `SinkBinManager`'s `dummyCallback`s, but... good luck with that
  Other ideas welcome. --JAB (9/24/12)
  */
  private def setBufferSize(width: Int, height: Int) {
    try {
      player.stop() // Trying to replace an element in an unstopped pipeline is a Very Bad Idea(tm)
      binManager.replaceElementByName(sizeFilter.getName, ElementManager.generateSizeFilter(width, height))
    }
    catch {
      case e: IndexOutOfBoundsException => throw new ExtensionException(e.getMessage, e)
    }
  }

  object OpenMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.StringType))
    override def perform(args: Array[Argument], context: Context) {

      val (width, height) = determineWorldDimensions(context)
      setBufferSize(width.toInt, height.toInt)

      try {
        import java.io.{ File, FileNotFoundException }
        val filePath = context.attachCurrentDirectory(args(0).getString)
        if (new File(filePath).exists())
          player.set("uri", "file://" + filePath)
        else
          throw new FileNotFoundException("Could not find file: " + filePath)
      }
      catch {
        case e: IOException => throw new ExtensionException(e.getMessage, e)
      }

    }
  }

  object StartMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      setVideoSink(binManager.sinkBin)
      player.play()
    }
  }

  object StopMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      player.pause()
    }
  }

  object OpenPlayer extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      val (width, height) = determineWorldDimensions(context)
      val videoSink       = frameVideo.getElement
      setVideoSink(videoSink)
      player.play()
      frameVideo.setPreferredSize(new Dimension(width.toInt, height.toInt))
      playerFrame.add(frameVideo, BorderLayout.CENTER)
      playerFrame.pack()
      playerFrame.setVisible(true)
    }
  }

  object ClosePlayer extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      playerFrame.setVisible(false)
    }
  }

  // A necessary semi-hack for swapping in new video sinks on the fly
  // We avoid this code when we are already using the same sink, so as to avoid NULLing the player's state
  // (which would effectively prevent the resuming of paused video streams)
  private def setVideoSink(sink: Element) {
    if (!(videoSinkOpt exists (_ eq sink))) {
      player.stop()
      player.setVideoSink(sink)
      videoSinkOpt = Option(sink)
    }
  }

  object IsPlaying extends VideoReporter {
    override def getSyntax                                       = Syntax.reporterSyntax(Syntax.BooleanType)
    override def report(args: Array[Argument], context: Context) = Boolean.box(player.isPlaying)
  }

  object SetTimeSeconds extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      seek(ClockTime.fromSeconds(args(0).getDoubleValue.longValue))
    }
  }

  object SetTimeMilliseconds extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      seek(ClockTime.fromMillis(args(0).getDoubleValue.longValue))
    }
  }

  // This `seek` method is a "flushing seek", so that seeking can be done in either PLAYING or PAUSED state,
  // without any worry of a PAUSED-state seek causing a deadlock
  private def seek(time: ClockTime) {
    import org.gstreamer.{ Format, SeekFlags, SeekType }
    player.seek(1.0, Format.TIME, SeekFlags.FLUSH, SeekType.SET, time.getNanoSeconds, SeekType.NONE, ClockTime.NONE.getNanoSeconds)
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