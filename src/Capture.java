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
	
			double patchSize = context.getAgent().world().patchSize();
			width = (float) (args[0].getDoubleValue() * patchSize);
			height = (float) (args[1].getDoubleValue() * patchSize);
			
			System.out.println("width: " + width);
			System.out.println("height: " + height);

			cameraPipeline = new Pipeline("camera-capture");

			Element videofilter = ElementFactory.make("capsfilter", "filter");
			videofilter.setCaps(Caps.fromString("video/x-raw-rgb, width=640, height=480"
							+ ", bpp=32, depth=32, framerate=30/1"));


			Element conv = ElementFactory.make("ffmpegcolorspace", "ColorConverter");
			Element webcamSource = ElementFactory.make(capturePlugin, "source");
			
			final RGBDataAppSink rgbSink = new RGBDataAppSink("rgb", 
				new RGBDataAppSink.Listener() {
					public void rgbFrame(int w, int h, IntBuffer buffer) {
						currentFrameBuffer = buffer;
				}
			});
			
			Element capsfilter = ElementFactory.make("capsfilter", "caps");
			
			Caps filterCaps = Caps.fromString("video/x-raw-rgb, width=" + (int)width + ", height=" + (int)height
							+ " , bpp=32, depth=32, framerate=30/1");
			capsfilter.setCaps(filterCaps);
			
			Element scale = ElementFactory.make("videoscale", "scaler");
			
			cameraPipeline.addMany(webcamSource, conv, videofilter, scale, capsfilter, rgbSink);
			Element.linkMany(webcamSource, conv, videofilter, scale, capsfilter, rgbSink);
			
			cameraPipeline.getBus().connect(new Bus.ERROR() {
				public void errorMessage(GstObject source, int code, String message) {
					System.out.println("Error occurred: " + message);
					Gst.quit();
				}
			});

			cameraPipeline.getBus().connect(new Bus.STATE_CHANGED() {
				public void stateChanged(GstObject source, State old, State current, State pending) {
					if (source == cameraPipeline) {
						System.out.println("Pipeline state changed from " + old + " to " + current);
					}
				}
			});

			cameraPipeline.getBus().connect(new Bus.EOS() {
				public void endOfStream(GstObject source) {
					System.out.println("Finished playing file");
				//	play.seek(0);
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
				
				int[] data = currentFrameBuffer.array();
				return Yoshi.getBufferedImage(data, (int)width, (int)height);
			} catch (Exception e) {
				e.printStackTrace();
				throw new ExtensionException(e.getMessage());
			}
		}
	}
}
