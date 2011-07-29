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
import java.net.SocketAddress;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.http.util.HttpTunnelMessageUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Upstream handler which is responsible for determining whether a received HTTP
 * request is a legal tunnel request, and if so, invoking the appropriate
 * request method on the {@link ServerMessageSwitch} to service the request.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelAcceptedChannelHandler extends SimpleChannelUpstreamHandler {

	public static final String NAME = "AcceptedServerChannelRequestDispatch";

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelAcceptedChannelHandler.class);

	private final HttpTunnelServerChannel parent;

	public HttpTunnelAcceptedChannelHandler(HttpTunnelServerChannel parent) {
		this.parent = parent;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		final HttpRequest request = (HttpRequest) e.getMessage();

		try {
			// send channel
			if (HttpTunnelMessageUtils.isOpenTunnelRequest(request))
				handleOpenTunnel(ctx);
			// send channel
			else if (HttpTunnelMessageUtils.isSendDataRequest(request))
				handleSendData(ctx, request);
			// poll channel
			else if (HttpTunnelMessageUtils.isReceiveDataRequest(request))
				handleReceiveData(ctx, request);
			// send channel
			else if (HttpTunnelMessageUtils.isCloseTunnelRequest(request))
				handleCloseTunnel(ctx, request);
			else
				throw new IllegalArgumentException("invalid request to netty HTTP tunnel gateway");
		}
		catch (Exception ex) {
			this.respondWithRejection(ctx, request, ex.getMessage());
		}
	}

	private void handleOpenTunnel(ChannelHandlerContext ctx) {
		final InetSocketAddress remoteAddress = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
		final HttpTunnelAcceptedChannel tunnel = parent.createTunnel(remoteAddress);

		if (LOG.isDebugEnabled())
			LOG.debug("open tunnel request received from " + ctx.getChannel().getRemoteAddress() + " - allocated ID " + tunnel.getTunnelId());

		this.respondWith(ctx, HttpTunnelMessageUtils.createTunnelOpenResponse(tunnel.getTunnelId()));
	}

	private void handleCloseTunnel(ChannelHandlerContext ctx, HttpRequest request) {
		final HttpTunnelAcceptedChannel tunnel = parent.getTunnel(HttpTunnelMessageUtils.extractTunnelId(request));
		if (tunnel == null) {
			// If the tunnel doesn't exist then close it on the other end
			this.respondWith(ctx, HttpTunnelMessageUtils.createTunnelCloseResponse()).addListener(ChannelFutureListener.CLOSE);
			return;
		}

		if (LOG.isDebugEnabled())
			LOG.debug("close tunnel request received for tunnel " + tunnel.getTunnelId());

		tunnel.internalClose(false, Channels.future(tunnel));
		this.respondWith(ctx, HttpTunnelMessageUtils.createTunnelCloseResponse()).addListener(ChannelFutureListener.CLOSE);
	}

	private void handleSendData(ChannelHandlerContext ctx, HttpRequest request) {
		final HttpTunnelAcceptedChannel tunnel = parent.getTunnel(HttpTunnelMessageUtils.extractTunnelId(request));
		if (tunnel == null) {
			// If the tunnel doesn't exist then close it on the other end
			this.respondWith(ctx, HttpTunnelMessageUtils.createTunnelCloseResponse()).addListener(ChannelFutureListener.CLOSE);
			return;
		}

		if (LOG.isDebugEnabled())
			LOG.debug("send data request received for tunnel " + tunnel.getTunnelId());

		if (HttpHeaders.getContentLength(request, 0) == 0 || request.getContent() == null || request.getContent().readableBytes() == 0) {
			this.respondWithRejection(ctx, request, "Send data requests must contain data");
			return;
		}

		tunnel.internalReceiveMessage(request.getContent());
		this.respondWith(ctx, HttpTunnelMessageUtils.createSendDataResponse());
	}

	private void handleReceiveData(ChannelHandlerContext ctx, HttpRequest request) {
		final HttpTunnelAcceptedChannel tunnel = parent.getTunnel(HttpTunnelMessageUtils.extractTunnelId(request));
		if (tunnel == null) {
			// If the tunnel doesn't exist then close it on the other end
			this.respondWith(ctx, HttpTunnelMessageUtils.createTunnelCloseResponse()).addListener(ChannelFutureListener.CLOSE);
			return;
		}

		if (LOG.isDebugEnabled())
			LOG.debug("poll data request received for tunnel " + tunnel.getTunnelId());

		tunnel.pollQueuedData(ctx.getChannel());
	}

	/**
	 * Sends the provided response back on the channel, returning the created
	 * ChannelFuture for this operation.
	 */
	private ChannelFuture respondWith(ChannelHandlerContext ctx, HttpResponse response) {
		return Channels.write(ctx.getChannel(), response);
	}

	/**
	 * Sends an HTTP 400 message back to on the channel with the specified error
	 * message, and asynchronously closes the channel after this is successfully
	 * sent.
	 */
	private void respondWithRejection(ChannelHandlerContext ctx, HttpRequest rejectedRequest, String errorMessage) {
		if (LOG.isWarnEnabled()) {
			final SocketAddress remoteAddress = ctx.getChannel().getRemoteAddress();

			String tunnelId = HttpTunnelMessageUtils.extractTunnelId(rejectedRequest);
			if (tunnelId == null)
				tunnelId = "<UNKNOWN>";

			LOG.warn("Rejecting request from " + remoteAddress + " representing tunnel " + tunnelId + ": " + errorMessage);
		}

		final HttpResponse rejection = HttpTunnelMessageUtils.createRejection(rejectedRequest, errorMessage);
		this.respondWith(ctx, rejection).addListener(ChannelFutureListener.CLOSE);
	}
}
