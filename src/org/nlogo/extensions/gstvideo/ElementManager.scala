package org.nlogo.extensions.gstvideo

import org.nlogo.api.ExtensionException

import org.gstreamer.{ Caps, Element, ElementFactory, elements }
import elements.{ AppSink, AppSrc, BaseSrc, BaseTransform, PlayBin2 }

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/26/12
 * Time: 3:57 PM
 */

// Wrapping over `ElementFactory` in an attempt to make it more typesafe and less aesthetically-hideous
// Refine return types as you see fit
object ElementManager {

  def generateAppSrc               = generate[AppSrc]       ("appsrc",           "src")
  def generateAppSink              = generate[AppSink]      ("appsink",          "sink")
  def generateBalancer             = generate[BaseTransform]("videobalance",     "balance")
  def generateColorspaceConverter  = generate[BaseTransform]("ffmpegcolorspace", "colorspace-converter")
  def generateFreezer              = generate[Element]      ("imagefreeze",      "freeze")
  def generateScaler               = generate[BaseTransform]("videoscale",       "scale")
  def generateVideoFilter          = generate[BaseTransform]("capsfilter",       "video-filter")
  def generateVideoRate            = generate[Element]      ("videorate",        "video-rate")
  def generateWebcamSource         = generate[BaseSrc]      (Webcam.pluginName,  "capture")

  def generatePlayBin(name: String) = new PlayBin2(name)

  def generateSizeFilter(width: Int, height: Int) : BaseTransform = {
    val sizeFilter = generate[BaseTransform]("capsfilter", "size-filter")
    sizeFilter.setCaps(new Caps("video/x-raw-rgb, width=%d, height=%d".format(width, height)))
    sizeFilter
  }

  def generateRateFilter(fps: Int) : BaseTransform = {
    val rateFilter = generate[BaseTransform]("capsfilter", "rate-filter")
    rateFilter.setCaps(new Caps("video/x-raw-rgb, framerate=%d/1".format(fps)))
    rateFilter
  }

  // Type erasuuuure!!!  Thou hast smitest me yet again, this fine September eve!
  // Willest thou ne'er cease to shun thine own from attaining code as holy as he who sittest on high?
  // WILLEST THOU?!  --JAB
  def generate[T <: Element : Manifest](typeName: String, elemName: String = null) : T = {
    ElementFactory.make(typeName, elemName) match {
      case x if (x.getClass == implicitly[Manifest[T]].erasure) =>
        x.asInstanceOf[T]
      case other =>
        throw new ExtensionException("Unable to generate element that matches type parameter T; " +
                                     "created one with type %s, instead.".format(other.getClass.getName))
    }
  }

}
