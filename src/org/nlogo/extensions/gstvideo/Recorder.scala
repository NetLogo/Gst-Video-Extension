package org.nlogo.extensions.gstvideo

import java.io.File
import org.gstreamer.{ Buffer, elements }, elements.RGBDataFileSink

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/18/12
 * Time: 12:44 PM
 */

// A wrapper around a recording sink
class Recorder {

  private var isRecording                      = false
  private var sinkOpt: Option[RGBDataFileSink] = None

  def dispose() {
    sinkOpt foreach (_.dispose())
  }

  def push(buffer: Buffer) {
    if (isRecording) sinkOpt foreach (_.pushRGBFrame(buffer))
    else             buffer.dispose()
  }

  def start() {
    sinkOpt foreach { sink => sink.start(); isRecording = true }
  }

  def stop() {
    sinkOpt foreach { sink => sink.stop(); isRecording = false }
  }

  def reconstructSink(name: String, width: Int, height: Int, fps: Int,
                      encoderStr: String, encoderPropNames: Array[String],
                      encoderPropData: Array[AnyRef], muxerStr: String, file: File) {
    val sink = new RGBDataFileSink("Recorder", width, height, fps, encoderStr, encoderPropNames, encoderPropData, muxerStr, file)
    sink.setPreQueueSize(0)
    sink.setSrcQueueSize(60)
    sinkOpt = Option(sink)
  }

}
