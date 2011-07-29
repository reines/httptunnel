package org.jboss.netty.channel.socket.http.util;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

public class ForwardingFutureListener implements ChannelFutureListener {

	private final ChannelFuture targetFuture;

	public ForwardingFutureListener(ChannelFuture targetFuture) {
		this.targetFuture = targetFuture;
	}

	@Override
	public void operationComplete(ChannelFuture future) throws Exception {
		if (targetFuture == null)
			return;

		if (future.isSuccess())
			targetFuture.setSuccess();
		else
			targetFuture.setFailure(future.getCause());
	}
}
