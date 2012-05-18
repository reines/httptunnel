/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.yammer.httptunnel.util;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;

/**
 * A buffer of incoming messages read from the HTTP tunnel. Messages are
 * buffered to allow for setting a channel unreadable. A buffered can have both
 * a capacity and an upper bound. When the buffer reaches it's capacity it is
 * marked as being over capacity, however will continue to accept messages until
 * the upper bound is exceeded.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public class IncomingBuffer<T> implements Runnable {

	/**
	 * The default maximum capacity of the buffer.
	 */
	public static final int DEFAULT_CAPACITY = 10;

	/**
	 * The default maximum size of the buffer.
	 */
	public static final int DEFAULT_BOUNDS = Integer.MAX_VALUE;

	private final Channel channel;
	private final ExecutorService workerExecutor;
	private final Queue<T> buffer;

	private int capacity;
	private int bounds;

	public IncomingBuffer(Channel channel) {
		this(channel, DEFAULT_CAPACITY);
	}

	public IncomingBuffer(Channel channel, int capacity) {
		this(channel, capacity, DEFAULT_BOUNDS);
	}

	public IncomingBuffer(Channel channel, int capacity, int bounds) {
		this.channel = channel;
		this.capacity = capacity;
		this.bounds = bounds;

		workerExecutor = Executors.newSingleThreadExecutor();
		buffer = new LinkedList<T>();

		new Thread(this).start();
	}

	/**
	 * @return the current value of the maximum capacity of the buffer.
	 */
	public int getCapacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	/**
	 * @return the current value of the maximum size of the buffer.
	 */
	public int getBounds() {
		return bounds;
	}

	public void setBounds(int bounds) {
		this.bounds = bounds;
	}

	public int size() {
		return buffer.size();
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
			// Block while we have no messages, or we aren't meant to be reading
			// them
			while (buffer.isEmpty() || !channel.isReadable()) {
				try {
					this.wait();
				}
				catch (InterruptedException e) {
				}
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
