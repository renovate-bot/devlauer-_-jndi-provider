package de.elnarion.jndi.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.junit.jupiter.api.Test;

import de.elnarion.jndi.server.NamingBeanImpl;
import de.elnarion.jndi.server.Util;

class NonSerializableBindingTest {

	private static final String JNDI_REFERENCE_NAME = "testreference/testref";
	private static final String JNDI_ORIG_NAME = "test/testb/testc";

	@Test
	void bindNonSerializableObject() throws NamingException {
		Testclass testobject = new Testclass();
		testobject.setTestLong(12345);
		testobject.setTestString("asdf");
		NamingBeanImpl namingBean = new NamingBeanImpl();
		namingBean.start();

		InitialContext initialContext = new InitialContext();
		Util.bind(initialContext, JNDI_ORIG_NAME, testobject);
		Util.createLinkRef(initialContext, JNDI_REFERENCE_NAME, JNDI_ORIG_NAME);
		Testclass lookupValue = (Testclass) initialContext.lookup(JNDI_ORIG_NAME);
		Testclass referencedLookupValue = (Testclass) initialContext.lookup(JNDI_REFERENCE_NAME);
		

		assertEquals(testobject, lookupValue);
		assertEquals(testobject, referencedLookupValue);
	}

	public class Testclass {
		private String testString;
		private long testLong;

		public String getTestString() {
			return testString;
		}

		public void setTestString(String testString) {
			this.testString = testString;
		}

		public long getTestLong() {
			return testLong;
		}

		public void setTestLong(long testLong) {
			this.testLong = testLong;
		}
	}
}
