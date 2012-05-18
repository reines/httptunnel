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

package com.yammer.httptunnel.server;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.channel.AbstractServerChannel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import com.yammer.httptunnel.state.BindState;
import com.yammer.httptunnel.util.TunnelIdGenerator;

/**
 * The server end of an HTTP tunnel, created by an
 * {@link HttpTunnelServerChannelFactory}. Channels of this type are designed to
 * emulate a normal TCP based server socket channel as far as is feasible within
 * the limitations of the HTTP 1.0 protocol, and the usage patterns permitted by
 * commonly used HTTP proxies and firewalls.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public class HttpTunnelServerChannel extends AbstractServerChannel implements ServerSocketChannel {

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelServerChannel.class);

	private static final Random random;

	static {
		random = new Random();
	}

	private final String tunnelIdPrefix;
	private final ConcurrentHashMap<String, HttpTunnelAcceptedChannel> tunnels;
	private final ServerSocketChannel realChannel;
	private final HttpTunnelServerChannelConfig config;

	private final AtomicBoolean opened;
	private final AtomicReference<BindState> bindState;

	protected HttpTunnelServerChannel(ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink, ServerSocketChannelFactory inboundFactory, ChannelGroup realConnections) {
		super(factory, pipeline, sink);

		tunnelIdPrefix = Long.toHexString(random.nextLong());
		tunnels = new ConcurrentHashMap<String, HttpTunnelAcceptedChannel>();

		config = new HttpTunnelServerChannelConfig();
		realChannel = inboundFactory.newChannel(this.createRealPipeline(realConnections));
		config.setRealChannel(realChannel);

		opened = new AtomicBoolean(true);
		bindState = new AtomicReference<BindState>(BindState.UNBOUND);

		realConnections.add(realChannel);

		Channels.fireChannelOpen(this);
	}

	@Override
	public HttpTunnelServerChannelConfig getConfig() {
		return config;
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return this.isBound() ? realChannel.getLocalAddress() : null;
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return null; // server channels never have a remote address
	}

	@Override
	public boolean isBound() {
		return bindState.get() == BindState.BOUND;
	}

	@Override
	protected synchronized boolean setClosed() {
		final boolean success = super.setClosed();
		Channels.fireChannelClosed(this);

		return success;
	}

	synchronized ChannelFuture internalClose(final ChannelFuture closeFuture) {
		if (!opened.getAndSet(false)) {
			closeFuture.setSuccess();
			return closeFuture;
		}

		// First unbind
		internalUnbind(Channels.future(this)).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				// Finally close
				internalDoClose(closeFuture);
			}
		});

		return closeFuture;
	}

	synchronized ChannelFuture internalDoClose(final ChannelFuture closeFuture) {
		if (LOG.isDebugEnabled())
			LOG.debug("HTTP Tunnel server channel closing");

		final ChannelFutureListener closeListener = new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					if (LOG.isDebugEnabled())
						LOG.debug("Tunnel closed");

					setClosed();

					if (closeFuture != null)
						closeFuture.setSuccess();
				}
				else {
					if (LOG.isWarnEnabled())
						LOG.warn("Failed to close tunnel");

					setClosed();

					if (closeFuture != null)
						closeFuture.setFailure(future.getCause());
				}
			}
		};

		realChannel.close().addListener(closeListener);

		return closeFuture;
	}

	synchronized ChannelFuture internalBind(final InetSocketAddress addr, final ChannelFuture bindFuture) {
		// Update the bind state - if we fail then throw an illegal state
		// exception
		if (!bindState.compareAndSet(BindState.UNBOUND, BindState.BINDING)) {
			final Exception error = new IllegalStateException("Already bound or in the process of binding");

			bindFuture.setFailure(error);
			Channels.fireExceptionCaught(this, error);

			return bindFuture;
		}

		if (LOG.isDebugEnabled())
			LOG.debug("HTTP Tunnel server channel binding to " + addr);

		final ChannelFutureListener bindListener = new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					bindState.set(BindState.BOUND);
					Channels.fireChannelBound(HttpTunnelServerChannel.this, addr);

					if (bindFuture != null)
						bindFuture.setSuccess();
				}
				else {
					bindState.set(BindState.UNBOUND);
					Channels.fireExceptionCaught(HttpTunnelServerChannel.this, future.getCause());

					if (bindFuture != null)
						bindFuture.setFailure(future.getCause());
				}
			}
		};

		realChannel.bind(addr).addListener(bindListener);

		return bindFuture;
	}

	synchronized ChannelFuture internalUnbind(final ChannelFuture unbindFuture) {
		if (!bindState.compareAndSet(BindState.BOUND, BindState.UNBINDING)) {
			unbindFuture.setSuccess();
			return unbindFuture;
		}

		final ChannelFutureListener unbindListener = new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					bindState.set(BindState.UNBOUND);
					Channels.fireChannelUnbound(HttpTunnelServerChannel.this);

					if (unbindFuture != null)
						unbindFuture.setSuccess();
				}
				else {
					bindState.set(BindState.UNBOUND);
					Channels.fireExceptionCaught(HttpTunnelServerChannel.this, future.getCause());

					if (unbindFuture != null)
						unbindFuture.setFailure(future.getCause());
				}
			}
		};

		realChannel.unbind().addListener(unbindListener);

		return unbindFuture;
	}

	public HttpTunnelAcceptedChannel createTunnel(InetSocketAddress remoteAddress) {
		final TunnelIdGenerator tunnelIdGenerator = config.getTunnelIdGenerator();

		final ChannelPipeline childPipeline;

		try {
			childPipeline = config.getPipelineFactory().getPipeline();
		}
		catch (Exception e) {
			throw new ChannelPipelineException("Failed to initialize a pipeline.", e);
		}

		final String tunnelId = String.format("%s_%s", tunnelIdPrefix, tunnelIdGenerator.generateId());
		final HttpTunnelAcceptedChannel tunnel = new HttpTunnelAcceptedChannel(this, this.getFactory(), childPipeline, new HttpTunnelAcceptedChannelSink(), remoteAddress, tunnelId);

		tunnels.put(tunnelId, tunnel);

		Channels.fireChannelOpen(tunnel);
		Channels.fireChannelBound(tunnel, this.getLocalAddress());
		Channels.fireChannelConnected(tunnel, remoteAddress);

		return tunnel;
	}

	public HttpTunnelAcceptedChannel getTunnel(String tunnelId) {
		if (tunnelId == null)
			throw new IllegalArgumentException("no tunnel id specified in request");

		return tunnels.get(tunnelId);
	}

	public HttpTunnelAcceptedChannel removeTunnel(String tunnelId) {
		return tunnels.remove(tunnelId);
	}

	private ChannelPipeline createRealPipeline(ChannelGroup realConnections) {
		final ChannelPipelineFactory realPipelineFactory = new HttpTunnelAcceptedChannelPipelineFactory(this);

		final ChannelPipeline pipeline;
		try {
			pipeline = realPipelineFactory.getPipeline();
		}
		catch (Exception e) {
			throw new ChannelPipelineException("Failed to initialize a pipeline.", e);
		}

		pipeline.addFirst(HttpTunnelServerChannelHandler.NAME, new HttpTunnelServerChannelHandler(this, realPipelineFactory, realConnections));

		return pipeline;
	}
}
