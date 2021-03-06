/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.video;

import org.gstreamer.Gst;
import org.gstreamer.Plugin;
import org.gstreamer.Registry;

import java.io.File;
import java.util.List;

// TODO:
// http://forum.processing.org/topic/gsoc-2012-processing-video#25080000001473557

/**
 * This class contains some basic functions used by the rest of the classes in
 * this library.
 */
public class Video {

  protected enum Platform {
    MACOSX, WINDOWS, LINUX, OTHER
  }

  // Streams type constants.
  static public final int AUDIO = 0;
  static public final int VIDEO = 1;
  static public final int RAW   = 2;

  // Priority is given to the system install of GStreamer if this is set to true. 
  public static boolean systemGStreamer = false;  
  public static String systemGStreamerPath;
  public static String systemPluginsFolder = "gstreamer-0.10";
  public static Platform platform;


  // Default locations of the global install of gstreamer for each platform:
  static {

    String osname = System.getProperty("os.name");

    if (osname.contains("Mac")) {
      platform = Platform.MACOSX;
    } else if (osname.contains("Windows")) {
      platform = Platform.WINDOWS;
    } else if (osname.equals("Linux")) {  // true for the ibm vm
      platform = Platform.LINUX;
    } else {}

    switch (platform) {
      case MACOSX:
        systemGStreamerPath = "/System/Library/Frameworks/GStreamer.framework/Versions/Current/lib";
        break;
      case WINDOWS:
        systemGStreamerPath = "";
        break;
      case LINUX:
        systemGStreamerPath = "/usr/lib";
        break;
    }

  }

  // Default location of the local install of gstreamer. Suggested by Charles Bourasseau. 
  // When it is left as empty string, GSVideo will attempt to use the path from GSLibraryPath.get(),
  // otherwise it will use it as the path to the folder where the libgstreamer.dylib and other 
  // files are located.  
  public static String localGStreamerPath = "";
  public static String localPluginsFolder = "plugins";
      
  // Direct buffer pass enabled by default. With this mode enabled, no new buffers are created
  // and disposed by the GC in each frame (thanks to Octavi Estape for suggesting this improvement)
  // which should help performance in most situations.
  public static boolean passDirectBuffer = true; 

  // OpenGL texture used as buffer sink by default, when the renderer is GL-based. This 
  // can improve performance significantly, since the video frames are automatically
  // copied into the texture without passing through the pixels arrays, as well as 
  // having the color conversion into RGBA handled natively by gstreamer.
  public static boolean useGLBufferSink = false;  
  
  // Path that the video library will use to load the gstreamer native libs from.
  // It is buit either from the system or local paths.
  protected static String gstreamerBinPath = "";
  protected static String gstreamerPluginsPath = "";

  protected static boolean defaultGLibContext = false;
  
  protected static long INSTANCES_COUNT = 0;
  
  protected static int bitsJVM;
  static {
    bitsJVM = Integer.parseInt(System.getProperty("sun.arch.data.model"));
  }  
  
  static public void init(String[] args) {
    if (INSTANCES_COUNT == 0) {
      initImpl(args);
    }
    INSTANCES_COUNT++;
  }
  
  static public void restart() {
    removePlugins();
    Gst.deinit();
    initImpl(new String[0]);
  }
    
  static protected void initImpl(String[] args) {

    switch (platform) {
      case MACOSX:
        setMacOSXPath();
        break;
      case WINDOWS:
        setWindowsPath();
        break;
      case LINUX:
        // Linux only supports global gstreamer for now.
        systemGStreamer = true;
        setLinuxPath();
        break;
    }

    if (!gstreamerBinPath.equals("")) {
      System.setProperty("jna.library.path", gstreamerBinPath);
    }

    if ((platform == Platform.LINUX) && !systemGStreamer) {
      System.err.println("Loading local version of GStreamer not supported in Linux at this time.");
    }

    if ((platform == Platform.WINDOWS) && !systemGStreamer) {
      LibraryLoader loader = LibraryLoader.getInstance();
      if (loader == null) {
        System.err.println("Cannot load local version of GStreamer libraries.");
      }
    }

    if ((platform == Platform.MACOSX) && !systemGStreamer) {
      // Nothing to do here, since the dylib mechanism in OSX doesn't require the
      // library loader.      
    }    
    
    Gst.setUseDefaultContext(defaultGLibContext);
    Gst.init("NetLogo Video Extension", args);

    addPlugins();
  }

