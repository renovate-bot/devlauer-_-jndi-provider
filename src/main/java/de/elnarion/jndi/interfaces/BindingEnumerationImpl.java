package de.elnarion.jndi.interfaces;

import java.util.Collection;
import java.util.Iterator;

import javax.naming.Binding;
import javax.naming.NamingEnumeration;

public class BindingEnumerationImpl
		implements NamingEnumeration<Binding> {
	// Constants -----------------------------------------------------

	// Attributes ----------------------------------------------------
	Iterator<Binding> iter;

	// Static --------------------------------------------------------

	// Constructors --------------------------------------------------
	BindingEnumerationImpl(Collection<Binding> list) {
			iter = list.iterator();
		}

	// Public --------------------------------------------------------

	// Enumeration implementation ------------------------------------
	public boolean hasMoreElements() {
		return iter.hasNext();
	}

	public Binding nextElement() {
		return iter.next();
	}

	// NamingEnumeration implementation ------------------------------
	public boolean hasMore() {
		return iter.hasNext();
	}

	public Binding next() {
		return iter.next();
	}

	public void close() {
		iter = null;
	}

	// Y overrides ---------------------------------------------------

	// Package protected ---------------------------------------------

	// Protected -----------------------------------------------------

	// Private -------------------------------------------------------

	// Inner classes -------------------------------------------------
}