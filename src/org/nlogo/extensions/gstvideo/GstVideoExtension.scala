package org.nlogo.extensions.gstvideo

import java.util.ArrayList
import org.nlogo.api.{ DefaultClassManager, ExtensionManager, PrimitiveManager }
import processing.video.Video

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
    primitiveManager.addPrimitive("camera-start-fullscreen",    Camera.StartFullscreen)
    primitiveManager.addPrimitive("camera-stop-fullscreen",     Camera.StopFullscreen)
    primitiveManager.addPrimitive("movie-open",                 Movie.OpenMovie)
    primitiveManager.addPrimitive("movie-start",                Movie.StartMovie)
    primitiveManager.addPrimitive("movie-stop",                 Movie.StopMovie)
    primitiveManager.addPrimitive("movie-close",                Movie.CloseMovie)
    primitiveManager.addPrimitive("movie-open-player",          Movie.OpenPlayer)
    primitiveManager.addPrimitive("movie-image",                Movie.Image)
    primitiveManager.addPrimitive("movie-playing?",             Movie.IsPlaying)
    primitiveManager.addPrimitive("movie-set-time-secs",        Movie.SetTimeSeconds)
    primitiveManager.addPrimitive("movie-set-time-millisecs",   Movie.SetTimeMilliseconds)
    primitiveManager.addPrimitive("movie-time-secs",            Movie.CurrentTimeSeconds)
    primitiveManager.addPrimitive("movie-time-millisecs",       Movie.CurrentTimeMilliseconds)
    primitiveManager.addPrimitive("movie-duration-secs",        Movie.MovieDurationSeconds)
    primitiveManager.addPrimitive("movie-duration-millisecs",   Movie.MovieDurationMilliseconds)
    primitiveManager.addPrimitive("movie-set-hue",              Movie.SetHue)
    primitiveManager.addPrimitive("movie-set-saturation",       Movie.SetSaturation)
    primitiveManager.addPrimitive("movie-set-brightness",       Movie.SetBrightness)
    primitiveManager.addPrimitive("movie-set-contrast",         Movie.SetContrast)
    primitiveManager.addPrimitive("movie-start-looping",        Movie.StartLooping)
    primitiveManager.addPrimitive("movie-stop-looping",         Movie.StopLooping)
    primitiveManager.addPrimitive("movie-start-fullscreen",     Movie.StartFullscreen)
    primitiveManager.addPrimitive("movie-stop-fullscreen",      Movie.StopFullscreen)
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