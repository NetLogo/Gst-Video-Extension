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

package org.nlogo.extensions.gstvideo;

import org.nlogo.api.DefaultClassManager;
import org.nlogo.api.PrimitiveManager;
import org.nlogo.api.Syntax;
import org.nlogo.api.Context;
import org.nlogo.api.DefaultReporter;
import org.nlogo.api.DefaultCommand;
import org.nlogo.api.Argument;
import org.nlogo.api.ExtensionException;
import org.nlogo.api.LogoException;

import org.gstreamer.*;
import org.gstreamer.Bus;
import org.gstreamer.Buffer;
import org.gstreamer.lowlevel.*;
import org.gstreamer.elements.*;

import org.gstreamer.elements.RGBDataFileSink;

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
	private static IntBuffer currentFrameBuffer;
	private static Element scale, balance;
	
	private static Element fpsCountOverlay;
	
	private static AppSink appSink;
	
	private static RGBDataFileSink recorder;
	private static boolean recording;
	
	private static Fraction framerate;
	
	private static final int WORST = 0;
	private static final int LOW = 1;
	private static final int MEDIUM = 2;
	private static final int HIGH = 3;
	private static final int BEST = 4;
	
	/*
	private static SequenceGrabber capture;
	private static QDGraphics graphics;
	*/

	public static void unload() throws ExtensionException {
		
		if (cameraPipeline != null) {
			cameraPipeline.setState(State.NULL);
			cameraPipeline.dispose();
			cameraPipeline = null;
		}
		
		if (scale != null)
			scale.dispose();
		if (balance != null)
			balance.dispose();
			
		scale = balance = null;
		
		if (appSink != null)
			appSink.dispose();
		appSink = null;
		
		fpsCountOverlay = null;
		
		/*
		try {
			if (capture != null) {
				capture.stop();
				capture = null;
				graphics = null;
				QTSession.close();
			}
		} catch (quicktime.std.StdQTException e) {
			throw new ExtensionException(e.getMessage());
		}
		*/
	}
	
	public static class StartRecording extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.StringType(), Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public static final int THEORA = 0;
		public static final int XVID = 1;
		public static final int X264 = 2;
		public static final int DIRAC = 3;
		public static final int MJPEG = 4;
		public static final int MJPEG2K = 5;
	
		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			double patchSize = context.getAgent().world().patchSize();
			float width = (float) (args[1].getDoubleValue() * patchSize);
			float height = (float) (args[2].getDoubleValue() * patchSize);   
			
			System.out.println("recording-width: " + (int)width);
			System.out.println("recording-height: " + (int)height);
			
			String filename = args[0].getString();
			
			int codecQuality = MEDIUM;
			int codecType = THEORA;

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
			
						
			File file = new File(filename);
			recorder = new RGBDataFileSink("Recorder", (int)width, (int)height, fps, encoder, propNames, propValues, muxer, file);
			
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
	
	

	public static class StartCamera extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			
			double patchSize = context.getAgent().world().patchSize();
			float width = (float) (args[0].getDoubleValue() * patchSize);
			float height = (float) (args[1].getDoubleValue() * patchSize);
			
			cameraPipeline.setState(State.PLAYING);
			
			/*
			try {
				QDRect rect = new QDRect(width, height);
				SGVideoChannel channel = getVideoChannel(rect);
				initializeChannel(channel, rect);
			} catch (quicktime.std.StdQTException e) {
				
				String msg = "Failed to open a session.	 QuickTime may not be installed properly. 
								Or your camera may not be connected or on.";
								
				if (System.getProperty("os.name").startsWith("Windows")) {
					msg += " Perhaps WinVDig is not installed.";
				}
				throw new ExtensionException(msg);
			} catch (quicktime.QTException e) {
				throw new ExtensionException(e.getMessage());
			}
			*/
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

	/*
	private static SGVideoChannel getVideoChannel(QDRect rect) throws quicktime.QTException, ExtensionException {
		
		unload();

		QTSession.open();

		// workaround for intel macs (from imagej)
		graphics = quicktime.util.EndianOrder.isNativeLittleEndian()
			? new QDGraphics(QDConstants.k32BGRAPixelFormat, rect)
			: new QDGraphics(QDGraphics.kDefaultPixelFormat, rect);

		capture = new SequenceGrabber();
		capture.setGWorld(graphics, null);

		return new SGVideoChannel(capture);
	}
	*/

	/*
	private static void initializeChannel(SGVideoChannel channel, QDRect rect) throws quicktime.QTException {
		
		channel.setBounds(rect);
		channel.setUsage(quicktime.std.StdQTConstants.seqGrabRecord |
						quicktime.std.StdQTConstants.seqGrabPreview |
						quicktime.std.StdQTConstants.seqGrabPlayDuringRecord);
		
		channel.setFrameRate(0);
		channel.setCompressorType(quicktime.std.StdQTConstants.kComponentVideoCodecType);

		QTFile movieFile = new QTFile(new java.io.File("NoFile"));
		capture.setDataOutput(null, quicktime.std.StdQTConstants.seqGrabDontMakeMovie);
		capture.prepare(true, true);
		capture.startRecord();
		capture.idle();
		capture.update(null);
	}
	*/

	public static class SelectCamera extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
	
			// From Processing's source
			final String capturePlugin = "qtkitvideosrc";
			final String devicePropertyName = "device-name"; 
			final String indexPropertyName = "device-index";
			
			int frameRateNumerator = 30;
			int frameRateDenominator = 1;
	
			double patchSize = context.getAgent().world().patchSize();
			float width = (float) (args[0].getDoubleValue() * patchSize);
			float height = (float) (args[1].getDoubleValue() * patchSize);
			
			System.out.println("======== World Information ========");
			System.out.println("width:  " + width);
			System.out.println("height: " + height);
			System.out.println("===================================");
			
			// Pipeline construction based on Processing
			// http://code.google.com/p/processing/source/browse/trunk/processing/java/libraries/video/src/processing/video/Capture.java

			// Pipeline
			cameraPipeline = new Pipeline("camera-capture");
			
			cameraPipeline.getBus().connect(new Bus.TAG() {
				public void tagsFound(GstObject source, TagList tagList) {
					for (String tagName : tagList.getTagNames()) {
						// Each tag can have multiple values, so print them all.
						for (Object tagData : tagList.getValues(tagName)) {
							System.out.printf("[%s]=%s\n", tagName, tagData);
						}
					}
				}
			});
			
			cameraPipeline.getBus().connect(new Bus.STATE_CHANGED() {
				public void stateChanged(GstObject source, State old, State current, State pending) {
					System.out.println("Pipeline state changed from " + old + " to " + current);
				
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
			});
			
			// Source
			Element webcamSource = ElementFactory.make(capturePlugin, null);

			// Conversion
			Element conv = ElementFactory.make("ffmpegcolorspace", null);
			Element videofilter = ElementFactory.make("capsfilter", null);
			videofilter.setCaps(Caps.fromString("video/x-raw-rgb, endianness=4321"
							+ ", bpp=32, depth=24, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216"));
							
			// Scale
			scale = ElementFactory.make("videoscale", null);
			
			// Balance
 			balance = ElementFactory.make("videobalance", null);
			
			// FPS textoverlay
			//fpsCountOverlay = ElementFactory.make("textoverlay", null);
			//fpsCountOverlay.set("text", "FPS: --");
			//fpsCountOverlay.set("font-desc", "normal 32");
			//fpsCountOverlay.set("halign", "right");
			//fpsCountOverlay.set("valign", "top");
			
			// Sink
			appSink = (AppSink)ElementFactory.make("appsink", null);
			appSink.set("max-buffers", 1);
			appSink.set("drop", true);
			
			String capsString = String.format("video/x-raw-rgb, width=%d, height=%d, bpp=32, depth=24," +  
											  "pixel-aspect-ratio=480/640", (int)width, (int)height);
			Caps filterCaps = Caps.fromString(capsString);
			appSink.setCaps(filterCaps);
			
			cameraPipeline.addMany(webcamSource, conv, videofilter, scale, balance, appSink);
			Element.linkMany      (webcamSource, conv, videofilter, scale, balance, appSink);
			
			
			// Bus callbacks
			cameraPipeline.getBus().connect(new Bus.ERROR() {
				public void errorMessage(GstObject source, int code, String message) {
					System.out.println("Error occurred: " + message);
				}
			});

			cameraPipeline.getBus().connect(new Bus.STATE_CHANGED() {
				public void stateChanged(GstObject source, State old, State current, State pending) {
					if (source == cameraPipeline) {
						System.out.println("Pipeline state changed from " + old + " to " + current);
						
					}
				}
			});

				/*
			Object selected = javax.swing.JOptionPane.showInputDialog(null, 
												"Select an input device: ", 
												"QTJ Extension",
												javax.swing.JOptionPane.QUESTION_MESSAGE,
												null, options, options[0]);
			*/
				
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
			/*
			if (capture == null) {
				throw new ExtensionException("There is no open camera session");
			}
			*/
			try {
				cameraPipeline.setState(State.NULL);
				/*
				capture.stop();
				capture = null;
				graphics = null;
				QTSession.close();
				*/
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
			/*
			if (capture == null) {
				throw new ExtensionException("There is no open camera session");
			}
			*/
			
			try {
				Buffer buffer = appSink.pullBuffer();
				
				Structure structure = buffer.getCaps().getStructure(0);
				int height = structure.getInteger("height");
				int width = structure.getInteger("width");
				
				IntBuffer intBuf = buffer.getByteBuffer().asIntBuffer();
				int[] imageData = new int[intBuf.capacity()];
				intBuf.get(imageData, 0, imageData.length);
				
				
			//	Buffer blackBuffer = new Buffer(width * height * 4);
				
				
				if (recording) {
					recorder.pushRGBFrame(buffer);
				}
		
				if (prevTime == 0)
					prevTime = System.currentTimeMillis();
					
				if (System.currentTimeMillis() - prevTime >= 1000) {
					
					//fpsCountOverlay.set("text", "FPS: " + frameCount);
					
					prevTime = System.currentTimeMillis();
					frameCount = 0;
					
					if (recorder != null)
						System.out.println("Dropped frames: " + recorder.getNumDroppedFrames());
				}
				
				frameCount++;
				
		//		blackBuffer.dispose();
				if (!recording)
					buffer.dispose();
			
				return GstVideo.getBufferedImage(imageData, width, height);
				
			} catch (Exception e) {
				e.printStackTrace();
				throw new ExtensionException(e.getMessage());
			}
		}
	}
}
