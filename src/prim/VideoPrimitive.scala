package org.nlogo.extensions.gstvideo.prim

import org.nlogo.api.{ DefaultCommand, DefaultReporter, Primitive }

sealed trait VideoPrimitive {
  self: Primitive =>
    override def getAgentClassString = "O"
}

abstract class VideoCommand  extends DefaultCommand  with VideoPrimitive
abstract class VideoReporter extends DefaultReporter with VideoPrimitive
