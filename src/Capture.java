// Video recording code from GSVideo (GPL license, below)

/**
 * Part of the GSVideo library: http://gsvideo.sourceforge.net/
 * Copyright (c) 2008-11 Andres Colubri 
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, version 2.1.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General
 * Public License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307  USA
 */

package org.nlogo.extensions.yoshi;

// Extensions API
import org.nlogo.api.DefaultClassManager;
import org.nlogo.api.PrimitiveManager;
import org.nlogo.api.Syntax;
import org.nlogo.api.Context;
import org.nlogo.api.DefaultReporter;
import org.nlogo.api.DefaultCommand;
import org.nlogo.api.Argument;
import org.nlogo.api.ExtensionException;
import org.nlogo.api.LogoException;

// GStreamer
import org.gstreamer.*;
import org.gstreamer.Bus;
import org.gstreamer.Buffer;
import org.gstreamer.lowlevel.*;
import org.gstreamer.elements.*;
import org.gstreamer.interfaces.Property;
import org.gstreamer.interfaces.PropertyProbe;

// Java
import java.nio.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.io.File;
import java.lang.reflect.*;

import java.io.File;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;


public strictfp class Capture {
	
	private static Pipeline cameraPipeline;
	
	// Webcam
	private static Element webcamSource;
	
	// Core pipeline elements
	private static Element scale, balance, conv;
	
	// Appsink
	private static AppSink appSink;
	
	// Edge detection
	private static boolean isDetectingEdges;
	
	private static Element edgeDetectConverter;
	private static Element edgeDetect;
	
	// Debug FPS (not currently in use)
	private static Element fpsCountOverlay;
	
	// Recording
	private static RGBDataFileSink2 recorder;
	private static boolean recording;
	
	private static Fraction framerate;
	
	public static void unload() throws ExtensionException {
		
		// Dispoe of the pipeline
		if (cameraPipeline != null) {
			cameraPipeline.setState(State.NULL);
			cameraPipeline.dispose();
			cameraPipeline = null;
		}
	}
	
	public static class StartRecording extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.StringType(), Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}
		
		/*
		This code is mostly from the opensource video library GSVideo by Andres Colubri
		*/
		
		private static final int THEORA = 0;
		private static final int XVID = 1;
		private static final int X264 = 2;
		private static final int DIRAC = 3;
		private static final int MJPEG = 4;
		private static final int MJPEG2K = 5;
		
		private static final int WORST = 0;
		private static final int LOW = 1;
		private static final int MEDIUM = 2;
		private static final int HIGH = 3;
		private static final int BEST = 4;
	
		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (recording)
				throw new ExtensionException("A recording seems to already be in progress");
			
			double patchSize = context.getAgent().world().patchSize();
			float width = (float) (args[1].getDoubleValue() * patchSize);
			float height = (float) (args[2].getDoubleValue() * patchSize);   
			
			System.out.println("recording-width: " + (int)width);
			System.out.println("recording-height: " + (int)height);
			
			String filename = args[0].getString();
			
			int codecQuality = MEDIUM;
			int codecType = X264;

			String[] propNames = null;
			Object[] propValues = null;

			String encoder = "";
			String muxer = "";

			// Determining container based on the filename extension.
			String fn = filename.toLowerCase(); 
			if (fn.endsWith(".ogg")) {
				muxer = "oggmux";
			} else if (fn.endsWith(".avi")) {
				muxer = "avimux";  
			} else if (fn.endsWith(".mov")) {
				muxer = "qtmux";
			} else if (fn.endsWith(".flv")) {
				muxer = "flvmux";
			} else if (fn.endsWith(".mkv")) {
				muxer = "matroskamux";
			} else if (fn.endsWith(".mp4")) {
				muxer = "mp4mux";
			} else if (fn.endsWith(".3gp")) {
				muxer = "gppmux";
			} else if (fn.endsWith(".mpg")) {
				muxer = "ffmux_mpeg";      
			} else if (fn.endsWith(".mj2")) {
				muxer = "mj2mux";      
			} else {
				throw new ExtensionException("Unrecognized video container");
			}

			// Configuring encoder.
			if (codecType == THEORA) {
				encoder = "theoraenc";

				propNames = new String[1];
				propValues = new Object[1];

				propNames[0] = "quality";
				Integer q = 31;
				if (codecQuality == WORST) {
					q = 0;
				} else if (codecQuality == LOW) {
					q = 15;
				} else if (codecQuality == MEDIUM) {
					q = 31;
				} else if (codecQuality == HIGH) {
					q = 47;
				} else if (codecQuality == BEST) {
					q = 63;
				}
				propValues[0] = q;      
			} else if (codecType == DIRAC) {
				encoder = "schroenc";

				propNames = new String[1];
				propValues = new Object[1];

				propNames[0] = "quality";
				Double q = 5.0d;
				if (codecQuality == WORST) {
					q = 0.0d;
				} else if (codecQuality == LOW) {
					q = 2.5d;
				} else if (codecQuality == MEDIUM) {
					q = 5.0d;
				} else if (codecQuality == HIGH) {
					q = 7.5d;
				} else if (codecQuality == BEST) {
					q = 10.0d;
				}
				
				propValues[0] = q; 
				
			} else if (codecType == XVID) {
				encoder = "xvidenc";

				// TODO: set Properties of xvidenc.
			} else if (codecType == X264) {
				encoder = "x264enc";

				propNames = new String[2];
				propValues = new Object[2];      

				// The pass property can take the following values:
				// (0): cbr              - Constant Bitrate Encoding (default)
				// (4): quant            - Constant Quantizer
				// (5): qual             - Constant Quality
				// (17): pass1            - VBR Encoding - Pass 1
				// (18): pass2            - VBR Encoding - Pass 2
				// (19): pass3            - VBR Encoding - Pass 3
				propNames[0] = "pass";
				Integer p = 5;
				propValues[0] = p;

				// When Constant Quality is specified for pass, then
				// the property quantizer is interpreted as the quality
				// level.
				propNames[1] = "quantizer";
				Integer q = 21;
				if (codecQuality == WORST) {
					q = 50;
				} else if (codecQuality == LOW) {
					q = 35;
				} else if (codecQuality == MEDIUM) {
					q = 21;
				} else if (codecQuality == HIGH) {
					q = 15;
				} else if (codecQuality == BEST) {
					q = 1;
				}
				
				propValues[1] = q;

				// The bitrate can be set with the bitrate property, which is integer and
				// has range: 1 - 102400. Default: 2048 Current: 2048.
				// This probably doesn't have any effect unless we set pass to cbr.
			} else if (codecType == MJPEG) {
				encoder = "jpegenc";

				propNames = new String[1];
				propValues = new Object[1];

				propNames[0] = "quality";
				Integer q = 85;
				if (codecQuality == WORST) {
					q = 0;
				} else if (codecQuality == LOW) {
					q = 30;
				} else if (codecQuality == MEDIUM) {
					q = 50;
				} else if (codecQuality == HIGH) {
					q = 85;
				} else if (codecQuality == BEST) {
					q = 100;
				}
				propValues[0] = q;      
			} else if (codecType == MJPEG2K) {
				encoder = "jp2kenc";
			} else {
				throw new ExtensionException("Unrecognized video container");
			}
		
			// Default to 30 fps 
			int fps = 30;
			
			if (framerate != null)
				fps = framerate.getNumerator() / framerate.getDenominator();
				
			System.out.println("Recording with FPS of " + fps);
						
			File file = new File(filename);
			recorder = new RGBDataFileSink2("Recorder", (int)width, (int)height, fps, encoder, propNames, propValues, muxer, file);
			
			recorder.start();
			
			recorder.setPreQueueSize(0);
		    recorder.setSrcQueueSize(60);
			
			recording = true;
		}
	}
	
	public static class StopRecording extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			recorder.stop();
			recording = false;
		}
	}
	
	public static class SetStrechToFillScreen extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.BooleanType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			boolean shouldAddBorders = !(args[0].getBooleanValue());
			scale.set("add-borders", shouldAddBorders);
		}
	}
	
	public static class SetContrast extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			double contrast = args[0].getDoubleValue();
			if (contrast >= 0 && contrast <= 2)
				balance.set("contrast", contrast);
			else
				throw new ExtensionException("Invalid contrast value: [0, 2] (default is 1)");
		}
	}
	
	public static class SetBrightness extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			double contrast = args[0].getDoubleValue();
			if (contrast >= -1 && contrast <= 1)
				balance.set("brightness", contrast);
			else
				throw new ExtensionException("Invalid brightness value: [-1, 1] (default is 0)");
		}
	}
	
	public static class SetHue extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			double contrast = args[0].getDoubleValue();
			if (contrast >= -1 && contrast <= 1)
				balance.set("hue", contrast);
			else
				throw new ExtensionException("Invalid hue value: [-1, 1] (default is 0)");
		}
	}
	
	public static class SetSaturation extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			double contrast = args[0].getDoubleValue();
			if (contrast >= 0 && contrast <= 2)
				balance.set("saturation", contrast);
			else
				throw new ExtensionException("Invalid saturation value: [0, 2] (default is 1)");
		}
	}
	
	public static class StartEdgeDetection extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			
			// Set up edgedetection if we're not already detecting edges
			if (!isDetectingEdges) {
				
				edgeDetectConverter = ElementFactory.make("ffmpegcolorspace", null);
				edgeDetect = ElementFactory.make("edgedetect", null);
			
				List<Pad> srcPads = webcamSource.getSrcPads();
				Pad srcPad = srcPads.get(0);
			
				srcPad.setBlocked(true);
			
				webcamSource.unlink(conv);
			
				cameraPipeline.addMany(edgeDetectConverter, edgeDetect);
				Element.linkMany(webcamSource, edgeDetectConverter, edgeDetect, conv);
			
				edgeDetectConverter.setState(cameraPipeline.getState());
				edgeDetect.setState(cameraPipeline.getState());
			
				srcPad.setBlocked(false);
			
				isDetectingEdges = true;
			}
		}
	}
	
	public static class StopEdgeDetection extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (isDetectingEdges) {
				
				List<Pad> srcPads = webcamSource.getSrcPads();
				Pad srcPad = srcPads.get(0);
			
				srcPad.setBlocked(true);
			
				Element.unlinkMany(webcamSource, edgeDetectConverter, edgeDetect, conv);
				
				edgeDetectConverter.setState(State.NULL);
				edgeDetect.setState(State.NULL);
				cameraPipeline.removeMany(edgeDetectConverter, edgeDetect);
				
				edgeDetectConverter.dispose();
				edgeDetect.dispose();
				
				edgeDetectConverter = null;
				edgeDetect = null;
			
				webcamSource.link(conv);
		
				srcPad.setBlocked(false);
				isDetectingEdges = false;
			}
		}
	}
	
	

	public static class StartCamera extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (cameraPipeline == null)
				throw new ExtensionException("There is no open camera session");
			
			double patchSize = context.getAgent().world().patchSize();
			float width = (float) (args[0].getDoubleValue() * patchSize);
			float height = (float) (args[1].getDoubleValue() * patchSize);
			
			cameraPipeline.setState(State.PLAYING);
		}
	}
	
	public static class IsRolling extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.BooleanType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			if (cameraPipeline != null) {
				State state = cameraPipeline.getState();
				return new Boolean(state == State.PLAYING);
			}
			return new Boolean(false);
		}
	}
	
	public static class IsRecording extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.BooleanType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			if (cameraPipeline != null)
				return new Boolean(recording);
			return new Boolean(false);
		}
	}

	public static class SelectCamera extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}
		
		// System detection from http://www.mkyong.com/java/how-to-detect-os-in-java-systemgetpropertyosname/
		private static boolean isWindows() {
			String os = System.getProperty("os.name").toLowerCase();
			// windows
			return (os.indexOf("win") >= 0);
		}

		private static boolean isMac() {
			String os = System.getProperty("os.name").toLowerCase();
			// Mac
			return (os.indexOf("mac") >= 0);
		}

		private static boolean isUnix() {
			String os = System.getProperty("os.name").toLowerCase();
			// linux or unix
			return (os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0);
		}
		
		private static boolean is32Bit() {
			String arch = System.getProperty("os.arch");
			return arch.contains("x86") && false;
		}
		
		private static boolean is64Bit() {
			String arch = System.getProperty("os.arch");
			return arch.contains("64");
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
	
			// From GSVideo's source
			String os = System.getProperty("os.name");
			
			String capturePlugin = null;
			String devicePropertyName = null;
			String indexPropertyName = null;
			
			if (isWindows()) {
				capturePlugin = "ksvideosrc";
				devicePropertyName = "device-name";
				indexPropertyName = "device-index";
			} else if (isMac()) {
				if (is32Bit()) {
					capturePlugin = "osxvideosrc";
					devicePropertyName = "device";
					// osxvideosrc doesn't have an index property. 
					indexPropertyName = "";
				} else if (is64Bit()) {
					capturePlugin = "qtkitvideosrc";
					indexPropertyName = "device-index";
				} else {
					// Hmmmmm....
				}
			} else if (isUnix()) {
				capturePlugin = "v4l2src";
				// The "device" property in v4l2src expects the device location (/dev/video0, etc). 
				// v4l2src has "device-name", which requires the human-readable name, but how to obtain
				// in linux?.
				devicePropertyName = "device";
				indexPropertyName = "device-fd";
			} else {
				throw new ExtensionException("Your system does not seem to be supported (supported: Mac OS X, Windows, Linux/Unix)");
			}
			
			
				
			double patchSize = context.getAgent().world().patchSize();
			float width      = (float) (args[0].getDoubleValue() * patchSize);
			float height     = (float) (args[1].getDoubleValue() * patchSize);
			
			System.out.println("======== World Information ========");
			System.out.println("width:  " + width);
			System.out.println("height: " + height);
			System.out.println("===================================");
			
			// Pipeline construction based on Processing
			// http://code.google.com/p/processing/source/browse/trunk/processing/java/libraries/video/src/processing/video/Capture.java

			if (cameraPipeline != null) {
				cameraPipeline.setState(State.NULL);
				cameraPipeline.dispose();
				cameraPipeline = null;
			}

			// Pipeline
			cameraPipeline = new Pipeline("camera-capture");
			
			cameraPipeline.getBus().connect(new Bus.ERROR() {
				public void errorMessage(GstObject source, int code, String message) {
					System.out.println("Error occurred: " + message);
				}
			});
			
			cameraPipeline.getBus().connect(new Bus.STATE_CHANGED() {
				public void stateChanged(GstObject source, State old, State current, State pending) {
					
					if (source == cameraPipeline) {
						
						System.out.println("Pipeline state changed from " + old + " to " + current + " (pending:" + pending + ")");
				
						if (old == State.READY && current == State.PAUSED) {
							// Something
							List<Pad> sinkPads = appSink.getSinkPads();
							Pad sinkPad = sinkPads.get(0);

							Caps sinkCaps = sinkPad.getNegotiatedCaps();
							System.out.println(sinkCaps);

							Structure structure = sinkCaps.getStructure(0);

							framerate = structure.getFraction("framerate");
							System.out.println("Camera FPS: " + framerate.getNumerator() + " / " + framerate.getDenominator());
							
						}	
					}
					
				}
			});
			
			// Source
			webcamSource = ElementFactory.make(capturePlugin, null);
			
		//	List<Property> properties = PropertyProbe.wrap(cameraPipeline).getProperties();
		//	if (properties == null)
		//		System.out.println("Failed to query properties");
			
			System.out.println("Camera index: " + webcamSource.get(indexPropertyName));
			
			// Conversion
			conv = ElementFactory.make("ffmpegcolorspace", null);
			Element videofilter = ElementFactory.make("capsfilter", null);
			videofilter.setCaps(Caps.fromString("video/x-raw-rgb, endianness=4321,"
							+ "red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216"));
							
			// Scale
			scale = ElementFactory.make("videoscale", null);
			
			// Balance
 			balance = ElementFactory.make("videobalance", null);
			
			// FPS textoverlay
			/*
			fpsCountOverlay = ElementFactory.make("textoverlay", null);
			fpsCountOverlay.set("text", "FPS: --");
			fpsCountOverlay.set("font-desc", "normal 32");
			fpsCountOverlay.set("halign", "right");
			fpsCountOverlay.set("valign", "top");
			*/
			
			// Sink
			appSink = (AppSink)ElementFactory.make("appsink", null);
			appSink.set("max-buffers", 1);
			appSink.set("drop", true);
			
			String capsString = String.format("video/x-raw-rgb, width=%d, height=%d," +  
											  "pixel-aspect-ratio=480/640", (int)width, (int)height);
			Caps filterCaps = Caps.fromString(capsString);
			appSink.setCaps(filterCaps);
			
			// Add and link
			cameraPipeline.addMany(webcamSource, conv, videofilter, scale, balance, appSink);
			Element.linkMany      (webcamSource, conv, videofilter, scale, balance, appSink);
			
				
		}
	}

	public static class StopCamera extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (cameraPipeline == null)
				throw new ExtensionException("There is no open camera session");
		
			try {
				cameraPipeline.setState(State.NULL);
			} catch (Exception e) {
				e.printStackTrace();
				throw new ExtensionException(e.getMessage());
			}
		}
	}

	public static class Image extends DefaultReporter {
		
		private static long prevTime;
		private static int frameCount;
		
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(new int[]{}, Syntax.WildcardType());
		}

		public String getAgentClassString() {
			return "O";
		}

		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (cameraPipeline == null)
				throw new ExtensionException("There is no open camera session");
			
			try {
				// Pull a buffer (or block until one is available or EOS)
				Buffer buffer = appSink.pullBuffer();
				
				// Get the buffer's dimensions
				Structure structure = buffer.getCaps().getStructure(0);
				int height = structure.getInteger("height");
				int width = structure.getInteger("width");
				
				// Copy the buffer's contents to a java IntBuffer
				IntBuffer intBuf = buffer.getByteBuffer().asIntBuffer();
				int[] imageData = new int[intBuf.capacity()];
				intBuf.get(imageData, 0, imageData.length);
				
				// If currently recording, push buffer off to RGBDataFileSink
				if (recording)
					recorder.pushRGBFrame(buffer);
		
				if (prevTime == 0)
					prevTime = System.currentTimeMillis();
					
				if (System.currentTimeMillis() - prevTime >= 1000) {
					
					if (fpsCountOverlay != null)
						fpsCountOverlay.set("text", "FPS: " + frameCount);
					
					prevTime = System.currentTimeMillis();
					frameCount = 0;
					
					/*
					if (recorder != null)
						System.out.println("Dropped frames: " + recorder.getNumDroppedFrames());
					*/
				}
				
				frameCount++;
				
				// If we push the buffer to the recorder (RGBDataFileSink) it
				// will dispose of the buffer for us.
				if (!recording)
					buffer.dispose();
			
				// Return a java BufferedImage
				return Yoshi.getBufferedImage(imageData, width, height);
				
			} catch (Exception e) {
				e.printStackTrace();
				throw new ExtensionException(e.getMessage());
			}
		}
	}
}
