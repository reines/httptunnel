import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.http.server.HttpTunnelServerChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class ServerTest {

	public static void main(String[] args) {
		new ServerTest(new InetSocketAddress("localhost", 8181));
	}

	private static Channel createServerChannel(InetSocketAddress addr, ChannelPipelineFactory pipelineFactory) {
		// TCP socket factory
		ServerSocketChannelFactory socketFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

		// HTTP socket factory
		socketFactory = new HttpTunnelServerChannelFactory(socketFactory);

		final ServerBootstrap bootstrap = new ServerBootstrap(socketFactory);
		bootstrap.setPipelineFactory(pipelineFactory);

		bootstrap.setOption("child.tcpNoDelay", true);
		bootstrap.setOption("reuseAddress", true);

		return bootstrap.bind(addr);
	}

	private final Channel channel;

	public ServerTest(InetSocketAddress addr) {
		channel = ServerTest.createServerChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new TestChannelHandler());
			}
		});
	}
}
