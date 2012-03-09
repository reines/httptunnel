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

package org.jboss.netty.channel.socket.http.util;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * Class which is used to forward the result of a channel future to another
 * channel future.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
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
