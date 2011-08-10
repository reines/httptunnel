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

import java.net.SocketAddress;

import org.jboss.netty.channel.socket.http.HttpTunnelChannelConfig;

/**
 * Configuration for the client end of an HTTP tunnel. Any socket channel
 * properties set here will be applied uniformly to the underlying send and poll
 * channels, created from the channel factory provided to the
 * {@link HttpTunnelClientChannelFactory}.
 * <p>
 * HTTP tunnel clients have the following additional options:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th>
 * <th>Associated setter method</th>
 * </tr>
 * <tr>
 * <td>{@code "proxyAddress"}</td>
 * <td>{@link #setProxyAddress(SocketAddress)}</td>
 * </tr>
 * <tr>
 * <td>{@code "writeBufferHighWaterMark"}</td>
 * <td>{@link #setWriteBufferHighWaterMark(int)}</td>
 * </tr>
 * <tr>
 * <td>{@code "writeBufferLowWaterMark"}</td>
 * <td>{@link #setWriteBufferLowWaterMark(int)}</td>
 * </tr>
 * </table>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
public interface HttpTunnelClientChannelConfig extends HttpTunnelChannelConfig {

	public void setUserAgent(String userAgent);
	public String getUserAgent();

	/**
	 * Specify a proxy to be used for the http tunnel. If this is null, then no
	 * proxy should be used, otherwise this should be a directly accessible
	 * IPv4/IPv6 address and port.
	 */
	public void setProxyAddress(SocketAddress proxyAddress);

	/**
	 * @return the address of the http proxy. If this is null, then no proxy
	 * should be used.
	 */
	public SocketAddress getProxyAddress();
}
