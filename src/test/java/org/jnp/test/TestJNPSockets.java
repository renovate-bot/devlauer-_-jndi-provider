/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import javax.naming.InitialContext;

import org.jnp.server.Main;
import org.jnp.server.NamingBeanImpl;
import org.jnp.test.support.ClientSocketFactory;
import org.jnp.test.support.ServerSocketFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A test of RMI custom sockets with the jnp JNDI provider.
 * 
 * @author Scott.Stark@jboss.org
 */
class TestJNPSockets {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestJNPSockets.class);

	/** The actual namingMain service impl bean */
	private static NamingBeanImpl namingBean;
	/** */
	private static Main namingMain = new Main("org.jnp.server");

	static int serverPort;

	@BeforeAll
	static void setUp() throws Exception {
		if (namingBean != null)
			return;

		namingBean = new NamingBeanImpl();
		namingBean.start();
		namingMain.setPort(0);
		namingMain.setBindAddress("localhost");
		namingMain.setClientSocketFactory(ClientSocketFactory.class.getName());
		namingMain.setServerSocketFactory(ServerSocketFactory.class.getName());
		namingMain.setNamingInfo(namingBean);
		namingMain.start();
		serverPort = namingMain.getPort();
	}

	@AfterAll
	static void tearDown() {
		namingBean.stop();
	}

	@Test
	void testAccess() throws Exception {
		Properties env = new Properties();
		env.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
		env.setProperty("java.naming.provider.url", "localhost:" + serverPort);
		env.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces");
		InitialContext ctx = new InitialContext(env);
		LOGGER.info("Connected to jnp service");
		ctx.list("");
		ctx.close();
		assertTrue(ClientSocketFactory.created, "No ClientSocketFactory was created");
		assertTrue(ServerSocketFactory.created, "No ServerSocketFactory was created");
	}

}
