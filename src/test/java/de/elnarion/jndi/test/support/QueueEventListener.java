/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package de.elnarion.jndi.test.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.event.NamespaceChangeListener;
import javax.naming.event.NamingEvent;
import javax.naming.event.NamingExceptionEvent;
import javax.naming.event.ObjectChangeListener;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author Scott.Stark@jboss.org
 */
public class QueueEventListener implements ObjectChangeListener, NamespaceChangeListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(QueueEventListener.class);
	private final Semaphore eventCount = new Semaphore(0);
	private final ArrayList<NamingEvent> events = new ArrayList<>();

	@SuppressWarnings("unused")

	public boolean waitOnEvent() throws InterruptedException {
		return waitOnEvent(1, TimeUnit.SECONDS);
	}

	public boolean waitOnEvent(long timeout, TimeUnit timeUnit) throws InterruptedException {
		return eventCount.tryAcquire(timeout, timeUnit);
	}

	public NamingEvent getEvent(int index) {
		return events.get(index);
	}

	public void objectChanged(NamingEvent evt) {
		LOGGER.info("Begin objectChanged, " + evt);
		events.add(evt);
		eventCount.release();
		LOGGER.info("End objectChanged, " + evt);
	}

	public void namingExceptionThrown(NamingExceptionEvent evt) {
		// do nothing
	}

	public void objectAdded(NamingEvent evt) {
		LOGGER.info("Begin objectAdded, " + evt);
		events.add(evt);
		eventCount.release();
		LOGGER.info("End objectAdded, " + evt);
	}

	public void objectRemoved(NamingEvent evt) {
		events.add(evt);
		eventCount.release();
	}

	public void objectRenamed(NamingEvent evt) {
		events.add(evt);
		eventCount.release();
	}

	public int getEventCount() {
		return events.size();
	}

}
