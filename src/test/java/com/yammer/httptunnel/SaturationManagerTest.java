package com.yammer.httptunnel;

import static com.yammer.httptunnel.state.SaturationStateChange.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.yammer.httptunnel.util.SaturationManager;

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
