package org.nlogo.extensions.gstvideo

import java.io.File

import org.gstreamer.{ Bin, Buffer, Element, elements }
import elements.RGBDataFileSink

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/18/12
 * Time: 12:44 PM
 */

// A proxy for a sometimes-there recording sink
class Recorder {

  private var sinkOpt: Option[RecorderSink] = None

  def dispose()            { sinkOpt foreach (_.dispose()) }
  def push(buffer: Buffer) { sinkOpt foreach (_.push(buffer)) }
  def start()              { sinkOpt foreach (_.start()) }
  def stop()               { sinkOpt foreach (_.stop()) }

  def reconstructSink(name: String, width: Int, height: Int, fps: Int,
                      encoderStr: String, encoderPropNames: Array[String],
                      encoderPropData: Array[AnyRef], muxerStr: String, file: File) {
    val sink = new RGBDataFileSink("Recorder", width, height, fps, encoderStr, encoderPropNames, encoderPropData, muxerStr, file)
    sink.setPreQueueSize(0)
    sink.setSrcQueueSize(60)
    sinkOpt foreach (_.dispose())
    sinkOpt = Option(new RecorderSink(sink, width, height))
  }

  // Ugh....  Yep, I'm really doing this!  It'd just be such a shame if GStreamer Java
  // were to just expose the height and width of the sink as public members.... --JAB
  private class RecorderSink(fileSink: RGBDataFileSink, width: Int, height: Int) {

    private var isRecording = false

    private val src  = ElementManager.generateAppSrc
    private val sink = ElementManager.generateAppSink

    private val elements = {
      val scale      = ElementManager.generateScaler
      val sizeFilter = ElementManager.generateSizeFilter(width, height)
      List(src, scale, sizeFilter, sink)
    }

    private val bin = {
      val bin = new Bin
      bin.addMany(elements: _*)
      Element.linkMany(elements: _*)
      bin
    }

    def dispose() {
      bin.stop()
      fileSink.dispose()
      elements foreach (_.dispose())
      bin.dispose()
    }

    def push(buffer: Buffer) {
      if (isRecording) fileSink.pushRGBFrame(scaleBuffer(buffer))
      else             buffer.dispose()
    }

    def start() {
      fileSink.start()
      isRecording = true
    }

    def stop() {
      fileSink.stop()
      isRecording = false
    }

    // As crazy as this looks, and as crazy as I probably sound when saying it, I'm pretty sure this is "the way"
    // you scale a buffer that isn't in a pipeline with GStreamer
    private def scaleBuffer(buffer: Buffer) : Buffer = {

      src.pushBuffer(buffer)

      bin.play()
      val newBuff = sink.pullBuffer()
      bin.pause()

      newBuff

    }

  }

}
