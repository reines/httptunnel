package com.yammer.httptunnel.integrated;

import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.junit.Ignore;
import org.junit.Test;

import com.yammer.httptunnel.util.NettyTestUtils;

public class ThroughputTest {

	public static final int DATA_AMOUNT = 1024; // 1Gb
	public static final int TIMEOUT = 2;

	// Ignored because really doing this over the loopback interface means nothing
	// This needs converted to run over multiple machines rather than as a unit test
	@Test @Ignore
	public void testThroughput() {
		final InetSocketAddress addr = new InetSocketAddress("localhost", 8888);

		final ThroughputChannelHandler serverHandler = new ThroughputChannelHandler();
		final Channel server = NettyTestUtils.createServerChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(serverHandler);
			}
		});

		final ThroughputChannelHandler clientHandler = new ThroughputChannelHandler();
		final Channel client = NettyTestUtils.createClientChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(clientHandler);
			}
		}, TIMEOUT);

		assertTrue("no client channel", clientHandler.channel != null);
		assertTrue("no accepted channel", serverHandler.channel != null);

		final ChannelBuffer buffer = ChannelBuffers.buffer(1024 * 128); // 128kb chunks
		buffer.writeZero(buffer.capacity());

		final int loop = DATA_AMOUNT * 8 / 2;
		final long startTime = System.currentTimeMillis();

		// Send client to server
		for (int i = 0;i < loop;i++) {
			buffer.resetReaderIndex();
			clientHandler.channel.write(buffer).awaitUninterruptibly();
		}

		// Send server to client
		for (int i = 0;i < loop;i++) {
			buffer.resetReaderIndex();
			serverHandler.channel.write(buffer).awaitUninterruptibly();
		}

		final long duration = (System.currentTimeMillis() - startTime) / 1000;
		final long transferred = serverHandler.read + clientHandler.read;

		System.out.println(String.format("Transferred:\t%s", FileUtils.byteCountToDisplaySize(transferred)));
		System.out.println(String.format("Duration:\t%s secs", duration));
		System.out.println(String.format("Speed:\t\t%s/sec", FileUtils.byteCountToDisplaySize(transferred / duration)));

		client.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);
		server.close().awaitUninterruptibly(TIMEOUT, TimeUnit.SECONDS);
	}

	private class ThroughputChannelHandler extends SimpleChannelHandler {

		private Channel channel;
		private long read;
		private long write;

		public ThroughputChannelHandler() {
			channel = null;
			read = 0;
			write = 0;
		}

		@Override
		public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
			this.channel = ctx.getChannel();
		}

		@Override
		public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) {
			write += e.getWrittenAmount();
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
			read += ((ChannelBuffer) e.getMessage()).capacity();
		}
	}
}
