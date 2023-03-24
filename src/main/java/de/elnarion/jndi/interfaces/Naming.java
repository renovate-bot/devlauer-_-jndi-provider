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

import javax.naming.*;
import java.rmi.Remote;
import java.util.Collection;

/**
 * The naming server/proxy interface
 *
 * @author Scott.Stark@jboss.org
 * @see NamingContext
 */
public interface Naming extends Remote {

	void bind(Name name, Object obj, String className) throws NamingException;

	void rebind(Name name, Object obj, String className) throws NamingException;

	void unbind(Name name) throws NamingException;

	Object lookup(Name name) throws NamingException;

	Collection<NameClassPair> list(Name name) throws NamingException;

	Collection<Binding> listBindings(Name name) throws NamingException;

	Context createSubcontext(Name name) throws NamingException;
}
