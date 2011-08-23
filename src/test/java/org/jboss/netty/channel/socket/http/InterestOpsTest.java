package org.jboss.netty.channel.socket.http;

import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.channel.socket.http.util.NettyTestUtils;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.junit.Test;

public class InterestOpsTest {

	public static final int TIMEOUT = 2;

	@Test
	public void testSetUnreadable() throws InterruptedException {
		final InetSocketAddress addr = new InetSocketAddress("localhost", 8181);

		final ReadableChannelHandler serverHandler = new ReadableChannelHandler();
		final Channel server = NettyTestUtils.createServerChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new StringEncoder(), new StringDecoder(), serverHandler);
			}
		});

		final ReadableChannelHandler clientHandler = new ReadableChannelHandler();
		final Channel client = NettyTestUtils.createClientChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new StringEncoder(), new StringDecoder(), clientHandler);
			}
		}, TIMEOUT);

		assertTrue("no client channel", clientHandler.channel != null);
		assertTrue("no accepted channel", serverHandler.channel != null);

		// Set the server end to be unreadable, this should prevent the message being received until we set it to readable again.
		assertTrue("unable to set unreadable", serverHandler.channel.setReadable(false).awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));
		assertTrue("server channel is readable", !serverHandler.channel.isReadable());

		// Send a test message
		clientHandler.channel.write("hello world").awaitUninterruptibly();

		// Wait and check it was sent correctly
		assertTrue("failed to write message", clientHandler.writtenLatch.await(TIMEOUT, TimeUnit.SECONDS));

		// Give the message some time to be delivered
		Thread.sleep(1000);

		// Check it hasn't been received - since the server end is unreadable it shouldn't be!
		assertTrue("server received message before readable", serverHandler.receivedLatch.getCount() == 1);

		// Set the server end of readable, the message should be delivered after this...
		assertTrue("unable to set readable", serverHandler.channel.setReadable(true).awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS));
		assertTrue("server channel is unreadable", serverHandler.channel.isReadable());

		// Give the message some time to be delivered
		Thread.sleep(1000);

		// Check that the message was indeed delivered
		assertTrue("failed to receive message", serverHandler.receivedLatch.await(TIMEOUT, TimeUnit.SECONDS));

		// Shut down both ends
		server.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);
		client.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);
	}

	private class ReadableChannelHandler extends SimpleChannelHandler {

		private final CountDownLatch writtenLatch;
		private final CountDownLatch receivedLatch;

		private Channel channel;

		public ReadableChannelHandler() {
			writtenLatch = new CountDownLatch(1);
			receivedLatch = new CountDownLatch(1);

			channel = null;
		}

		@Override
		public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
			this.channel = ctx.getChannel();
		}

		@Override
		public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) {
			writtenLatch.countDown();
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
			receivedLatch.countDown();
		}
	}
}
