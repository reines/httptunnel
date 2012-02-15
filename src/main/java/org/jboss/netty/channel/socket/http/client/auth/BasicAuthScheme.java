package org.jboss.netty.channel.socket.http.client.auth;

import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.jboss.netty.handler.codec.http.HttpRequest;

// http://tools.ietf.org/html/rfc2617
public class BasicAuthScheme implements AuthScheme {

	private static final String NAME = "basic";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String authenticate(HttpRequest request, Map<String, String> challenge, String username, String password) {
		final String credentials = String.format("%s:%s", username, password);

		return Base64.encodeBase64String(credentials.getBytes()).trim();
	}
}
