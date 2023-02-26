/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jnp.server;

import java.io.IOException;
import java.net.UnknownHostException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.List;

import javax.net.ServerSocketFactory;

/**
 * The Mbean interface for the jnp provider server.
 * 
 * @author Rickard Oberg
 * @author Scott.Stark@jboss.org
 */
public interface MainMBean extends NamingBean {
	// Attributes ---------------------------------------------------

	/**
	 * Sets the rmi port.
	 *
	 * @param port the new rmi port
	 */
	void setRmiPort(int port);

	/**
	 * Gets the rmi port.
	 *
	 * @return the rmi port
	 */
	int getRmiPort();

	/**
	 * Sets the port.
	 *
	 * @param port the new port
	 */
	void setPort(int port);

	/**
	 * Gets the port.
	 *
	 * @return the port
	 */
	int getPort();

	/**
	 * Sets the bind address.
	 *
	 * @param host the new bind address
	 * @throws UnknownHostException the unknown host exception
	 */
	void setBindAddress(String host) throws UnknownHostException;

	/**
	 * Gets the bind address.
	 *
	 * @return the bind address
	 */
	String getBindAddress();

	/**
	 * Sets the rmi bind address.
	 *
	 * @param host the new rmi bind address
	 * @throws UnknownHostException the unknown host exception
	 */
	void setRmiBindAddress(String host) throws UnknownHostException;

	/**
	 * Gets the rmi bind address.
	 *
	 * @return the rmi bind address
	 */
	String getRmiBindAddress();

	/**
	 * Sets the backlog.
	 *
	 * @param backlog the new backlog
	 */
	void setBacklog(int backlog);

	/**
	 * Gets the backlog.
	 *
	 * @return the backlog
	 */
	int getBacklog();

	/**
	 * Returns a URL suitable for use as a java.naming.provider.url value in a set
	 * of naming environment properties; i.e. one that can be used to connect to the
	 * lookup socket.
	 * <p>
	 * If there are {@link #getBootstrapURLs() multiple bootstrap URLs}, returns the
	 * first one in the list. TODO: that is is pretty arbitrary
	 * </p>
	 * 
	 * @return the URL, or <code>null</code> if no bound lookup socket exists
	 */
	String getBootstrapURL();

	/**
	 * Returns a list of URLs suitable for use as a java.naming.provider.url value
	 * in a set of naming environment properties; i.e. ones that can be used to
	 * connect to the lookup socket. There will be one URL per configured
	 * {@link #getBindAddress() bind address}.
	 * 
	 * @return the URLs, or <code>null</code> if no bound lookup socket exists
	 */
	List<String> getBootstrapURLs();

	/**
	 * Gets the naming info.
	 *
	 * @return the naming info
	 */
	public NamingBean getNamingInfo();

	/**
	 * Set the NamingBean/Naming implementation.
	 *
	 * @param info the new naming info
	 */
	public void setNamingInfo(NamingBean info);

	/**
	 * Get the call by value flag for jndi lookups.
	 * 
	 * @return true if all lookups are unmarshalled using the caller's TCL, false if
	 *         in VM lookups return the value by reference.
	 */
	boolean getCallByValue();

	/**
	 * Set the call by value flag for jndi lookups.
	 * 
	 * @param flag - true if all lookups are unmarshalled using the caller's TCL,
	 *             false if in VM lookups return the value by reference.
	 */
	void setCallByValue(boolean flag);

	/**
	 * Whether the MainMBean's Naming server will be installed as the
	 * NamingContext.setLocal global value
	 *
	 * @param flag the new install global service
	 */
	void setInstallGlobalService(boolean flag);

	/**
	 * Gets the install global service.
	 *
	 * @return the install global service
	 */
	boolean getInstallGlobalService();

	/**
	 * Get the UseGlobalService which defines whether the MainMBean's Naming server
	 * will initialized from the existing NamingContext.setLocal global value.
	 * 
	 * @return true if this should try to use VM global naming service, false
	 *         otherwise
	 */
	public boolean getUseGlobalService();

	/**
	 * Set the UseGlobalService which defines whether the MainMBean's Naming server
	 * will initialized from the existing NamingContext.setLocal global value. This
	 * allows one to export multiple servers via different transports and still
	 * share the same underlying naming service.
	 *
	 * @param flag the new use global service
	 */
	public void setUseGlobalService(boolean flag);

	/**
	 * The RMIClientSocketFactory implementation class.
	 *
	 * @param factoryClassName the new client socket factory
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 */
	void setClientSocketFactory(String factoryClassName)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException;

	/**
	 * Gets the client socket factory.
	 *
	 * @return the client socket factory
	 */
	String getClientSocketFactory();

	/**
	 * The RMIClientSocketFactory bean.
	 *
	 * @return the client socket factory bean
	 */
	public RMIClientSocketFactory getClientSocketFactoryBean();

	/**
	 * Sets the client socket factory bean.
	 *
	 * @param factory the new client socket factory bean
	 */
	public void setClientSocketFactoryBean(RMIClientSocketFactory factory);

	/**
	 * The RMIServerSocketFactory implementation class.
	 *
	 * @param factoryClassName the new server socket factory
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 */
	void setServerSocketFactory(String factoryClassName)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException;

	/**
	 * Gets the server socket factory.
	 *
	 * @return the server socket factory
	 */
	String getServerSocketFactory();

	/**
	 * The RMIServerSocketFactory bean.
	 *
	 * @return the server socket factory bean
	 */
	public RMIServerSocketFactory getServerSocketFactoryBean();

	/**
	 * Sets the server socket factory bean.
	 *
	 * @param factory the new server socket factory bean
	 */
	public void setServerSocketFactoryBean(RMIServerSocketFactory factory);

	/**
	 * The JNPServerSocketFactory implementation class.
	 *
	 * @return the JNP server socket factory bean
	 */
	ServerSocketFactory getJNPServerSocketFactoryBean();

	/**
	 * Sets the JNP server socket factory bean.
	 *
	 * @param factory the new JNP server socket factory bean
	 */
	void setJNPServerSocketFactoryBean(ServerSocketFactory factory);

	/**
	 * Gets the JNP server socket factory.
	 *
	 * @return the JNP server socket factory
	 */
	public String getJNPServerSocketFactory();

	/**
	 * Sets the JNP server socket factory.
	 *
	 * @param factoryClassName the new JNP server socket factory
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 */
	void setJNPServerSocketFactory(String factoryClassName)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException;

	/**
	 * Get the externally define Naming proxy instance.
	 *
	 * @return the naming proxy
	 * @throws Exception the exception
	 */
	public Object getNamingProxy() throws Exception;

	/**
	 * Sets the naming proxy.
	 *
	 * @param proxy the new naming proxy
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void setNamingProxy(Object proxy) throws IOException;

	/**
	 * Get any exception seen during the lookup listening port creation.
	 *
	 * @return the lookup listener exception
	 */
	public Exception getLookupListenerException();

	// Operations ----------------------------------------------------

	/**
	 * Start.
	 *
	 * @throws Exception the exception
	 */
	public void start() throws Exception;

	/**
	 * Stop.
	 *
	 * @throws Exception the exception
	 */
	public void stop() throws Exception;

}
