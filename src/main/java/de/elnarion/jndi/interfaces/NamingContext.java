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

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.ConnectException;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Binding;
import javax.naming.CannotProceedException;
import javax.naming.CommunicationException;
import javax.naming.ConfigurationException;
import javax.naming.Context;
import javax.naming.ContextNotEmptyException;
import javax.naming.InitialContext;
import javax.naming.InvalidNameException;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.ServiceUnavailableException;
import javax.naming.event.EventContext;
import javax.naming.event.NamingListener;
import javax.naming.spi.NamingManager;
import javax.naming.spi.ResolveResult;
import javax.net.SocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides the jnp provider Context implementation. It is a Context
 * interface wrapper for a RMI Naming instance that is obtained from either the
 * local server instance or by locating the server given by the
 * Context.PROVIDER_URL value.
 *
 * This class also serves as the jnp url resolution context. jnp style urls
 * passed to the
 * 
 * @author oberg
 * @author scott.stark@jboss.org
 * @author Galder Zamarreño
 */
public class NamingContext implements EventContext, java.io.Serializable {
	// Constants -----------------------------------------------------
	/**
	 * @since 1.7
	 */
	static final long serialVersionUID = 8906455608484282128L;
	/**
	 * The javax.net.SocketFactory impl to use for the bootstrap socket
	 */
	public static final String JNP_SOCKET_FACTORY = "jnp.socketFactory";
	/**
	 * The local address to bind the connected bootstrap socket to
	 */
	public static final String JNP_LOCAL_ADDRESS = "jnp.localAddress";
	/**
	 * The local port to bind the connected bootstrap socket to
	 */
	public static final String JNP_LOCAL_PORT = "jnp.localPort";
	/**
	 * A flag to disable the broadcast discovery queries
	 */
	public static final String JNP_DISABLE_DISCOVERY = "jnp.disableDiscovery";
	/**
	 * The cluster partition discovery should be restricted to
	 */
	public static final String JNP_PARTITION_NAME = "jnp.partitionName";
	/**
	 * The multicast IP/address to which the discovery query is sent
	 */
	public static final String JNP_DISCOVERY_GROUP = "jnp.discoveryGroup";
	/**
	 * The port to which the discovery query is sent
	 */
	public static final String JNP_DISCOVERY_PORT = "jnp.discoveryPort";

	/** The time-to-live for the multicast discovery packets */
	public static final String JNP_DISCOVERY_TTL = "jnp.discoveryTTL";

	/**
	 * The time in MS to wait for a discovery query response
	 */
	public static final String JNP_DISCOVERY_TIMEOUT = "jnp.discoveryTimeout";
	/**
	 * An internal property added by parseNameForScheme if the input name uses a url
	 * prefix that was removed during cannonicalization. This is needed to avoid
	 * modification of the incoming Name.
	 */
	public static final String JNP_PARSED_NAME = "jnp.parsedName";
	/**
	 * A flag indicating the style of names passed to NamingManager method. True for
	 * api expected relative names, false for absolute names as used historically by
	 * the jboss naming implementation.
	 */
	public static final String JNP_USE_RELATIVE_NAME = "jnp.useRelativeName";
	/**
	 * An integer that controls the number of connection retry attempts will be made
	 * on the initial connection to the naming server. This only applies to
	 * ConnectException failures. A value <= 1 means that only one attempt will be
	 * made.
	 */
	public static final String JNP_MAX_RETRIES = "jnp.maxRetries";
	/**
	 * The Naming instance to use for the root Context creation
	 */
	public static final String JNP_NAMING_INSTANCE = "jnp.namingInstance";
	/**
	 * The name to associate with Naming instance to use for the root Context
	 */
	public static final String JNP_NAMING_INSTANCE_NAME = "jnp.namingInstanceName";

	/**
	 * Whether or not the order of the provider list is significant. Default
	 * behavior assumes that it is which can lead to bad behavior if one of the
	 * initial URLs is not contactable.
	 */
	public static final String JNP_UNORDERED_PROVIDER_LIST = "jnp.unorderedProviderList";

	/**
	 * Global JNP disable discovery system property:
	 * -Djboss.global.jnp.disableDiscovery=[true|false] At the VM level, this
	 * property controls how disable discovery behaves in absence of per context
	 * jnp.disableDiscovery property.
	 */
	private static final boolean GLOBAL_JNP_DISABLE_DISCOVERY = Boolean
			.valueOf(getSystemProperty("jboss.global.jnp.disableDiscovery", "false"));

