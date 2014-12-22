package com.google.common.base;

// Borrowed from zipkin-hbase hack

public final class Stopwatch {
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