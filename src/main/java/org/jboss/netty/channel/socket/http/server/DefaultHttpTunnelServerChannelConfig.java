package org.jboss.netty.channel.socket.http.server;

import java.util.Map;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelConfig;
import org.jboss.netty.channel.socket.http.util.DefaultTunnelIdGenerator;
import org.jboss.netty.channel.socket.http.util.TunnelIdGenerator;

public class DefaultHttpTunnelServerChannelConfig implements HttpTunnelServerChannelConfig {

	public static final String DEFAULT_USER_AGENT = "HttpTunnel";

	public static final String USER_AGENT_OPTION = "userAgent";
	public static final String PIPELINE_FACTORY_OPTION = "pipelineFactory";
	public static final String TUNNEL_ID_GENERATOR_OPTION = "tunnelIdGenerator";

	private static final String PROP_PKG = "org.jboss.netty.channel.socket.http.";

	private static final String PROP_UserAgent =				PROP_PKG + USER_AGENT_OPTION;

	private String userAgent;
	private ServerSocketChannel realChannel;
	private TunnelIdGenerator tunnelIdGenerator;
	private ChannelPipelineFactory pipelineFactory;

	public DefaultHttpTunnelServerChannelConfig() {
		userAgent = System.getProperty(PROP_UserAgent, DEFAULT_USER_AGENT);

		realChannel = null;
		tunnelIdGenerator = new DefaultTunnelIdGenerator();

		pipelineFactory = null;
	}

	void setRealChannel(ServerSocketChannel realChannel) {
		this.realChannel = realChannel;
	}

	ServerSocketChannelConfig getWrappedConfig() {
		return realChannel.getConfig();
	}

	@Override
	public int getBacklog() {
		return this.getWrappedConfig().getBacklog();
	}

	@Override
	public void setBacklog(int backlog) {
		this.getWrappedConfig().setBacklog(backlog);
	}

	@Override
	public int getReceiveBufferSize() {
		return this.getWrappedConfig().getReceiveBufferSize();
	}

	@Override
	public void setReceiveBufferSize(int receiveBufferSize) {
		this.getWrappedConfig().setReceiveBufferSize(receiveBufferSize);
	}

	@Override
	public boolean isReuseAddress() {
		return this.getWrappedConfig().isReuseAddress();
	}

	@Override
	public void setReuseAddress(boolean reuseAddress) {
		this.getWrappedConfig().setReuseAddress(reuseAddress);
	}

	@Override
	public ChannelBufferFactory getBufferFactory() {
		return this.getWrappedConfig().getBufferFactory();
	}

	@Override
	public void setBufferFactory(ChannelBufferFactory bufferFactory) {
		this.getWrappedConfig().setBufferFactory(bufferFactory);
	}

	@Override
	public int getConnectTimeoutMillis() {
		return this.getWrappedConfig().getConnectTimeoutMillis();
	}

	@Override
	public void setConnectTimeoutMillis(int connectTimeoutMillis) {
		this.getWrappedConfig().setConnectTimeoutMillis(connectTimeoutMillis);
	}

	@Override
	public ChannelPipelineFactory getPipelineFactory() {
		return pipelineFactory;
	}

	@Override
	public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
		this.pipelineFactory = pipelineFactory;
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
	public TunnelIdGenerator getTunnelIdGenerator() {
		return tunnelIdGenerator;
	}

	@Override
	public void setTunnelIdGenerator(TunnelIdGenerator tunnelIdGenerator) {
		this.tunnelIdGenerator = tunnelIdGenerator;
	}

	@Override
	public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
		this.getWrappedConfig().setPerformancePreferences(connectionTime, latency, bandwidth);
	}

	@Override
	public boolean setOption(String key, Object value) {
		if (PIPELINE_FACTORY_OPTION.equalsIgnoreCase(key)) {
			this.setPipelineFactory((ChannelPipelineFactory) value);
			return true;
		}

		if (TUNNEL_ID_GENERATOR_OPTION.equalsIgnoreCase(key)) {
			this.setTunnelIdGenerator((TunnelIdGenerator) value);
			return true;
		}

		if (USER_AGENT_OPTION.equalsIgnoreCase(key)) {
			this.setUserAgent((String) value);
			return true;
		}

		return this.getWrappedConfig().setOption(key, value);
	}

	@Override
	public void setOptions(Map<String, Object> options) {
		for (Entry<String, Object> e : options.entrySet()) {
			setOption(e.getKey(), e.getValue());
		}
	}
}
