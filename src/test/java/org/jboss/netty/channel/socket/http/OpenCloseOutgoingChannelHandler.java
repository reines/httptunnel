package org.jboss.netty.channel.socket.http;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

class OpenCloseOutgoingChannelHandler extends SimpleChannelHandler {

	private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenCloseOutgoingChannelHandler.class);

	private final CountDownLatch openLatch;
	private final CountDownLatch closeLatch;
	private final CountDownLatch messageLatch;

	private Channel channel;

	OpenCloseOutgoingChannelHandler(int expectedMessages) {
		openLatch = new CountDownLatch(3);
		closeLatch = new CountDownLatch(3);
		messageLatch = new CountDownLatch(expectedMessages);

		channel = null;
	}

	Channel getChannel() {
		return channel;
	}

	public void assertSatisfied(int timeout) throws InterruptedException {
		// Wait for the open events
		assertTrue("Missed some client open events (" + openLatch + ")", openLatch.await(timeout, TimeUnit.SECONDS));

		// Wait for the message event
		assertTrue("Missed client message sent event (" + messageLatch + ")", messageLatch.await(timeout, TimeUnit.SECONDS));

		// Wait for the close events
		assertTrue("Missed some client close events (" + closeLatch + ")", closeLatch.await(timeout, TimeUnit.SECONDS));
	}

	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// First open event
		if (openLatch.getCount() == 3)
			openLatch.countDown();

		channel = ctx.getChannel();

		logger.info("client channelOpen: " + openLatch);
	}

	@Override
	public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// Second open event
		if (openLatch.getCount() == 2)
			openLatch.countDown();

		logger.info("client channelBound: " + openLatch);
	}

	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// Final open event
		if (openLatch.getCount() == 1)
			openLatch.countDown();

		logger.info("client channelConnected: " + openLatch);
	}

	@Override
	public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// First close event
		if (closeLatch.getCount() == 3)
			closeLatch.countDown();

		logger.info("client channelDisconnected: " + closeLatch);
	}

	@Override
	public void channelUnbound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// Second close event
		if (closeLatch.getCount() == 2)
			closeLatch.countDown();

		logger.info("client channelUnbound: " + closeLatch);
	}

	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// Final close event
		if (closeLatch.getCount() == 1)
			closeLatch.countDown();

		logger.info("client channelClosed: " + closeLatch);
	}

	@Override
	public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
		messageLatch.countDown();

		logger.info("client writeComplete: " + messageLatch);
	}
}
