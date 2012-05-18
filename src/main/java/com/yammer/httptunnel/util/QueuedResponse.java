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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;

/**
 * Class used for holding reference to an outgoing response, along with the
 * associated write future.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public class QueuedResponse {
	private final ChannelBuffer data;
	private final ChannelFuture future;

	/**
	 * Construct a new instance storing the given response data and channel
	 * future.
	 */
	public QueuedResponse(ChannelBuffer data, ChannelFuture future) {
		this.data = data;
		this.future = future;
	}

	/**
	 * @return the length of the response data.
	 */
	public int getLength() {
		return data.readableBytes();
	}

	/**
	 * @return the response data.
	 */
	public ChannelBuffer getData() {
		return data;
	}

	/**
	 * @return the channel future associated with the response.
	 */
	public ChannelFuture getFuture() {
		return future;
	}
}
