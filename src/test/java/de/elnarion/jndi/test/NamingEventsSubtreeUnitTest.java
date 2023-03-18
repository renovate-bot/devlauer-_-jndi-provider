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
package de.elnarion.jndi.test;

import de.elnarion.jndi.interfaces.ValueWrapper;
import de.elnarion.jndi.server.ExecutorEventMgr;
import de.elnarion.jndi.server.NamingBeanImpl;
import de.elnarion.jndi.test.support.QueueEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.event.EventContext;
import javax.naming.event.NamingEvent;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Scott.Stark@jboss.org
 */
class NamingEventsSubtreeUnitTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(NamingEventsSubtreeUnitTest.class);
	private final QueueEventListener listener = new QueueEventListener();
	/** The actual namingMain service impl bean */
	private NamingBeanImpl namingBean;

	@BeforeEach
	void setUp() throws Exception {
		namingBean = new NamingBeanImpl();
		namingBean.setInstallGlobalService(true);
		namingBean.setEventMgr(new ExecutorEventMgr());
		namingBean.start();
	}

	@AfterEach
	void tearDown() {
		namingBean.stop();
	}


	@Test
	void testAddRemoveSubtree() throws Exception {
		LOGGER.info("Entering AddRemoveSubtree");
		Properties env = new Properties();
		env.setProperty("java.naming.factory.initial", "de.elnarion.jndi.interfaces.LocalOnlyContextFactory");
		env.setProperty("java.naming.factory.url.pkgs", "de.elnarion.jndi.interfaces");
		InitialContext ic = new InitialContext(env);
		LOGGER.info("Created InitialContext");
		Context ctx = (Context) ic.lookup("");
		assertTrue(ctx instanceof EventContext, "Context is an EventContext");
		EventContext ectx = (EventContext) ctx;
		ectx.addNamingListener("", EventContext.SUBTREE_SCOPE, listener);
		LOGGER.info("Added NamingListener");
		ctx.bind("testAddObject", "testAddObject.bind");
		LOGGER.info("Object bound");
		assertTrue(listener.waitOnEvent(2, TimeUnit.SECONDS), "Saw bind event");
		NamingEvent event = listener.getEvent(0);
		assertEquals(NamingEvent.OBJECT_ADDED, event.getType(), "OBJECT_ADDED");
		assertNull(event.getOldBinding(), "getOldBinding");
		assertEquals("testAddObject.bind", getValue(event.getNewBinding()));

		ctx.rebind("testAddObject", "testAddObject.rebind");
		assertTrue(listener.waitOnEvent(2, TimeUnit.SECONDS), "Saw rebind event");
		event = listener.getEvent(1);
		assertEquals(NamingEvent.OBJECT_CHANGED, event.getType(), "OBJECT_CHANGED");
		assertEquals("testAddObject.bind", getValue(event.getOldBinding()));
		assertEquals("testAddObject.rebind", getValue(event.getNewBinding()));

		ctx.unbind("testAddObject");
		assertTrue(listener.waitOnEvent(2, TimeUnit.SECONDS), "Saw unbind event");
		event = listener.getEvent(2);
		assertEquals(NamingEvent.OBJECT_REMOVED, event.getType(), "OBJECT_REMOVED");
		assertEquals("testAddObject.rebind", getValue(event.getOldBinding()));
		assertNull(event.getNewBinding(), "getNewBinding");

		// Create a subcontext
		Context subctx = ctx.createSubcontext("subctx");
		listener.waitOnEvent(2, TimeUnit.SECONDS);
		assertEquals(4, listener.getEventCount(), "Should be 4 events");
		event = listener.getEvent(3);
		assertEquals(NamingEvent.OBJECT_ADDED, event.getType(), "OBJECT_ADDED");
		assertNull(event.getOldBinding(), "getOldBinding");
		assertEquals(subctx, getValue(event.getNewBinding()), "getNewBinding");

		// Creating a binding under subctx should produce an event
		subctx.bind("subctx.testAddObject", "testAddObject.subctx.bind");
		assertTrue(listener.waitOnEvent(2, TimeUnit.SECONDS), "Wait on subctx bind");
		event = listener.getEvent(4);
		assertEquals(NamingEvent.OBJECT_ADDED, event.getType(), "OBJECT_ADDED");
		LOGGER.info("Leaving AddRemoveSubtree");
	}

	protected Object getValue(Binding binding) {
		Object obj = binding.getObject();
		if (obj instanceof ValueWrapper) {
			ValueWrapper mvp = (ValueWrapper) obj;
			obj = mvp.get();
		}
		return obj;
	}
}
