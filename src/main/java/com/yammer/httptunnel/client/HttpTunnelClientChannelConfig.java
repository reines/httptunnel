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

package com.yammer.httptunnel.client;

import com.yammer.httptunnel.HttpTunnelChannelConfig;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Configuration for the client end of an HTTP tunnel. Any socket channel
 * properties set here will be applied uniformly to the underlying send and poll
 * channels, created from the channel factory provided to the
 * {@link HttpTunnelClientChannelFactory}.
 */
public class HttpTunnelClientChannelConfig extends HttpTunnelChannelConfig {
	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelClientChannelConfig.class);

	/**
	 * The default user-agent used for HTTP tunnel requests.
	 */
	public static final String DEFAULT_USER_AGENT = "HttpTunnel";

	static final String USER_AGENT_OPTION = "userAgent";

	private static final String PROP_PKG = "org.jboss.netty.channel.socket.http.";

	private static final String PROP_UserAgent = PROP_PKG + USER_AGENT_OPTION;

	private final SocketChannelConfig sendChannelConfig;
	private final SocketChannelConfig pollChannelConfig;

	private String userAgent;

	HttpTunnelClientChannelConfig(SocketChannelConfig sendChannelConfig, SocketChannelConfig pollChannelConfig) {
		this.sendChannelConfig = sendChannelConfig;
		this.pollChannelConfig = pollChannelConfig;

		userAgent = System.getProperty(PROP_UserAgent, DEFAULT_USER_AGENT);
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	/* HTTP TUNNEL SPECIFIC CONFIGURATION */
	// TODO Support all options in the old tunnel (see
	// HttpTunnelingSocketChannelConfig)
	// Mostly SSL, virtual host, and URL prefix
	@Override
	public boolean setOption(String key, Object value) {
		if (USER_AGENT_OPTION.equalsIgnoreCase(key)) {
			this.setUserAgent((String) value);
			return true;
		}

		return super.setOption(key, value);
	}

	/* GENERIC SOCKET CHANNEL CONFIGURATION */

	@Override
	public int getReceiveBufferSize() {
		return pollChannelConfig.getReceiveBufferSize();
	}

	@Override
	public void setReceiveBufferSize(int receiveBufferSize) {
		pollChannelConfig.setReceiveBufferSize(receiveBufferSize);
		sendChannelConfig.setReceiveBufferSize(receiveBufferSize);
	}

	@Override
	public int getSendBufferSize() {
		return pollChannelConfig.getSendBufferSize();
	}

	@Override
	public void setSendBufferSize(int sendBufferSize) {
		pollChannelConfig.setSendBufferSize(sendBufferSize);
		sendChannelConfig.setSendBufferSize(sendBufferSize);
	}

	@Override
	public int getSoLinger() {
		return pollChannelConfig.getSoLinger();
	}

	@Override
	public void setSoLinger(int soLinger) {
		pollChannelConfig.setSoLinger(soLinger);
		sendChannelConfig.setSoLinger(soLinger);
	}

	@Override
	public int getTrafficClass() {
		return pollChannelConfig.getTrafficClass();
	}

	@Override
	public void setTrafficClass(int trafficClass) {
		pollChannelConfig.setTrafficClass(1);
		sendChannelConfig.setTrafficClass(1);
	}

	@Override
	public boolean isKeepAlive() {
		return pollChannelConfig.isKeepAlive();
	}

	@Override
	public void setKeepAlive(boolean keepAlive) {
		pollChannelConfig.setKeepAlive(keepAlive);
		sendChannelConfig.setKeepAlive(keepAlive);
	}

	@Override
	public boolean isReuseAddress() {
		return pollChannelConfig.isReuseAddress();
	}

	@Override
	public void setReuseAddress(boolean reuseAddress) {
		pollChannelConfig.setReuseAddress(reuseAddress);
		sendChannelConfig.setReuseAddress(reuseAddress);
	}

	@Override
	public boolean isTcpNoDelay() {
		return pollChannelConfig.isTcpNoDelay();
	}

	@Override
	public void setTcpNoDelay(boolean tcpNoDelay) {
		pollChannelConfig.setTcpNoDelay(true);
		sendChannelConfig.setTcpNoDelay(true);
	}

	@Override
	public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
		pollChannelConfig.setPerformancePreferences(connectionTime, latency, bandwidth);
		sendChannelConfig.setPerformancePreferences(connectionTime, latency, bandwidth);
	}
}
