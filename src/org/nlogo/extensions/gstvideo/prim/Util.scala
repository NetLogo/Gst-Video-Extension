package org.nlogo.extensions.gstvideo.prim

object Util {

  private val fileExtToMuxerNameMap = Map(
    "3gp" -> "gppmux",
    "avi" -> "avimux",
    "flv" -> "flvmux",
    "mj2" -> "mj2mux",
    "mkv" -> "matroskamux",
    "mov" -> "qtmux",
    "mp4" -> "mp4mux",
    "mpg" -> "ffmux_mpeg",
    "ogg" -> "oggmux"
  )

  def determineMuxer(filename: String) : Option[String] = {
    val fileExt = filename.toLowerCase.reverse.takeWhile(_ != '.').reverse
    fileExtToMuxerNameMap.get(fileExt)
  }

}
