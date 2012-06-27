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
	
	private static Element player;
	private static IntBuffer currentFrameBuffer;
	private static float width, height;
//	private static javax.swing.JFrame playerFrame;
//	private static QDGraphics graphics;
	
	private static final GstElementAPI gst_api = GstNative.load(GstElementAPI.class);

	public static void unload() {

	}

	public static class OpenMovie extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.StringType(), Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			
			double patchSize = context.getAgent().world().patchSize();
	//		final float width = (float) (args[1].getDoubleValue() * patchSize);
	//		final float height = (float) (args[2].getDoubleValue() * patchSize);
	
			width = (float) (args[1].getDoubleValue() * patchSize);
			height = (float) (args[2].getDoubleValue() * patchSize);
			
			System.out.println("patch-size: " + patchSize);
			
			System.err.println("width: " + width);
			System.err.println("height: " + height);

			try {
				String filename = context.attachCurrentDirectory(args[0].getString());
				java.io.File file = new java.io.File(filename);

				if (player == null)
					throw new ExtensionException("No player is currently open.");
				
				System.err.println("file://" + filename);
				
				player.setState(State.NULL);
				player.set("uri", "file://" + filename);

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
			
			Format[] fmt = { Format.TIME };
			Double newPos = args[0].getDoubleValue();
			
			gst_api.gst_element_seek_simple(player, fmt[0], SeekFlags.FLUSH, newPos.longValue());
			
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
			if (player == null) {
				throw new ExtensionException("there is no movie loaded");
			}
			*/
			
			if (player == null) {
				player = ElementFactory.make("playbin2", "player");

				final RGBDataAppSink rgbSink = new RGBDataAppSink("rgb", 
					new RGBDataAppSink.Listener() {
						public void rgbFrame(int w, int h, IntBuffer buffer) {
							System.out.println("frame...");
							currentFrameBuffer = buffer;
							width = w;
							height = h;
						}
					});
	
					player.set("video-sink", rgbSink);
					player.set("audio-sink", null);
			}
			
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
			System.out.println(player.getState());
			if (player.getState() == State.PLAYING)
				return new Boolean(true);
			else
				return new Boolean(false);
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
			
			Format[] fmt = { Format.TIME };
			long[] duration = { 0 };
			
			gst_api.gst_element_query_duration(player, fmt, duration);
			
			return new Double(duration[0]);
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
			
			Format[] fmt = { Format.TIME };
			long[] position = { 0 };
			
			gst_api.gst_element_query_position(player, fmt, position);
			
			return new Double(position[0]);
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
				int[] data = currentFrameBuffer.array();
				return Yoshi.getBufferedImage(data, (int)width, (int)height);
			} catch (Exception e) {
				e.printStackTrace();
				throw new ExtensionException(e.getMessage());
			}
		}
	}
}
