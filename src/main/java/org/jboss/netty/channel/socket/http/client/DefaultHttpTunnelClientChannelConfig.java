package org.jboss.netty.channel.socket.http.client;

import java.net.SocketAddress;

import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.channel.socket.http.DefaultHttpTunnelChannelConfig;

public class DefaultHttpTunnelClientChannelConfig extends DefaultHttpTunnelChannelConfig implements HttpTunnelClientChannelConfig {

	public static final String PROXY_ADDRESS_OPTION = "proxyAddress";

	private final SocketChannelConfig sendChannelConfig;
	private final SocketChannelConfig pollChannelConfig;

	private volatile SocketAddress proxyAddress;

	public DefaultHttpTunnelClientChannelConfig(SocketChannelConfig sendChannelConfig, SocketChannelConfig pollChannelConfig) {
		this.sendChannelConfig = sendChannelConfig;
		this.pollChannelConfig = pollChannelConfig;

		proxyAddress = null;
	}

	/* HTTP TUNNEL SPECIFIC CONFIGURATION */
	// TODO Support all options in the old tunnel (see
	// HttpTunnelingSocketChannelConfig)
	// Mostly SSL, virtual host, and URL prefix
	@Override
	public boolean setOption(String key, Object value) {
		if (PROXY_ADDRESS_OPTION.equals(key)) {
			this.setProxyAddress((SocketAddress) value);
			return true;
		}

		return super.setOption(key, value);
	}

	@Override
	public SocketAddress getProxyAddress() {
		return proxyAddress;
	}

	@Override
	public void setProxyAddress(SocketAddress proxyAddress) {
		this.proxyAddress = proxyAddress;
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
