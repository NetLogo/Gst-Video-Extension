package org.nlogo.extensions.yoshi;

import org.nlogo.api.DefaultClassManager;
import org.nlogo.api.PrimitiveManager;
import org.nlogo.api.Syntax;
import org.nlogo.api.Context;
import org.nlogo.api.DefaultReporter;
import org.nlogo.api.DefaultCommand;
import org.nlogo.api.Argument;
import org.nlogo.api.ExtensionException;
import org.nlogo.api.ExtensionManager;
import org.nlogo.api.LogoException;

import java.awt.image.*;

import org.gstreamer.*;

public strictfp class Yoshi extends DefaultClassManager {
	
	public void runOnce(org.nlogo.api.ExtensionManager em) throws ExtensionException {
		// Init GStreamer
		
		String args[] = {"--gst-debug-level=2"};
	//	String args[] = {""};
		
		Gst.init("Yoshi", args);
		// first check that we can find the quicktime jar
		// if we can't it throws an extension exception ev 3/3/09
		em.getFile("yoshi/gstreamer-java-1.5.jar");
		em.getFile("yoshi/jna.jar");
	//	em.addToLibraryPath(this, "lib");
	}

	public void load(PrimitiveManager primitiveManager) {
		
		primitiveManager.addPrimitive
			("camera-image", new Capture.Image());
		primitiveManager.addPrimitive
			("camera-start", new Capture.StartCamera());
		primitiveManager.addPrimitive
			("camera-stop", new Capture.StopCamera());
		primitiveManager.addPrimitive
			("camera-is-rolling?", new Capture.IsRolling());
		primitiveManager.addPrimitive
			("camera-select", new Capture.SelectCamera());
		primitiveManager.addPrimitive
			("camera-set-stretches", new Capture.SetStrechToFillScreen());
		primitiveManager.addPrimitive
			("camera-set-contrast", new Capture.SetContrast());
		primitiveManager.addPrimitive
			("camera-set-brightness", new Capture.SetBrightness());
		primitiveManager.addPrimitive
			("camera-set-hue", new Capture.SetHue());
		primitiveManager.addPrimitive
			("camera-set-saturation", new Capture.SetSaturation());
		primitiveManager.addPrimitive
			("camera-start-recording", new Capture.StartRecording());
		primitiveManager.addPrimitive
			("camera-stop-recording", new Capture.StopRecording());
		primitiveManager.addPrimitive
			("camera-is-recording?", new Capture.IsRecording());
		// Expirimental
		primitiveManager.addPrimitive
			("camera-start-edgedetection", new Capture.StartEdgeDetection());
		primitiveManager.addPrimitive
			("camera-stop-edgedetection", new Capture.StopEdgeDetection());
		
		primitiveManager.addPrimitive
			("movie-open", new Movie.OpenMovie());
		primitiveManager.addPrimitive
			("movie-start", new Movie.StartMovie());
		primitiveManager.addPrimitive
			("movie-stop", new Movie.StopMovie());
		primitiveManager.addPrimitive
			("movie-open-player", new Movie.OpenPlayer());
		primitiveManager.addPrimitive
			("movie-close", new Movie.CloseMovie());
		primitiveManager.addPrimitive
			("movie-image", new Movie.Image());
		primitiveManager.addPrimitive
			("movie-set-time-secs", new Movie.SetTimeSeconds());
		primitiveManager.addPrimitive
			("movie-set-time-millisecs", new Movie.SetTimeMilliseconds());
		primitiveManager.addPrimitive
			("movie-duration-secs", new Movie.MovieDurationSeconds());
		primitiveManager.addPrimitive
			("movie-duration-millisecs", new Movie.MovieDurationMilliseconds());
		primitiveManager.addPrimitive
			("movie-time-secs", new Movie.CurrentTimeSeconds());
		primitiveManager.addPrimitive
			("movie-time-millisecs", new Movie.CurrentTimeMilliseconds());
		primitiveManager.addPrimitive
			("movie-playing?", new Movie.IsPlaying());
		primitiveManager.addPrimitive
			("movie-set-stretches", new Movie.SetStrechToFillScreen());
		primitiveManager.addPrimitive
			("movie-set-contrast", new Movie.SetContrast());
		primitiveManager.addPrimitive
			("movie-set-brightness", new Movie.SetBrightness());
		primitiveManager.addPrimitive
			("movie-set-hue", new Movie.SetHue());
		primitiveManager.addPrimitive
			("movie-set-saturation", new Movie.SetSaturation());
		primitiveManager.addPrimitive
			("movie-set-frame-cache-size", new Movie.SetFrameCacheSize());
		primitiveManager.addPrimitive
			("movie-set-looping", new Movie.SetLooping());
		primitiveManager.addPrimitive
			("movie-debug", new Movie.DebugCommand());
			
	}

	public void unload(ExtensionManager em) throws ExtensionException {
		
		try {
			Movie.unload();
		} catch (NoClassDefFoundError e) {
			System.out.println("Movie wasn't loaded for some reason");
		}
		
		try {
			Capture.unload();
		} catch (NoClassDefFoundError e) {
			System.out.println("Capture wasn't loaded for some reason");
		}
		
		/*
		// Since native libraries cannot be loaded in more than one classloader at once
		// and even though we are going dispose of this classloader we can't be sure
		// it will be GC'd before we want to reload this extension, we unload it manually
		// as described here: http://forums.sun.com/thread.jspa?forumID=52&threadID=283774
		// This is a hack, but it works. ev 6/25/09
		
		try {
			ClassLoader classLoader = this.getClass().getClassLoader();
			java.lang.reflect.Field field = ClassLoader.class.getDeclaredField("nativeLibraries");
			field.setAccessible(true);
			java.util.Vector libs = (java.util.Vector) field.get(classLoader);
			for (Object o : libs) {
				java.lang.reflect.Method finalize = o.getClass().getDeclaredMethod("finalize", new Class[0]);
				finalize.setAccessible(true);
				finalize.invoke(o, new Object[0]);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		*/
	}

	static java.awt.image.BufferedImage getBufferedImage(int[] data, int width, int height) {
		return new java.awt.image.BufferedImage(colorModel, getRaster(getRGBSampleModel(width, height), data), false, null);
	}

//	private static final DirectColorModel colorModel = new DirectColorModel(32, 0xff, 0xff00, 0xff0000);
	private static final DirectColorModel colorModel = new DirectColorModel(32, 0xff0000, 0xff00, 0xff);

	private static SampleModel getRGBSampleModel(int width, int height) {
		WritableRaster wr = colorModel.createCompatibleWritableRaster(1, 1);
		SampleModel sampleModel = wr.getSampleModel();
		sampleModel = sampleModel.createCompatibleSampleModel(width, height);
		return sampleModel;
	}

	private static WritableRaster getRaster(SampleModel model, int[] data) {
		return Raster.createWritableRaster(model, new DataBufferInt(data, data.length, 0), null);
	}

	@Override
	public java.util.List<String> additionalJars() {
		return new java.util.ArrayList<String>() {{
			add("gstreamer-java-1.5.jar");
			add("jna.jar");
		}};
	}
}
