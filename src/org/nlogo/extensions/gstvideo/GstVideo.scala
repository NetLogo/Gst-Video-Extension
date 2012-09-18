package org.nlogo.extensions.gstvideo

import java.util.ArrayList
import org.nlogo.api.{ DefaultClassManager, ExtensionManager, PrimitiveManager }
import processing.video.Video

class GstVideo extends DefaultClassManager {

  override def runOnce(em: ExtensionManager) {

   	// val args = Array("--gst-debug-level=2")
    val args = Array("")
    Video.init(args)

    //@ LIES!
    // first check that we can find the quicktime jar
		// if we can't it throws an extension exception ev 3/3/09
	  //	em.addToLibraryPath(this, "lib");
    em.getFile("gst-video/gstreamer-java-1.5.jar")
    em.getFile("gst-video/jna.jar")

  }

  override def load(primitiveManager: PrimitiveManager) {
    primitiveManager.addPrimitive("camera-image",               Capture.image)
    primitiveManager.addPrimitive("camera-start",               Capture.StartCamera)
    primitiveManager.addPrimitive("camera-stop",                Capture.StopCamera)
    primitiveManager.addPrimitive("camera-is-rolling?",         Capture.IsRolling)
    primitiveManager.addPrimitive("camera-init",                Capture.InitCamera)
    primitiveManager.addPrimitive("camera-start-fullscreen",    Capture.StartFullscreen)
    primitiveManager.addPrimitive("camera-stop-fullscreen",     Capture.StopFullscreen)
    primitiveManager.addPrimitive("camera-set-contrast",        Capture.SetContrast)
    primitiveManager.addPrimitive("camera-set-brightness",      Capture.SetBrightness)
    primitiveManager.addPrimitive("camera-set-hue",             Capture.SetHue)
    primitiveManager.addPrimitive("camera-set-saturation",      Capture.SetSaturation)
    primitiveManager.addPrimitive("camera-start-recording",     Capture.StartRecording)
    primitiveManager.addPrimitive("camera-stop-recording",      Capture.StopRecording)
    primitiveManager.addPrimitive("movie-image",                Movie.image)
    primitiveManager.addPrimitive("movie-open",                 Movie.OpenMovie)
    primitiveManager.addPrimitive("movie-start",                Movie.StartMovie)
    primitiveManager.addPrimitive("movie-stop",                 Movie.StopMovie)
    primitiveManager.addPrimitive("movie-open-player",          Movie.OpenPlayer)
    primitiveManager.addPrimitive("movie-close",                Movie.CloseMovie)
    primitiveManager.addPrimitive("movie-set-time-secs",        Movie.SetTimeSeconds)
    primitiveManager.addPrimitive("movie-set-time-millisecs",   Movie.SetTimeMilliseconds)
    primitiveManager.addPrimitive("movie-duration-secs",        Movie.MovieDurationSeconds)
    primitiveManager.addPrimitive("movie-duration-millisecs",   Movie.MovieDurationMilliseconds)
    primitiveManager.addPrimitive("movie-time-secs",            Movie.CurrentTimeSeconds)
    primitiveManager.addPrimitive("movie-time-millisecs",       Movie.CurrentTimeMilliseconds)
    primitiveManager.addPrimitive("movie-playing?",             Movie.IsPlaying)
    primitiveManager.addPrimitive("movie-start-fullscreen",     Movie.StartFullscreen)
    primitiveManager.addPrimitive("movie-stop-fullscreen",      Movie.StopFullscreen)
    primitiveManager.addPrimitive("movie-set-contrast",         Movie.SetContrast)
    primitiveManager.addPrimitive("movie-set-brightness",       Movie.SetBrightness)
    primitiveManager.addPrimitive("movie-set-hue",              Movie.SetHue)
    primitiveManager.addPrimitive("movie-set-saturation",       Movie.SetSaturation)
    primitiveManager.addPrimitive("movie-start-looping",        Movie.StartLooping)
    primitiveManager.addPrimitive("movie-stop-looping",         Movie.StopLooping)
  }

  override def unload(em: ExtensionManager) {

    try Movie.unload()
    catch {
      case e: NoClassDefFoundError => println("Movie wasn't loaded for some reason")
    }

    try Capture.unload()
    catch {
      case e: NoClassDefFoundError => println("Capture wasn't loaded for some reason")
    }

  }

  override def additionalJars = new ArrayList[String]()

}