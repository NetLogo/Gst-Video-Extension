package org.nlogo.extensions.gstvideo

import collection.mutable.MutableList
import org.gstreamer.{ Bin, Element, GhostPad, Pad }

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 9/24/12
 * Time: 1:03 PM
 */

class SinkBinManager(rawElements: Seq[Element], padName: String = "sink_pad") {

  val sinkBin = {
    val bin = new Bin
    bin.addMany(rawElements: _*)
    Element.linkMany(rawElements: _*)
    bin.addPad(new GhostPad(padName, rawElements.head.getSinkPads.get(0)))
    bin
  }

  private val elements = MutableList(rawElements: _*) // Gross....  GStreamer is slowly making me a terrible person --JAB

  def dispose() {
    sinkBin.dispose()
  }

  // Based on procedure described here: http://cgit.freedesktop.org/gstreamer/gstreamer/tree/docs/design/part-block.txt
  def replaceElementByName(name: String, replacement: Element) {

    def getElementsAtAndAroundIndex(index: Int, list: Seq[Element]) : (Option[Element], Element, Option[Element]) = {
      // Don't even THINK about trying to elegantly put all this inside the pattern match; _both_ start/end checks _need_ to occur!
      val noPre  = index == 0
      val noPost = index == (list.length - 1)
      index match {
        case x if (x < 0 || x >= list.length) =>
          throw new IndexOutOfBoundsException("Invalid index for list of length %d: %d".format(list.length, x))
        case x =>
          (if (noPre) None else Option(list(x - 1)), list(x), if (noPost) None else Option(list(x + 1)))
      }
    }

    val index = elements.indexWhere(_.getName == name)
    val (preOpt, item, postOpt) = getElementsAtAndAroundIndex(index, elements)
    sinkBin.remove(item)
    sinkBin.add(replacement)
    elements.update(index, replacement)

    /*

    Umm... so... this thing is kind of stupid.

    The elements need to be blocked/unblocked asynchronously, no matter what
    Also, I need the element-replacement code to run whether or not the pipeline is streaming
    If it _is_ streaming, these callbacks get called as soon as streaming is put on hold
    HOWEVER, if it _isn't_ streaming, the callbacks never get called at all (which is just brilliant, let me tell you...)

    As a result, the callbacks--while they would be _nice_ to use, since they'd ensure that I'm not tinkering with a live stream--
    are entirely useless, because they can't guarantee that my code gets run--unless I want to risk run it twice most of the time

    This would be a lot easier/nicer if the library just did the logical thing and called the callbacks regardless of
    whether or not the pipeline is streaming, but... nope!

    */
    val dummyCallback = new org.gstreamer.lowlevel.GstPadAPI.PadBlockCallback {
      override def callback(p1: Pad, p2: Boolean, p3: com.sun.jna.Pointer) {}
    }

    import collection.JavaConverters.collectionAsScalaIterableConverter
    val preSrcs = preOpt map (_.getSrcPads.asScala) getOrElse item.getSrcPads.asScala
    preSrcs foreach (_.setBlockedAsync(true, dummyCallback))

    val originalState = item.getState
    preOpt  foreach (_.unlink(item))
    postOpt foreach (item.unlink(_))
    item.stop()

    postOpt foreach (replacement.link(_))
    preOpt foreach (_.link(replacement))
    replacement.setState(originalState)

    preSrcs foreach (_.setBlockedAsync(false, dummyCallback))

  }

}

