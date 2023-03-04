package de.elnarion.jndi.test;

import org.junit.jupiter.api.Test;

import de.elnarion.jndi.server.NamingBeanImpl;

class StartupTest {

	@Test
	void testStartupShutdown() throws Exception {
		NamingBeanImpl namingBean = new NamingBeanImpl();
		namingBean.start();
		System.out.println("Test");
		namingBean.stop();
		
	}

}
