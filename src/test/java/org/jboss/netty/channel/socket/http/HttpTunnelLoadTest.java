package org.jboss.netty.channel.socket.http;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.http.util.NettyTestUtils;
import org.junit.Test;

public class HttpTunnelLoadTest {

	public static final int TIMEOUT = 2;
	public static final int MESSAGE_COUNT = 1000;
	public static final int DATA_SIZE = 1024 * 256; // 256kb

	private static Random random;

	static {
		random = new Random();
	}

	private static ChannelBuffer createMessage(int size) throws IOException {
		final byte[] bytes = new byte[size];
		random.nextBytes(bytes);

		return ChannelBuffers.wrappedBuffer(bytes);
	}

	@Test
	public void testLoad() throws InterruptedException, IOException {
		final CountDownLatch messageLatch = new CountDownLatch(1);
		final long expectedData = MESSAGE_COUNT * DATA_SIZE;

		// Create a buffer of the given size with random data in it
		final ChannelBuffer message = HttpTunnelLoadTest.createMessage(DATA_SIZE);
		assertTrue("failed to create dummy message", message.readableBytes() == DATA_SIZE);

		final InetSocketAddress addr = new InetSocketAddress("localhost", 8181);

		final OpenCloseIncomingChannelHandler<ChannelBuffer> serverHandler = new OpenCloseIncomingChannelHandler<ChannelBuffer>(1) {
			long receivedData = 0;

			@Override
			public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
				super.messageReceived(ctx, e);

				receivedData += ((ChannelBuffer) e.getMessage()).readableBytes();
				if (receivedData >= expectedData)
					messageLatch.countDown();

				System.out.println("Received: " + FileUtils.byteCountToDisplaySize(receivedData) + " / " + FileUtils.byteCountToDisplaySize(expectedData));
			}
		};

		final Channel serverChannel = NettyTestUtils.createServerChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(serverHandler);
			}
		});

		// Server should be open and bound
		assertTrue("server isn't open after connect", serverChannel.isOpen());
		assertTrue("server isn't bound after connect", serverChannel.isBound());

		final OpenCloseOutgoingChannelHandler clientHandler = new OpenCloseOutgoingChannelHandler(MESSAGE_COUNT);
		final Channel clientChannel = NettyTestUtils.createClientChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(clientHandler);
			}
		}, TIMEOUT);

		// Check we actually managed to connect
		assertTrue("failed to connect", clientChannel != null);

		final Channel acceptedChannel = serverHandler.getChannel();
		assertTrue("no accepted channel found, the channelOpen event is most likely missing", acceptedChannel != null);

		// Client channel should be open, bound, and connected
		assertTrue("client isn't open after connect", clientChannel.isOpen());
		assertTrue("client isn't bound after connect", clientChannel.isBound());
		assertTrue("client isn't connected after connect", clientChannel.isConnected());

		// Server channel should be open and bound, but *maybe* not yet connected
		assertTrue("server isn't open after connect", acceptedChannel.isOpen());
		assertTrue("server isn't bound after connect", acceptedChannel.isBound());

		// Send a test message
		for (int i = 0;i < MESSAGE_COUNT;i++)
			clientChannel.write(message).awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		// Close the channel
		assertTrue("client failed to close", clientChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));

		// Check we received the correct client events
		clientHandler.assertSatisfied(TIMEOUT);

		// Check we received the correct server events
		serverHandler.assertSatisfied(TIMEOUT);

		assertTrue("failed to receive all data", messageLatch.await(TIMEOUT, TimeUnit.SECONDS));

		if (serverChannel != null && serverChannel.isOpen())
			serverChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		if (clientChannel != null && clientChannel.isOpen())
			clientChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		if (acceptedChannel != null && acceptedChannel.isOpen())
			acceptedChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);
	}
}
