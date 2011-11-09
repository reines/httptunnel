package org.jboss.netty.channel.socket.http.client;

import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.channel.socket.http.DefaultHttpTunnelChannelConfig;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;

public class DefaultHttpTunnelClientChannelConfig extends DefaultHttpTunnelChannelConfig implements HttpTunnelClientChannelConfig {
    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(DefaultHttpTunnelClientChannelConfig.class);

	public static final String DEFAULT_USER_AGENT = "HttpTunnelClient";

	public static final String PROXY_ADDRESS_OPTION = "proxyAddress";
	public static final String USER_AGENT_OPTION = "userAgent";

	private final SocketChannelConfig sendChannelConfig;
	private final SocketChannelConfig pollChannelConfig;

	private String userAgent;
	private SocketAddress proxyAddress;

	public DefaultHttpTunnelClientChannelConfig(SocketChannelConfig sendChannelConfig, SocketChannelConfig pollChannelConfig) {
		this.sendChannelConfig = sendChannelConfig;
		this.pollChannelConfig = pollChannelConfig;

		userAgent = DEFAULT_USER_AGENT;
		proxyAddress = null;
	}

	/* HTTP TUNNEL SPECIFIC CONFIGURATION */
	// TODO Support all options in the old tunnel (see
	// HttpTunnelingSocketChannelConfig)
	// Mostly SSL, virtual host, and URL prefix
	@Override
	public boolean setOption(String key, Object value) {
		if (PROXY_ADDRESS_OPTION.equalsIgnoreCase(key)) {
            SocketAddress proxyAddress = null;
            if (value instanceof SocketAddress) proxyAddress = (SocketAddress) value;
            if (value instanceof String) {
                try {
                    URL proxyAddressUrl = new URL((String)value);
                    proxyAddress = new InetSocketAddress(proxyAddressUrl.getHost(), proxyAddressUrl.getPort());
                } catch (MalformedURLException e) {
                    LOG.warn("Failed to configure http proxy", e);
                    return false;
                }
            }
			if (proxyAddress != null) {
                this.setProxyAddress(proxyAddress);
                return true;
            }
            return false;
		}

		if (USER_AGENT_OPTION.equalsIgnoreCase(key)) {
			this.setUserAgent((String) value);
			return true;
		}

		return super.setOption(key, value);
	}

	@Override
	public String getUserAgent() {
		return userAgent;
	}

	@Override
	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
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
