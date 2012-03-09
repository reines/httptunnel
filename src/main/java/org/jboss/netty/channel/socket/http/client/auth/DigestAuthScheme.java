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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.channel.socket.http.client.ProxyAuthenticationException;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * An implementation of HTTP digest authentication for use with proxy
 * authentication. See RFC 2617.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author Jamie Furness (jamie@onedrum.com)
 * @author OneDrum Ltd.
 */
public class DigestAuthScheme implements AuthScheme {

	private static final String NAME = "digest";
	private static final String[] requiredParams = {"realm", "nonce", "qop"}; // Technically qop is optional, but *should* be implemented

	private static final Random random;

	static {
		random = new Random();
	}

	private static String bytesToHex(byte[] bytes) {
		final Formatter formatter = new Formatter();

		for (byte b : bytes)
			formatter.format("%02x", b);

		return formatter.toString();
	}

	private static String generateNonce() {
		final byte[] bytes = new byte[8];

		random.nextBytes(bytes);

		return bytesToHex(bytes);
	}

	private static String[] parseAlgorithms(String algorithms) {
		if (algorithms == null)
			return new String[]{"MD5"};

		return algorithms.split(",");
	}

	private static String generateHA1(String username, String realm, String password) throws NoSuchAlgorithmException {
		final MessageDigest digest = MessageDigest.getInstance("MD5");

		digest.update(String.format("%s:%s:%s", username, realm, password).getBytes());
		return bytesToHex(digest.digest());
	}

	private static String generateHA2(String method, String uri) throws NoSuchAlgorithmException {
		final MessageDigest digest = MessageDigest.getInstance("MD5");

		digest.update(String.format("%s:%s", method, uri).getBytes());
		return bytesToHex(digest.digest());
	}

	private static String generateResult(String HA1, String nonce, String nc, String cnonce, String qop, String HA2) throws NoSuchAlgorithmException {
		final MessageDigest digest = MessageDigest.getInstance("MD5");

		digest.update(String.format("%s:%s:%s:%s:%s:%s", HA1, nonce, nc, cnonce, qop, HA2).getBytes());
		return bytesToHex(digest.digest());
	}

	private String cnonce;
	private int counter;

	public DigestAuthScheme() {
		cnonce = DigestAuthScheme.generateNonce();
		counter = 0;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String authenticate(HttpRequest request, Map<String, String> challenge, String username, String password) throws ProxyAuthenticationException {
		// Check we have all the required params
		for (String paramName : requiredParams) {
			if (!challenge.containsKey(paramName))
				throw new ProxyAuthenticationException("Missing parameter: " + paramName);
		}

		// Check algorithms contains MD5, it's all we support
		final String[] supportedAlgorithms = DigestAuthScheme.parseAlgorithms(challenge.get("algorithm"));
		if (!ArrayUtils.contains(supportedAlgorithms, "MD5"))
			throw new ProxyAuthenticationException("No supported algorithm found");

		final List<String> response = new LinkedList<String>();

		response.add(String.format("realm=\"%s\"", challenge.get("realm")));
		response.add(String.format("nonce=\"%s\"", challenge.get("nonce")));
		response.add(String.format("username=\"%s\"", username));
		response.add(String.format("uri=\"%s\"", request.getUri()));

		// Check qop contains auth, it's all we support
		final String[] qopOptions = challenge.get("qop").split(",");
		if (!ArrayUtils.contains(qopOptions, "auth"))
			throw new ProxyAuthenticationException("No supported QOP found");

		counter++;

		response.add("qop=auth");
		response.add(String.format("cnonce=\"%s\"", cnonce));

		final String nc = StringUtils.leftPad(Integer.toHexString(counter), 8, '0');
		response.add(String.format("nc=%s", nc));

		// If the opaque param is set, copy it over
		if (challenge.containsKey("opaque"))
			response.add(String.format("opaque=\"%s\"", challenge.get("opaque")));

		try {
			final String HA1 = DigestAuthScheme.generateHA1(username, challenge.get("realm"), password);
			final String HA2 = DigestAuthScheme.generateHA2(request.getMethod().toString(), request.getUri());

			response.add(String.format("response=\"%s\"", DigestAuthScheme.generateResult(HA1, challenge.get("nonce"), nc, cnonce, "auth", HA2)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new ProxyAuthenticationException(e.getMessage());
		}

		return StringUtils.join(response.iterator(), ", ");
	}
}
