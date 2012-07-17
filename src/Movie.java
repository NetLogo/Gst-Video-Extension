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
import org.gstreamer.Buffer;
import org.gstreamer.elements.*;
import org.gstreamer.swing.VideoComponent;

// Java 
import javax.swing.*;
import java.awt.*;

import java.nio.IntBuffer;
import java.io.File;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public strictfp class Movie {
	
	private final static String PLAYER_NULL_EXCEPTION_MSG = "no movie appears to be open";
	
	private static PlayBin2 player;
	private static Buffer lastBuffer;
	
	private static int frameRateNum, frameRateDenom;
	
	private static Element fpsCountOverlay, scale, balance;
	private static Element sizeFilter, conv;
	
	private static int worldWidth, worldHeight;
	
	private static Bin sinkBin;
	private static AppSink appSink;
	
	private static javax.swing.JFrame playerFrame;
	private static VideoComponent playerFrameVideoComponent;
//	private static QDGraphics graphics;
	
//	private static final GstElementAPI gst_api = GstNative.load(GstElementAPI.class);
		

	public static void unload() {
		if (player != null) {
			player.setState(State.NULL);
			player = null;
		}
		
		sinkBin = null;
	}
	
	
	public static class SetStrechToFillScreen extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.BooleanType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (scale == null)
				throw new ExtensionException("no scale element seems to exist");
			
			boolean shouldAddBorders = !(args[0].getBooleanValue());
			scale.set("add-borders", shouldAddBorders);
			if (playerFrameVideoComponent != null)
				playerFrameVideoComponent.setKeepAspect(shouldAddBorders);
		}
	}
	
	public static class SetFrameCacheSize extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			
			if (player == null || appSink == null)
				throw new ExtensionException("there is either no movie open or the pipeline is misconfigured");
			
			double brightness = args[0].getDoubleValue();
			if (brightness >= -1 && brightness <= 1)
				balance.set("brightness", brightness);
			else
				throw new ExtensionException("invalid brightness value: [-1, 1] (default is 0)");
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
			
			if (balance == null)
				throw new ExtensionException("no videobalance element seems to exist");
			
			double contrast = args[0].getDoubleValue();
			if (contrast >= 0 && contrast <= 2)
				balance.set("contrast", contrast);
			else
				throw new ExtensionException("invalid contrast value: [0, 2] (default is 1)");
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
			
			if (balance == null)
				throw new ExtensionException("no videobalance element seems to exist");
			
			double brightness = args[0].getDoubleValue();
			if (brightness >= -1 && brightness <= 1)
				balance.set("brightness", brightness);
			else
				throw new ExtensionException("invalid brightness value: [-1, 1] (default is 0)");
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
			
			if (balance == null)
				throw new ExtensionException("no videobalance element seems to exist");
			
			double contrast = args[0].getDoubleValue();
			if (contrast >= -1 && contrast <= 1)
				balance.set("hue", contrast);
			else
				throw new ExtensionException("invalid hue value: [-1, 1] (default is 0)");
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
			
			if (balance == null)
				throw new ExtensionException("no videobalance element seems to exist");
			
			double contrast = args[0].getDoubleValue();
			if (contrast >= 0 && contrast <= 2)
				balance.set("saturation", contrast);
			else
				throw new ExtensionException("invalid saturation value: [0, 2] (default is 1)");
		}
	}
	
	public static class DebugCommand extends DefaultCommand {
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument args[], Context context) throws ExtensionException, LogoException {
			System.out.println("=============== Running debug command(s) ===============");
			System.out.println("DYNAMICALLY LINKING BALANCE INTO PIPELINE");
			
			List<Pad> sinkPads = sinkBin.getSinkPads();
			Pad sinkPad = sinkPads.get(0);
			
			Caps sinkCaps = sinkPad.getNegotiatedCaps();
			System.out.println(sinkCaps);
			
			Structure structure = sinkCaps.getStructure(0);

			int width = structure.getInteger("width");
			int height = structure.getInteger("height");
			
			sinkPads = sizeFilter.getSrcPads();
			sinkPad = sinkPads.get(0);
			
			sinkPad.setBlocked(true);
			
			if (sinkPad.isBlocking()) {
				System.out.println("blocking...");
				String capsString = String.format("video/x-raw-rgb, width=%d, height=%d, pixel-aspect-ratio=%d/%d", worldWidth, worldHeight, width, height);
				Caps sizeCaps = Caps.fromString(capsString);
				sizeFilter.setCaps(sizeCaps);
			}
			
			sinkPad.setBlocked(false);
			
		}
	}

	public static class OpenMovie extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.StringType(), Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}
		
		private void installCallbacks(Bus bus) {			
			/*
			bus.connect(new Bus.TAG() {
				public void tagsFound(GstObject source, TagList tagList) {
					for (String tagName : tagList.getTagNames()) {
						// Each tag can have multiple values, so print them all.
						for (Object tagData : tagList.getValues(tagName)) {
							System.out.printf("[%s]=%s\n", tagName, tagData);
						}
					}
				}
			});
			*/
			
			bus.connect(new Bus.ERROR() {
				public void errorMessage(GstObject source, int code, String message) {
					System.out.println("Error occurred: " + message + "(" +  code + ")");
				}
			});
			

			bus.connect(new Bus.STATE_CHANGED() {
				public void stateChanged(GstObject source, State old, State current, State pending) {
					if (source == player) {
						System.out.println("Pipeline state changed from " + old + " to " + current);
						
						if (old == State.READY && current == State.PAUSED) {
							// Something
						}
						
					}
				}
			});

			bus.connect(new Bus.EOS() {
				public void endOfStream(GstObject source) {
					System.out.println("Finished playing file");
					player.setState(State.PAUSED);
				}
			});
			
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			
			double patchSize = context.getAgent().world().patchSize();
				
			float width = (float) (args[1].getDoubleValue() * patchSize);
			float height = (float) (args[2].getDoubleValue() * patchSize);
			
			System.out.println("======== World Information ========");
			System.out.println("patch-size : " + patchSize);
			System.out.println("width      : " + width);
			System.out.println("height     : " + height);
			System.out.println("===================================");
			
			worldWidth = (int)width;
			worldHeight = (int)height;	
			
			String filename = null;
			
			try {
				filename = context.attachCurrentDirectory(args[0].getString());
				java.io.File file = new java.io.File(filename);
			} catch (java.io.IOException e) {
				throw new ExtensionException(e.getMessage());
			}

			if (player == null && filename != null) {
				player = new PlayBin2("player");
				
				// Watch for errors and log them
				installCallbacks(player.getBus());

				sinkBin = new Bin();
				
				sinkBin.connect(new Element.PAD_ADDED() {
					public void padAdded(Element e, final Pad p) {
						System.out.println("PAD ADDED: " + p);
					}
				});
				
				player.connect(new Element.PAD_ADDED() {
					public void padAdded(Element e, final Pad p) {
						System.out.println("PAD ADDED: " + p);
					}
				});
				
				
				appSink = (AppSink)ElementFactory.make("appsink", null);
				appSink.set("max-buffers", 1);
				appSink.set("drop", true);
				
				// appSink.set("enable-last-buffer", true);

				conv = ElementFactory.make("ffmpegcolorspace", null);
			 	scale = ElementFactory.make("videoscale", null);


				sizeFilter = ElementFactory.make("capsfilter", null);
				
				String capsString = String.format("video/x-raw-rgb, width=%d, height=%d", (int)width, (int)height);
		//		String capsString = String.format("video/x-raw-rgb, width=%d, height=%d, pixel-aspect-ratio=%d/%d", (int)width, (int)height, 1, 1);
				Caps sizeCaps = Caps.fromString(capsString);
				sizeFilter.setCaps(sizeCaps);
				
				// FPS textoverlay
				fpsCountOverlay = ElementFactory.make("textoverlay", null);
				fpsCountOverlay.set("text", "FPS: --");
				fpsCountOverlay.set("font-desc", "normal 32");
				fpsCountOverlay.set("halign", "right");
				fpsCountOverlay.set("valign", "top");
				
				balance = ElementFactory.make("videobalance", null);
				Element rate = ElementFactory.make("videorate", null);
				
				sinkBin.addMany(scale, sizeFilter, balance, conv, fpsCountOverlay, rate, appSink);
				
				if (!scale.link(sizeFilter))
					System.out.println("Problem with scale->caps");
				if (!sizeFilter.link(balance))
					System.out.println("Problem with sizeFilter->balance");
				if (!balance.link(conv))
					System.out.println("Problem with caps->conv");
				if (!conv.link(fpsCountOverlay))
					System.out.println("Problem with conv->overlay");
				if (!fpsCountOverlay.link(rate))
					System.out.println("Problem with overlay->rate");
					
				List<Pad> pads = scale.getSinkPads();
				Pad sinkPad = pads.get(0);

				GhostPad ghost = new GhostPad("sink", sinkPad);
				sinkBin.addPad(ghost);

				// Snippet from http://opencast.jira.com/svn/MH/trunk/modules/matterhorn-composer-gstreamer/src/main/java/org/opencastproject/composer/gstreamer/engine/GStreamerEncoderEngine.java
				Caps some_caps = new Caps("video/x-raw-rgb"
								+ ", bpp=32, depth=24, red_mask=(int)65280, green_mask=(int)16711680, blue_mask=(int)-16777216, alpha_mask=(int)255, framerate=30/1");
								
				if (!Element.linkPadsFiltered(rate, "src", appSink, "sink", some_caps)) {
					throw new ExtensionException("Failed linking ffmpegcolorspace with appsink");
				}
				
				

				player.setVideoSink(sinkBin);
			}
			
			System.out.println("attempting to load file://" + filename);
		
			player.setState(State.NULL);
			player.set("uri", "file://" + filename);
				
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
			if (player == null)
				throw new ExtensionException("there is no movie open");
			System.err.println("starting movie (in theory...)");
			player.setState(State.PLAYING);
		}
	}
	
	public static class SetTimeSeconds extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			if (player == null)
				throw new ExtensionException("there is no movie open");
			
			Double newPos = args[0].getDoubleValue();
			player.seek(ClockTime.fromSeconds(newPos.longValue()));
			
		}
	}

	public static class SetTimeMilliseconds extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}

		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			if (player == null)
				throw new ExtensionException("there is no movie open");
			
			Double newPos = args[0].getDoubleValue();
			player.seek(ClockTime.fromMillis(newPos.longValue()));
			
		}
	}

	public static class OpenPlayer extends DefaultCommand {
		
		public Syntax getSyntax() {
			return Syntax.commandSyntax(new int[]{Syntax.NumberType(), Syntax.NumberType()});
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public void perform(Argument[] args, Context context) throws ExtensionException, LogoException {
			
			if (player == null)
				throw new ExtensionException("there is no movie open");
			
			double patchSize = context.getAgent().world().patchSize();
			
			float width = (float) (args[0].getDoubleValue() * patchSize);
			float height = (float) (args[1].getDoubleValue() * patchSize);
			
			playerFrame = new JFrame("NetLogo: Yoshi Video Extension - External Video Frame");
			
			playerFrameVideoComponent = new VideoComponent();
			Element videosink = playerFrameVideoComponent.getElement();
			
	//		playerFrameVideoComponent.showFPS(true);
			
			State currentState = player.getState();
			
			// It seems to switch video sinks the pipeline needs to 
			// be reconfigured.  Set to NULL and rebuild.
			player.setState(State.NULL);
			player.setVideoSink(videosink);
			player.setState(currentState);
			
			playerFrame.add(playerFrameVideoComponent, BorderLayout.CENTER);
			playerFrameVideoComponent.setPreferredSize(new Dimension((int)width, (int)height));
			
			playerFrame.pack();
			playerFrame.setVisible(true);
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
			if (player == null)
				throw new ExtensionException("there is no movie open");
			
			return new Boolean(player.isPlaying());
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
			if (player == null)
				throw new ExtensionException("there is no movie open");
				
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
				throw new ExtensionException("there is no movie open");
			
		//	player.setState(State.NULL);
		//	player = null;
		
			if (playerFrame != null) {
				playerFrame.dispose();
				playerFrame = null;
			}
			
			// It seems to switch video sinks the pipeline needs to 
			// be reconfigured.  Set to NULL and rebuild.
			State currentState = player.getState();
			
			player.setState(State.NULL);
			player.setVideoSink(sinkBin);
			player.setState(currentState);
		}
	}
	
	public static class MovieDurationSeconds extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.NumberType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			if (player == null)
				throw new ExtensionException("there is no movie open");
			
			long duration = player.queryDuration(TimeUnit.SECONDS);
			
			return new Double(duration);
		}
	}
	
	public static class MovieDurationMilliseconds extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.NumberType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			if (player == null)
				throw new ExtensionException("there is no movie open");
			
			long duration = player.queryDuration(TimeUnit.MILLISECONDS);
			
			return new Double(duration);
		}
	}

	public static class CurrentTimeSeconds extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.NumberType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			if (player == null)
				throw new ExtensionException("there is no movie open");
			
			long position = player.queryPosition(TimeUnit.SECONDS);
			
			return new Double(position);
		}
	}
	
	public static class CurrentTimeMilliseconds extends DefaultReporter {
		public Syntax getSyntax() {
			return Syntax.reporterSyntax(Syntax.NumberType());
		}

		public String getAgentClassString() {
			return "O";
		}
		
		public Object report(Argument args[], Context context) throws ExtensionException, LogoException {
			if (player == null)
				throw new ExtensionException("there is no movie open");
			
			long position = player.queryPosition(TimeUnit.MILLISECONDS);
			
			return new Double(position);
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
			try {
				
				if (player == null || appSink == null)
					throw new ExtensionException("either no movie is open or pipeline is not constructed properly");
				
				// Attempt to grab a buffer from the appSink.  If not available or 
				// EOS has been reached, render and return the most recent buffer (if available)
				Buffer buffer = appSink.pullBuffer();
			
				if (buffer == null)
					buffer = lastBuffer;
				
				// Get buffer dimensions
				Structure structure = buffer.getCaps().getStructure(0);
				
				int bufferWidth = structure.getInteger("width");
				int bufferHeight = structure.getInteger("height");
				
				IntBuffer intBuf = buffer.getByteBuffer().asIntBuffer();
				int[] imageData = new int[intBuf.capacity()];
				intBuf.get(imageData, 0, imageData.length);
				
				// FPS calculations
				if (prevTime == 0)
					prevTime = System.currentTimeMillis();
					
				if (System.currentTimeMillis() - prevTime >= 1000) {
					
					fpsCountOverlay.set("text", "FPS: " + frameCount);
					
					prevTime = System.currentTimeMillis();
					frameCount = 0;		
				}
				
				frameCount++;
				
				// If a buffer was cached and is not currently being
				// relied on, dispose it now and cache current buffer
				if (lastBuffer != null && buffer != lastBuffer)
					lastBuffer.dispose();
				
				lastBuffer = buffer;
								
				return Yoshi.getBufferedImage(imageData, bufferWidth, bufferHeight);
				
			} catch (Exception e) {
				e.printStackTrace();
				throw new ExtensionException(e.getMessage());
			}
		}
	}
}
