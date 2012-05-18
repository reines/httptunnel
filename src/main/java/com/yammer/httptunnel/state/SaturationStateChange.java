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
 * Represents the state change of a channel in response in the amount of pending
 * data to be sent - either no change occurs, the channel becomes desaturated
 * (indicating that writing can safely commence) or it becomes saturated
 * (indicating that writing should cease).
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public enum SaturationStateChange {
	/**
	 * There was no change in the channels saturation state.
	 */
	NO_CHANGE,

	/**
	 * The channel is no longer saturated and writing can safely commence.
	 */
	DESATURATED,

	/**
	 * The channel has become saturated and writing should cease until it
	 * becomes desaturated.
	 */
	SATURATED;
}
