package org.nlogo.extensions.gstvideo

import org.nlogo.api.ExtensionException

/**
 * Created by IntelliJ IDEA.
 * User: Jason
 * Date: 9/28/12
 * Time: 2:02 PM
 */

/*****************************
 *             OS            *
 *****************************/

sealed abstract class OS(identifierRegex: String) {
  private val idRegex = ("(?i)" + identifierRegex).r // The "(?i)" makes this regex case-insensitive
  def matchesOn(input: String) = idRegex.pattern.matcher(input).matches()
  def fileProtocol = "file://"
}

object OS {
  private val osList = List(Windows, Mac, Linux)
  lazy val id : OS = Option(System.getProperty("os.name")) flatMap (name => osList find (_.matchesOn(name))) getOrElse Other
}

case object Windows extends OS(".*win.*") { override def fileProtocol = "file:///"  /* Yep!  --JAB */ }
case object Mac     extends OS(".*mac.*")
case object Linux   extends OS(".*(nix|nux).*")
case object Other   extends OS(".*")


/*****************************
 *            ARCH           *
 *****************************/

sealed trait Arch

object Arch {
  lazy val id : Arch = { // Referentially transparent: No.  Only needs to be computed once per session: Yes. --JAB
    val arches = Option(System.getProperty("os.arch")) ++ Option(System.getenv.get("PROCESSOR_ARCHITEW6432"))
    if (arches exists (_.endsWith("64"))) Arch64 else Arch32
  }
}

case object Arch32 extends Arch
case object Arch64 extends Arch


/*****************************
 *           WebCam          *
 *****************************/

object Webcam {

  val os   = OS.id
  val arch = Arch.id

  // Inspiration taken from Andres Colubri's GSVideo (http://gsvideo.svn.sourceforge.net/viewvc/gsvideo/trunk/src/codeanticode/gsvideo/GSCapture.java?revision=251&view=markup)
  lazy val pluginName = (os, arch) match {
    case (Windows, _)  => "ksvideosrc"
    case (Mac, Arch64) => "qtkitvideosrc"
    case (Mac, Arch32) => "osxvideosrc"
    case (Linux, _)    => "v4l2src"
    case _ =>
      val osName = Option(System.getProperty("os.name")) getOrElse "<empty/unknown>"
      throw new ExtensionException("""Webcam usage is not supported on your system.
                                     |Your operating system name is "%s", but Mac OS, Windows, Linux, or Unix required""".format(osName))
  }

}

