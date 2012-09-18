package org.nlogo.extensions.gstvideo

/**
 *
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/17/12
 * Time: 5:17 PM
 */

trait Quality

object Quality {
  object Worst  extends Quality
  object Low    extends Quality
  object Medium extends Quality
  object High   extends Quality
  object Best   extends Quality
}

trait Codec {
  protected def quality: Quality
  def getProps : (Array[String], Array[AnyRef], String)
}

object Codec {

  import Quality._

  class Theora(override protected val quality: Quality) extends Codec {
    override def getProps : (Array[String], Array[AnyRef], String) = {
      val q = Int.box(quality match {
        case Worst  => 0
        case Low    => 15
        case Medium => 31
        case High   => 47
        case Best   => 63
      })
      (Array("quality"), Array(q), "theoraenc")
    }
  }

  // TODO: set Properties of xvidenc. (Not my note, but probably also goes for MJPEG2K --JAB 9/17/12)
  class XVid(override protected val quality: Quality) extends Codec {
    override def getProps : (Array[String], Array[AnyRef], String) = (Array(), Array(), "xvidenc")
  }

  class X264(override protected val quality: Quality) extends Codec {
    override def getProps : (Array[String], Array[AnyRef], String) = {
      // The pass property can take the following values:
      // (0): cbr              - Constant Bitrate Encoding (default)
      // (4): quant            - Constant Quantizer
      // (5): qual             - Constant Quality
      // (17): pass1            - VBR Encoding - Pass 1
      // (18): pass2            - VBR Encoding - Pass 2
      // (19): pass3            - VBR Encoding - Pass 3
      val pass = Int.box(5)
      val q = Int.box(quality match {
        case Worst  => 50
        case Low    => 35
        case Medium => 21
        case High   => 15
        case Best   => 1
      })
      (Array("pass", "quantizer"), Array(pass, q), "x264enc")
    }
  }

  class Dirac(override protected val quality: Quality) extends Codec {
    override def getProps : (Array[String], Array[AnyRef], String) = {
      val q = Double.box(quality match {
        case Worst  => 0.0
        case Low    => 2.5
        case Medium => 5.0
        case High   => 7.5
        case Best   => 10.0
      })
      (Array("quality"), Array(q), "shroenc")
    }
  }

  class MJPEG(override protected val quality: Quality) extends Codec {
    override def getProps : (Array[String], Array[AnyRef], String) = {
      val q = Int.box(quality match {
        case Worst  => 0
        case Low    => 30
        case Medium => 50
        case High   => 85
        case Best   => 100
      })
      (Array("quality"), Array(q), "jpegenc")
    }
  }

  class MJPEG2K(override protected val quality: Quality) extends Codec {
    override def getProps : (Array[String], Array[AnyRef], String) = (Array(), Array(), "jp2kenc")
  }

}
