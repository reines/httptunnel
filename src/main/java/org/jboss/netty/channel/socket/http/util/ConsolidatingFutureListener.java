package org.jboss.netty.channel.socket.http.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

public class ConsolidatingFutureListener implements ChannelFutureListener {

	private final ChannelFuture completionFuture;
	private final AtomicInteger eventsLeft;

	public ConsolidatingFutureListener(ChannelFuture completionFuture, int numToConsolidate) {
		this.completionFuture = completionFuture;

		eventsLeft = new AtomicInteger(numToConsolidate);
	}

	@Override
	public void operationComplete(ChannelFuture future) throws Exception {
		if (!future.isSuccess())
			this.futureFailed(future);
		else if (eventsLeft.decrementAndGet() == 0)
			this.allFuturesComplete();
	}

	protected void allFuturesComplete() {
		if (completionFuture == null)
			return;

		completionFuture.setSuccess();
	}

	protected void futureFailed(ChannelFuture future) {
		if (completionFuture == null)
			return;

		completionFuture.setFailure(future.getCause());
	}
}
