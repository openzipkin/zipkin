package com.google.common.base;

//HBase requires an old version of Guava, and Twitter requires a new one.
//HBase only needs the Stopwatch class from guava, so it's simplest to add this class before the actual Guava in the classpath, so HBase sees this old shim.
public final class Stopwatch{
	private long start;
	private long duration;
	
	public Stopwatch() {
	}
	
	public Stopwatch start() {
		start = System.nanoTime();
		duration = 0;
		return this;
	}
	
	public Stopwatch stop() {
		duration = System.nanoTime() - start;
		start = 0;
		return this;
	}
	
	public Stopwatch reset() {
		start = 0;
		duration = 0;
		return this;
	}
	
	public long elapsedNanos() {
		return (start == 0) ? duration : (System.nanoTime() - start);
	}
	
	public long elapsedMillis() {
		return elapsedNanos() / 1000000;
	}
}