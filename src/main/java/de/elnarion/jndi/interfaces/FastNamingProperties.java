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
package de.elnarion.jndi.interfaces;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.*;

/**
 * This class exists because the JNDI API set wisely uses java.util.Properties
 * which extends Hashtable, a threadsafe class. The NamingParser uses a static
 * instance, making it a global source of contention. This results in a huge
 * scalability problem, which can be seen in ECPerf, as sometimes half of the
 * worker threads are stuck waiting for this stupid lock, sometimes themselves
 * holdings global locks, e.g. to the AbstractInstanceCache.
 *
 * @author <a href="mailto:sreich@apple.com">Stefan Reich</a>
 */
class FastNamingProperties extends Properties {
	/** serialVersionUID */
	private static final long serialVersionUID = 190486940953472275L;

	FastNamingProperties() {
	}

	@Override
	public synchronized Object setProperty(String s1, String s2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized void load(InputStream is) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getProperty(String s) {
		switch (s) {
			case "jndi.syntax.direction":
				return "left_to_right";
			case "jndi.syntax.ignorecase":
				return "false";
			case "jndi.syntax.separator":
				return "/";
			default:
				return null;
		}
	}

	@Override
	public String getProperty(String name, String defaultValue) {
		String ret = getProperty(name);
		if (ret == null) {
			ret = defaultValue;
		}
		return ret;
	}

	@Override
	public Enumeration<Object> propertyNames() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void list(PrintStream ps) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void list(PrintWriter ps) {
		throw new UnsupportedOperationException();
	}

	// methods from Hashtable

	@Override
	public synchronized int size() {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized boolean isEmpty() {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized Enumeration<Object> keys() {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized Enumeration<Object> elements() {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized boolean contains(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized boolean containsValue(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized boolean containsKey(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized Object get(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized Object put(Object o1, Object o2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized Object remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized void putAll(Map<?, ?> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized Object clone() { // NOSONAR - not implemented
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized String toString() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Object> keySet() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Map.Entry<Object, Object>> entrySet() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Collection<Object> values() {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized boolean equals(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized int hashCode() {
		throw new UnsupportedOperationException();
	}
}
