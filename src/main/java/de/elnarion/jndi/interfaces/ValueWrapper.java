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

import java.io.Serializable;

/**
 * An encapsulation of a JNDI binding as the raw object. When accessed in the
 * same VM as the JNP server, the raw object reference is used.
 * 
 * @author Scott.Stark@jboss.org
 */
public class ValueWrapper implements Serializable {
	/** @since 3.2.0 */
	private static final long serialVersionUID = -3403843515711139134L;

	private transient Object value;

	/** Creates a new instance of MashalledValuePair */
	public ValueWrapper(Object value) {
		this.value = value;
	}

	public Object get() {
		return value;
	}
}
