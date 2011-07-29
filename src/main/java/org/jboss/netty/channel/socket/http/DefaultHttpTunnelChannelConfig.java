/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.http;

import org.jboss.netty.channel.DefaultChannelConfig;

public abstract class DefaultHttpTunnelChannelConfig extends DefaultChannelConfig implements HttpTunnelChannelConfig {

	public static final int MIN_HIGH_WATER_MARK = 1;
	public static final int MIN_LOW_WATER_MARK = 0;
	public static final int DEFAULT_HIGH_WATER_MARK = 64 * 1024; // 64kb
	public static final int DEFAULT_LOW_WATER_MARK = 32 * 1024; // 32kb

	public static final String HIGH_WATER_MARK_OPTION = "writeBufferhHighWaterMark";
	public static final String LOW_WATER_MARK_OPTION = "writeBufferLowWaterMark";

	protected volatile int writeBufferLowWaterMark;
	protected volatile int writeBufferHighWaterMark;

	public DefaultHttpTunnelChannelConfig() {
		writeBufferLowWaterMark = DEFAULT_LOW_WATER_MARK;
		writeBufferHighWaterMark = DEFAULT_HIGH_WATER_MARK;
	}

	@Override
	public int getWriteBufferHighWaterMark() {
		return writeBufferHighWaterMark;
	}

	@Override
	public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
		if (writeBufferHighWaterMark <= writeBufferLowWaterMark)
			throw new IllegalArgumentException("Write buffer high water mark must be strictly greater than the low water mark");

		if (writeBufferHighWaterMark < MIN_HIGH_WATER_MARK)
			throw new IllegalArgumentException("Cannot set write buffer high water mark lower than " + MIN_HIGH_WATER_MARK);

		this.writeBufferHighWaterMark = writeBufferHighWaterMark;
	}

	@Override
	public int getWriteBufferLowWaterMark() {
		return writeBufferLowWaterMark;
	}

	@Override
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
