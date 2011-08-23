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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.http.BindState;
import org.jboss.netty.channel.socket.http.ConnectState;
import org.jboss.netty.channel.socket.http.IncomingBuffer;
import org.jboss.netty.channel.socket.http.SaturationManager;
import org.jboss.netty.channel.socket.http.SaturationStateChange;
import org.jboss.netty.channel.socket.http.util.ConsolidatingFutureListener;
import org.jboss.netty.channel.socket.http.util.HttpTunnelMessageUtils;
import org.jboss.netty.channel.socket.http.util.WriteFragmenter;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * The client end of an HTTP tunnel, created by an
 * {@link HttpTunnelClientChannelFactory}. Channels of this type are designed to
 * emulate a normal TCP based socket channel as far as is feasible within the
 * limitations of the HTTP 1.1 protocol, and the usage patterns permitted by
 * commonly used HTTP proxies and firewalls.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
public class HttpTunnelClientChannel extends AbstractChannel implements SocketChannel {

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelClientChannel.class);

	private final SocketChannel sendChannel;
	private final SocketChannel pollChannel;

	private final HttpTunnelClientChannelConfig config;
	private final SaturationManager saturationManager;

	private final AtomicBoolean opened;
	private final AtomicReference<BindState> bindState;
	private final AtomicReference<ConnectState> connectState;
	private final AtomicReference<ChannelFuture> connectFuture;

	private volatile String tunnelId;
	private volatile InetSocketAddress remoteAddress;

	private final WorkerCallbacks callbackProxy;
	private final IncomingBuffer<ChannelBuffer> incomingBuffer;

	/**
	 * @see HttpTunnelClientChannelFactory#newChannel(ChannelPipeline)
	 */
	protected HttpTunnelClientChannel(ChannelFactory factory, ChannelPipeline pipeline, HttpTunnelClientChannelSink sink, ClientSocketChannelFactory outboundFactory, ChannelGroup realConnections, ExecutorService bossExecutor, ExecutorService workerExecutor) {
		super (null, factory, pipeline, sink);

		callbackProxy = new WorkerCallbacks();

		incomingBuffer = new IncomingBuffer<ChannelBuffer>(this, bossExecutor, workerExecutor);

		sendChannel = outboundFactory.newChannel(this.createSendPipeline());
		pollChannel = outboundFactory.newChannel(this.createPollPipeline());;

		config = new DefaultHttpTunnelClientChannelConfig(sendChannel.getConfig(), pollChannel.getConfig());
		saturationManager = new SaturationManager(config.getWriteBufferLowWaterMark(), config.getWriteBufferHighWaterMark());

		opened = new AtomicBoolean(true);
		bindState = new AtomicReference<BindState>(BindState.UNBOUND);
		connectState = new AtomicReference<ConnectState>(ConnectState.DISCONNECTED);
		connectFuture = new AtomicReference<ChannelFuture>(null);

		tunnelId = null;
		remoteAddress = null;

		realConnections.add(sendChannel);
		realConnections.add(pollChannel);

		Channels.fireChannelOpen(this);
	}

	@Override
	public HttpTunnelClientChannelConfig getConfig() {
		return config;
	}

	@Override
	public boolean isBound() {
		return bindState.get() == BindState.BOUND;
	}

	@Override
	public boolean isConnected() {
		return connectState.get() == ConnectState.CONNECTED;
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return this.isBound() ? sendChannel.getLocalAddress() : null;
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return this.isConnected() ? remoteAddress : null;
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

		// First disconnect
		internalDisconnect(Channels.future(this)).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				// Then unbind
				internalUnbind(Channels.future(HttpTunnelClientChannel.this)).addListener(new ChannelFutureListener() {
					@Override
					public void operationComplete(ChannelFuture future) throws Exception {
						// Finally close
						internalDoClose(closeFuture);
					}
				});
			}
		});

		return closeFuture;
	}

	synchronized ChannelFuture internalDoClose(ChannelFuture future) {
		if (LOG.isDebugEnabled())
			LOG.debug("HTTP Tunnel client channel closing");

		int openChannels = 0;
		if (sendChannel.isOpen()) openChannels++;
		if (pollChannel.isOpen()) openChannels++;

		// If there are no open channels we don't need to try close them
		if (openChannels == 0) {
			this.setClosed();
			future.setSuccess();

			return future;
		}

		final ChannelFutureListener closeListener = new ConsolidatingFutureListener(future, openChannels) {
			@Override
			protected void futureFailed(ChannelFuture future) {
				if (LOG.isWarnEnabled())
					LOG.warn("Failed to close one of the child channels of tunnel " + tunnelId);

				setClosed();
				super.futureFailed(future);
			}

			@Override
			protected void allFuturesComplete() {
				if (LOG.isDebugEnabled())
					LOG.debug("Tunnel " + tunnelId + " closed");

				setClosed();
				super.allFuturesComplete();
			}
		};

		sendChannel.close().addListener(closeListener);
		pollChannel.close().addListener(closeListener);

		return future;
	}

	synchronized ChannelFuture internalBind(InetSocketAddress addr, ChannelFuture future) {
		// Update the bind state - if we fail then throw an illegal state exception
		if (!bindState.compareAndSet(BindState.UNBOUND, BindState.BINDING)) {
			final Exception error = new IllegalStateException("Already bound or in the process of binding");

			future.setFailure(error);
			Channels.fireExceptionCaught(this, error);

			return future;
		}

		return this.internalDoBind(addr, future);
	}

	synchronized ChannelFuture internalDoBind(final InetSocketAddress addr, ChannelFuture future) {
		if (LOG.isDebugEnabled())
			LOG.debug("HTTP Tunnel client channel binding to " + addr);

		final ChannelFutureListener bindListener = new ConsolidatingFutureListener(future, 2) {
			@Override
			protected void allFuturesComplete() {
				bindState.set(BindState.BOUND);
				Channels.fireChannelBound(HttpTunnelClientChannel.this, addr);

				super.allFuturesComplete();
			}

			@Override
			protected void futureFailed(ChannelFuture future) {
				bindState.set(BindState.UNBOUND);
				Channels.fireExceptionCaught(HttpTunnelClientChannel.this, future.getCause());

				super.futureFailed(future);
			}
		};

		// bind the send channel to the specified local address, and the poll
		// channel to an ephemeral port on the same interface as the send channel
		final InetSocketAddress pollAddr;
		if (addr.isUnresolved())
			pollAddr = InetSocketAddress.createUnresolved(addr.getHostName(), 0);
		else
			pollAddr = new InetSocketAddress(addr.getAddress(), 0);

		sendChannel.bind(addr).addListener(bindListener);
		pollChannel.bind(pollAddr).addListener(bindListener);

		return future;
	}

	synchronized ChannelFuture internalUnbind(ChannelFuture future) {
		if (!bindState.compareAndSet(BindState.BOUND, BindState.UNBINDING)) {
			future.setSuccess();
			return future;
		}

		if (LOG.isDebugEnabled())
			LOG.debug("HTTP Tunnel client channel unbinding");

		int boundChannels = 0;
		if (sendChannel.isBound()) boundChannels++;
		if (pollChannel.isBound()) boundChannels++;

		// If there are no bound channels we don't need to try unbind them
		if (boundChannels == 0) {
			bindState.set(BindState.UNBOUND);
			Channels.fireChannelUnbound(this);

			future.setSuccess();
			return future;
		}

		final ChannelFutureListener unbindListener = new ConsolidatingFutureListener(future, boundChannels) {
			@Override
			protected void allFuturesComplete() {
				bindState.set(BindState.UNBOUND);
				Channels.fireChannelUnbound(HttpTunnelClientChannel.this);

				super.allFuturesComplete();
			}

			@Override
			protected void futureFailed(ChannelFuture future) {
				bindState.set(BindState.UNBOUND);
				Channels.fireExceptionCaught(HttpTunnelClientChannel.this, future.getCause());

				super.futureFailed(future);
			}
		};

		sendChannel.unbind().addListener(unbindListener);
		pollChannel.unbind().addListener(unbindListener);

		return future;
	}

	void internalConnect(InetSocketAddress addr, ChannelFuture future) {
		// Update the connection state - if we fail then throw an illegal state exception
		if (!connectState.compareAndSet(ConnectState.DISCONNECTED, ConnectState.CONNECTING))
			return;

		if (LOG.isDebugEnabled())
			LOG.debug("HTTP Tunnel client channel connecting to " + addr);

		/*
		 * if we are using a proxy, the remoteAddress is swapped here for the
		 * address of the proxy. The send and poll channels can later ask for
		 * the correct server address using getServerHostName().
		 */

		remoteAddress = addr;
		connectFuture.set(future);

		// Check if we are already bound or should bind to an address
		if (bindState.compareAndSet(BindState.UNBOUND, BindState.BINDING))
			this.internalDoBind(new InetSocketAddress(0), Channels.future(this));

		final SocketAddress connectAddr;
		if (config.getProxyAddress() != null)
			connectAddr = config.getProxyAddress();
		else
			connectAddr = remoteAddress;

		Channels.connect(sendChannel, connectAddr);
	}

	synchronized ChannelFuture internalDisconnect(ChannelFuture future) {
		if (!connectState.compareAndSet(ConnectState.CONNECTED, ConnectState.DISCONNECTING)) {
			future.setSuccess();
			return future;
		}

		if (LOG.isDebugEnabled())
			LOG.debug("HTTP Tunnel client channel disconnecting");

		int connectedChannels = 0;
		if (sendChannel.isConnected()) connectedChannels++;
		if (pollChannel.isConnected()) connectedChannels++;

		// If there are no connected channels we don't need to try disconnect them
		if (connectedChannels == 0) {
			remoteAddress = null;

			connectState.set(ConnectState.DISCONNECTED);
			Channels.fireChannelDisconnected(this);

			future.setSuccess();
			return future;
		}

		final ChannelFutureListener disconnectListener = new ConsolidatingFutureListener(future, connectedChannels) {
			@Override
			protected void allFuturesComplete() {
				remoteAddress = null;

				connectState.set(ConnectState.DISCONNECTED);
				Channels.fireChannelDisconnected(HttpTunnelClientChannel.this);

				super.allFuturesComplete();
			}

			@Override
			protected void futureFailed(ChannelFuture future) {
				connectState.set(ConnectState.DISCONNECTED);
				Channels.fireExceptionCaught(HttpTunnelClientChannel.this, future.getCause());

				super.futureFailed(future);
			}
		};

		sendChannel.disconnect().addListener(disconnectListener);
		pollChannel.disconnect().addListener(disconnectListener);

		return future;
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

		Channels.write(sendChannel, messageBuffer).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				updateSaturationStatus(-messageSize);

				if (future.isSuccess()) {
					// Fire a write complete event
					Channels.fireWriteComplete(HttpTunnelClientChannel.this, messageSize);

					messageFuture.setSuccess();
				}
				else
					messageFuture.setFailure(future.getCause());
			}
		});

		return messageFuture;
	}

	synchronized ChannelFuture internalSetInterestOps(int ops, ChannelFuture future) {
		super.setInterestOpsNow(ops);
		Channels.fireChannelInterestChanged(this);

		// Update the incoming buffer
		incomingBuffer.onInterestOpsChanged();

		future.setSuccess();
		return future;
	}

	synchronized void internalFailConnect(Throwable cause) {
		if (LOG.isDebugEnabled())
			LOG.debug("HTTP Tunnel client channel failed");

		if (sendChannel.isOpen())
			sendChannel.close();

		if (pollChannel.isOpen())
			pollChannel.close();

		remoteAddress = null;

		connectState.set(ConnectState.DISCONNECTED);

		final ChannelFuture future = connectFuture.getAndSet(null);
		if (future != null)
			future.setFailure(cause);

		Channels.fireExceptionCaught(this, cause);
	}

	private ChannelPipeline createSendPipeline() {
		final ChannelPipeline pipeline = Channels.pipeline();

		pipeline.addLast("reqencoder", new HttpRequestEncoder()); // downstream
		pipeline.addLast("respdecoder", new HttpResponseDecoder()); // upstream
		pipeline.addLast("aggregator", new HttpChunkAggregator(HttpTunnelMessageUtils.MAX_BODY_SIZE)); // upstream
		pipeline.addLast(HttpTunnelClientChannelSendHandler.NAME, new HttpTunnelClientChannelSendHandler(callbackProxy)); // both
		pipeline.addLast("writeFragmenter", new WriteFragmenter(HttpTunnelMessageUtils.MAX_BODY_SIZE));

		return pipeline;
	}

	private ChannelPipeline createPollPipeline() {
		final ChannelPipeline pipeline = Channels.pipeline();

		pipeline.addLast("reqencoder", new HttpRequestEncoder()); // downstream
		pipeline.addLast("respdecoder", new HttpResponseDecoder()); // upstream
		pipeline.addLast("aggregator", new HttpChunkAggregator(HttpTunnelMessageUtils.MAX_BODY_SIZE)); // upstream
		pipeline.addLast(HttpTunnelClientChannelPollHandler.NAME, new HttpTunnelClientChannelPollHandler(callbackProxy)); // both

		return pipeline;
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

	/**
	 * Contains the implementing methods of HttpTunnelClientWorkerOwner, so that
	 * these are hidden from the public API.
	 */
	private class WorkerCallbacks implements HttpTunnelClientWorkerOwner {

		private String serverHostName;

		public WorkerCallbacks() {
			serverHostName = null;
		}

		@Override
		public void onConnectRequest(ChannelFuture connectFuture, InetSocketAddress remoteAddress) {
			HttpTunnelClientChannel.this.internalConnect(remoteAddress, connectFuture);
		}

		@Override
		public void onDisconnectRequest(ChannelFuture connectFuture) {
			HttpTunnelClientChannel.this.internalClose(connectFuture);
		}

		@Override
		public void onTunnelOpened(String tunnelId) {
			HttpTunnelClientChannel.this.tunnelId = tunnelId;

			final HttpTunnelClientChannelPollHandler pollHandler = pollChannel.getPipeline().get(HttpTunnelClientChannelPollHandler.class);
			pollHandler.setTunnelId(tunnelId);

			Channels.connect(pollChannel, sendChannel.getRemoteAddress());
		}

		@Override
		public void fullyEstablished() {
			connectState.set(ConnectState.CONNECTED);
			connectFuture.get().setSuccess();

			Channels.fireChannelConnected(HttpTunnelClientChannel.this, remoteAddress);
		}

		@Override
		public void onMessageReceived(ChannelBuffer message) {
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

		@Override
		public String getServerHostName() {
			if (serverHostName == null)
				serverHostName = HttpTunnelMessageUtils.convertToHostString(remoteAddress);

			return serverHostName;
		}

		@Override
		public String getUserAgent() {
			return config.getUserAgent();
		}
	}
}
