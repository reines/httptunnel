package org.jboss.netty.channel.socket.http.util;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;

public class QueuedResponse {
	private final ChannelBuffer data;
	private final ChannelFuture future;

	public QueuedResponse(ChannelBuffer data, ChannelFuture future) {
		this.data = data;
		this.future = future;
	}

	public ChannelBuffer getData() {
		return data;
	}

	public ChannelFuture getFuture() {
		return future;
	}
}
