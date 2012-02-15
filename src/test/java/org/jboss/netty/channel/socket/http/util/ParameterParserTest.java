package org.jboss.netty.channel.socket.http.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class ParameterParserTest {

	@Test
	public void testSingleParam() {
		final ParameterParser parser = new ParameterParser("Realm=\"test\"");
		final Map<String, String> params = parser.split(',');

		assertEquals("Incorrect number of parameters found", 1, params.size());

		assertTrue("Missing parameter key: Realm", params.containsKey("realm"));
		assertEquals("Unmatched value for key: Realm", "test", params.get("realm"));
	}

	@Test
	public void testMultiParams() {
		final ParameterParser parser = new ParameterParser("Realm=\"test\", Key=value");
		final Map<String, String> params = parser.split(',');

		assertEquals("Incorrect number of parameters found", 2, params.size());

		assertTrue("Missing parameter key: Realm", params.containsKey("realm"));
		assertEquals("Unmatched value for key: Realm", "test", params.get("realm"));

		assertTrue("Missing parameter key: Key", params.containsKey("key"));
		assertEquals("Unmatched value for key: Key", "value", params.get("key"));
	}

	@Test
	public void testEscapedParams() {
		final ParameterParser parser = new ParameterParser("Test=\"a key, with a delim and a \\\"\"");
		final Map<String, String> params = parser.split(',');

		assertEquals("Incorrect number of parameters found", 1, params.size());

		assertTrue("Missing parameter key: Test", params.containsKey("test"));
		assertEquals("Unmatched value for key: Test", "a key, with a delim and a \\\"", params.get("test"));
	}
}
