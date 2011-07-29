package org.jboss.netty.channel.socket.http;

import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;

/**
 * Configuration for HTTP tunnels. Where possible, properties set on this
 * configuration will be applied to the two channels that service sending and
 * receiving data on this end of the tunnel.
 * <p>
 * HTTP tunnel clients have the following additional options:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th>
 * <th>Associated setter method</th>
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
public interface HttpTunnelChannelConfig extends SocketChannelConfig {

	/**
	 * Similarly to
	 * {@link org.jboss.netty.channel.socket.nio.NioSocketChannelConfig#setWriteBufferHighWaterMark(int)
	 * NioSocketChannelConfig.setWriteBufferHighWaterMark()}, the high water
	 * mark refers to the buffer size at which a user of the channel should stop
	 * writing. When the number of queued bytes exceeds the high water mark,
	 * {@link org.jboss.netty.channel.Channel#isWritable() Channel.isWritable()}
	 * will return false. Once the number of queued bytes falls below the
	 * {@link #setWriteBufferLowWaterMark(int) low water mark},
	 * {@link org.jboss.netty.channel.Channel#isWritable() Channel.isWritable()}
	 * will return true again, indicating that the client can begin to send more
	 * data.
	 *
	 * @see NioSocketChannelConfig#setWriteBufferHighWaterMark(int)
	 */
	public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark);
	public int getWriteBufferHighWaterMark();

	/**
	 * The low water mark refers to the "safe" size of the queued byte buffer at
	 * which more data can be enqueued. When the
	 * {@link #setWriteBufferHighWaterMark(int) high water mark} is exceeded,
	 * {@link org.jboss.netty.channel.Channel#isWritable()
	 * Channel.isWriteable()} will return false until the buffer drops below
	 * this level. By creating a sufficient gap between the high and low water
	 * marks, rapid oscillation between "write enabled" and "write disabled" can
	 * be avoided.
	 *
	 * @see org.jboss.netty.channel.socket.nio.NioSocketChannelConfig#setWriteBufferLowWaterMark(int)
	 */
	public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark);
	public int getWriteBufferLowWaterMark();
}
