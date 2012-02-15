/*
 *
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.jboss.netty.channel.socket.http.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;

/**
 * A simple parser intended to parse sequences of name/value pairs.
 * Parameter values are exptected to be enclosed in quotes if they
 * contain unsafe characters, such as '=' characters or separators.
 * Parameter values are optional and can be omitted.
 *
 * <p>
 *  <code>param1 = value; param2 = "anything goes; really"; param3</code>
 * </p>
 *
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:jamie@onedrum.com">Jamie Furness</a>
 *
 * @since 3.0
 */
public class ParameterParser {

	/** String to be parsed */
	private final char[] chars;

	/** Current position in the string */
	private int pos;

	/** Default ParameterParser constructor */
	public ParameterParser(String input) {
		if (input == null)
			input = "";

		chars = input.toCharArray();
		pos = 0;
	}

	/** A helper method to process the parsed token. */
	private String getToken(int start, int end, boolean quoted) {
		// Trim leading white spaces
		while ((start < end) && (Character.isWhitespace(chars[start])))
			start++;

		// Trim trailing white spaces
		while ((end > start) && (Character.isWhitespace(chars[end - 1])))
			end--;

		// Strip away quotes if necessary
		if (quoted) {
			if (((end - start) >= 2) && (chars[start] == '"') && (chars[end - 1] == '"')) {
				start++;
				end--;
			}
		}

		if (end >= start)
			return new String(chars, start, end - start);

		return null;
	}

	/**
	 * Parse out a token until any of the given terminators is encountered.
	 */
	private String parseToken(final char[] terminators) {
		int start = pos;
		int end = pos;

		while (pos < chars.length) {
			char ch = chars[pos];
			if (ArrayUtils.contains(terminators, ch))
				break;

			end++;
			pos++;
		}

		return this.getToken(start, end, false);
	}

	/**
	 * Parse out a token until any of the given terminators is encountered.
	 * Special characters in quoted tokens are escaped.
	 */
	private String parseQuotedToken(final char[] terminators) {
		int start = pos;
		int end = pos;

		boolean quoted = false;
		boolean charEscaped = false;

		while (pos < chars.length) {
			char ch = chars[pos];
			if (!quoted && ArrayUtils.contains(terminators, ch))
				break;

			if (!charEscaped && ch == '"')
				quoted = !quoted;

			charEscaped = (!charEscaped && ch == '\\');
			end++;
			pos++;
		}

		return this.getToken(start, end, true);
	}

	public Map<String, String> split(char separator) {
		final Map<String, String> params = new HashMap<String, String>();

		pos = 0;

		while (pos < chars.length) {
			final String paramName = this.parseToken(new char[] { '=', separator }).toLowerCase();
			String paramValue = null;

			if (pos < chars.length && (chars[pos] == '=')) {
				pos++; // skip '='
				paramValue = this.parseQuotedToken(new char[] { separator });
			}

			if (pos < chars.length && (chars[pos] == separator))
				pos++; // skip separator

			if (paramName != null && !(paramName.isEmpty() && paramValue == null))
				params.put(paramName, paramValue);
		}

		return params;
	}
}