	/**
	 * Global JNP unordered provider list system property:
	 * -Djboss.global.jnp.unorderedProviderList=[true|false] At the VM level, this
	 * property controls how unordered provider list behaves in absence of per
	 * context jnp.unorderedProviderList. Default is false.
	 */
	private static final String GLOBAL_UNORDERED_PROVIDER_LIST = System
			.getProperty("jboss.global.jnp.unorderedProviderList", "false");

	public static String getSystemProperty(final String name, final String defaultValue) {
		String prop;
		prop = System.getProperty(name, defaultValue);
		return prop;
	}

	/**
	 * The default discovery multicast information
	 */
	public final static String DEFAULT_DISCOVERY_GROUP_ADDRESS = "230.0.0.4";
	public final static int DEFAULT_DISCOVERY_GROUP_PORT = 1102;
	public final static int DEFAULT_DISCOVERY_TIMEOUT = 5000;

	/**
	 * An obsolete constant replaced by the JNP_MAX_RETRIES value
	 */
	public static int MAX_RETRIES = 1;
	/**
	 * The JBoss logging interface
	 */
	private static Logger log = LoggerFactory.getLogger(NamingContext.class);

	// Static --------------------------------------------------------

	/**
	 * The jvm local server used for non-transport access to the naming server
	 * 
	 * @see #checkRef(Hashtable)
	 * @see {@linkplain LocalOnlyContextFactory}
	 */
	private static Naming localServer;
	private static RuntimePermission GET_LOCAL_SERVER = new RuntimePermission(
			"org.jboss.naming.NamingContext.getLocal");
	private static RuntimePermission SET_LOCAL_SERVER = new RuntimePermission(
			"org.jboss.naming.NamingContext.setLocal");
	private static int HOST_INDEX = 0;
	private static int PORT_INDEX = 1;

	// Attributes ----------------------------------------------------
	Naming naming;
	Hashtable env;
	Name prefix;

	NameParser parser = new NamingParser();

	// Static --------------------------------------------------------

	// Cache of naming server stubs
	// This is a critical optimization in the case where new InitialContext
	// is performed often. The server stub will be shared between all those
	// calls, which will improve performance.
	// Weak references are used so if no contexts use a particular server
	// it will be removed from the cache.
	static ConcurrentHashMap<InetSocketAddress, WeakReference<Naming>> cachedServers = new ConcurrentHashMap<InetSocketAddress, WeakReference<Naming>>();

	static void addServer(InetSocketAddress addr, Naming server) {
		// Add server to map
		synchronized (NamingContext.class) {
			WeakReference<Naming> ref = new WeakReference<Naming>(server);
			cachedServers.put(addr, ref);
		}
	}

	static void removeServer(Hashtable serverEnv) {
		// JBAS-4622. Always do this.
		Object hostKey = serverEnv.remove("hostKey");
		if (hostKey != null) {
			synchronized (NamingContext.class) {
				cachedServers.remove(hostKey);
			}
		}
	}

	/**
	 * Called to remove any url scheme atoms and extract the naming service
	 * hostname:port information.
	 * 
	 * @param n the name component to the parsed. After returning n will have all
	 *          scheme related atoms removed.
	 * @return the naming service hostname:port information string if name contained
	 *         the host information.
	 */
	static String parseNameForScheme(Name n, Hashtable nameEnv) throws InvalidNameException {
		String serverInfo = null;
		if (n.size() > 0) {
			String scheme = n.get(0);
			int schemeLength = 0;
			if (scheme.startsWith("java:"))
				schemeLength = 5;
			else if (scheme.startsWith("jnp:"))
				schemeLength = 4;
			else if (scheme.startsWith("jnps:"))
				schemeLength = 5;
			else if (scheme.startsWith("jnp-http:"))
				schemeLength = 9;
			else if (scheme.startsWith("jnp-https:"))
				schemeLength = 10;
			if (schemeLength > 0) {
				// Make a copy of the name to avoid
				n = (Name) n.clone();
				String suffix = scheme.substring(schemeLength);
				if (suffix.length() == 0) {
					// Scheme was "url:/..."
					n.remove(0);
					if (n.size() > 1 && n.get(0).equals("")) {
						// Scheme was "url://hostname:port/..."
						// Get hostname:port value for the naming server
						serverInfo = n.get(1);
						n.remove(0);
						n.remove(0);
						// If n is a empty atom remove it or else a '/' will result
						if (n.size() == 1 && n.get(0).length() == 0)
							n.remove(0);
					}
				} else {
					// Scheme was "url:foo" -> reinsert "foo"
					n.remove(0);
					n.add(0, suffix);
				}
				if (nameEnv != null)
					nameEnv.put(JNP_PARSED_NAME, n);
			}
		}
		return serverInfo;
	}

