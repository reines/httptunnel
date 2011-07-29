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
package org.jboss.netty.channel.socket.http.client;

import java.net.InetSocketAddress;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * Sink of a client channel, deals with sunk events and then makes appropriate
 * calls on the channel itself to push data.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelClientChannelSink extends AbstractChannelSink {

	@Override
	public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
		final HttpTunnelClientChannel channel = (HttpTunnelClientChannel) e.getChannel();

		if (e instanceof ChannelStateEvent) {
			final ChannelStateEvent event = (ChannelStateEvent) e;
			switch (event.getState()) {
				case OPEN: {
					final boolean opened = (Boolean) event.getValue();
					if (!opened)
						channel.internalClose(e.getFuture());

					break;
				}

				case BOUND: {
					// Unbind
					if (event.getValue() == null)
						channel.internalUnbind(event.getFuture());
					// Attempted to bind to a valid address
					else if (event.getValue() instanceof InetSocketAddress) {
						final InetSocketAddress addr = (InetSocketAddress) event.getValue();
						channel.internalBind(addr, event.getFuture());
					}
					else {
						Exception error = new IllegalArgumentException("Can only bind to an InetSocketAddress");
						channel.internalFailConnect(error);
					}

					break;
				}

				case CONNECTED: {
					// Disconnect
					if (event.getValue() == null)
						channel.internalDisconnect(event.getFuture());
					// Attempt to connect to a valid address
					else if (event.getValue() instanceof InetSocketAddress) {
						final InetSocketAddress addr = (InetSocketAddress) event.getValue();
						channel.internalConnect(addr, event.getFuture());
					}
					else {
						Exception error = new IllegalArgumentException("Can only connect to an InetSocketAddress");
						channel.internalFailConnect(error);
					}

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
		else if (e instanceof MessageEvent) {
			final MessageEvent message = (MessageEvent) e;
			// Ask the channel to deliver this message
			channel.sendMessage(message);

			return;
		}
	}
}