  static protected void addPlugins() {
    if (!gstreamerPluginsPath.equals("")) {
      Registry reg = Registry.getDefault();
      boolean res;
      res = reg.scanPath(gstreamerPluginsPath);
      if (!res) {
        System.err.println("Cannot load GStreamer plugins from " + gstreamerPluginsPath);
      }
    }       
  }
  
  static protected void removePlugins() {
    Registry reg = Registry.getDefault();
    List<Plugin> list = reg.getPluginList();
    for (Plugin plg : list) {
      reg.removePlugin(plg);
    }    
  }
  
  static protected void setLinuxPath() {
    if (systemGStreamer && lookForGlobalGStreamer()) {
      gstreamerBinPath = "";
      gstreamerPluginsPath = "";
    } else {
      systemGStreamer = false;
      if (localGStreamerPath.equals("")) {
        LibraryPath libPath = new LibraryPath();
        String path = libPath.get();
        gstreamerBinPath = buildGStreamerBinPath(path, "/linux" + bitsJVM);
        gstreamerPluginsPath = gstreamerBinPath + "/" + localPluginsFolder;        
      } else {
        gstreamerBinPath = localGStreamerPath;
        gstreamerPluginsPath = localGStreamerPath + "/" + localPluginsFolder;
      }       
    }
  }

  static protected void setWindowsPath() {
    if (systemGStreamer && lookForGlobalGStreamer()) {
      gstreamerBinPath = "";
      gstreamerPluginsPath = "";
    } else {
      systemGStreamer = false;
      if (localGStreamerPath.equals("")) {
        LibraryPath libPath = new LibraryPath();
        String path = libPath.get();
        gstreamerBinPath = buildGStreamerBinPath(path, "\\windows" + bitsJVM);
        gstreamerPluginsPath = gstreamerBinPath + "\\" + localPluginsFolder;
      } else {
        gstreamerBinPath = localGStreamerPath;
        gstreamerPluginsPath = localGStreamerPath + "\\" + localPluginsFolder;
      }    
    }
  }

  static protected void setMacOSXPath() {
    if (systemGStreamer && lookForGlobalGStreamer()) {
      gstreamerBinPath = systemGStreamerPath;
      gstreamerPluginsPath = systemGStreamerPath + "/" + systemPluginsFolder;
    } else {
      systemGStreamer = false;  
      if (localGStreamerPath.equals("")) {
        LibraryPath libPath = new LibraryPath();
        String path = libPath.get();        
        gstreamerBinPath = buildGStreamerBinPath(path, "/macosx" + bitsJVM);
        gstreamerPluginsPath = gstreamerBinPath + "/" + localPluginsFolder;
      } else {
        gstreamerBinPath = localGStreamerPath;
        gstreamerPluginsPath = localGStreamerPath + "/" + localPluginsFolder;
      }
    }
  }

  static protected boolean lookForGlobalGStreamer() {    
    LibraryPath libPath = new LibraryPath();
    String locPath = libPath.get();
    locPath = locPath.replace("/", System.getProperty("file.separator"));
    
    String[] searchPaths = null;
    if (!systemGStreamerPath.equals("")) {
      searchPaths = new String[] {systemGStreamerPath};
    }
    
    if (searchPaths == null) {
      String lpaths = System.getProperty("java.library.path");
      String pathsep = System.getProperty("path.separator");    
      searchPaths = lpaths.split(pathsep);
    }
    
    for (int i = 0; i < searchPaths.length; i++) {      
      String path = searchPaths[i];
      if ((locPath.equals("") || path.indexOf(locPath) == -1) && libgstreamerPresent(path, "libgstreamer")) {
        systemGStreamerPath = path;
        return true;
      }      
    }
    return false;
  }
  
  static protected boolean libgstreamerPresent(String dir, String file) {
    File libPath = new File(dir);
    String[] files = libPath.list();
    if (files != null) {
      for (int i = 0; i < files.length; i++) {
        if (-1 < files[i].indexOf(file)) {
          return true;
        }
      }
    }
    return false;
  }
  
  static protected String buildGStreamerBinPath(String base, String os) {        
    File path = new File(base + os);
    if (path.exists()) {
      return base + os;	
    } else {
    	
      return base;	
    }
  }
  
  static protected float nanoSecToSecFrac(float nanosec) {
    for (int i = 0; i < 3; i++)
      nanosec /= 1E3;
    return nanosec;
  }

  static protected long secToNanoLong(float sec) {
    Float f = new Float(sec * 1E9);
    return f.longValue();
  }
}
