package com.yammer.httptunnel.util;

import org.jboss.netty.channel.MessageEvent;

import com.yammer.metrics.core.TimerContext;

public class TimedMessageEventWrapper {

	private final MessageEvent event;
	private final TimerContext context;

	public TimedMessageEventWrapper(MessageEvent event, TimerContext context) {
		this.event = event;
		this.context = context;
	}

	public MessageEvent getEvent() {
		return event;
	}

	public TimerContext getContext() {
		return context;
	}
}
