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

package com.yammer.httptunnel.client;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import com.yammer.httptunnel.util.HttpTunnelMessageUtils;
import com.yammer.httptunnel.util.TimedMessageEventWrapper;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.Timer;

/**
 * Pipeline component which deals with sending data from the client to server.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelClientChannelSendHandler extends SimpleChannelHandler {

	public static final String NAME = "client2server";

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelClientChannelSendHandler.class);

	private final Meter connectionMeter = Metrics.newMeter(HttpTunnelClientChannelSendHandler.class, "channelOpen", "channelOpen", TimeUnit.SECONDS);
	private final Timer requestTimer = Metrics.newTimer(HttpTunnelClientChannelSendHandler.class, "requests", TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
	private final Histogram requestSizes = Metrics.newHistogram(HttpTunnelClientChannelPollHandler.class, "requestSize");

	private final HttpTunnelClientWorkerOwner tunnelChannel;
	private final AtomicBoolean disconnecting;
	private final ConcurrentLinkedQueue<TimedMessageEventWrapper> queuedWrites;
	private final AtomicInteger pendingRequestCount;

	private String tunnelId;
	private ChannelStateEvent postShutdownEvent;
	private long sendRequestTime;

	public HttpTunnelClientChannelSendHandler(HttpTunnelClientWorkerOwner tunnelChannel) {
		this.tunnelChannel = tunnelChannel;

		disconnecting = new AtomicBoolean(false);
		queuedWrites = new ConcurrentLinkedQueue<TimedMessageEventWrapper>();
		pendingRequestCount = new AtomicInteger(0);

		Metrics.newGauge(HttpTunnelClientChannelSendHandler.class, "queuedWrites", new Gauge<Integer>() {
		    @Override
		    public Integer value() {
		        return queuedWrites.size();
		    }
		});

		tunnelId = null;
		postShutdownEvent = null;
		sendRequestTime = 0;
	}

	public String getTunnelId() {
		return tunnelId;
	}

	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// If this tunnel has already been opened then don't try open it
		if (tunnelId == null) {
			if (LOG.isDebugEnabled())
				LOG.debug("connection to " + e.getValue() + " succeeded - sending open tunnel request");

			final HttpRequest request = HttpTunnelMessageUtils.createOpenTunnelRequest(tunnelChannel.getServerHostName(), tunnelChannel.getUserAgent());
			final Channel channel = ctx.getChannel();
			final DownstreamMessageEvent event = new DownstreamMessageEvent(channel, Channels.future(channel), request, channel.getRemoteAddress());

			queuedWrites.offer(new TimedMessageEventWrapper(event, requestTimer.time()));
			pendingRequestCount.incrementAndGet();
		}

		// Send our first chunk of data
		this.sendQueuedData(ctx);
	}

	@Override
	public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		if (!tunnelChannel.isConnecting() && !tunnelChannel.isConnected())
			return;

		if (LOG.isDebugEnabled())
			LOG.debug("Send channel for tunnel " + tunnelId + " failed");

		// TODO: What state was our last send in? if the last send failed stick
		// it on the front of the queue?
		pendingRequestCount.decrementAndGet();

		// The send channel was closed forcefully rather than by a shutdown
		tunnelChannel.underlyingChannelFailed();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		final HttpResponse response = (HttpResponse) e.getMessage();

		if (HttpTunnelMessageUtils.isOKResponse(response)) {
			if (LOG.isDebugEnabled()) {
				long rtt = System.nanoTime() - sendRequestTime;
				LOG.debug("OK response received for tunnel " + tunnelId + ", after " + rtt + " ns");
			}

			this.sendNextAfterResponse(ctx);
		}
		else if (HttpTunnelMessageUtils.isTunnelOpenResponse(response)) {
			tunnelId = HttpTunnelMessageUtils.extractCookie(response);

			if (LOG.isDebugEnabled())
				LOG.debug("tunnel open request accepted - id " + tunnelId);

			connectionMeter.mark();
			tunnelChannel.onTunnelOpened(tunnelId);

			this.sendNextAfterResponse(ctx);
		}
		else if (HttpTunnelMessageUtils.isTunnelCloseResponse(response)) {
			if (LOG.isDebugEnabled()) {
				if (disconnecting.get())
					LOG.debug("server acknowledged disconnect for tunnel " + tunnelId);
				else
					LOG.debug("server closed tunnel " + tunnelId);
			}

			ctx.sendDownstream(postShutdownEvent);
		}
		else {
			if (LOG.isWarnEnabled())
				LOG.warn("unknown response (" + response.getStatus().getCode() + ") received for tunnel " + tunnelId + ", closing connection");

			ctx.getChannel().close();
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		final Throwable error = e.getCause();

		if (error instanceof IOException // Connection reset etc
			|| error instanceof ClosedChannelException || error instanceof IllegalArgumentException) { // Invalid
																										// protocol
																										// format
																										// -
																										// bots
																										// etc
			if (LOG.isDebugEnabled())
				LOG.debug("Exception from HttpTunnel send handler: " + error);

			return;
		}

		if (LOG.isWarnEnabled())
			LOG.warn("Exception from HttpTunnel send handler: " + error);
	}

	private void sendNextAfterResponse(ChannelHandlerContext ctx) {
		if (pendingRequestCount.decrementAndGet() > 0) {
			if (LOG.isDebugEnabled())
				LOG.debug("Immediately sending next send request for tunnel " + tunnelId);

			this.sendQueuedData(ctx);
		}
	}

	private synchronized void sendQueuedData(ChannelHandlerContext ctx) {
		if (disconnecting.get()) {
			if (LOG.isDebugEnabled())
				LOG.debug("sending close request for tunnel " + tunnelId);

			final HttpRequest closeRequest = HttpTunnelMessageUtils.createCloseTunnelRequest(tunnelChannel.getServerHostName(), tunnelId, tunnelChannel.getUserAgent());
			Channels.write(ctx, Channels.future(ctx.getChannel()), closeRequest);
		}
		else {
			if (LOG.isDebugEnabled())
				LOG.debug("sending next request for tunnel " + tunnelId);

			final TimedMessageEventWrapper wrapper = queuedWrites.poll();
			try {
				sendRequestTime = System.nanoTime();
				ctx.sendDownstream(wrapper.getEvent());
			}
			finally {
				wrapper.getContext().stop();
			}
		}
	}

	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		if (LOG.isDebugEnabled())
			LOG.debug("request to send data for tunnel " + tunnelId);

		final ChannelFuture future = e.getFuture();

		if (disconnecting.get()) {
			if (LOG.isWarnEnabled())
				LOG.warn("rejecting write request for tunnel " + tunnelId + " received after disconnect requested");

			final Exception error = new IllegalStateException("tunnel is closing");
			future.setFailure(error);

			return;
		}

		final ChannelBuffer data = (ChannelBuffer) e.getMessage();
		final HttpRequest request = HttpTunnelMessageUtils.createSendDataRequest(tunnelChannel.getServerHostName(), tunnelId, data, tunnelChannel.getUserAgent());

		final Channel channel = ctx.getChannel();
		final DownstreamMessageEvent translatedEvent = new DownstreamMessageEvent(channel, future, request, channel.getRemoteAddress());

		queuedWrites.offer(new TimedMessageEventWrapper(translatedEvent, requestTimer.time()));
		if (pendingRequestCount.incrementAndGet() == 1)
			this.sendQueuedData(ctx);
		else {
			if (LOG.isDebugEnabled())
				LOG.debug("write request for tunnel " + tunnelId + " queued");
		}
	}

	@Override
	public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
		requestSizes.update(e.getWrittenAmount());

		super.writeComplete(ctx, e);
	}

	@Override
	public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		this.shutdownTunnel(ctx, e);
	}

	@Override
	public void disconnectRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		this.shutdownTunnel(ctx, e);
	}

	@Override
	public void unbindRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		this.shutdownTunnel(ctx, e);
	}

	private void shutdownTunnel(ChannelHandlerContext ctx, ChannelStateEvent postShutdownEvent) {
		if (LOG.isDebugEnabled())
			LOG.debug("tunnel shutdown requested for send channel of tunnel " + tunnelId);

		if (!ctx.getChannel().isConnected()) {
			if (LOG.isDebugEnabled())
				LOG.debug("send channel of tunnel " + tunnelId + " is already disconnected");

			ctx.sendDownstream(postShutdownEvent);
			return;
		}

		if (!disconnecting.compareAndSet(false, true)) {
			if (LOG.isDebugEnabled())
				LOG.debug("tunnel shutdown process already initiated for tunnel " + tunnelId);

			return;
		}

		this.postShutdownEvent = postShutdownEvent;

		// if the channel is idle, send a close request immediately
		if (pendingRequestCount.incrementAndGet() == 1)
			this.sendQueuedData(ctx);
	}
}
