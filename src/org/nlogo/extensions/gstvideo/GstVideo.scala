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
    primitiveManager.addPrimitive("camera-image",                   Capture.image)
    primitiveManager.addPrimitive("camera-start",               new Capture.StartCamera)
    primitiveManager.addPrimitive("camera-stop",                new Capture.StopCamera)
    primitiveManager.addPrimitive("camera-is-rolling?",         new Capture.IsRolling)
    primitiveManager.addPrimitive("camera-select",              new Capture.SelectCamera)
    primitiveManager.addPrimitive("camera-set-stretches",       new Capture.SetStrechToFillScreen)
    primitiveManager.addPrimitive("camera-set-contrast",        new Capture.SetContrast)
    primitiveManager.addPrimitive("camera-set-brightness",      new Capture.SetBrightness)
    primitiveManager.addPrimitive("camera-set-hue",             new Capture.SetHue)
    primitiveManager.addPrimitive("camera-set-saturation",      new Capture.SetSaturation)
    primitiveManager.addPrimitive("camera-start-recording",     new Capture.StartRecording)
    primitiveManager.addPrimitive("camera-stop-recording",      new Capture.StopRecording)
    primitiveManager.addPrimitive("movie-image",                    Movie.image)
    primitiveManager.addPrimitive("movie-open",                 new Movie.OpenMovie)
    primitiveManager.addPrimitive("movie-start",                new Movie.StartMovie)
    primitiveManager.addPrimitive("movie-stop",                 new Movie.StopMovie)
    primitiveManager.addPrimitive("movie-open-player",          new Movie.OpenPlayer)
    primitiveManager.addPrimitive("movie-close",                new Movie.CloseMovie)
    primitiveManager.addPrimitive("movie-set-time-secs",        new Movie.SetTimeSeconds)
    primitiveManager.addPrimitive("movie-set-time-millisecs",   new Movie.SetTimeMilliseconds)
    primitiveManager.addPrimitive("movie-duration-secs",        new Movie.MovieDurationSeconds)
    primitiveManager.addPrimitive("movie-duration-millisecs",   new Movie.MovieDurationMilliseconds)
    primitiveManager.addPrimitive("movie-time-secs",            new Movie.CurrentTimeSeconds)
    primitiveManager.addPrimitive("movie-time-millisecs",       new Movie.CurrentTimeMilliseconds)
    primitiveManager.addPrimitive("movie-playing?",             new Movie.IsPlaying)
    primitiveManager.addPrimitive("movie-set-stretches",        new Movie.SetStrechToFillScreen)
    primitiveManager.addPrimitive("movie-set-contrast",         new Movie.SetContrast)
    primitiveManager.addPrimitive("movie-set-brightness",       new Movie.SetBrightness)
    primitiveManager.addPrimitive("movie-set-hue",              new Movie.SetHue)
    primitiveManager.addPrimitive("movie-set-saturation",       new Movie.SetSaturation)
    primitiveManager.addPrimitive("movie-set-frame-cache-size", new Movie.SetFrameCacheSize)
    primitiveManager.addPrimitive("movie-set-looping",          new Movie.SetLooping)
    primitiveManager.addPrimitive("movie-debug",                new Movie.DebugCommand)
  }

  override def unload(em: ExtensionManager) {

    try Movie.unload()
    catch {
      case e: NoClassDefFoundError =>
        println("Movie wasn't loaded for some reason")
    }

    try Capture.unload()
    catch {
      case e: NoClassDefFoundError =>
        println("Capture wasn't loaded for some reason")
    }

  }

  override def additionalJars = new ArrayList[String]()

}