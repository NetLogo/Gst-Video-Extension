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

import javax.swing.*;
import java.awt.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.io.File;
import java.lang.reflect.*;

import java.awt.event.*;
import java.awt.image.*;

public strictfp class Movie {
	
	private static PlayBin2 player;
	private static IntBuffer currentFrameBuffer;
	private static float width, height;
	private static Bin sink;
	
	private static AppSink appSink;
	private static boolean frameAvailable;
	
	
//	private static javax.swing.JFrame playerFrame;
//	private static QDGraphics graphics;
	
//	private static final GstElementAPI gst_api = GstNative.load(GstElementAPI.class);
		

	public static void unload() {
		if (player != null) {
			player.setState(State.NULL);
			player = null;
		}
		
		sink = null;
	}

	public static class OpenMovie extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.StringType(), Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}
		
		private void installCallbacks() {
			Bus playerBus = player.getBus();
			
			playerBus.connect(new Bus.ERROR() {
				public void errorMessage(GstObject source, int code, String message) {
					System.out.println("Error occurred: " + message);
				}
			});

			playerBus.connect(new Bus.STATE_CHANGED() {
				public void stateChanged(GstObject source, State old, State current, State pending) {
					if (source == player) {
						System.out.println("Pipeline state changed from " + old + " to " + current);
					}
				}
			});

			playerBus.connect(new Bus.EOS() {
				public void endOfStream(GstObject source) {
					System.out.println("Finished playing file");
					player.setState(State.PAUSED);
				}
			});
			
			player.connect(new Element.PAD_ADDED() {
				public void padAdded(Element e, final Pad p) {
					System.out.println("PAD ADDED: " + p);
				}
			});
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			
			double patchSize = context.getAgent().world().patchSize();
				
			width = (float) (args[1].getDoubleValue() * patchSize);
			height = (float) (args[2].getDoubleValue() * patchSize);
			
			System.out.println("======== World Information ========");
			System.out.println("patch-size : " + patchSize);
			System.out.println("width      : " + width);
			System.out.println("height     : " + height);
			System.out.println("===================================");

			try {
				String filename = context.attachCurrentDirectory(args[0].getString());
				java.io.File file = new java.io.File(filename);

				if (player == null) {
					player = new PlayBin2("player");

					installCallbacks();

					sink = new Bin();

					sink.connect(new Element.PAD_ADDED() {
						public void padAdded(Element e, final Pad p) {
							System.out.println("PAD ADDED: " + p);
						}
					});

					appSink = (AppSink)ElementFactory.make("appsink", null);

					Element conv = ElementFactory.make("ffmpegcolorspace", null);
					Element scale = ElementFactory.make("videoscale", null);
					Element capsfilter = ElementFactory.make("capsfilter", null);
					Element caps = ElementFactory.make("capsfilter", null);

					sink.addMany	(scale, caps, conv, appSink);

					String capsString = String.format("video/x-raw-rgb, width=%d, height=%d", (int)width, (int)height);
					Caps sizeCaps = Caps.fromString(capsString);
					caps.setCaps(sizeCaps);

					if (!scale.link(caps))
						System.out.println("Problem with scale->caps");
					if (!caps.link(conv))
						System.out.println("Problem with caps->conv");

					List<Pad> pads = scale.getSinkPads();
					Pad sinkPad = pads.get(0);

					GhostPad ghost = new GhostPad("sink", sinkPad);
					sink.addPad(ghost);

					// Snippet from http://opencast.jira.com/svn/MH/trunk/modules/matterhorn-composer-gstreamer/src/main/java/org/opencastproject/composer/gstreamer/engine/GStreamerEncoderEngine.java
					Caps some_caps = new Caps("video/x-raw-rgb"
									+ ", bpp=32, depth=32, framerate=30/1, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216, alpha_mask=(int)255");
					if (!Element.linkPadsFiltered(conv, "src", appSink, "sink", some_caps)) {
						throw new ExtensionException("Failed linking ffmpegcolorspace with appsink");
					}

					player.setVideoSink(sink);
				
					System.err.println("file://" + filename);
				
					player.setState(State.NULL);
					player.set("uri", "file://" + filename);
				}
				
				/*
				Runnable runnable = new Runnable() {
					public void run() {
						try {
			  				QTSession.open();
			  
							QDRect rect = new QDRect(width, height);
							// workaround for intel macs (found from imagej)
							graphics = quicktime.util.EndianOrder.isNativeLittleEndian()
										? new QDGraphics(QDConstants.k32BGRAPixelFormat, rect)
										: new QDGraphics(QDGraphics.kDefaultPixelFormat, rect);

							quicktime.io.QTFile qtfile = new quicktime.io.QTFile(file);
	 						quicktime.io.OpenMovieFile openMovieFile = quicktime.io.OpenMovieFile.asRead(qtfile);
							movie = quicktime.std.movies.Movie.fromFile(openMovieFile);
							movie.setGWorld(graphics, null);
							movie.setBounds(rect);
						} catch (quicktime.QTException e) {
							org.nlogo.util.Exceptions.handle(e);
							//throw new ExtensionException ( e.getMessage() ) ;
						}
					}
				};
				*/
			//	((org.nlogo.window.GUIWorkspace) ((org.nlogo.nvm.ExtensionContext) context).workspace()).waitFor(runnable);
			} catch (java.io.IOException e) {
				throw new ExtensionException(e.getMessage());
			}
		}
	}

	public static class StartMovie extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			if (player == null) {
				throw new ExtensionException("there is no movie open");
			}
			System.err.println("Starting movie (in theory...)");
			player.setState(State.PLAYING);
			
	//		System.out.println(sink.getState());
			
		}
	}
	
	public static class SetTime extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			if (player == null) {
				throw new ExtensionException("there is no movie open");
			}
			
			Double newPos = args[0].getDoubleValue();
			player.seek(ClockTime.fromNanos(newPos.longValue()));
			
		}
	}

	public static class OpenPlayer extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			
			/*
			try {
				java.awt.Component c = quicktime.app.view.QTFactory.makeQTComponent(movie).asComponent();
				playerFrame = new javax.swing.JFrame();
				playerFrame.add(c);
				QDRect bounds = movie.getBounds();
				playerFrame.setVisible(true);
				playerFrame.setSize(new java.awt.Dimension(bounds.getWidth(), bounds.getHeight()));
			} catch (quicktime.QTException e) {
				throw new ExtensionException(e.getMessage());
			}
			*/
		}
	}

	public static class IsPlaying extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.BooleanType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			return new Boolean(player.isPlaying());
		}
	}
	
	public static class HasNewFrame extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.BooleanType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			return new Boolean(frameAvailable);
		}
	}
	

	public static class StopMovie extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			if (player == null) {
				throw new ExtensionException("there is no movie loaded");
			}
			player.setState(State.PAUSED);
		}
	}

	public static class CloseMovie extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			
			if (player == null)
				throw new ExtensionException("no movie to close");
			
			player.setState(State.NULL);
			player = null;
			
			/*
			movie = null;
			graphics = null;
			
			if (playerFrame != null) {
				playerFrame.dispose();
				playerFrame = null;
			}
			QTSession.close();
			*/
		}
	}
	
	public static class MovieDuration extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.NumberType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (player == null) 
				throw new ExtensionException("No valid player found to query duration for");
			
			long duration = player.queryDuration(Format.TIME);
			
			return new Double(duration);
		}
	}

	public static class CurrentTime extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.NumberType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (player == null) 
				throw new ExtensionException("No valid player found to query duration for");
			
			long position = player.queryPosition(Format.TIME);
			
			return new Double(position);
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
			try {
				
			//	GObject buff = (GObject)player.get("frame");
			//	System.out.println(buff);
			//	IntBuffer intBuffer = ((ByteBuffer) buff.getByteBuffer().rewind()).asIntBuffer();
				
		//		int[] data = currentFrameBuffer.array();
		
				if (appSink == null)
					throw new ExtensionException("No sink");
				
				Buffer buffer = appSink.pullBuffer();
				
				if (buffer == null)
					throw new ExtensionException("No Buffer");
					
				Structure structure = buffer.getCaps().getStructure(0);
				int buff_height = structure.getInteger("height");
				int buff_width = structure.getInteger("width");
				
				IntBuffer intBuf = buffer.getByteBuffer().asIntBuffer();
				int[] imageData = new int[intBuf.capacity()];
				intBuf.get(imageData, 0, imageData.length);
				
				buffer.dispose();
								
				return Yoshi.getBufferedImage(imageData, buff_width, buff_height);
				
			} catch (Exception e) {
				e.printStackTrace();
				throw new ExtensionException(e.getMessage());
			}
		}
	}
}
