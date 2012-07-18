package com.yammer.httptunnel.integrated;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.junit.After;
import org.junit.Test;

import com.yammer.httptunnel.util.NettyTestUtils;

public class HttpTunnelLoadTest {

	public static final int TIMEOUT = 2;
	public static final int MESSAGE_COUNT = 100;
	public static final int DATA_SIZE = 1024 * 256; // 256kb

	private class ThroughputIncomingChannelHandler extends OpenCloseIncomingChannelHandler<ChannelBuffer> {

		private final CountDownLatch receivedLatch;
		private final CountDownLatch sentLatch;
		private final long expectedData;

		private long receivedData;
		private long sentData;

		public ThroughputIncomingChannelHandler(long expectedData) {
			super (1);

			this.expectedData = expectedData;

			receivedLatch = new CountDownLatch(1);
			sentLatch = new CountDownLatch(1);

			receivedData = 0;
			sentData = 0;
		}

		@Override
		public void assertSatisfied(int timeout) throws InterruptedException {
			final boolean sentSuccess = sentLatch.await(timeout, TimeUnit.SECONDS);
			final boolean receiveSuccess = receivedLatch.await(timeout, TimeUnit.SECONDS);

			System.out.println("server sent: " + FileUtils.byteCountToDisplaySize(sentData) + " / " + FileUtils.byteCountToDisplaySize(expectedData) + " (" + sentData + " / " + expectedData + ", missing " + (expectedData - sentData) + " bytes)");
			System.out.println("server received: " + FileUtils.byteCountToDisplaySize(receivedData) + " / " + FileUtils.byteCountToDisplaySize(expectedData) + " (" + receivedData + " / " + expectedData + ", missing " + (expectedData - receivedData) + " bytes)");

			// Wait for the data events
			assertTrue("Missed some server sent events", sentSuccess);
			assertTrue("Missed some server receive events", receiveSuccess);
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
			super.messageReceived(ctx, e);

			final ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

			receivedData += buffer.readableBytes();
			if (receivedData >= expectedData)
				receivedLatch.countDown();
		}

		@Override
		public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
			super.writeComplete(ctx, e);

			sentData += e.getWrittenAmount();
			if (sentData >= expectedData)
				sentLatch.countDown();
		}
	};

	private class ThroughputOutgoingChannelHandler extends OpenCloseOutgoingChannelHandler {

		private final CountDownLatch receivedLatch;
		private final CountDownLatch sentLatch;
		private final long expectedData;

		private long receivedData;
		private long sentData;

		public ThroughputOutgoingChannelHandler(long expectedData) {
			super (1);

			this.expectedData = expectedData;

			receivedLatch = new CountDownLatch(1);
			sentLatch = new CountDownLatch(1);

			receivedData = 0;
			sentData = 0;
		}

		@Override
		public void assertSatisfied(int timeout) throws InterruptedException {
			final boolean sentSuccess = sentLatch.await(timeout, TimeUnit.SECONDS);
			final boolean receiveSuccess = receivedLatch.await(timeout, TimeUnit.SECONDS);

			System.out.println("client sent: " + FileUtils.byteCountToDisplaySize(sentData) + " / " + FileUtils.byteCountToDisplaySize(expectedData) + " (" + sentData + " / " + expectedData + ", missing " + (expectedData - sentData) + " bytes)");
			System.out.println("client received: " + FileUtils.byteCountToDisplaySize(receivedData) + " / " + FileUtils.byteCountToDisplaySize(expectedData) + " (" + receivedData + " / " + expectedData + ", missing " + (expectedData - receivedData) + " bytes)");

			// Wait for the data events
			assertTrue("Missed some client sent events", sentSuccess);
			assertTrue("Missed some client receive events", receiveSuccess);
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
			super.messageReceived(ctx, e);

			final ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

			receivedData += buffer.readableBytes();
			if (receivedData >= expectedData)
				receivedLatch.countDown();
		}

		@Override
		public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
			super.writeComplete(ctx, e);

			sentData += e.getWrittenAmount();
			if (sentData >= expectedData)
				sentLatch.countDown();
		}
	};

	private Channel serverChannel;
	private Channel clientChannel;
	private Channel acceptedChannel;

	@After
	public void tearDown() {
		if (serverChannel != null && serverChannel.isOpen())
			serverChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		if (clientChannel != null && clientChannel.isOpen())
			clientChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		if (acceptedChannel != null && acceptedChannel.isOpen())
			acceptedChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);
	}

	@Test
	public void testLoad() throws InterruptedException, IOException {
		// Create a buffer of the given size with random data in it
		final ChannelBuffer message = NettyTestUtils.createRandomData(DATA_SIZE);
		assertTrue("failed to create dummy message", message.readableBytes() == DATA_SIZE);

		final InetSocketAddress addr = new InetSocketAddress(NettyTestUtils.getLocalHost(), 8181);

		final ThroughputIncomingChannelHandler serverHandler = new ThroughputIncomingChannelHandler(MESSAGE_COUNT * DATA_SIZE);
		serverChannel = NettyTestUtils.createServerChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(serverHandler);
			}
		});

		// Server should be open and bound
		assertTrue("server isn't open after connect", serverChannel.isOpen());
		assertTrue("server isn't bound after connect", serverChannel.isBound());

		final ThroughputOutgoingChannelHandler clientHandler = new ThroughputOutgoingChannelHandler(MESSAGE_COUNT * DATA_SIZE);
		clientChannel = NettyTestUtils.createClientChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(clientHandler);
			}
		}, TIMEOUT);

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

		// Send test messages from the client
		for (int i = 0;i < MESSAGE_COUNT;i++)
			clientChannel.write(message).awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		// Send test messages from the server
		for (int i = 0;i < MESSAGE_COUNT;i++)
			acceptedChannel.write(message).awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);

		// Check we received the correct server events
		serverHandler.assertSatisfied(TIMEOUT * 3);

		// Check we received the correct client events
		clientHandler.assertSatisfied(TIMEOUT * 3);

		// Close the channel
		assertTrue("client failed to close", clientChannel.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));
	}
}