	public static Naming getLocal() {
		return localServer;
	}

	public static void setLocal(Naming server) {
		localServer = server;
	}

	// Constructors --------------------------------------------------
	public NamingContext(Hashtable e, Name baseName, Naming server) throws NamingException {
		if (baseName == null)
			this.prefix = parser.parse("");
		else
			this.prefix = baseName;

		if (e != null)
			this.env = (Hashtable) e.clone();
		else
			this.env = new Hashtable();

		this.naming = server;
	}

	// Public --------------------------------------------------------
	public Naming getNaming() {
		return this.naming;
	}

	public void setNaming(Naming server) {
		this.naming = server;
	}

	// Context implementation ----------------------------------------
	public void rebind(String name, Object obj) throws NamingException {
		rebind(getNameParser(name).parse(name), obj);
	}

	public void rebind(Name name, Object obj) throws NamingException {
		Hashtable refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		// Allow state factories to change the stored object
		obj = getStateToBind(obj, name, refEnv);

		try {
			String className = null;

			// Referenceable
			if (obj instanceof Referenceable)
				obj = ((Referenceable) obj).getReference();

			if (!(obj instanceof Reference)) {
				if (obj != null)
					className = obj.getClass().getName();
				obj = createMarshalledValuePair(obj);
			} else {
				className = ((Reference) obj).getClassName();
			}
			try {
				naming.rebind(getAbsoluteName(name), obj, className);
			} catch (RemoteException re) {
				// Check for JBAS-4574.
				if (handleStaleNamingStub(re, refEnv)) {
					// try again with new naming stub
					naming.rebind(getAbsoluteName(name), obj, className);
				} else {
					// Not JBAS-4574. Throw exception and let outer logic handle it.
					throw re;
				}
			}
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			cctx.rebind(cpe.getRemainingName(), obj);
		} catch (IOException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		}
	}

	public void bind(String name, Object obj) throws NamingException {
		bind(getNameParser(name).parse(name), obj);
	}

	public void bind(Name name, Object obj) throws NamingException {
		Hashtable refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		// Allow state factories to change the stored object
		obj = getStateToBind(obj, name, refEnv);

		try {
			String className = null;

			// Referenceable
			if (obj instanceof Referenceable)
				obj = ((Referenceable) obj).getReference();

			if (!(obj instanceof Reference)) {
				if (obj != null)
					className = obj.getClass().getName();

				// Normal object - serialize using a MarshalledValuePair
				obj = createMarshalledValuePair(obj);
			} else {
				className = ((Reference) obj).getClassName();
			}
			name = getAbsoluteName(name);

			try {
				naming.bind(name, obj, className);
			} catch (RemoteException re) {
				// Check for JBAS-4574.
				if (handleStaleNamingStub(re, refEnv)) {
					// try again with new naming stub
					naming.bind(name, obj, className);
				} else {
					// Not JBAS-4574. Throw exception and let outer logic handle it.
					throw re;
				}
			}
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			cctx.bind(cpe.getRemainingName(), obj);
		} catch (IOException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		}
	}

	public Object lookup(String name) throws NamingException {
		return lookup(getNameParser(name).parse(name));
	}

