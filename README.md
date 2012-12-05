#Project information:
This project is an alternative video extension for NetLogo.  It utilizes the open source video-processing framework GStreamer for smooth, cross-platform distribution.

__Version:__ 1.0<br>
__Supported operating systems:__ Mac OS X 10.7+, Windows 7<br>
__Supported video formats:__ avi, mp4, mov, flv, ogg<br>
<b>Unsupported video formats (for now):</b> wmv, mkv

#Installation from Zip File Download
* Download the archive that corresponds to your operating
* Unzip it to the 'extensions' folder of the NetLogo installation that you want to use it with
* Ensure that the resultant folder is named "gst-video"–<b>not</b> "gst-video-mac" or "gst-video-windows"
* You should now be all set.  Please feel free to play with the extension by using the 'gst-video-test.nlogo' model file in 'extensions/gst-video/models' folder

#Installation from Source:
* Navigate to the directory of your NetLogo installation's 'extensions' folder
  * (varies, i.e. `cd /Applications/NetLogo\ 5.0.2/extensions`)
* Download the source into the 'extensions' folder of an existing NetLogo installation, and ensure that the folder is named "gst-video" 
  * `git clone https://github.com/NetLogo/Gst-Video-Extension gst-video`
* Enter the directory for the extension
  * `cd gst-video`
* Run the Makefile
  * `make`
  * If you are told "make: ../../bin/scalac: No such file or directory", you need to set up your `SCALA_HOME` variable.
    * If you installed Scala through Homebrew, you can set up this variable by running `export SCALA_HOME=/usr/local/Cellar/scala/<x>/`, where `<x>` is the version number of your Scala installation (likely "2.9.1" or "2.9.2")
* You should now be all set.  Please feel free to play with the extension by using the 'gst-video-test.nlogo' model file in 'extensions/gst-video/models' folder

