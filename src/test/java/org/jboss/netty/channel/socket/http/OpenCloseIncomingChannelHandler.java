package org.jboss.netty.channel.socket.http;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

class OpenCloseIncomingChannelHandler extends SimpleChannelHandler {

    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(OpenCloseIncomingChannelHandler.class);

    private final CountDownLatch openLatch;
    private final CountDownLatch closeLatch;
    private final CountDownLatch messageLatch;

    OpenCloseIncomingChannelHandler() {
        openLatch = new CountDownLatch(3);
        closeLatch = new CountDownLatch(3);
        messageLatch = new CountDownLatch(1);
    }

    public void assertSatisfied(int timeout) throws InterruptedException {
        // Wait for the open events
        assertTrue("Missed some server open events (" + openLatch + ")", openLatch.await(timeout, TimeUnit.SECONDS));

        // Wait for the message event
        assertTrue("Missed server message received event (" + messageLatch + ")", messageLatch.await(timeout, TimeUnit.SECONDS));

        // Wait for the close events
        assertTrue("Missed some server close events (" + closeLatch + ")", closeLatch.await(timeout, TimeUnit.SECONDS));
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // First open event
        if (openLatch.getCount() == 3)
            openLatch.countDown();

        logger.info("server channelOpen: " + openLatch);
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Second open event
        if (openLatch.getCount() == 2)
            openLatch.countDown();

        logger.info("server channelBound: " + openLatch);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Final open event
        if (openLatch.getCount() == 1)
            openLatch.countDown();

        logger.info("server channelConnected: " + openLatch);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // First close event
        if (closeLatch.getCount() == 3)
            closeLatch.countDown();

        logger.info("server channelDisconnected: " + closeLatch);
    }

    @Override
    public void channelUnbound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Second close event
        if (closeLatch.getCount() == 2)
            closeLatch.countDown();

        logger.info("server channelUnbound: " + closeLatch);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Final close event
        if (closeLatch.getCount() == 1)
            closeLatch.countDown();

        logger.info("server channelClosed: " + closeLatch);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        messageLatch.countDown();

        logger.info("server messageReceived: " + messageLatch);
    }
}
