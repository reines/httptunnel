import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.http.client.HttpTunnelClientChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

public class ClientTest {

	public static final int TIMEOUT = 10;
	public static final int ITERATIONS = Integer.MAX_VALUE;

	public static void main(String[] args) {
		new ClientTest(new InetSocketAddress("localhost", 8181));
	}

	private static Channel createClientChannel(InetSocketAddress addr, ChannelPipelineFactory pipelineFactory, int timeout) {
		// TCP socket factory
		ClientSocketChannelFactory socketFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

		// HTTP socket factory
		socketFactory = new HttpTunnelClientChannelFactory(socketFactory);

		final ClientBootstrap bootstrap = new ClientBootstrap(socketFactory);
		bootstrap.setPipelineFactory(pipelineFactory);

		bootstrap.setOption("tcpNoDelay", true);
//		bootstrap.setOption("proxyAddress", new InetSocketAddress("localhost", 8888));

		final ChannelFuture future = bootstrap.connect(addr);

		try { future.await(timeout, TimeUnit.SECONDS); } catch (InterruptedException e) { }

		// If we managed to connect then set the channel and type
		if (future.isSuccess())
			return future.getChannel();

		// Otherwise cancel the attempt and give up
		future.cancel();
		return null;
	}

	private final Channel channel;

	public ClientTest(InetSocketAddress addr) {
		channel = ClientTest.createClientChannel(addr, new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new TestChannelHandler());
			}
		}, TIMEOUT);

		final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
		buffer.writeBytes("hello world".getBytes());

		for (int i = 0;i < ITERATIONS;i++) {
			try { Thread.sleep(1000); } catch (InterruptedException e) { }

			System.out.println("sent: " + channel.write(buffer).awaitUninterruptibly().isSuccess());
		}

		channel.close();
	}
}
