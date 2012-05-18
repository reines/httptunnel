/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.yammer.httptunnel.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * Class which is used to consolidate multiple channel futures into one, by
 * listening to the individual futures and upon failure producing a failed
 * future, or upon a given number of successful completions producing a
 * successful future.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
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
