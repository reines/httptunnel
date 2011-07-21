package org.jboss.netty.channel.socket.http;

import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.junit.Test;

public class HttpTunnelEventTest {

    public static final int TIMEOUT = 2;

    private Channel createServerChannel(InetSocketAddress addr, ChannelPipelineFactory pipelineFactory) {
        // TCP socket factory
        ServerSocketChannelFactory socketFactory =
                new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool());

        // HTTP socket factory
        socketFactory = new HttpTunnelServerChannelFactory(socketFactory);

        final ServerBootstrap bootstrap = new ServerBootstrap(socketFactory);
        bootstrap.setPipelineFactory(pipelineFactory);

        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("reuseAddress", true);

        return bootstrap.bind(addr);
    }

    private Channel createClientChannel(InetSocketAddress addr, ChannelPipelineFactory pipelineFactory) {
        // TCP socket factory
        ClientSocketChannelFactory socketFactory =
                new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool());

        // HTTP socket factory
        socketFactory = new HttpTunnelClientChannelFactory(socketFactory);

        final ClientBootstrap bootstrap = new ClientBootstrap(socketFactory);
        bootstrap.setPipelineFactory(pipelineFactory);

        bootstrap.setOption("tcpNoDelay", true);

        final ChannelFuture future = bootstrap.connect(addr);

        try { future.await(TIMEOUT, TimeUnit.SECONDS); } catch (InterruptedException e) { }

        // If we managed to connect then set the channel and type
        if (future.isSuccess())
            return future.getChannel();

        // Otherwise cancel the attempt and give up
        future.cancel();
        return null;
    }

    /**
     * Here we close the client first, which should close the client channel and the server accepted channel,
     * but not the server channel itself.
     */
    @Test
    public void testOpenCloseClientChannel() throws InterruptedException {
        final InetSocketAddress addr = new InetSocketAddress("localhost", 8181);

        final OpenCloseIncomingChannelHandler serverHandler = new OpenCloseIncomingChannelHandler();
        final Channel server = this.createServerChannel(addr, new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new StringEncoder(), serverHandler, new StringDecoder());
            }
        });

        // Server should be open and bound
        assertTrue("server isn't open after connect", server.isOpen());
        assertTrue("server isn't bound after connect", server.isBound());

        final OpenCloseOutgoingChannelHandler clientHandler = new OpenCloseOutgoingChannelHandler();
        final Channel client = this.createClientChannel(addr, new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new StringEncoder(), clientHandler, new StringDecoder());
            }
        });

        // Check we actually managed to connect
        assertTrue(client != null);

        // Client should be open, bound, and connected
        assertTrue("client isn't open after connect", client.isOpen());
        assertTrue("client isn't bound after connect", client.isBound());
        assertTrue("client isn't connected after connect", client.isConnected());

        // Send a test message
        client.write("hello world").await();

        // Close the channel
        client.close().await();

        // Client shouldn't be open, bound, or connected
        assertTrue("client is connected after close", !client.isConnected());
        assertTrue("client is bound after close", !client.isBound());
        assertTrue("client is open after close", !client.isOpen());

        // Check we received the correct client events
        clientHandler.assertSatisfied(TIMEOUT);

        // Check we received the correct server events
        serverHandler.assertSatisfied(TIMEOUT);

        // Close the server
        server.close().await();

        // Server shouldn't be open or bound
        assertTrue("server is bound after close", !server.isBound());
        assertTrue("server is open after close", !server.isOpen());
    }

    /**
     * Here we close the server first, which should close the server channel itself, the server accepted channel,
     * and the client channel.
     */
    @Test
    public void testOpenCloseServerChannel() throws InterruptedException {
        final InetSocketAddress addr = new InetSocketAddress("localhost", 8181);

        final OpenCloseIncomingChannelHandler serverHandler = new OpenCloseIncomingChannelHandler();
        final Channel server = this.createServerChannel(addr, new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new StringEncoder(), serverHandler, new StringDecoder());
            }
        });

        // Server should be open and bound
        assertTrue("server isn't open after connect", server.isOpen());
        assertTrue("server isn't bound after connect", server.isBound());

        final OpenCloseOutgoingChannelHandler clientHandler = new OpenCloseOutgoingChannelHandler();
        final Channel client = this.createClientChannel(addr, new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new StringEncoder(), clientHandler, new StringDecoder());
            }
        });

        // Check we actually managed to connect
        assertTrue(client != null);

        // Client should be open, bound, and connected
        assertTrue("client isn't open after connect", client.isOpen());
        assertTrue("client isn't bound after connect", client.isBound());
        assertTrue("client isn't connected after connect", client.isConnected());

        // Send a test message
        client.write("hello world").await();

        // Close the server
        server.close().await();

        // Server shouldn't be open or bound
        assertTrue("server is bound after close", !server.isBound());
        assertTrue("server is open after close", !server.isOpen());

        // Check we received the correct server events
        serverHandler.assertSatisfied(TIMEOUT);

        // Check we received the correct client events
        clientHandler.assertSatisfied(TIMEOUT);

        // Closing the server should have closed the client automatically

        // Client shouldn't be open, bound, or connected
        assertTrue("client is connected after close", !client.isConnected());
        assertTrue("client is bound after close", !client.isBound());
        assertTrue("client is open after close", !client.isOpen());
    }
}
