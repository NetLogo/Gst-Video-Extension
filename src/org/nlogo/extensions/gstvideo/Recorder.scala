package org.nlogo.extensions.gstvideo

import java.io.File
import org.gstreamer.{ Buffer, elements }, elements.RGBDataFileSink

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/18/12
 * Time: 12:44 PM
 */

// A wrapper around a sink
class Recorder(name: String, width: Int, height: Int, fps: Int, encoderStr: String,
               encoderPropNames: Array[String], encoderPropData: Array[AnyRef],
               muxerStr: String, file: File) {

  private var isRecording = false  //@ This variable may well be replacable by `sink.isPlaying`; investigate
  private val sink        = new RGBDataFileSink("Recorder", width, height, fps, encoderStr,
                                                encoderPropNames, encoderPropData, muxerStr, file)

  sink.setPreQueueSize(0)
  sink.setSrcQueueSize(60)

  def dispose() {
    sink.dispose()
  }

  def push(buffer: Buffer) {
    if (isRecording) sink.pushRGBFrame(buffer)
    else             buffer.dispose()
  }

  def start() {
    sink.start()
    isRecording = true
  }

  def stop() {
    sink.stop()
    isRecording = false
  }

}
