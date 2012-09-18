package org.nlogo.extensions.gstvideo

import java.awt.{ BorderLayout, Dimension }
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.JFrame

import org.gstreamer.{ Bin, Buffer, Bus, Caps, ClockTime, Element, elements, ElementFactory, GhostPad }
import org.gstreamer.{ GstObject, Pad, State, swing, TagList }
import elements.{ AppSink, PlayBin2 }
import swing.VideoComponent

import org.nlogo.api.{ Argument, Context, ExtensionException, Syntax }

object Movie {
  def unload() {
    if (player != null) {
      player.setState(State.NULL)
      player = null
    }
    sinkBin = null
  }

  private var player: PlayBin2 = null
  private var lastBuffer: Buffer = null
  private var looping = false
  private var scale: Element = null
  private var balance: Element = null
  private var sizeFilter: Element = null
  private var conv: Element = null
  private var worldWidth = 0
  private var worldHeight = 0
  private var sinkBin: Bin = null
  private var appSink: AppSink = null
  private var playerFrame: JFrame = null
  private var playerFrameVideoComponent: VideoComponent = null

  object SetStrechToFillScreen extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.BooleanType))
    override def perform(args: Array[Argument], context: Context) {
      if (scale == null) throw new ExtensionException("no scale element seems to exist")
      val shouldAddBorders = !(args(0).getBooleanValue)
      scale.set("add-borders", shouldAddBorders)
      if (playerFrameVideoComponent != null) playerFrameVideoComponent.setKeepAspect(shouldAddBorders)
    }
  }

  object SetFrameCacheSize extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      if (player == null || appSink == null) throw new ExtensionException("there is either no movie open or the pipeline is misconfigured")
      val brightness = args(0).getDoubleValue
      if (brightness >= -1 && brightness <= 1) balance.set("brightness", brightness)
      else throw new ExtensionException("invalid brightness value: [-1, 1] (Video is 0)")
    }
  }

  //@ UGHHHHHHHHHH!  WHY DID HE DUPLICATE THE CODE FOR THESE FILTERS?!?!??!?!
  object SetContrast extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      if (balance == null) throw new ExtensionException("no videobalance element seems to exist")
      val contrast = args(0).getDoubleValue
      if (contrast >= 0 && contrast <= 2) balance.set("contrast", contrast)
      else throw new ExtensionException("invalid contrast value: [0, 2] (Video is 1)")
    }
  }

  object SetBrightness extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      if (balance == null) throw new ExtensionException("no videobalance element seems to exist")
      val brightness = args(0).getDoubleValue
      if (brightness >= -1 && brightness <= 1) balance.set("brightness", brightness)
      else throw new ExtensionException("invalid brightness value: [-1, 1] (Video is 0)")
    }
  }

  object SetHue extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      if (balance == null) throw new ExtensionException("no videobalance element seems to exist")
      val contrast = args(0).getDoubleValue
      if (contrast >= -1 && contrast <= 1) balance.set("hue", contrast)
      else throw new ExtensionException("invalid hue value: [-1, 1] (Video is 0)")
    }
  }

  object SetSaturation extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      if (balance == null) throw new ExtensionException("no videobalance element seems to exist")
      val contrast = args(0).getDoubleValue
      if (contrast >= 0 && contrast <= 2) balance.set("saturation", contrast)
      else throw new ExtensionException("invalid saturation value: [0, 2] (Video is 1)")
    }
  }

  object SetLooping extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.BooleanType))
    override def perform(args: Array[Argument], context: Context) {
      looping = args(0).getBooleanValue
    }
  }

  object DebugCommand extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      println("=============== Running debug command(s) ===============")
    }
  }

  object OpenMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.StringType, Syntax.NumberType, Syntax.NumberType))
    private def installCallbacks(bus: Bus) {
      bus.connect(new Bus.INFO {
        def infoMessage(source: GstObject, code: Int, message: String) {
          println("Code: " + code + " | Message: " + message)
        }
      })
      bus.connect(new Bus.TAG {
        //@ Oh, how I love code redundancy
        def tagsFound(source: GstObject, tagList: TagList) {
          import scala.collection.JavaConversions._
          for {
            tagName <- tagList.getTagNames
            tagData <- tagList.getValues(tagName)
          } { println("[%s]=%s".format(tagName, tagData)) }
        }
      })
      bus.connect(new Bus.ERROR {
        def errorMessage(source: GstObject, code: Int, message: String) {
          println("Error occurred: " + message + "(" + code + ")")
        }
      })
      bus.connect(new Bus.STATE_CHANGED {
        def stateChanged(source: GstObject, old: State, current: State, pending: State) {
          if (source == player) {
            println("Pipeline state changed from " + old + " to " + current)
            if (old == State.READY && current == State.PAUSED) {
              val sinkPads = sinkBin.getSinkPads
              val sinkPad = sinkPads.get(0)
              val sinkCaps = sinkPad.getNegotiatedCaps
              println(sinkCaps)
              val structure = sinkCaps.getStructure(0)
              val width = structure.getInteger("width")
              val height = structure.getInteger("height")
              println("video-width: " + width)
              println("video-height: " + height)
            }
          }
        }
      })
      bus.connect(new Bus.EOS {
        def endOfStream(source: GstObject) {
          println("Finished playing file")
          if (looping) player.seek(ClockTime.fromSeconds(0))
          else player.setState(State.PAUSED)
        }
      })
    }

    override def perform(args: Array[Argument], context: Context) {
      val patchSize = context.getAgent.world.patchSize
      val width = args(1).getDoubleValue * patchSize
      val height = args(2).getDoubleValue * patchSize
      println("======== World Information ========")
      println("patch-size : " + patchSize)
      println("width      : " + width)
      println("height     : " + height)
      println("===================================")
      worldWidth = width.toInt
      worldHeight = height.toInt
      var filename: String = null
      try filename = context.attachCurrentDirectory(args(0).getString)
      catch {
        case e: IOException => throw new ExtensionException(e.getMessage)
      }
      if (player == null && filename != null) {
        player = new PlayBin2("player")
        installCallbacks(player.getBus) // Watch for errors and log them
        sinkBin = new Bin
        sinkBin.connect(new Element.PAD_ADDED {
          def padAdded(e: Element, p: Pad) {
            println("PAD ADDED: " + p)
          }
        })
        player.connect(new Element.PAD_ADDED {
          def padAdded(e: Element, p: Pad) {
            println("PAD ADDED: " + p)
          }
        })
        appSink = ElementFactory.make("appsink", null).asInstanceOf[AppSink] //@ Pattern match
        appSink.set("max-buffers", 1)
        appSink.set("drop", true)
       	// appSink.set("enable-last-buffer", true);
        conv = ElementFactory.make("ffmpegcolorspace", null)
        scale = ElementFactory.make("videoscale", null)
        sizeFilter = ElementFactory.make("capsfilter", null)
        val capsString = "video/x-raw-rgb, width=%d, height=%d".format(width.toInt, height.toInt)
        val sizeCaps = Caps.fromString(capsString)
        sizeFilter.setCaps(sizeCaps)

        balance = ElementFactory.make("videobalance", null)
        val rate = ElementFactory.make("videorate", null)
        sinkBin.addMany(scale, sizeFilter, balance, conv, rate, appSink)
        if (!scale.link(sizeFilter)) println("Problem with scale->caps")
        if (!sizeFilter.link(balance)) println("Problem with sizeFilter->balance")
        if (!balance.link(conv)) println("Problem with caps->conv")
        if (!conv.link(rate)) println("Problem with conv->overlay")
        val pads = scale.getSinkPads
        val sinkPad = pads.get(0)
        val ghost = new GhostPad("sink", sinkPad)
        sinkBin.addPad(ghost)
        // Snippet from http://opencast.jira.com/svn/MH/trunk/modules/matterhorn-composer-gstreamer/src/main/java/org/opencastproject/composer/gstreamer/engine/GStreamerEncoderEngine.java
        val some_caps = new Caps("video/x-raw-rgb" + ", bpp=32, depth=24, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216, alpha_mask=(int)255")
        if (!Element.linkPadsFiltered(rate, "src", appSink, "sink", some_caps)) {
          throw new ExtensionException("Failed linking ffmpegcolorspace with appsink")
        }
        player.setVideoSink(sinkBin)
      }
      println("attempting to load file://" + filename)
      player.setState(State.NULL)
      player.set("uri", "file://" + filename)
    }
  }

  object StartMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      if (player == null) throw new ExtensionException("there is no movie open")
      System.err.println("starting movie (in theory...)")
      player.setState(State.PLAYING)
    }
  }

  object SetTimeSeconds extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      if (player == null) throw new ExtensionException("there is no movie open")
      val newPos = args(0).getDoubleValue
      player.seek(ClockTime.fromSeconds(newPos.longValue))
    }
  }

  object SetTimeMilliseconds extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      if (player == null) throw new ExtensionException("there is no movie open")
      val newPos = args(0).getDoubleValue
      player.seek(ClockTime.fromMillis(newPos.longValue))
    }
  }

  object OpenPlayer extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      if (player == null) throw new ExtensionException("there is no movie open")
      val patchSize = context.getAgent.world.patchSize
      val width  = args(0).getDoubleValue * patchSize
      val height = args(1).getDoubleValue * patchSize
      playerFrame = new JFrame("NetLogo: GstVideo Extension - External Video Frame")
      playerFrameVideoComponent = new VideoComponent
      val videosink = playerFrameVideoComponent.getElement
      val currentState = player.getState
     	// It seems to switch video sinks the pipeline needs to
			// be reconfigured.  Set to NULL and rebuild.
      player.setState(State.NULL)
      player.setVideoSink(videosink)
      player.setState(currentState)
      playerFrame.add(playerFrameVideoComponent, BorderLayout.CENTER)
      playerFrameVideoComponent.setPreferredSize(new Dimension(width.toInt, height.toInt))
      playerFrame.pack()
      playerFrame.setVisible(true)
    }
  }

  object IsPlaying extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.BooleanType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      Boolean.box(player != null && player.isPlaying)
    }
  }

  object StopMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      if (player == null) throw new ExtensionException("there is no movie open")
      else                player.setState(State.PAUSED)
    }
  }

  object CloseMovie extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int]())
    override def perform(args: Array[Argument], context: Context) {
      if (player == null) throw new ExtensionException("there is no movie open")
      //	player.setState(State.NULL);
		  //	player = null;
      if (playerFrame != null) {
        playerFrame.dispose()
        playerFrame = null
      }
      // It seems to switch video sinks the pipeline needs to
			// be reconfigured.  Set to NULL and rebuild.
      val currentState = player.getState
      player.setState(State.NULL)
      player.setVideoSink(sinkBin)
      player.setState(currentState)
    }
  }

  object MovieDurationSeconds extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      if (player == null) throw new ExtensionException("there is no movie open")
      val duration = player.queryDuration(TimeUnit.SECONDS)
      Double.box(duration)
    }
  }

  object MovieDurationMilliseconds extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      if (player == null) throw new ExtensionException("there is no movie open")
      val duration = player.queryDuration(TimeUnit.MILLISECONDS)
      Double.box(duration)
    }
  }

  object CurrentTimeSeconds extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      if (player == null) throw new ExtensionException("there is no movie open")
      val position = player.queryPosition(TimeUnit.SECONDS)
      Double.box(position)
    }
  }

  object CurrentTimeMilliseconds extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      if (player == null) throw new ExtensionException("there is no movie open")
      val position = player.queryPosition(TimeUnit.MILLISECONDS)
      Double.box(position)
    }
  }

  val image = Util.Image {

    if (player == null || appSink == null)
      throw new ExtensionException("either no movie is open or pipeline is not constructed properly")

    Option(appSink.pullBuffer) getOrElse lastBuffer

  } {
    buffer =>
      // If a buffer was cached and is not currently being relied on, dispose it now and cache current buffer
      if (lastBuffer != null && buffer != lastBuffer) lastBuffer.dispose()
      else                                            lastBuffer = buffer
  }

}