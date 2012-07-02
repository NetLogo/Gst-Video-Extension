package org.nlogo.extensions.yoshi;

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

import java.nio.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.io.File;
import java.lang.reflect.*;


import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;


public strictfp class Capture {
	
	private static Pipeline cameraPipeline;
	private static IntBuffer currentFrameBuffer;
	private static Element scale;
	
	private static AppSink appSink;
	
	/*
	private static SequenceGrabber capture;
	private static QDGraphics graphics;
	*/
	private static float width, height;

	public static void unload() throws ExtensionException {
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
			width = (float) (args[0].getDoubleValue() * patchSize);
			height = (float) (args[1].getDoubleValue() * patchSize);
			
			System.out.println("======== World Information ========");
			System.out.println("width:  " + width);
			System.out.println("height: " + height);
			System.out.println("===================================");
			
			// Pipeline construction based on Processing
			// http://code.google.com/p/processing/source/browse/trunk/processing/java/libraries/video/src/processing/video/Capture.java

			cameraPipeline = new Pipeline("camera-capture");

			Element videofilter = ElementFactory.make("capsfilter", "filter");
			videofilter.setCaps(Caps.fromString("video/x-raw-rgb, width=640, height=480"
							+ ", bpp=32, depth=32, framerate=30/1"));
							
			
			Element conv = ElementFactory.make("ffmpegcolorspace", null);
			Element webcamSource = ElementFactory.make(capturePlugin, null);
			
			appSink = (AppSink)ElementFactory.make("appsink", null);
			appSink.set("max-buffers", 1);
			
			/*
			final RGBDataAppSink rgbSink = new RGBDataAppSink("rgb", 
				new RGBDataAppSink.Listener() {
					public void rgbFrame(int w, int h, IntBuffer buffer) {
						currentFrameBuffer = buffer;
					}
				}
			);
			*/
			
			Element capsfilter = ElementFactory.make("capsfilter", "caps");
			
			String capsString = String.format("video/x-raw-rgb, width=%d, height=%d, bpp=32, depth=32, framerate=30/1, pixel-aspect-ratio=480/640", (int)width, (int)height);
			Caps filterCaps = Caps.fromString(capsString);
			
			appSink.setCaps(filterCaps);
			
			scale = ElementFactory.make("videoscale", "scaler");
			
			cameraPipeline.addMany(webcamSource, conv, videofilter, scale, appSink);
			Element.linkMany(webcamSource, conv, videofilter, scale, appSink);
			
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
				/**
				capture.idle();
				capture.update(null);
				PixMap map = graphics.getPixMap();
				RawEncodedImage image = map.getPixelData();
				int intsPerRow = image.getRowBytes() / 4;
				int height = graphics.getBounds().getHeight();

				int[] data = new int[intsPerRow * height];
				image.copyToArray(0, data, 0, data.length);

				return QTJExtension.getBufferedImage(data, intsPerRow, height);
				*/
				
				/*
				Buffer buffer = (Buffer)cameraPipeline.get("frame");
				int[] data = (int[]) buffer.asIntBuffer().array();
				*/
				
			//	Thread.sleep(30);
				
			//	int[] data = currentFrameBuffer.array();
				
				Buffer buffer = appSink.pullBuffer();
				
				// From http://opencast.jira.com/svn/MH/trunk/modules/matterhorn-composer-gstreamer/src/main/java/org/opencastproject/composer/gstreamer/engine/GStreamerEncoderEngine.java
				
				Structure structure = buffer.getCaps().getStructure(0);
		 		int origHeight = structure.getInteger("height");
		    	int origWidth = structure.getInteger("width");
				
				IntBuffer intBuf = buffer.getByteBuffer().asIntBuffer();
				int[] imageData = new int[intBuf.capacity()];
				intBuf.get(imageData, 0, imageData.length);
				
				BufferedImage originalImage = new BufferedImage(origWidth, origHeight, BufferedImage.TYPE_INT_ARGB);
			    originalImage.setRGB(0, 0, origWidth, origHeight, imageData, 0, origWidth);
			
			//	return Yoshi.getBufferedImage(imageData, (int)width, (int)height);
				return originalImage;
			} catch (Exception e) {
				e.printStackTrace();
				throw new ExtensionException(e.getMessage());
			}
		}
	}
}
