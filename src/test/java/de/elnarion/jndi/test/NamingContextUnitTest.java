/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.naming.NamingException;

import org.junit.jupiter.api.Test;

import de.elnarion.jndi.interfaces.NamingContext;

/**
 * NamingContextUnitTest.
 * 
 * @author Galder Zamarreño
 */
class NamingContextUnitTest {

	@Test
	void testShouldDiscoveryHappen() throws Exception {
		PublicExposeNamingContext ctx = new PublicExposeNamingContext();
		assertTrue(ctx.shouldDiscoveryHappen(false, null));
		assertFalse(ctx.shouldDiscoveryHappen(false, "true"));
		assertTrue(ctx.shouldDiscoveryHappen(false, "false"));
		assertFalse(ctx.shouldDiscoveryHappen(true, null));
		assertFalse(ctx.shouldDiscoveryHappen(true, "true"));
		assertTrue(ctx.shouldDiscoveryHappen(true, "false"));
	}

	@SuppressWarnings("serial")
	public class PublicExposeNamingContext extends NamingContext {
		public PublicExposeNamingContext() throws NamingException {
			super(null, null, null);
		}

		public boolean shouldDiscoveryHappen(boolean globalDisableDiscovery, String perCtxDisableDiscovery) {
			return super.shouldDiscoveryHappen(globalDisableDiscovery, perCtxDisableDiscovery);
		}
	}
}
