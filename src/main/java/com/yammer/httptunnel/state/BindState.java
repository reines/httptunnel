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

package com.yammer.httptunnel.state;

/**
 * Represents the bind state change of a channel - either it is not bound, in
 * the process of binding (after which, if successful, it will become bound),
 * bound, or in the process of unbinding (after which it will become unbound).
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public enum BindState {
	/**
	 * The channel is currently not bound to any address.
	 */
	UNBOUND,

	/**
	 * The channel is currently in the process of binding to a local address.
	 */
	BINDING,

	/**
	 * The channel is currently bound to a local address.
	 */
	BOUND,

	/**
	 * The channel is currently in the process of unbinding from a local
	 * address.
	 */
	UNBINDING;
}