	public Object lookup(Name name) throws NamingException {
		Hashtable refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		// Empty?
		if (name.isEmpty())
			return new NamingContext(refEnv, prefix, naming);

		try {
			int maxTries = 1;
			try {
				String n = (String) refEnv.get(JNP_MAX_RETRIES);
				if (n != null)
					maxTries = Integer.parseInt(n);
				if (maxTries <= 0)
					maxTries = 1;
			} catch (Exception e) {
				log.debug("Failed to get JNP_MAX_RETRIES, using 1", e);
			}
			Name n = getAbsoluteName(name);
			Object res = null;
			boolean debug = log.isDebugEnabled();
			for (int i = 0; i < maxTries; i++) {
				try {
					try {
						res = naming.lookup(n);
					} catch (RemoteException re) {
						// Check for JBAS-4574.
						if (handleStaleNamingStub(re, refEnv)) {
							// try again with new naming stub
							res = naming.lookup(n);
						}
						// Check for JBPAPP-6447.
						else if (handleDyingServer(re, refEnv)) {
							// try again with new naming stub
							res = naming.lookup(n);
						} else {
							// Throw exception and let outer logic handle it.
							throw re;
						}
					} catch (ServiceUnavailableException re) {
						// Check for JBPAPP-8152/JBPAPP-8305
						if (handleServerStartupShutdown(re, refEnv)) {
							// try again with new naming stub
							res = naming.lookup(n);
						} else {
							// Throw exception and let outer logic handle it.
							throw re;
						}
					}
					// If we got here, we succeeded, so break the loop
					break;
				} catch (ConnectException ce) {
					int retries = maxTries - i - 1;
					if (debug)
						log.debug("Connect failed, retry count: " + retries, ce);
					// We may overload server so sleep and retry
					if (retries > 0) {
						try {
							Thread.sleep(1);
						} catch (InterruptedException ignored) {
						}
						continue;
					}
					// Throw the exception to flush the bad server
					throw ce;
				}
			}
			if (res instanceof MarshalledValuePair) {
				MarshalledValuePair mvp = (MarshalledValuePair) res;
				Object storedObj = mvp.get();
				return getObjectInstanceWrapFailure(storedObj, name, refEnv);
			} else if (res instanceof MarshalledObject) {
				MarshalledObject mo = (MarshalledObject) res;
				return mo.get();
			} else if (res instanceof Context) {
				// Add env
				Enumeration keys = refEnv.keys();
				while (keys.hasMoreElements()) {
					String key = (String) keys.nextElement();
					((Context) res).addToEnvironment(key, refEnv.get(key));
				}
				return res;
			} else if (res instanceof ResolveResult) {
				// Dereference partial result
				ResolveResult rr = (ResolveResult) res;
				Object resolveRes = rr.getResolvedObj();
				Object context;
				Object instanceID;

				if (resolveRes instanceof LinkRef) {
					context = resolveLink(resolveRes, null);
					instanceID = ((LinkRef) resolveRes).getLinkName();
				} else {
					context = getObjectInstanceWrapFailure(resolveRes, name, refEnv);
					instanceID = context;
				}

				if ((context instanceof Context) == false) {
					throw new NotContextException(instanceID + " is not a Context");
				}
				Context ncontext = (Context) context;
				return ncontext.lookup(rr.getRemainingName());
			} else if (res instanceof LinkRef) {
				// Dereference link
				res = resolveLink(res, refEnv);
			} else if (res instanceof Reference) {
				// Dereference object
				res = getObjectInstanceWrapFailure(res, name, refEnv);
				if (res instanceof LinkRef)
					res = resolveLink(res, refEnv);
			}

			return res;
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			return cctx.lookup(cpe.getRemainingName());
		} catch (IOException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		} catch (ClassNotFoundException e) {
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		}
	}

	public void unbind(String name) throws NamingException {
		unbind(getNameParser(name).parse(name));
	}

	public void unbind(Name name) throws NamingException {
		Hashtable refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		try {
			try {
				naming.unbind(getAbsoluteName(name));
			} catch (RemoteException re) {
				// Check for JBAS-4574.
				if (handleStaleNamingStub(re, refEnv)) {
					// try again with new naming stub
					naming.unbind(getAbsoluteName(name));
				} else {
					// Not JBAS-4574. Throw exception and let outer logic handle it.
					throw re;
				}
			}
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			cctx.unbind(cpe.getRemainingName());
		} catch (IOException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		}
	}

	public void rename(String oldname, String newname) throws NamingException {
		rename(getNameParser(oldname).parse(oldname), getNameParser(newname).parse(newname));
	}

	public void rename(Name oldName, Name newName) throws NamingException {
		bind(newName, lookup(oldName));
		unbind(oldName);
	}

	public NamingEnumeration list(String name) throws NamingException {
		return list(getNameParser(name).parse(name));
	}

	public NamingEnumeration list(Name name) throws NamingException {
		Hashtable refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		try {
			Collection c = null;
			try {
				c = naming.list(getAbsoluteName(name));
			} catch (RemoteException re) {
				// Check for JBAS-4574.
				if (handleStaleNamingStub(re, refEnv)) {
					// try again with new naming stub
					c = naming.list(getAbsoluteName(name));
				} else {
					// Not JBAS-4574. Throw exception and let outer logic handle it.
					throw re;
				}
			}
			return new NamingEnumerationImpl(c);
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			return cctx.list(cpe.getRemainingName());
		} catch (IOException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		}
	}

	public NamingEnumeration listBindings(String name) throws NamingException {
		return listBindings(getNameParser(name).parse(name));
	}

