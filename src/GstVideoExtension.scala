package org.nlogo.extensions.gstvideo

import java.util.ArrayList
import processing.video.Video
import org.nlogo.api.{ DefaultClassManager, ExtensionManager, PrimitiveManager }
import prim.{ Camera, Movie }

class GstVideoExtension extends DefaultClassManager {

  override def runOnce(em: ExtensionManager) {
    // Video.init(Array("--gst-debug-level=2"))
    Video.init(Array(""))
  }

  override def load(primitiveManager: PrimitiveManager) {
    primitiveManager.addPrimitive("camera-init",                Camera.InitCamera)
    primitiveManager.addPrimitive("camera-start",               Camera.StartCamera)
    primitiveManager.addPrimitive("camera-stop",                Camera.StopCamera)
    primitiveManager.addPrimitive("camera-start-recording",     Camera.StartRecording)
    primitiveManager.addPrimitive("camera-stop-recording",      Camera.StopRecording)
    primitiveManager.addPrimitive("camera-image",               Camera.Image)
    primitiveManager.addPrimitive("camera-is-rolling?",         Camera.IsRolling)
    primitiveManager.addPrimitive("camera-set-hue",             Camera.SetHue)
    primitiveManager.addPrimitive("camera-set-saturation",      Camera.SetSaturation)
    primitiveManager.addPrimitive("camera-set-brightness",      Camera.SetBrightness)
    primitiveManager.addPrimitive("camera-set-contrast",        Camera.SetContrast)
    primitiveManager.addPrimitive("camera-keep-aspect-ratio",   Camera.KeepAspect)
    primitiveManager.addPrimitive("camera-ignore-aspect-ratio", Camera.IgnoreAspect)
    primitiveManager.addPrimitive("movie-open",                 Movie.OpenMovie)
    primitiveManager.addPrimitive("movie-start",                Movie.StartMovie)
    primitiveManager.addPrimitive("movie-stop",                 Movie.StopMovie)
    primitiveManager.addPrimitive("movie-open-player",          Movie.OpenPlayer)
    primitiveManager.addPrimitive("movie-close-player",         Movie.ClosePlayer)
    primitiveManager.addPrimitive("movie-image",                Movie.Image)
    primitiveManager.addPrimitive("movie-playing?",             Movie.IsPlaying)
    primitiveManager.addPrimitive("movie-set-time-secs",        Movie.SetTimeSeconds)
    primitiveManager.addPrimitive("movie-set-time-ms",          Movie.SetTimeMilliseconds)
    primitiveManager.addPrimitive("movie-get-time-secs",        Movie.CurrentTimeSeconds)
    primitiveManager.addPrimitive("movie-get-time-ms",          Movie.CurrentTimeMilliseconds)
    primitiveManager.addPrimitive("movie-get-length-secs",      Movie.MovieDurationSeconds)
    primitiveManager.addPrimitive("movie-get-length-ms",        Movie.MovieDurationMilliseconds)
    primitiveManager.addPrimitive("movie-set-hue",              Movie.SetHue)
    primitiveManager.addPrimitive("movie-set-saturation",       Movie.SetSaturation)
    primitiveManager.addPrimitive("movie-set-brightness",       Movie.SetBrightness)
    primitiveManager.addPrimitive("movie-set-contrast",         Movie.SetContrast)
    primitiveManager.addPrimitive("movie-start-looping",        Movie.StartLooping)
    primitiveManager.addPrimitive("movie-stop-looping",         Movie.StopLooping)
    primitiveManager.addPrimitive("movie-keep-aspect-ratio",    Movie.KeepAspect)
    primitiveManager.addPrimitive("movie-ignore-aspect-ratio",  Movie.IgnoreAspect)
  }

  override def unload(em: ExtensionManager) {

    try Movie.unload()
    catch {
      case e: NoClassDefFoundError => println("Movie wasn't loaded for some reason")
    }

    try Camera.unload()
    catch {
      case e: NoClassDefFoundError => println("Camera wasn't loaded for some reason")
    }

  }

  override def additionalJars = new ArrayList[String]()

}
