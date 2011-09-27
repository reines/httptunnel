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
package org.jboss.netty.channel.socket.http.client;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.http.util.HttpTunnelMessageUtils;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Pipeline component which controls the client poll loop to the server.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelClientChannelPollHandler extends SimpleChannelHandler {

	public static final String NAME = "server2client";

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelClientChannelPollHandler.class);

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
		if (LOG.isDebugEnabled())
			LOG.debug("Poll channel for tunnel " + tunnelId + " established");

		tunnelChannel.fullyEstablished();
		this.sendPoll(ctx.getChannel());
	}

	@Override
	public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		if (!tunnelChannel.isConnected())
			return;

		if (LOG.isDebugEnabled())
			LOG.debug("Poll channel for tunnel " + tunnelId + " failed");

		// The poll channel was closed forcefully rather than by a shutdown
		tunnelChannel.underlyingChannelFailed();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		final HttpResponse response = (HttpResponse) e.getMessage();

		if (HttpTunnelMessageUtils.isOKResponse(response)) {
			long rtTime = System.nanoTime() - pollTime;
			if (LOG.isDebugEnabled())
				LOG.debug("OK response received for poll on tunnel " + tunnelId + " after " + rtTime + " ns");

			tunnelChannel.onMessageReceived(response.getContent());
			this.sendPoll(ctx.getChannel());
		}
		else if (HttpTunnelMessageUtils.isPingResponse(response)) {
			long rtTime = System.nanoTime() - pollTime;
			if (LOG.isDebugEnabled())
				LOG.debug("Ping response received for poll on tunnel " + tunnelId + " after " + rtTime + " ns");

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

		if (error instanceof IOException				// Connection reset etc
		|| error instanceof ClosedChannelException
		|| error instanceof IllegalArgumentException) {	// Invalid protocol format - bots etc
			if (LOG.isDebugEnabled())
				LOG.debug("Exception from HttpTunnel send handler: " + error);

			return;
		}

		if (LOG.isWarnEnabled())
			LOG.warn("Exception from HttpTunnel poll handler: " + error);
	}

	private void sendPoll(Channel channel) {
		if (!channel.isOpen())
			return;

		pollTime = System.nanoTime();
		if (LOG.isDebugEnabled())
			LOG.debug("sending poll request for tunnel " + tunnelId);

		final HttpRequest request = HttpTunnelMessageUtils.createReceiveDataRequest(tunnelChannel.getServerHostName(), tunnelId, tunnelChannel.getUserAgent());
		channel.write(request);
	}
}
