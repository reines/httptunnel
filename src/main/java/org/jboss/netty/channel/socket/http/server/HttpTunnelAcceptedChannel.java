/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.http.server;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.http.IncomingBuffer;
import org.jboss.netty.channel.socket.http.SaturationManager;
import org.jboss.netty.channel.socket.http.SaturationStateChange;
import org.jboss.netty.channel.socket.http.util.ChannelFutureAggregator;
import org.jboss.netty.channel.socket.http.util.ForwardingFutureListener;
import org.jboss.netty.channel.socket.http.util.HttpTunnelMessageUtils;
import org.jboss.netty.channel.socket.http.util.QueuedResponse;
import org.jboss.netty.channel.socket.http.util.WriteSplitter;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Represents the server end of an HTTP tunnel, created after a legal tunnel
 * creation request is received from a client. The server end of a tunnel does
 * not have any directly related TCP connections - the connections used by a
 * client are likely to change over the lifecycle of a tunnel, especially when
 * an HTTP proxy is in use.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelAcceptedChannel extends AbstractChannel implements SocketChannel {

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelAcceptedChannel.class);

	private final HttpTunnelServerChannel parent;
	private final HttpTunnelAcceptedChannelConfig config;
	private final SaturationManager saturationManager;
	private final InetSocketAddress remoteAddress;
	private final InetSocketAddress localAddress;
	private final String tunnelId;

	private final AtomicBoolean opened;

	private final AtomicReference<Channel> pollChannel;
	private final Queue<QueuedResponse> queuedResponses;
	private final IncomingBuffer<ChannelBuffer> incomingBuffer;

	protected HttpTunnelAcceptedChannel(HttpTunnelServerChannel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink, InetSocketAddress remoteAddress, String tunnelId) {
		super (parent, factory, pipeline, sink);

		this.parent = parent;
		this.remoteAddress = remoteAddress;
		this.tunnelId = tunnelId;

		localAddress = parent.getLocalAddress();
		config = new DefaultHttpTunnelAcceptedChannelConfig();

		saturationManager = new SaturationManager(config.getWriteBufferLowWaterMark(), config.getWriteBufferHighWaterMark());

		opened = new AtomicBoolean(true);

		pollChannel = new AtomicReference<Channel>(null);
		queuedResponses = new ConcurrentLinkedQueue<QueuedResponse>();

		incomingBuffer = new IncomingBuffer<ChannelBuffer>(this, Executors.newSingleThreadExecutor());
		incomingBuffer.start();
	}

	String getTunnelId() {
		return tunnelId;
	}

	@Override
	public HttpTunnelAcceptedChannelConfig getConfig() {
		return config;
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return this.isBound() ? localAddress : null;
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return this.isConnected() ? remoteAddress : null;
	}

	@Override
	public boolean isBound() {
		return parent.getTunnel(tunnelId) != null;
	}

	@Override
	public boolean isConnected() {
		return parent.getTunnel(tunnelId) != null;
	}

	@Override
	public boolean setClosed() {
		final boolean success = super.setClosed();
		Channels.fireChannelClosed(this);

		return success;
	}

	synchronized ChannelFuture internalClose(boolean sendCloseRequest, ChannelFuture future) {
		if (!opened.getAndSet(false)) {
			future.setSuccess();
			return future;
		}

		// Closed from the server end - we should notify the client
		if (sendCloseRequest) {
			final Channel channel = pollChannel.getAndSet(null);
			// response channel is already in use, client will be notified of close at next opportunity
			if (channel != null && channel.isOpen())
				Channels.write(channel, HttpTunnelMessageUtils.createTunnelCloseResponse());
		}

		Channels.fireChannelDisconnected(this);
		Channels.fireChannelUnbound(this);

		parent.removeTunnel(tunnelId);
		this.setClosed();

		future.setSuccess();
		return future;
	}

	synchronized ChannelFuture internalSetInterestOps(int ops, ChannelFuture future) {
		super.setInterestOpsNow(ops);
		Channels.fireChannelInterestChanged(this);

		// Update the incoming buffer
		incomingBuffer.onInterestOpsChanged();

		future.setSuccess();
		return future;
	}

	synchronized void internalReceiveMessage(ChannelBuffer message) {
		if (!opened.get()) {
			if (LOG.isWarnEnabled())
				LOG.warn("Received message while channel is closed");

			return;
		}

		// Attempt to queue this message in the incoming buffer
		if (!incomingBuffer.offer(message)) {
			if (LOG.isWarnEnabled())
				LOG.warn("Incoming buffer rejected message, dropping");

			return;
		}

		// If the buffer is over capacity start congestion control
		if (incomingBuffer.overCapacity()) {
			// TODO: Send a "stop sending shit" message!
			// TODO: What about when to send the "start sending shit again" message?
		}
	}

	synchronized ChannelFuture sendMessage(MessageEvent message) {
		final ChannelFuture messageFuture = message.getFuture();

		if (!this.isConnected()) {
			final Exception error = new IllegalStateException("Unable to send message when not connected");

			messageFuture.setFailure(error);
			Channels.fireExceptionCaught(this, error);

			return messageFuture;
		}

		saturationManager.updateThresholds(config.getWriteBufferLowWaterMark(), config.getWriteBufferHighWaterMark());

		// Deliver the message using the underlying channel
		final ChannelBuffer messageBuffer = (ChannelBuffer) message.getMessage();
		final int messageSize = messageBuffer.readableBytes();

		updateSaturationStatus(messageSize);

		messageFuture.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				updateSaturationStatus(-messageSize);
			}
		});

		final ChannelFutureAggregator aggregator = new ChannelFutureAggregator(messageFuture);
		final List<ChannelBuffer> fragments = WriteSplitter.split(messageBuffer, HttpTunnelMessageUtils.MAX_BODY_SIZE);

		if (LOG.isDebugEnabled())
			LOG.debug("routing outbound data for tunnel " + tunnelId);

		for (ChannelBuffer fragment : fragments) {
			final ChannelFuture fragmentFuture = Channels.future(this);
			aggregator.addFuture(fragmentFuture);

			queuedResponses.offer(new QueuedResponse(fragment, fragmentFuture));
		}

		this.sendQueuedData();

		return messageFuture;
	}

	synchronized void pollQueuedData(Channel channel) {
		if (!this.pollChannel.compareAndSet(null, channel))
			throw new IllegalStateException("Only one poll request at a time per tunnel allowed");

		this.sendQueuedData();
	}

	synchronized void sendQueuedData() {
		final Channel channel = pollChannel.getAndSet(null);
		// no response channel, or another thread has already used it
		if (channel == null)
			return;

		final QueuedResponse messageToSend = queuedResponses.poll();
		// no data to send, restore the response channel and bail out
		if (messageToSend == null) {
			pollChannel.set(channel);
			return;
		}

		if (LOG.isDebugEnabled())
			LOG.debug("sending response for tunnel id " + tunnelId + " to " + channel.getRemoteAddress());

		final HttpResponse response = HttpTunnelMessageUtils.createRecvDataResponse(messageToSend.getData());
		final ChannelFuture future = messageToSend.getFuture();

		Channels.write(channel, response).addListener(new ForwardingFutureListener(future));
	}

	void updateSaturationStatus(int queueSizeDelta) {
		final SaturationStateChange transition = saturationManager.queueSizeChanged(queueSizeDelta);

		switch (transition) {
			case SATURATED: {
				this.fireWriteEnabled(false);
				break;
			}

			case DESATURATED: {
				this.fireWriteEnabled(true);
				break;
			}

			case NO_CHANGE: {
				break;
			}
		}
	}

	private void fireWriteEnabled(boolean enabled) {
		int ops = OP_READ;
		if (!enabled)
			ops |= OP_WRITE;

		this.internalSetInterestOps(ops, Channels.future(this));
	}
}