	public NamingEnumeration listBindings(Name name) throws NamingException {
		Hashtable refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		try {
			// Get list
			Collection bindings = null;
			try {
				// Get list
				bindings = naming.listBindings(getAbsoluteName(name));
			} catch (RemoteException re) {
				// Check for JBAS-4574.
				if (handleStaleNamingStub(re, refEnv)) {
					// try again with new naming stub
					bindings = naming.listBindings(getAbsoluteName(name));
				} else {
					// Not JBAS-4574. Throw exception and let outer logic handle it.
					throw re;
				}
			}
			Collection realBindings = new ArrayList(bindings.size());

			// Convert marshalled objects
			Iterator i = bindings.iterator();
			while (i.hasNext()) {
				Binding binding = (Binding) i.next();
				Object obj = binding.getObject();
				if (obj instanceof MarshalledValuePair) {
					try {
						obj = ((MarshalledValuePair) obj).get();
					} catch (ClassNotFoundException e) {
						NamingException ex = new CommunicationException();
						ex.setRootCause(e);
						throw ex;
					}
				} else if (obj instanceof MarshalledObject) {
					try {
						obj = ((MarshalledObject) obj).get();
					} catch (ClassNotFoundException e) {
						NamingException ex = new CommunicationException();
						ex.setRootCause(e);
						throw ex;
					}
				}
				realBindings.add(new Binding(binding.getName(), binding.getClassName(), obj));
			}

			// Return transformed list of bindings
			return new NamingEnumerationImpl(realBindings);
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			return cctx.listBindings(cpe.getRemainingName());
		} catch (IOException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		}
	}

	public String composeName(String name, String prefix) throws NamingException {
		Name result = composeName(parser.parse(name), parser.parse(prefix));
		return result.toString();
	}

	public Name composeName(Name name, Name prefix) throws NamingException {
		Name result = (Name) (prefix.clone());
		result.addAll(name);
		return result;
	}

	public NameParser getNameParser(String name) throws NamingException {
		return parser;
	}

	public NameParser getNameParser(Name name) throws NamingException {
		return getNameParser(name.toString());
	}

	public Context createSubcontext(String name) throws NamingException {
		return createSubcontext(getNameParser(name).parse(name));
	}

	public Context createSubcontext(Name name) throws NamingException {
		if (name.size() == 0)
			throw new InvalidNameException("Cannot pass an empty name to createSubcontext");

		Hashtable refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		try {
			name = getAbsoluteName(name);
			try {
				return naming.createSubcontext(name);
			} catch (RemoteException re) {
				// Check for JBAS-4574.
				if (handleStaleNamingStub(re, refEnv)) {
					// try again with new naming stub
					return naming.createSubcontext(name);
				} else {
					// Not JBAS-4574. Throw exception and let outer logic handle it.
					throw re;
				}
			}
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			return cctx.createSubcontext(cpe.getRemainingName());
		} catch (IOException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		}
	}

	public Object addToEnvironment(String propName, Object propVal) throws NamingException {
		Object old = env.get(propName);
		env.put(propName, propVal);
		return old;
	}

	public Object removeFromEnvironment(String propName) throws NamingException {
		return env.remove(propName);
	}

	public Hashtable getEnvironment() throws NamingException {
		return env;
	}

	public void close() throws NamingException {
		env = null;
		naming = null;
	}

	public String getNameInNamespace() throws NamingException {
		return prefix.toString();
	}

	public void destroySubcontext(String name) throws NamingException {
		destroySubcontext(getNameParser(name).parse(name));
	}

	public void destroySubcontext(Name name) throws NamingException {
		if (!list(name).hasMore()) {
			unbind(name);
		} else
			throw new ContextNotEmptyException();
	}

	public Object lookupLink(String name) throws NamingException {
		return lookupLink(getNameParser(name).parse(name));
	}

	/**
	 * Lookup the object referred to by name but don't dereferrence the final
	 * component. This really just involves returning the raw value returned by the
	 * Naming.lookup() method.
	 * 
	 * @return the raw object bound under name.
	 */
	public Object lookupLink(Name name) throws NamingException {
		Hashtable refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		if (name.isEmpty())
			return lookup(name);

		Object link = null;
		try {
			Name n = getAbsoluteName(name);
			try {
				link = naming.lookup(n);
			} catch (RemoteException re) {
				// Check for JBAS-4574.
				if (handleStaleNamingStub(re, refEnv)) {
					// try again with new naming stub
					link = naming.lookup(n);
				} else {
					// Not JBAS-4574. Throw exception and let outer logic handle it.
					throw re;
				}
			}
			if (!(link instanceof LinkRef) && link instanceof Reference)
				link = getObjectInstance(link, name, null);
			;
		} catch (IOException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		} catch (Exception e) {
			NamingException ex = new NamingException("Could not lookup link");
			ex.setRemainingName(name);
			ex.setRootCause(e);
			throw ex;
		}
		return link;
	}

