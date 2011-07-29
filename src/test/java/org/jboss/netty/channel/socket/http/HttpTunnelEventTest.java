package org.jboss.netty.channel.socket.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.http.client.HttpTunnelClientChannelFactory;
import org.jboss.netty.channel.socket.http.server.HttpTunnelServerChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpTunnelEventTest {

	public static final int TIMEOUT = 2;

	private OpenCloseIncomingChannelHandler serverHandler;
	private OpenCloseOutgoingChannelHandler clientHandler;

	private Channel serverChannel;
	private Channel clientChannel;
	private Channel acceptedChannel;

	private Channel createServerChannel(InetSocketAddress addr, ChannelPipelineFactory pipelineFactory) {
		// TCP socket factory
		ServerSocketChannelFactory socketFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

		// HTTP socket factory
		socketFactory = new HttpTunnelServerChannelFactory(socketFactory);

		final ServerBootstrap bootstrap = new ServerBootstrap(socketFactory);
		bootstrap.setPipelineFactory(pipelineFactory);

		bootstrap.setOption("child.tcpNoDelay", true);
		bootstrap.setOption("reuseAddress", true);

		return bootstrap.bind(addr);
	}

	private Channel createClientChannel(InetSocketAddress addr, ChannelPipelineFactory pipelineFactory) {
		// TCP socket factory
		ClientSocketChannelFactory socketFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

		// HTTP socket factory
		socketFactory = new HttpTunnelClientChannelFactory(socketFactory);

		final ClientBootstrap bootstrap = new ClientBootstrap(socketFactory);
		bootstrap.setPipelineFactory(pipelineFactory);

		bootstrap.setOption("tcpNoDelay", true);

		final ChannelFuture future = bootstrap.connect(addr);

		try { future.await(TIMEOUT, TimeUnit.SECONDS); } catch (InterruptedException e) { }

		// If we managed to connect then set the channel and type
		if (future.isSuccess())
			return future.getChannel();

		// Otherwise cancel the attempt and give up
		future.cancel();
		return null;
	}

	@Before
	public void setUp() throws InterruptedException {
		final InetSocketAddress addr = new InetSocketAddress("localhost", 8181);

		serverHandler = new OpenCloseIncomingChannelHandler();
		serverChannel = this.createServerChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new StringEncoder(), new StringDecoder(), serverHandler);
			}
		});

		// Server should be open and bound
		assertTrue("server isn't open after connect", serverChannel.isOpen());
		assertTrue("server isn't bound after connect", serverChannel.isBound());

		clientHandler = new OpenCloseOutgoingChannelHandler();
		clientChannel = this.createClientChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new StringEncoder(), new StringDecoder(), clientHandler);
			}
		});

		// Check we actually managed to connect
		assertTrue("failed to connect", clientChannel != null);

		acceptedChannel = serverHandler.getChannel();
		assertTrue("no accepted channel found, the channelOpen event is most likely missing", acceptedChannel != null);

		// Client channel should be open, bound, and connected
		assertTrue("client isn't open after connect", clientChannel.isOpen());
		assertTrue("client isn't bound after connect", clientChannel.isBound());
		assertTrue("client isn't connected after connect", clientChannel.isConnected());

		// Server channel should be open and bound, but *maybe* not yet connected
		assertTrue("server isn't open after connect", acceptedChannel.isOpen());
		assertTrue("server isn't bound after connect", acceptedChannel.isBound());
	}

	@After
	public void tearDown() {
		if (serverChannel != null && serverChannel.isOpen())
			serverChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		if (clientChannel != null && clientChannel.isOpen())
			clientChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		if (acceptedChannel != null && acceptedChannel.isOpen())
			acceptedChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);
	}

	/**
	 * Here we close the client first, which should close the client channel and
	 * the server accepted channel, but not the server channel itself.
	 */
	@Test
	public void testOpenCloseClientChannel() throws InterruptedException {
		// Send a test message
		final String message = "hello world";
		assertTrue("failed to write message", clientChannel.write(message).awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));

		// Close the channel
		assertTrue("client failed to close", clientChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));

		// Check we received the correct client events
		clientHandler.assertSatisfied(TIMEOUT);

		// Check we received the correct server events
		serverHandler.assertSatisfied(TIMEOUT);

		assertEquals("the received message doesn't match the sent message", message, serverHandler.getMessageReceived());

		// Client channel shouldn't be open, bound, or connected
		assertTrue("client is connected after close", !clientChannel.isConnected());
		assertTrue("client is bound after close", !clientChannel.isBound());
		assertTrue("client is open after close", !clientChannel.isOpen());

		// Accepted channel shouldn't be open, bound, or connected
		assertTrue("accepted is connected after close", !acceptedChannel.isConnected());
		assertTrue("accepted is bound after close", !acceptedChannel.isBound());
		assertTrue("accepted is open after close", !acceptedChannel.isOpen());

		// Server should be open and bound
		assertTrue("server isn't bound", serverChannel.isBound());
		assertTrue("server isn't open", serverChannel.isOpen());
	}

	/**
	 * Here we close the server first, which should close the server channel
	 * itself, the server accepted channel, and the client channel.
	 */
	@Test
	public void testOpenCloseServerChannel() throws InterruptedException {
		// Send a test message
		final String message = "hello world";
		assertTrue("failed to write message", clientChannel.write(message).awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));

		// Give a second for the message to reach the server before we kill off the server
		Thread.sleep(1000);

		// Close the channel
		assertTrue("server failed to close", serverChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));

		assertEquals("the received message doesn't match the sent message", message, serverHandler.getMessageReceived());

		// Client channel shouldn't be open, bound, or connected
		assertTrue("client isn't connected after close", clientChannel.isConnected());
		assertTrue("client isn't bound after close", clientChannel.isBound());
		assertTrue("client isn't open after close", clientChannel.isOpen());

		// Accepted channel shouldn't be open, bound, or connected
		assertTrue("accepted isn't connected after close", acceptedChannel.isConnected());
		assertTrue("accepted isn't bound after close", acceptedChannel.isBound());
		assertTrue("accepted isn't open after close", acceptedChannel.isOpen());

		// Server shouldn't be open or bound
		assertTrue("server is connected after close", !serverChannel.isConnected());
		assertTrue("server is bound after close", !serverChannel.isBound());
		assertTrue("server is open after close", !serverChannel.isOpen());
	}

	/**
	 * Here we close the accepted channel first, which should close the server
	 * accepted channel and the client channel, but not the server itself.
	 */
	@Test
	public void testOpenCloseAcceptedChannel() throws InterruptedException {
		// Send a test message
		final String message = "hello world";
		assertTrue("failed to write message", clientChannel.write(message).awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));

		// Give a second for the message to reach the server before we kill off the server
		Thread.sleep(1000);

		// Close the channel
		assertTrue("client failed to close", acceptedChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));

		// Check we received the correct client events
		clientHandler.assertSatisfied(TIMEOUT);

		// Check we received the correct server events
		serverHandler.assertSatisfied(TIMEOUT);

		assertEquals("the received message doesn't match the sent message", message, serverHandler.getMessageReceived());

		// Client channel shouldn't be open, bound, or connected
		assertTrue("client is connected after close", !clientChannel.isConnected());
		assertTrue("client is bound after close", !clientChannel.isBound());
		assertTrue("client is open after close", !clientChannel.isOpen());

		// Accepted channel shouldn't be open, bound, or connected
		assertTrue("accepted is connected after close", !acceptedChannel.isConnected());
		assertTrue("accepted is bound after close", !acceptedChannel.isBound());
		assertTrue("accepted is open after close", !acceptedChannel.isOpen());

		// Server should be open and bound
		assertTrue("server isn't bound", serverChannel.isBound());
		assertTrue("server isn't open", serverChannel.isOpen());
	}
}
