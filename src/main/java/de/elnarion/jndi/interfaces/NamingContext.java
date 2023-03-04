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

import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.naming.Binding;
import javax.naming.CannotProceedException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.ContextNotEmptyException;
import javax.naming.InitialContext;
import javax.naming.InvalidNameException;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NameClassPair;
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
	 * An obsolete constant replaced by the JNP_MAX_RETRIES value
	 */
	public static final int MAX_RETRIES = 1;
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

	// Attributes ----------------------------------------------------
	transient Naming naming;
	transient Hashtable<String, Object> env;
	Name prefix;

	transient NameParser parser = new NamingParser();

	// Static --------------------------------------------------------

	static void removeServer(Hashtable<String, Object> serverEnv) {
		// JBAS-4622. Always do this.
		serverEnv.remove("hostKey");
	}

	/**
	 * Called to remove any url scheme atoms and extract the naming service
	 * information.
	 *
	 * @param n       the name component to the parsed. After returning n will have
	 *                all scheme related atoms removed.
	 * @param nameEnv the name env
	 * @throws InvalidNameException the invalid name exception
	 */
	static void parseNameForScheme(Name n, Hashtable<String, Object> nameEnv) throws InvalidNameException {
		if (n.size() > 0) {
			String scheme = n.get(0);
			int schemeLength = 0;
			if (scheme.startsWith("java:"))
				schemeLength = 5;
			else if (scheme.startsWith("jnp:"))
				schemeLength = 4;
			else if (scheme.startsWith("jnps:"))
				schemeLength = 5;
			if (schemeLength > 0) {
				// Make a copy of the name to avoid
				n = (Name) n.clone();
				String suffix = scheme.substring(schemeLength);
				if (suffix.length() == 0) {
					// Scheme was "url:/..."
					n.remove(0);
				} else {
					// Scheme was "url:foo" -> reinsert "foo"
					n.remove(0);
					n.add(0, suffix);
				}
				if (nameEnv != null)
					nameEnv.put(JNP_PARSED_NAME, n);
			}
		}
	}

	public static Naming getLocal() {
		return localServer;
	}

	public static void setLocal(Naming server) {
		localServer = server;
	}

	// Constructors --------------------------------------------------
	@SuppressWarnings("unchecked")
	public NamingContext(Hashtable<String, Object> e, Name baseName, Naming server) throws NamingException { // NOSONAR
																												// -
																												// Hashtable
																												// because
																												// of
																												// javax.naming
																												// use
		if (baseName == null)
			this.prefix = parser.parse("");
		else
			this.prefix = baseName;

		if (e != null)
			this.env = (Hashtable<String, Object>) e.clone();
		else
			this.env = new Hashtable<>();

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
		Hashtable<String, Object> refEnv = getEnv(name);
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
			naming.rebind(getAbsoluteName(name), obj, className);
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
		Hashtable<String, Object> refEnv = getEnv(name);
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

			naming.bind(name, obj, className);
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
		Hashtable<String, Object> refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		// Empty?
		if (name.isEmpty())
			return new NamingContext(refEnv, prefix, naming);

		try {
			Name n = getAbsoluteName(name);
			Object res = null;
			res = lookupValueWithExceptionHandling(refEnv, n);
			if (res instanceof MarshalledValuePair) {
				MarshalledValuePair mvp = (MarshalledValuePair) res;
				Object storedObj = mvp.get();
				return getObjectInstanceWrapFailure(storedObj, name, refEnv);
			} else if (res instanceof MarshalledObject) {
				MarshalledObject<?> mo = (MarshalledObject<?>) res;
				return mo.get();
			} else if (res instanceof Context) {
				// Add env
				Enumeration<String> keys = refEnv.keys();
				while (keys.hasMoreElements()) {
					String key = keys.nextElement();
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

				if (!(context instanceof Context)) {
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

	private Object lookupValueWithExceptionHandling(Hashtable<String, Object> refEnv, Name n )
			throws NamingException {
		try {
			return naming.lookup(n);
		} catch (ServiceUnavailableException re) {
			// Check for JBPAPP-8152/JBPAPP-8305
			if (handleServerStartupShutdown(re, refEnv)) {
				// try again with new naming stub
				return naming.lookup(n);
			} else {
				// Throw exception and let outer logic handle it.
				throw re;
			}
		}
	}

	public void unbind(String name) throws NamingException {
		unbind(getNameParser(name).parse(name));
	}

	public void unbind(Name name) throws NamingException {
		Hashtable<String, Object> refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		try {
			naming.unbind(getAbsoluteName(name));
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			cctx.unbind(cpe.getRemainingName());
		}
	}

	public void rename(String oldname, String newname) throws NamingException {
		rename(getNameParser(oldname).parse(oldname), getNameParser(newname).parse(newname));
	}

	public void rename(Name oldName, Name newName) throws NamingException {
		bind(newName, lookup(oldName));
		unbind(oldName);
	}

	public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
		return list(getNameParser(name).parse(name));
	}

	public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
		Hashtable<String, Object> refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		try {
			Collection<NameClassPair> c = null;
			c = naming.list(getAbsoluteName(name));
			return new NameClassPairEnumerationImpl(c);
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			return cctx.list(cpe.getRemainingName());
		}
	}

	public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
		return listBindings(getNameParser(name).parse(name));
	}

	public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
		Hashtable<String, Object> refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		try {
			// Get list
			Collection<?> bindings = null;
			// Get list
			bindings = naming.listBindings(getAbsoluteName(name));
			Collection<Binding> realBindings = new ArrayList<>(bindings.size());

			// Convert marshalled objects
			Iterator<?> i = bindings.iterator();
			while (i.hasNext()) {
				Binding binding = (Binding) i.next();
				Object obj = binding.getObject();
				if (obj instanceof MarshalledValuePair) {
					obj = extracted(obj);
				} else if (obj instanceof MarshalledObject) {
					obj = ((MarshalledObject<?>) obj).get();
				}
				realBindings.add(new Binding(binding.getName(), binding.getClassName(), obj));
			}

			// Return transformed list of bindings
			return new BindingEnumerationImpl(realBindings);
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			return cctx.listBindings(cpe.getRemainingName());
		} catch (IOException|ClassNotFoundException e) {
			naming = null;
			removeServer(refEnv);
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		} 
	}

	private Object extracted(Object obj) throws IOException, NamingException {
		try {
			obj = ((MarshalledValuePair) obj).get();
		} catch (ClassNotFoundException e) {
			NamingException ex = new CommunicationException();
			ex.setRootCause(e);
			throw ex;
		}
		return obj;
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

		Hashtable<String, Object> refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		try {
			name = getAbsoluteName(name);
			return naming.createSubcontext(name);
		} catch (CannotProceedException cpe) {
			cpe.setEnvironment(refEnv);
			Context cctx = NamingManager.getContinuationContext(cpe);
			return cctx.createSubcontext(cpe.getRemainingName());
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

	public Hashtable<String, Object> getEnvironment() throws NamingException {
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
		Hashtable<String, Object> refEnv = getEnv(name);
		checkRef(refEnv);
		Name parsedName = (Name) refEnv.get(JNP_PARSED_NAME);
		if (parsedName != null)
			name = parsedName;

		if (name.isEmpty())
			return lookup(name);

		Object link = null;
		try {
			Name n = getAbsoluteName(name);
			link = naming.lookup(n);
			if (!(link instanceof LinkRef) && link instanceof Reference)
				link = getObjectInstance(link, name, null);
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
		if (!(naming instanceof NamingEvents)) {
			Class<?> cls = naming.getClass();
			String cs = cls.getName() + ", CS:" + cls.getProtectionDomain().getCodeSource().toString();
			throw new UnsupportedOperationException("Naming implementation does not support NamingExt, : " + cs);
		}
		NamingEvents next = (NamingEvents) naming;
		next.addNamingListener(this, target, scope, l);
	}

	public void addNamingListener(String target, int scope, NamingListener l) throws NamingException {
		Name targetName = parser.parse(target);
		addNamingListener(targetName, scope, l);
	}

	public void removeNamingListener(NamingListener l) throws NamingException {
		if (!(naming instanceof NamingEvents)) {
			throw new UnsupportedOperationException("Naming implementation does not support NamingExt");
		}
		NamingEvents next = (NamingEvents) naming;
		next.removeNamingListener(l);
	}

	public boolean targetMustExist() throws NamingException {
		if (!(naming instanceof NamingEvents)) {
			throw new UnsupportedOperationException("Naming implementation does not support NamingExt");
		}
		NamingEvents next = (NamingEvents) naming;
		boolean targetMustExist = true;
		targetMustExist = next.targetMustExist();
		return targetMustExist;
	}
	// End EventContext methods

	protected Object resolveLink(Object res, Hashtable<String, Object> refEnv) throws NamingException {
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
	private boolean useAbsoluteName(Hashtable<String, Object> env) {
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
	private Object getStateToBind(Object obj, Name name, Hashtable<String, Object> env) throws NamingException {
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
	private Object getObjectInstance(Object obj, Name name, Hashtable<String, Object> env) throws Exception {
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
	private Object getObjectInstanceWrapFailure(Object obj, Name name, Hashtable<String, Object> env)
			throws NamingException {
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

	private void checkRef(Hashtable<String, Object> refEnv) throws NamingException {
		if (naming == null) {
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
					while (tokenizer.hasMoreElements()) {
						String url = tokenizer.nextToken();
						// Parse the url into a host:port form, stripping any protocol
						Name urlAsName = getNameParser("").parse(url);
						parseNameForScheme(urlAsName, null);
					}
					tokenizer = new StringTokenizer(urls, ",");
				}
				while (tokenizer.hasMoreElements()) {
					String url = tokenizer.nextToken();
					// Parse the url into a host:port form, stripping any protocol
					Name urlAsName = getNameParser("").parse(url);
					parseNameForScheme(urlAsName, null);
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
		else if (n.get(0).equals("")) // Absolute name
			return n.getSuffix(1);
		else // Add prefix
			return composeName(n, prefix);
	}

	private Hashtable<String, Object> getEnv(Name n) throws InvalidNameException {
		Hashtable<String, Object> nameEnv = env;
		env.remove(JNP_PARSED_NAME);
		parseNameForScheme(n, nameEnv);
		return nameEnv;
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
	private boolean handleServerStartupShutdown(Exception e, Hashtable<String, Object> refEnv) {
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
