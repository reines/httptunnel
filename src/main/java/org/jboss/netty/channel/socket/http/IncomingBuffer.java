package org.jboss.netty.channel.socket.http;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;

public class IncomingBuffer<T> implements Runnable {

	public static final int DEFAULT_CAPACITY = 10;
	public static final int DEFAULT_BOUNDS = Integer.MAX_VALUE;

	private final Channel channel;
	private final ExecutorService workerExecutor;
	private final Queue<T> buffer;

	private int capacity;
	private int bounds;

	public IncomingBuffer(Channel channel) {
		this (channel, DEFAULT_CAPACITY);
	}

	public IncomingBuffer(Channel channel, int capacity) {
		this (channel, capacity, DEFAULT_BOUNDS);
	}

	public IncomingBuffer(Channel channel, int capacity, int bounds) {
		this.channel = channel;
		this.capacity = capacity;
		this.bounds = bounds;

		workerExecutor = Executors.newSingleThreadExecutor();
		buffer = new LinkedList<T>();

		new Thread(this).start();
	}

	public int getCapacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public int getBounds() {
		return bounds;
	}

	public void setBounds(int bounds) {
		this.bounds = bounds;
	}

	public synchronized boolean offer(T item) {
		if (buffer.size() >= bounds)
			return false;

		buffer.offer(item);
		this.notifyAll();

		return true;
	}

	public synchronized boolean overCapacity() {
		return buffer.size() > capacity;
	}

	public synchronized void onInterestOpsChanged() {
		this.notifyAll();
	}

	@Override
	public synchronized void run() {
		while (true) {
			// Block while we have no messages, or we aren't meant to be reading them
			while (buffer.isEmpty() || !channel.isReadable()) {
				try { this.wait(); } catch (InterruptedException e) { }
			}

			final T item = buffer.poll();
			workerExecutor.execute(new Runnable() {
				@Override
				public void run() {
					Channels.fireMessageReceived(channel, item);
				}
			});
		 }
	}
}
