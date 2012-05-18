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
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import com.yammer.httptunnel.util.HttpTunnelMessageUtils;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;

/**
 * Pipeline component which controls the client poll loop to the server.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelClientChannelPollHandler extends SimpleChannelHandler {

	public static final String NAME = "server2client";

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelClientChannelPollHandler.class);

	private final Meter connectionMeter = Metrics.newMeter(HttpTunnelClientChannelPollHandler.class, "channelOpen", "channelOpen", TimeUnit.SECONDS);
	private final Meter pollMeter = Metrics.newMeter(HttpTunnelClientChannelPollHandler.class, "pollMeter", "pollMeter", TimeUnit.SECONDS);
	private final Histogram requestSizes = Metrics.newHistogram(HttpTunnelClientChannelPollHandler.class, "requestSize");

	private final HttpTunnelClientWorkerOwner tunnelChannel;

	private String tunnelId;
	private long pollTime;

	public HttpTunnelClientChannelPollHandler(HttpTunnelClientWorkerOwner tunnelChannel) {
		this.tunnelChannel = tunnelChannel;

		tunnelId = null;
		pollTime = 0;
	}

	public void setTunnelId(String tunnelId) {
		this.tunnelId = tunnelId;
	}

	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		if (tunnelChannel.isConnecting() || tunnelChannel.isConnected()) {
			if (LOG.isDebugEnabled())
				LOG.debug("Poll channel for tunnel " + tunnelId + " established");

			connectionMeter.mark();
			tunnelChannel.fullyEstablished();
		}

		// Send our first poll data request
		this.sendPoll(ctx.getChannel());
	}

	@Override
	public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		if (!tunnelChannel.isConnecting() && !tunnelChannel.isConnected())
			return;

		if (LOG.isDebugEnabled())
			LOG.debug("Poll channel for tunnel " + tunnelId + " failed");

		// TODO: What state was our last send in? if the last send failed stick
		// it on the front of the queue?

		// The poll channel was closed forcefully rather than by a shutdown
		tunnelChannel.underlyingChannelFailed();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		final HttpResponse response = (HttpResponse) e.getMessage();

		if (HttpTunnelMessageUtils.isOKResponse(response)) {
			if (LOG.isDebugEnabled()) {
				long rtt = System.nanoTime() - pollTime;
				LOG.debug("OK response received for poll on tunnel " + tunnelId + " after " + rtt + " ns");
			}

			tunnelChannel.onMessageReceived(response.getContent());
			this.sendPoll(ctx.getChannel());
		}
		else if (HttpTunnelMessageUtils.isPingResponse(response)) {
			if (LOG.isDebugEnabled()) {
				long rtt = System.nanoTime() - pollTime;
				LOG.debug("Ping response received for poll on tunnel " + tunnelId + " after " + rtt + " ns");
			}

			this.sendPoll(ctx.getChannel());
		}
		else if (HttpTunnelMessageUtils.isTunnelCloseResponse(response)) {
			tunnelChannel.onDisconnectRequest(Channels.future(ctx.getChannel()));
		}
		else {
			if (LOG.isWarnEnabled())
				LOG.warn("non-OK response received for poll on tunnel " + tunnelId);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		final Throwable error = e.getCause();

		if (error instanceof IOException // Connection reset etc
			|| error instanceof ClosedChannelException
			|| error instanceof IllegalArgumentException) {  // Invalid protocol format - bots etc
			if (LOG.isDebugEnabled())
				LOG.debug("Exception from HttpTunnel send handler: " + error);

			return;
		}

		if (LOG.isWarnEnabled())
			LOG.warn("Exception from HttpTunnel poll handler: " + error);
	}

	@Override
	public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
		requestSizes.update(e.getWrittenAmount());

		super.writeComplete(ctx, e);
	}

	private void sendPoll(Channel channel) {
		if (!channel.isOpen())
			return;

		pollTime = System.nanoTime();
		if (LOG.isDebugEnabled())
			LOG.debug("sending poll request for tunnel " + tunnelId);

		pollMeter.mark();

		final HttpRequest request = HttpTunnelMessageUtils.createReceiveDataRequest(tunnelChannel.getServerHostName(), tunnelId, tunnelChannel.getUserAgent());
		channel.write(request);
	}
}