	// Begin EventContext methods
	public void addNamingListener(Name target, int scope, NamingListener l) throws NamingException {
		if ((naming instanceof NamingEvents) == false) {
			Class<?> cls = naming.getClass();
			String cs = cls.getName() + ", CS:" + cls.getProtectionDomain().getCodeSource().toString();
			throw new UnsupportedOperationException("Naming implementation does not support NamingExt, : " + cs);
		}
		NamingEvents next = (NamingEvents) naming;
		try {
			next.addNamingListener(this, target, scope, l);
		} catch (RemoteException e) {
			CommunicationException ce = new CommunicationException("addNamingListener failed");
			ce.initCause(e);
		}
	}

	public void addNamingListener(String target, int scope, NamingListener l) throws NamingException {
		Name targetName = parser.parse(target);
		addNamingListener(targetName, scope, l);
	}

	public void removeNamingListener(NamingListener l) throws NamingException {
		if ((naming instanceof NamingEvents) == false) {
			throw new UnsupportedOperationException("Naming implementation does not support NamingExt");
		}
		NamingEvents next = (NamingEvents) naming;
		try {
			next.removeNamingListener(l);
		} catch (RemoteException e) {
			CommunicationException ce = new CommunicationException("removeNamingListener failed");
			ce.initCause(e);
		}
	}

	public boolean targetMustExist() throws NamingException {
		if ((naming instanceof NamingEvents) == false) {
			throw new UnsupportedOperationException("Naming implementation does not support NamingExt");
		}
		NamingEvents next = (NamingEvents) naming;
		boolean targetMustExist = true;
		try {
			targetMustExist = next.targetMustExist();
		} catch (RemoteException e) {
			CommunicationException ce = new CommunicationException("removeNamingListener failed");
			ce.initCause(e);
		}
		return targetMustExist;
	}
	// End EventContext methods

	protected Object resolveLink(Object res, Hashtable refEnv) throws NamingException {
		Object linkResult = null;
		try {
			LinkRef link = (LinkRef) res;
			String ref = link.getLinkName();
			if (ref.startsWith("./"))
				linkResult = lookup(ref.substring(2));
			else if (refEnv != null)
				linkResult = new InitialContext(refEnv).lookup(ref);
			else
				linkResult = new InitialContext().lookup(ref);
		} catch (Exception e) {
			NamingException ex = new NamingException("Could not dereference object");
			ex.setRootCause(e);
			throw ex;
		}
		return linkResult;
	}

	protected boolean shouldDiscoveryHappen(boolean globalDisableDiscovery, String perCtxDisableDiscovery) {
		boolean debug = log.isDebugEnabled();
		if (!globalDisableDiscovery) {
			// No global disable, so act as before.
			if (Boolean.valueOf(perCtxDisableDiscovery) == Boolean.TRUE) {
				if (debug)
					log.debug("Skipping discovery due to disable flag in context");
				return false;
			}
		} else {
			// Global disable on but double check whether there's a per context override.
			// If disableDiscovery in context is explicitly set to false, do discovery.
			if (perCtxDisableDiscovery == null || Boolean.valueOf(perCtxDisableDiscovery) == Boolean.TRUE) {
				if (debug)
					log.debug(
							"Skipping discovery due to disable flag in context, or disable flag globally (and no override in context)");
				return false;
			}
		}

		return true;
	}

	// Private -------------------------------------------------------

	/**
	 * Isolate the creation of the MarshalledValuePair in a privileged block when
	 * running under a security manager so the following permissions can be isolated
	 * from the caller: RuntimePermission("createClassLoader")
	 * ReflectPermission("suppressAccessChecks")
	 * SerializablePermission("enableSubstitution")
	 * 
	 * @return the MarshalledValuePair wrapping obj
	 */
	private Object createMarshalledValuePair(final Object obj) throws IOException {
		MarshalledValuePair mvp = null;
		mvp = new MarshalledValuePair(obj);
		return mvp;
	}

	/**
	 * Determine the form of the name to pass to the NamingManager operations. This
	 * is supposed to be a context relative name according to the javaodcs for
	 * NamingManager, but historically the absolute name of the target context has
	 * been passed in.
	 * 
	 * @param env - the env of NamingContext that op was called on
	 * @return true if the legacy and technically incorrect absolute name should be
	 *         used, false if the context relative name should be used.
	 */
	private boolean useAbsoluteName(Hashtable env) {
		if (env == null)
			return true;
		String useRelativeName = (String) env.get(JNP_USE_RELATIVE_NAME);
		return Boolean.valueOf(useRelativeName) == Boolean.FALSE;
	}

