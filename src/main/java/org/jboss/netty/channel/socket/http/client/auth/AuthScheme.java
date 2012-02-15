package org.jboss.netty.channel.socket.http.client.auth;

import java.util.Map;

import org.jboss.netty.channel.socket.http.client.ProxyAuthenticationException;
import org.jboss.netty.handler.codec.http.HttpRequest;

public interface AuthScheme {

	public String getName();

	public String authenticate(HttpRequest request, Map<String, String> challenge, String username, String password) throws ProxyAuthenticationException;
}
