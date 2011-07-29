/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.http.server;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * Sink for the server end of an http tunnel. Data sent down through the server
 * end is dispatched from here to the ServerMessageSwitch, which queues the data
 * awaiting a poll request from the client end of the tunnel.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelAcceptedChannelSink extends AbstractChannelSink {

	@Override
	public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
		final HttpTunnelAcceptedChannel channel = (HttpTunnelAcceptedChannel) e.getChannel();

		if (e instanceof ChannelStateEvent) {
			final ChannelStateEvent event = (ChannelStateEvent) e;
			switch (event.getState()) {
				case OPEN: {
					final boolean opened = (Boolean) event.getValue();
					if (!opened)
						channel.internalClose(true, e.getFuture());

					break;
				}

				case INTEREST_OPS: {
					final int interestOps = (Integer) event.getValue();
					channel.internalSetInterestOps(interestOps, event.getFuture());

					break;
				}
			}

			return;
		}

		if (e instanceof MessageEvent) {
			final MessageEvent message = (MessageEvent) e;
			// Ask the channel to deliver this message
			channel.sendMessage(message);

			return;
		}
	}
}
