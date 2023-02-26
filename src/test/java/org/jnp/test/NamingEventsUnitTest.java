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
package org.jnp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Properties;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.event.EventContext;
import javax.naming.event.NamingEvent;

import org.jnp.interfaces.MarshalledValuePair;
import org.jnp.server.ExecutorEventMgr;
import org.jnp.server.NamingBeanImpl;
import org.jnp.test.support.QueueEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Scott.Stark@jboss.org
 */
class NamingEventsUnitTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(NamingEventsUnitTest.class);
	/** The actual namingMain service impl bean */
	private NamingBeanImpl namingBean;
	private QueueEventListener listener = new QueueEventListener();

	@BeforeEach
	void setUp() throws Exception {
		namingBean = new NamingBeanImpl();
		namingBean.setInstallGlobalService(true);
		namingBean.setEventMgr(new ExecutorEventMgr());
		namingBean.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		namingBean.stop();
	}

	@Test
	void testAddRemoveOneLevel() throws Exception {
		Properties env = new Properties();
		env.setProperty("java.naming.factory.initial", "org.jnp.interfaces.LocalOnlyContextFactory");
		env.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces");
		InitialContext ic = new InitialContext(env);
		LOGGER.info("Created InitialContext");
		Context ctx = (Context) ic.lookup("");
		assertTrue(ctx instanceof EventContext, "Context is an EventContext");
		EventContext ectx = (EventContext) ctx;
		ectx.addNamingListener("", EventContext.ONELEVEL_SCOPE, listener);
		LOGGER.info("Added NamingListener");
		ctx.bind("testAddObject", "testAddObject.bind");
		assertTrue(listener.waitOnEvent(), "Saw bind event");
		NamingEvent event = listener.getEvent(0);
		assertEquals(NamingEvent.OBJECT_ADDED, event.getType(), "OBJECT_ADDED");
		assertNull(event.getOldBinding(), "getOldBinding");
		assertEquals("testAddObject.bind", getValue(event.getNewBinding()));

		ctx.rebind("testAddObject", "testAddObject.rebind");
		assertTrue(listener.waitOnEvent(), "Saw rebind event");
		event = listener.getEvent(1);
		assertEquals(NamingEvent.OBJECT_CHANGED, event.getType(), "OBJECT_CHANGED");
		assertEquals("testAddObject.bind", getValue(event.getOldBinding()));
		assertEquals("testAddObject.rebind", getValue(event.getNewBinding()));

		ctx.unbind("testAddObject");
		assertTrue(listener.waitOnEvent(), "Saw unbind event");
		event = listener.getEvent(2);
		assertEquals(NamingEvent.OBJECT_REMOVED, event.getType(), "OBJECT_REMOVED");
		assertEquals("testAddObject.rebind", getValue(event.getOldBinding()));
		assertNull(event.getNewBinding(), "getNewBinding");

		// Create a subcontext
		Context subctx = ctx.createSubcontext("subctx");
		listener.waitOnEvent();
		assertEquals(4, listener.getEventCount(), "Should be 4 events");
		event = listener.getEvent(3);
		assertEquals(NamingEvent.OBJECT_ADDED, event.getType(), "OBJECT_ADDED");
		assertNull(event.getOldBinding(), "getOldBinding");
		assertEquals(subctx, getValue(event.getNewBinding()), "getNewBinding");

		// Creating a binding under subctx should not produce an event
		subctx.bind("subctx.testAddObject", "testAddObject.subctx.bind");
		assertFalse(listener.waitOnEvent(), "Wait on subctx bind");
		assertEquals(4, listener.getEventCount(), "Still should be 4 events");
	}

	@Test
	void testAddRemoveSubtree() throws Exception {
		Properties env = new Properties();
		env.setProperty("java.naming.factory.initial", "org.jnp.interfaces.LocalOnlyContextFactory");
		env.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces");
		InitialContext ic = new InitialContext(env);
		LOGGER.info("Created InitialContext");
		Context ctx = (Context) ic.lookup("");
		assertTrue(ctx instanceof EventContext, "Context is an EventContext");
		EventContext ectx = (EventContext) ctx;
		ectx.addNamingListener("", EventContext.SUBTREE_SCOPE, listener);
		LOGGER.info("Added NamingListener");
		ctx.bind("testAddObject", "testAddObject.bind");
		LOGGER.info("Object bound");
		assertTrue(listener.waitOnEvent(), "Saw bind event");
		NamingEvent event = listener.getEvent(0);
		assertEquals(NamingEvent.OBJECT_ADDED, event.getType(), "OBJECT_ADDED");
		assertNull(event.getOldBinding(), "getOldBinding");
		assertEquals("testAddObject.bind", getValue(event.getNewBinding()));

		ctx.rebind("testAddObject", "testAddObject.rebind");
		assertTrue(listener.waitOnEvent(), "Saw rebind event");
		event = listener.getEvent(1);
		assertEquals(NamingEvent.OBJECT_CHANGED, event.getType(), "OBJECT_CHANGED");
		assertEquals("testAddObject.bind", getValue(event.getOldBinding()));
		assertEquals("testAddObject.rebind", getValue(event.getNewBinding()));

		ctx.unbind("testAddObject");
		assertTrue(listener.waitOnEvent(), "Saw unbind event");
		event = listener.getEvent(2);
		assertEquals(NamingEvent.OBJECT_REMOVED, event.getType(), "OBJECT_REMOVED");
		assertEquals("testAddObject.rebind", getValue(event.getOldBinding()));
		assertNull(event.getNewBinding(), "getNewBinding");

		// Create a subcontext
		Context subctx = ctx.createSubcontext("subctx");
		listener.waitOnEvent();
		assertEquals(4, listener.getEventCount(), "Should be 4 events");
		event = listener.getEvent(3);
		assertEquals(NamingEvent.OBJECT_ADDED, event.getType(), "OBJECT_ADDED");
		assertNull(event.getOldBinding(), "getOldBinding");
		assertEquals(subctx, getValue(event.getNewBinding()), "getNewBinding");

		// Creating a binding under subctx should produce an event
		subctx.bind("subctx.testAddObject", "testAddObject.subctx.bind");
		assertTrue(listener.waitOnEvent(), "Wait on subctx bind");
		event = listener.getEvent(4);
		assertEquals(NamingEvent.OBJECT_ADDED, event.getType(), "OBJECT_ADDED");
	}

	protected Object getValue(Binding binding) throws ClassNotFoundException, IOException {
		Object obj = binding.getObject();
		if (obj instanceof MarshalledValuePair) {
			MarshalledValuePair mvp = (MarshalledValuePair) obj;
			obj = mvp.get();
		}
		return obj;
	}
}
