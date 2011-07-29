package org.jboss.netty.channel.socket.http.server;

import org.jboss.netty.channel.socket.http.DefaultHttpTunnelChannelConfig;

public class DefaultHttpTunnelAcceptedChannelConfig extends DefaultHttpTunnelChannelConfig implements HttpTunnelAcceptedChannelConfig {

	private static final int SO_LINGER_DISABLED = -1;
	private static final int FAKE_SEND_BUFFER_SIZE = 16 * 1024; // 16kb
	private static final int FAKE_RECEIVE_BUFFER_SIZE = 16 * 1024; // 16kb
	private static final int DEFAULT_TRAFFIC_CLASS = 0; // based on the values in RFC 791

	@Override
	public boolean isTcpNoDelay() {
		return true;
	}

	@Override
	public void setTcpNoDelay(boolean tcpNoDelay) {
		// we do not allow the value to be changed, as it will not be honoured
	}

	@Override
	public int getSoLinger() {
		return SO_LINGER_DISABLED;
	}

	@Override
	public void setSoLinger(int soLinger) {
		// we do not allow the value to be changed, as it will not be honoured
	}

	@Override
	public int getSendBufferSize() {
		return FAKE_SEND_BUFFER_SIZE;
	}

	@Override
	public void setSendBufferSize(int sendBufferSize) {
		// we do not allow the value to be changed, as it will not be honoured
	}

	@Override
	public int getReceiveBufferSize() {
		return FAKE_RECEIVE_BUFFER_SIZE;
	}

	@Override
	public void setReceiveBufferSize(int receiveBufferSize) {
		// we do not allow the value to be changed, as it will not be honoured
	}

	@Override
	public boolean isKeepAlive() {
		return true;
	}

	@Override
	public void setKeepAlive(boolean keepAlive) {
		// we do not allow the value to be changed, as it will not be honoured
	}

	@Override
	public int getTrafficClass() {
		return DEFAULT_TRAFFIC_CLASS;
	}

	@Override
	public void setTrafficClass(int trafficClass) {
		// we do not allow the value to be changed, as it will not be honoured
	}

	@Override
	public boolean isReuseAddress() {
		return false;
	}

	@Override
	public void setReuseAddress(boolean reuseAddress) {
		// we do not allow the value to be changed, as it will not be honoured
	}

	@Override
	public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
		// we do not allow the value to be changed, as it will not be honoured
	}
}
