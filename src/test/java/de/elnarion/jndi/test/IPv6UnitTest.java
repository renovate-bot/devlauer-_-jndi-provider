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

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Properties;

import javax.naming.Name;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.elnarion.jndi.interfaces.Naming;
import de.elnarion.jndi.interfaces.NamingContext;
import de.elnarion.jndi.server.Main;
import de.elnarion.jndi.server.NamingBeanImpl;

/**
 * Tests of IPv6 addresses
 * 
 * @author Scott.Stark@jboss.org
 */
class IPv6UnitTest {
	private static Logger LOGGER = LoggerFactory.getLogger(IPv6UnitTest.class);
	/** The actual namingMain service impl bean */
	private static NamingBeanImpl namingBean;
	/** */
	private static Main namingMain = new Main("de.elnarion.jndi.server");

	static int serverPort;

	@BeforeAll
	static void setUp() throws Exception {
		if (namingBean != null)
			return;

		InetAddress localhost = InetAddress.getByName("localhost");
		InetAddress localhostIPv6 = InetAddress.getByName("::1");

		// Set the java.rmi.server.hostname to the bind address if not set
		if (System.getProperty("java.rmi.server.hostname") == null) {
			LOGGER.debug("Set java.rmi.server.hostname to localhost");
			System.setProperty("java.rmi.server.hostname", "localhost");
		}
		namingBean = new NamingBeanImpl();
		namingBean.start();
		namingMain.setPort(0);
		namingMain.setBindAddress("localhost");
		InetAddress[] addresses = { localhost, localhostIPv6, };
		namingMain.setBindAddresses(Arrays.asList(addresses));
		namingMain.setNamingInfo(namingBean);
		namingMain.start();
		serverPort = namingMain.getPort();
	}

	/**
	 * Access the naming instance over the ipv4 localhost address
	 * 
	 * @throws Exception
	 */
	@Test
	void testNamingContextIPv4Localhost() throws Exception {
		Properties env = new Properties();
		env.setProperty("java.naming.factory.initial", "de.elnarion.jndi.interfaces.NamingContextFactory");
		env.setProperty("java.naming.provider.url", "::1:" + serverPort);
		env.setProperty("java.naming.factory.url", "org.jboss.naming:de.elnarion.jndi.interfaces");
		Name baseName = null;
		Naming server = null;
		NamingContext nc = new NamingContext(env, baseName, server);
		nc.list("");
	}

	/**
	 * Access the naming instance over the ipv6 localhost address
	 * 
	 * @throws Exception
	 */
	@Test
	void testNamingContextIPv6Localhost() throws Exception {
		Properties env = new Properties();

		env.setProperty("java.naming.factory.initial", "de.elnarion.jndi.interfaces.NamingContextFactory");
		env.setProperty("java.naming.provider.url", "::1:" + serverPort);
		env.setProperty("java.naming.factory.url", "org.jboss.naming:de.elnarion.jndi.interfaces");
		Name baseName = null;
		Naming server = null;
		NamingContext nc = new NamingContext(env, baseName, server);
		nc.list("");
	}
}