	/**
	 * Use the NamingManager.getStateToBind to obtain the actual object to bind into
	 * jndi.
	 * 
	 * @param obj  - the value passed to bind/rebind
	 * @param name - the name passed to bind/rebind
	 * @param env  - the env of NamingContext that bind/rebind was called on
	 * @return the object to bind to the naming server
	 * @throws NamingException
	 */
	private Object getStateToBind(Object obj, Name name, Hashtable env) throws NamingException {
		if (useAbsoluteName(env))
			name = getAbsoluteName(name);
		return NamingManager.getStateToBind(obj, name, this, env);
	}

	/**
	 * Use the NamingManager.getObjectInstance to resolve the raw object obtained
	 * from the naming server.
	 * 
	 * @param obj  - raw value obtained from the naming server
	 * @param name - the name passed to the lookup op
	 * @param env  - the env of NamingContext that the op was called on
	 * @return the fully resolved object
	 * @throws Exception
	 */
	private Object getObjectInstance(Object obj, Name name, Hashtable env) throws Exception {
		if (useAbsoluteName(env))
			name = getAbsoluteName(name);
		final Object obtained = NamingManager.getObjectInstance(obj, name, this, env);
		if (obtained instanceof Reference) {
			final Reference ref = (Reference) obtained;
			throw MissingObjectFactoryException.create(ref.getFactoryClassName(), name);
		}
		return obtained;
	}

	/**
	 * Resolve the final object and wrap any non-NamingException errors in a
	 * NamingException with the cause passed as the root cause.
	 * 
	 * @param obj  - raw value obtained from the naming server
	 * @param name - the name passed to the lookup op
	 * @param env  - the env of NamingContext that the op was called on
	 * @return the fully resolved object
	 * @throws NamingException
	 */
	private Object getObjectInstanceWrapFailure(Object obj, Name name, Hashtable env) throws NamingException {
		try {
			return getObjectInstance(obj, name, env);
		} catch (NamingException e) {
			throw e;
		} catch (Exception e) {
			NamingException ex = new NamingException("Could not dereference object");
			ex.setRootCause(e);
			throw ex;
		}
	}

	private void checkRef(Hashtable refEnv) throws NamingException {
		if (naming == null) {
			String host = "localhost";
			int port = 1099;
			Exception serverEx = null;

			// Locate first available naming service
			String urls = (String) refEnv.get(Context.PROVIDER_URL);
			if (urls != null && urls.length() > 0) {
				StringTokenizer tokenizer = new StringTokenizer(urls, ",");

				// If provider list is unordered, first check for a cached connection to any
				// provider
				String unorderedProviderList = (String) refEnv.get(JNP_UNORDERED_PROVIDER_LIST);

				// If unset, set to global which defaults to false.
				if (unorderedProviderList == null) {
					unorderedProviderList = GLOBAL_UNORDERED_PROVIDER_LIST;
				}

				if (Boolean.valueOf(unorderedProviderList) == Boolean.TRUE) {
					while (naming == null && tokenizer.hasMoreElements()) {
						String url = tokenizer.nextToken();
						// Parse the url into a host:port form, stripping any protocol
						Name urlAsName = getNameParser("").parse(url);
						String server = parseNameForScheme(urlAsName, null);
						if (server != null)
							url = server;
					}
					tokenizer = new StringTokenizer(urls, ",");
				}

				if (naming == null) {
					while (naming == null && tokenizer.hasMoreElements()) {
						String url = tokenizer.nextToken();
						// Parse the url into a host:port form, stripping any protocol
						Name urlAsName = getNameParser("").parse(url);
						String server = parseNameForScheme(urlAsName, null);
						if (server != null)
							url = server;
					}
				}

				// If there is still no server, try discovery
				Exception discoveryFailure = null;
				if (naming == null) {
					if (naming == null) {
						StringBuffer buffer = new StringBuffer(50);
						buffer.append("Could not obtain connection to any of these urls: ").append(urls);
						if (discoveryFailure != null) {
							StringWriter sw = new StringWriter();
							PrintWriter pw = new PrintWriter(sw);
							discoveryFailure.printStackTrace(pw);
							buffer.append(" and discovery failed with error: ").append(sw.toString());
						}
						CommunicationException ce = new CommunicationException(buffer.toString());

						ce.setRootCause(serverEx);
						throw ce;
					}
				}
			} else {
				// Use server in same JVM
				naming = localServer;
			}
		}
	}

	private Name getAbsoluteName(Name n) throws NamingException {
		if (n.isEmpty())
			return composeName(n, prefix);
		else if (n.get(0).toString().equals("")) // Absolute name
			return n.getSuffix(1);
		else // Add prefix
			return composeName(n, prefix);
	}

