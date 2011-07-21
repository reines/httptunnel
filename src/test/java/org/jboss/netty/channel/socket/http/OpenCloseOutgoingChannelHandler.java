package org.jboss.netty.channel.socket.http;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

class OpenCloseOutgoingChannelHandler extends SimpleChannelHandler {

    private final CountDownLatch openLatch;
    private final CountDownLatch closeLatch;
    private final CountDownLatch messageLatch;

    OpenCloseOutgoingChannelHandler() {
        openLatch = new CountDownLatch(3);
        closeLatch = new CountDownLatch(3);
        messageLatch = new CountDownLatch(1);
    }

    public void assertSatisfied(int timeout) throws InterruptedException {
        // Wait for the open events
        assertTrue("Missed some outgoing open events", openLatch.await(timeout, TimeUnit.SECONDS));

        // Wait for the message event
        assertTrue("Missed outgoing message sent event", messageLatch.await(timeout, TimeUnit.SECONDS));

        // Wait for the close events
        assertTrue("Missed some outgoing close events", closeLatch.await(timeout, TimeUnit.SECONDS));
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // First open event
        if (openLatch.getCount() == 3)
            openLatch.countDown();
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Second open event
        if (openLatch.getCount() == 2)
            openLatch.countDown();
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Final open event
        if (openLatch.getCount() == 1)
            openLatch.countDown();
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // First close event
        if (closeLatch.getCount() == 3)
            closeLatch.countDown();
    }

    @Override
    public void channelUnbound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Second close event
        if (closeLatch.getCount() == 2)
            closeLatch.countDown();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Final close event
        if (closeLatch.getCount() == 1)
            closeLatch.countDown();
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
        messageLatch.countDown();
    }
}
