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

  private lazy val player      = new PlayBin2("player")
  private lazy val playerFrame = new JFrame("NetLogo: GstVideo Extension - External Video Frame")
  private lazy val frameVideo  = new VideoComponent
  private lazy val scale       = ElementFactory.make("videoscale", null)
  private lazy val sinkBin     = new Bin

  private var lastBufferOpt: Option[Buffer] = None
  private var looping                       = false //@ Surely, there's some way to encapsulate this away somewhere

  override def unload() {
    player.setState(State.NULL)
    sinkBin.dispose()
  }

  //@ Maybe extract this to `VideoPrimitiveManager`, too
  object SetStrechToFillScreen extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.BooleanType))
    override def perform(args: Array[Argument], context: Context) {
      val shouldAddBorders = !(args(0).getBooleanValue)
      scale.set("add-borders", shouldAddBorders)
      frameVideo.setKeepAspect(shouldAddBorders)
    }
  }

  //@ Better yet: `StartLooping` and `StopLooping`
  object SetLooping extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.BooleanType))
    override def perform(args: Array[Argument], context: Context) {
      looping = args(0).getBooleanValue
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
      val filename =
        try context.attachCurrentDirectory(args(0).getString)
        catch {
          case e: IOException => throw new ExtensionException(e.getMessage)
        }
      if (filename != null) {
        installCallbacks(player.getBus) // Watch for errors and log them
        //@ Oh, beautiful.  Two of the exact same listener attached to different elements
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
       	// appSink.set("enable-last-buffer", true);
        val conv = ElementFactory.make("ffmpegcolorspace", null) //@ Redundancy
        val sizeFilter = ElementFactory.make("capsfilter", null)
        val capsString = "video/x-raw-rgb, width=%d, height=%d".format(width.toInt, height.toInt)
        val sizeCaps = Caps.fromString(capsString)
        sizeFilter.setCaps(sizeCaps)

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
      System.err.println("starting movie (in theory...)")
      player.setState(State.PLAYING)
    }
  }

  object SetTimeSeconds extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val newPos = args(0).getDoubleValue
      player.seek(ClockTime.fromSeconds(newPos.longValue))
    }
  }

  object SetTimeMilliseconds extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val newPos = args(0).getDoubleValue
      player.seek(ClockTime.fromMillis(newPos.longValue))
    }
  }

  object OpenPlayer extends VideoCommand {
    override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.NumberType, Syntax.NumberType))
    override def perform(args: Array[Argument], context: Context) {
      val patchSize = context.getAgent.world.patchSize
      val width  = args(0).getDoubleValue * patchSize
      val height = args(1).getDoubleValue * patchSize
      playerFrame.setVisible(true)
      val videosink = frameVideo.getElement
      val currentState = player.getState
     	// It seems to switch video sinks the pipeline needs to
			// be reconfigured.  Set to NULL and rebuild.
      player.setState(State.NULL)
      player.setVideoSink(videosink)
      player.setState(currentState)
      playerFrame.add(frameVideo, BorderLayout.CENTER)
      frameVideo.setPreferredSize(new Dimension(width.toInt, height.toInt))
      playerFrame.pack()
      playerFrame.setVisible(true)
    }
  }

  object IsPlaying extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.BooleanType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      Boolean.box(player.isPlaying)
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
      //	player.setState(State.NULL);
		  //	player = null;
      playerFrame.setVisible(false)
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
      val duration = player.queryDuration(TimeUnit.SECONDS)
      Double.box(duration)
    }
  }

  object MovieDurationMilliseconds extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      val duration = player.queryDuration(TimeUnit.MILLISECONDS)
      Double.box(duration)
    }
  }

  object CurrentTimeSeconds extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      val position = player.queryPosition(TimeUnit.SECONDS)
      Double.box(position)
    }
  }

  object CurrentTimeMilliseconds extends VideoReporter {
    override def getSyntax = Syntax.reporterSyntax(Syntax.NumberType)
    override def report(args: Array[Argument], context: Context) : AnyRef = {
      val position = player.queryPosition(TimeUnit.MILLISECONDS)
      Double.box(position)
    }
  }

  val image = Util.Image {
    Option(appSink.pullBuffer) getOrElse lastBufferOpt
  } {
    buffer =>
      // If a buffer was cached and is not currently being relied on, dispose it now and cache current buffer
      if (!lastBufferOpt.isEmpty && buffer != lastBufferOpt) lastBufferOpt.dispose()
      else                                                   lastBufferOpt = buffer
  }

}