	private Hashtable getEnv(Name n) throws InvalidNameException {
		Hashtable nameEnv = env;
		env.remove(JNP_PARSED_NAME);
		String serverInfo = parseNameForScheme(n, nameEnv);
		if (serverInfo != null) {
			// Set hostname:port value for the naming server
			nameEnv = (Hashtable) env.clone();
			nameEnv.put(Context.PROVIDER_URL, serverInfo);
		}
		return nameEnv;
	}

	/**
	 * JBAS-4574. Check if the given exception is because the server has been
	 * restarted while the cached naming stub hasn't been dgc-ed yet. If yes, we
	 * will flush out the naming stub from our cache and acquire a new stub. BW.
	 * 
	 * @param e      the exception that may be due to a stale stub
	 * @param refEnv the naming environment associated with the failed call
	 * 
	 * @return <code>true</code> if <code>e</code> indicates a stale naming stub and
	 *         we were able to succesfully flush the cache and acquire a new stub;
	 *         <code>false</code> otherwise.
	 */
	private boolean handleStaleNamingStub(Exception e, Hashtable refEnv) {
		if (e instanceof NoSuchObjectException || e.getCause() instanceof NoSuchObjectException) {
			try {
				if (log.isDebugEnabled()) {
					log.debug("Call failed with NoSuchObjectException, " + "flushing server cache and retrying", e);
				}
				naming = null;
				removeServer(refEnv);

				checkRef(refEnv);

				return true;
			} catch (Exception e1) {
				// Just log and return false; let caller continue processing
				// the original exception passed in to this method
				log.error("Caught exception flushing server cache and " + "re-establish naming after exception "
						+ e.getLocalizedMessage(), e1);
			}
		}
		return false;
	}

	/**
	 * JBPAPP-6447. Check if the given exception is because the server has died in
	 * the middle of an operation. If yes, we will flush out the naming stub from
	 * our cache and acquire a new stub.
	 *
	 * Triggers on java.rmi.UnmarshalException wrapping a SocketException or
	 * EOFException
	 *
	 * This must ONLY be used for idempotent operations where we don't care if the
	 * server may have already processed the request!
	 * 
	 * @param e      the exception that may be due to a dying server
	 * @param refEnv the naming environment associated with the failed call
	 * 
	 * @return <code>true</code> if <code>e</code> indicates a dying server and we
	 *         were able to succesfully flush the cache and acquire a new stub;
	 *         <code>false</code> otherwise.
	 */
	private boolean handleDyingServer(Exception e, Hashtable refEnv) {
		if (e instanceof UnmarshalException && e.getCause() != null
				&& (e.getCause() instanceof SocketException || e.getCause() instanceof EOFException)) {
			try {
				if (log.isDebugEnabled()) {
					log.debug("Call failed with UnmarshalException, " + "flushing server cache and retrying", e);
				}
				naming = null;
				removeServer(refEnv);

				checkRef(refEnv);

				return true;
			} catch (Exception e1) {
				// Just log and return false; let caller continue processing
				// the original exception passed in to this method
				log.error("Caught exception flushing server cache and " + "re-establish naming after exception "
						+ e.getLocalizedMessage(), e1);
			}
		}
		return false;
	}

	/**
	 * JBPAPP-8152/JBPAPP-8305. Check if the given exception is because the server
	 * is in the middle of starting up or shuting down. If yes, we will flush out
	 * the naming stub from our cache and acquire a new stub.
	 *
	 * Triggers on javax.naming.ServiceUnavailableException
	 *
	 * @param e      the exception that may be due to a server starting or stopping
	 * @param refEnv the naming environment associated with the failed call
	 * 
	 * @return <code>true</code> if <code>e</code> indicates a starting or stopping
	 *         server and we were able to succesfully flush the cache and acquire a
	 *         new stub; <code>false</code> otherwise.
	 */
	private boolean handleServerStartupShutdown(Exception e, Hashtable refEnv) {
		if (e instanceof ServiceUnavailableException) {
			try {
				if (log.isDebugEnabled()) {
					log.debug("Call failed with ServiceUnavailableException, " + "flushing server cache and retrying",
							e);
				}
				naming = null;
				removeServer(refEnv);

				checkRef(refEnv);

				return true;
			} catch (Exception e1) {
				// Just log and return false; let caller continue processing
				// the original exception passed in to this method
				log.error("Caught exception flushing server cache and " + "re-establish naming after exception "
						+ e.getLocalizedMessage(), e1);
			}
		}
		return false;
	}

	// Inner classes -------------------------------------------------
}
