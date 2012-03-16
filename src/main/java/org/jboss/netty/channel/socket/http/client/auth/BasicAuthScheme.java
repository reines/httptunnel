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

package org.jboss.netty.channel.socket.http.client.auth;

import java.util.Map;

import org.jboss.netty.channel.socket.http.util.Base64Coder;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * An implementation of HTTP basic authentication for use with proxy
 * authentication. See RFC 2617.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public class BasicAuthScheme implements AuthScheme {

	private static final String NAME = "basic";

	@Override
	public String getName() {
		return NAME;
	}

	/**
	 * Generates the authentication response header for the given request, using
	 * the given credentials. In the basic scheme neither the request or
	 * challenge are actually used.
	 */
	@Override
	public String authenticate(HttpRequest request, Map<String, String> challenge, String username, String password) {
		final String credentials = String.format("%s:%s", username, password);

		return new String(Base64Coder.encode(credentials.getBytes())).trim();
	}
}
