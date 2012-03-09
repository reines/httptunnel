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

package org.jboss.netty.channel.socket.http.state;

/**
 * Represents the connection state change of a channel - either it is not
 * connected, in the process of connecting (after which, if successful, it will
 * become connected), connected, or in the process of disconnecting (after which
 * it will become disconnected).
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public enum ConnectState {
	/**
	 * The channel is currently not connected to a remote endpoint.
	 */
	DISCONNECTED,

	/**
	 * The channel is currently in the process of connecting to a remote
	 * endpoint.
	 */
	CONNECTING,

	/**
	 * The channel is currently connected to a remote endpoint.
	 */
	CONNECTED,

	/**
	 * The channel is currently in the process of disconnecting from a remote
	 * endpoint.
	 */
	DISCONNECTING;
}
