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

package com.yammer.httptunnel.client;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import com.yammer.httptunnel.client.auth.AuthScheme;
import com.yammer.httptunnel.client.auth.BasicAuthScheme;
import com.yammer.httptunnel.client.auth.DigestAuthScheme;
import com.yammer.httptunnel.util.HttpTunnelMessageUtils;
import com.yammer.httptunnel.util.ParameterParser;
import com.yammer.httptunnel.util.StringUtils;

/**
 * Pipeline component which controls proxy authentication requests as well as
 * injecting no-cache and keep-alive headers.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelClientChannelProxyHandler extends SimpleChannelHandler {

	public static final String NAME = "proxyHandler";

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(HttpTunnelClientChannelProxyHandler.class);

	private static class ProxyAuthHandler {
		public static ProxyAuthHandler init(List<String> authRequests) throws ProxyAuthenticationException {
			final Map<String, String> reqAuthSchemes = new HashMap<String, String>();

			// Split out the scheme and parameters for each proxy auth header
			for (String authRequest : authRequests) {
				final int delim = authRequest.indexOf(' ');
				if (delim < 0)
					continue;

				final String authScheme = authRequest.substring(0, delim).toLowerCase();
				final String authParams = authRequest.substring(delim + 1);

				reqAuthSchemes.put(authScheme, authParams);
			}

			// If we found none then we have a malformed request
			if (reqAuthSchemes.isEmpty())
				throw new ProxyAuthenticationException("Malformed or missing proxy auth headers");

			// Find the first auth scheme we support that is also supported by
			// the server
			for (AuthScheme supportedScheme : proxyAuthSchemes) {
				final String authParams = reqAuthSchemes.get(supportedScheme.getName().toLowerCase());
				if (authParams == null)
					continue;

				// We found a supported auth scheme, parse the parameters and
				// attempt to generate a header
				final Map<String, String> params = new ParameterParser(authParams).split(',');

				return new ProxyAuthHandler(supportedScheme, params);
			}

			throw new ProxyAuthenticationException("Proxy requested unsupported proxy authentication scheme");
		}

		private final AuthScheme scheme;
		private final Map<String, String> challenge;

		private ProxyAuthHandler(AuthScheme scheme, Map<String, String> challenge) {
			this.scheme = scheme;
			this.challenge = challenge;
		}

		public String authenticate(HttpRequest request, String username, String password) throws Exception {
			return String.format("%s %s", StringUtils.capitalize(scheme.getName()), scheme.authenticate(request, challenge, username, password));
		}
	}

	private static final List<AuthScheme> proxyAuthSchemes = new LinkedList<AuthScheme>();

	static {
		// Add supported proxy auth schemes, in order of preference

		proxyAuthSchemes.add(new DigestAuthScheme());
		proxyAuthSchemes.add(new BasicAuthScheme());
	}

	private final HttpTunnelClientChannelConfig config;

	private AtomicReference<ProxyAuthHandler> proxyAuthHandler;

	public HttpTunnelClientChannelProxyHandler(HttpTunnelClientChannelConfig config) {
		this.config = config;

		proxyAuthHandler = new AtomicReference<ProxyAuthHandler>();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		if (e.getCause() instanceof ProxyAuthenticationException) {
			if (LOG.isWarnEnabled())
				LOG.warn("Error authenticating for proxy", e.getCause());
		}

		super.exceptionCaught(ctx, e);
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		final HttpResponse response = (HttpResponse) e.getMessage();

		if (HttpTunnelMessageUtils.isProxyAuthResponse(response)) {
			if (LOG.isDebugEnabled())
				LOG.debug("tunnel received HTTP 407 proxy auth required response");

			// Generate a proxy authentication header - throws an exception if
			// no supported auth method or credentials
			if (!proxyAuthHandler.compareAndSet(null, ProxyAuthHandler.init(response.getHeaders(HttpHeaders.Names.PROXY_AUTHENTICATE))))
				throw new ProxyAuthenticationException("Received HTTP 407 response even though we already provided credentials");

			if (LOG.isDebugEnabled())
				LOG.debug("resending request with proxy credentials (user: " + config.getProxyUsername() + ")");

			return;
		}

		ctx.sendUpstream(e);
	}

	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		final HttpRequest request = (HttpRequest) e.getMessage();

		// If we have a proxy auth header, add it into the request
		if (proxyAuthHandler.get() != null) {
			final String proxyAuthHeader = proxyAuthHandler.get().authenticate(request, config.getProxyUsername(), config.getProxyPassword());
			request.setHeader(HttpHeaders.Names.PROXY_AUTHORIZATION, proxyAuthHeader);
		}

		// request the connection be kept open for pipeling
		request.setHeader(HttpHeaders.Names.CONNECTION, "Keep-Alive");
		// request any proxy doesn't try give us a cached response
		request.setHeader(HttpHeaders.Names.PRAGMA, "No-Cache");

		ctx.sendDownstream(e);
	}
}
