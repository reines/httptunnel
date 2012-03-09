package org.jboss.netty.channel.socket.http;

import static org.junit.Assert.*;
import static org.jboss.netty.channel.socket.http.state.SaturationStateChange.*;

import org.jboss.netty.channel.socket.http.util.SaturationManager;
import org.junit.Before;
import org.junit.Test;

public class SaturationManagerTest {

	private SaturationManager manager;

	@Before
	public void setUp() {
		manager = new SaturationManager(100L, 200L);
	}

	@Test
	public void testQueueSizeChanged() {
		assertEquals(NO_CHANGE, manager.queueSizeChanged(100L));
		assertEquals(NO_CHANGE, manager.queueSizeChanged(99L));
		assertEquals(NO_CHANGE, manager.queueSizeChanged(1L));
		assertEquals(SATURATED, manager.queueSizeChanged(1L));
		assertEquals(NO_CHANGE, manager.queueSizeChanged(10L));

		assertEquals(NO_CHANGE, manager.queueSizeChanged(-10L));
		assertEquals(NO_CHANGE, manager.queueSizeChanged(-1L));
		assertEquals(NO_CHANGE, manager.queueSizeChanged(-1L));
		assertEquals(DESATURATED, manager.queueSizeChanged(-99L));
		assertEquals(NO_CHANGE, manager.queueSizeChanged(-100L));
	}
}
