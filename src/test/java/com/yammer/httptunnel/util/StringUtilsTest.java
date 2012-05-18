package com.yammer.httptunnel.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.yammer.httptunnel.util.StringUtils;

public class StringUtilsTest {

	@Test
	public void testCapitalize() {
		assertEquals("Hello world", StringUtils.capitalize("hello world"));
		assertEquals("Hello world", StringUtils.capitalize("Hello world"));
		assertEquals("Hello world", StringUtils.capitalize("Hello World"));
		assertEquals("Hello world", StringUtils.capitalize("HELLO WORLD"));

		assertEquals("Hello world?!", StringUtils.capitalize("hello world?!"));
	}

	@Test
	public void testLeftPad() {
		assertEquals("007", StringUtils.leftPad("7", 3, '0'));
		assertEquals("777", StringUtils.leftPad("777", 3, '0'));
		assertEquals("7777", StringUtils.leftPad("7777", 3, '0'));
		assertEquals("000", StringUtils.leftPad("0", 3, '0'));
	}

	@Test
	public void testInCharArray() {
		assertTrue(StringUtils.inStringArray(new String[]{"hello", "world"}, "hello"));
		assertTrue(StringUtils.inStringArray(new String[]{"hello", null}, "hello"));
		assertFalse(StringUtils.inStringArray(new String[]{"goodbye", "world"}, "hello"));
		assertFalse(StringUtils.inStringArray(new String[]{}, "hello"));
	}

	@Test
	public void testInStringArray() {
		assertTrue(StringUtils.inCharArray("abc".toCharArray(), 'a'));
		assertFalse(StringUtils.inCharArray("bcd".toCharArray(), 'a'));
		assertFalse(StringUtils.inCharArray("".toCharArray(), 'a'));
	}
}
