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

package org.jboss.netty.channel.socket.http;

import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.socket.SocketChannelConfig;

/**
 * Configuration for HTTP tunnels. Where possible, properties set on this
 * configuration will be applied to the two channels that service sending and
 * receiving data on this end of the tunnel.
 */
public abstract class HttpTunnelChannelConfig extends DefaultChannelConfig implements SocketChannelConfig {

	/**
     * The minimum value that the high water mark may be set to, in addition to the
     * constraint that the high water mark must be strictly greater than the low
     * water mark.
     */
	public static final int MIN_HIGH_WATER_MARK = 1;

	/**
     * The minimum value that the low water mark may be set to.
     */
	public static final int MIN_LOW_WATER_MARK = 0;

	/**
     * The default level for the write buffer's high water mark, presently set to
     * 64KByte.
     */
	public static final int DEFAULT_HIGH_WATER_MARK = 64 * 1024;

	/**
     * The default level for the write buffer's low water mark, presently set to
     * 32KByte.
     */
	public static final int DEFAULT_LOW_WATER_MARK = 32 * 1024;

	/**
	 * The default maximum delay (in seconds) between requests before a PING
	 * request is injected.
	 */
	public static final int DEFAULT_PING_DELAY = 5;

	static final String HIGH_WATER_MARK_OPTION = "writeBufferhHighWaterMark";
	static final String LOW_WATER_MARK_OPTION = "writeBufferLowWaterMark";

	private int writeBufferLowWaterMark;
	private int writeBufferHighWaterMark;
	private int pingDelay;

	protected HttpTunnelChannelConfig() {
		writeBufferLowWaterMark = DEFAULT_LOW_WATER_MARK;
		writeBufferHighWaterMark = DEFAULT_HIGH_WATER_MARK;
		pingDelay = DEFAULT_PING_DELAY;
	}

	/**
	 * The maximum delay (in seconds) between requests being sent by the HTTP
	 * tunnel. If no request has been dispatched within this time a PING request
	 * is constructed and dispatched.
	 */
	public void setPingDelay(int pingDelay) {
		this.pingDelay = pingDelay;
	}

	/**
	 * @return the current value (in seconds) of the ping delay.
	 */
	public int getPingDelay() {
		return pingDelay;
	}

	/**
	 * @return the current value (in bytes) of the high water mark.
	 */
	public int getWriteBufferHighWaterMark() {
		return writeBufferHighWaterMark;
	}

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
	 * @see org.jboss.netty.channel.socket.nio.NioSocketChannelConfig#setWriteBufferHighWaterMark(int)
	 */
	public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
		if (writeBufferHighWaterMark <= writeBufferLowWaterMark)
			throw new IllegalArgumentException("Write buffer high water mark must be strictly greater than the low water mark");

		if (writeBufferHighWaterMark < MIN_HIGH_WATER_MARK)
			throw new IllegalArgumentException("Cannot set write buffer high water mark lower than " + MIN_HIGH_WATER_MARK);

		this.writeBufferHighWaterMark = writeBufferHighWaterMark;
	}

	/**
	 * @return the current value (in bytes) of the low water mark.
	 */
	public int getWriteBufferLowWaterMark() {
		return writeBufferLowWaterMark;
	}

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
	public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
		if (writeBufferLowWaterMark >= writeBufferHighWaterMark)
			throw new IllegalArgumentException("Write buffer low water mark must be strictly less than the high water mark");

		if (writeBufferLowWaterMark < MIN_LOW_WATER_MARK)
			throw new IllegalArgumentException("Cannot set write buffer low water mark lower than " + MIN_LOW_WATER_MARK);

		this.writeBufferLowWaterMark = writeBufferLowWaterMark;
	}

	@Override
	public boolean setOption(String key, Object value) {
		if (HIGH_WATER_MARK_OPTION.equals(key)) {
			this.setWriteBufferHighWaterMark((Integer) value);
			return true;
		}
		else if (LOW_WATER_MARK_OPTION.equals(key)) {
			this.setWriteBufferLowWaterMark((Integer) value);
			return true;
		}

		return super.setOption(key, value);
	}
}
