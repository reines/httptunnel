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

package com.yammer.httptunnel.client.auth;

import java.util.Map;

import org.jboss.netty.handler.codec.http.HttpRequest;

import com.yammer.httptunnel.client.ProxyAuthenticationException;

/**
 * This interface is used by the client end of an http tunnel to handle proxy
 * authentication requests.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public interface AuthScheme {

	/**
	 * Gets the name of the authentication method supported by this instance.
	 */
	public String getName();

	/**
	 * Generates the authentication response header for the given request, using
	 * the given credentials.
	 */
	public String authenticate(HttpRequest request, Map<String, String> challenge, String username, String password) throws ProxyAuthenticationException;
}
