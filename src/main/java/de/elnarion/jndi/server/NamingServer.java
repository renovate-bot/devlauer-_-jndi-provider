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
package de.elnarion.jndi.server;

import de.elnarion.jndi.interfaces.Naming;
import de.elnarion.jndi.interfaces.NamingContext;
import de.elnarion.jndi.interfaces.NamingEvents;
import de.elnarion.jndi.interfaces.NamingParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.*;
import javax.naming.event.EventContext;
import javax.naming.event.NamingEvent;
import javax.naming.event.NamingListener;
import javax.naming.spi.ResolveResult;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in memory JNDI naming server implementation class.
 *
 * @author Rickard Oberg
 * @author patriot1burke
 * @author Scott.Stark@jboss.org
 */
public class NamingServer implements Naming, NamingEvents, java.io.Serializable {
	/** @since 1.12 at least */
	private static final long serialVersionUID = 4183855539507934373L;
	private static final Logger LOGGER = LoggerFactory.getLogger(NamingServer.class);
	// Constants -----------------------------------------------------
	protected final NamingParser parser = new NamingParser();
	/**  */
	protected final transient Map<String, Binding> table = createTable();
	// Attributes ----------------------------------------------------
	private final transient boolean debug;
	protected Name prefix;
	protected NamingServer parent;
	/** The NamingListeners registered with this context */
	private transient EventListeners listeners;
	/** The manager for EventContext listeners */
	private transient EventMgr eventMgr;

	// Static --------------------------------------------------------

	// Constructors --------------------------------------------------

	public NamingServer() throws NamingException {
		this(null, null);
	}

	public NamingServer(Name prefix, NamingServer parent) throws NamingException {
		this(prefix, parent, null);
	}

	public NamingServer(Name prefix, NamingServer parent, EventMgr eventMgr) throws NamingException {
		if (prefix == null)
			prefix = parser.parse("");
		this.prefix = prefix;
		this.parent = parent;
		this.eventMgr = eventMgr;
		this.debug = LOGGER.isDebugEnabled();
	}

	// Public --------------------------------------------------------

	// NamingListener registration
	public synchronized void addNamingListener(EventContext context, Name target, int scope, NamingListener l)
			throws NamingException {
		if (listeners == null)
			listeners = new EventListeners(context);
		if (debug)
			LOGGER.debug("addNamingListener, target: {}, scope: {}", target, scope);
		listeners.addNamingListener(context, target, scope, l);
	}

	public void removeNamingListener(NamingListener l) {
		if (listeners != null) {
			listeners.removeNamingListener(l);
		}
	}

	/**
	 * We don't need targets to exist?
	 *
	 * @return false
	 */
	public boolean targetMustExist() {
		return false;
	}

	// Naming implementation -----------------------------------------
	public synchronized void bind(Name name, Object obj, String className) throws NamingException {
		if (name.isEmpty()) {
			// Empty names are not allowed
			throw new InvalidNameException("An empty name cannot be passed to bind");
		} else if (name.size() > 1) {
			// Recurse to find correct context
			findRightContextAndBind(name, obj, className);
		} else {
			// Bind object
			bindAndNotifyListeners(name, obj, className);
		}
	}

	private void findRightContextAndBind(Name name, Object obj, String className) throws NamingException {
		Object ctx = getObject(name);
		if (ctx != null) {
			if (ctx instanceof NamingServer) {
				NamingServer ns = (NamingServer) ctx;
				ns.bind(name.getSuffix(1), obj, className);
			} else if (ctx instanceof Reference) {
				// Federation
				throwSpecificExceptionsForReference(name, ctx);
			} else {
				throw new NotContextException();
			}
		} else {
			throw new NameNotFoundException(name.toString() + " in: " + prefix);
		}
	}

	private void throwSpecificExceptionsForReference(Name name, Object ctx)
			throws CannotProceedException, NotContextException {
		createAndThrowCanNotProceedExceptionForNnsReference(name, ctx);
		throw new NotContextException();
	}

	private void createAndThrowCanNotProceedExceptionForNnsReference(Name name, Object ctx)
			throws CannotProceedException {
		if (((Reference) ctx).get("nns") != null) {
			CannotProceedException cpe = new CannotProceedException();
			cpe.setResolvedObj(ctx);
			cpe.setRemainingName(name.getSuffix(1));
			throw cpe;
		}
	}

	private void bindAndNotifyListeners(Name name, Object obj, String className)
			throws InvalidNameException, NameAlreadyBoundException {
		if (name.get(0).equals("")) {
			throw new InvalidNameException("An empty name cannot be passed to bind");
		} else {
			if (debug)
				LOGGER.debug("bind {}={}, {}", name, obj, className);
			try {
				getBinding(name);
				// Already bound
				throw new NameAlreadyBoundException(name.toString());
			} catch (NameNotFoundException e) {
				Name fullName = (Name) prefix.clone();
				fullName.addAll(name);

				Binding newb = setBinding(name, obj, className);
				// Notify event listeners
				this.fireEvent(fullName, null, newb, NamingEvent.OBJECT_ADDED, "bind");
			}
		}
	}

	public synchronized void rebind(Name name, Object obj, String className) throws NamingException {
		if (name.isEmpty()) {
			// Empty names are not allowed
			throw new InvalidNameException("An empty name cannot be passed to rebind");
		} else if (name.size() > 1) {
			// Recurse to find correct context
			rebindName(name, obj, className);
		} else {
			// Bind object
			bindToContext(name, obj, className);
		}
	}

	private void bindToContext(Name name, Object obj, String className) throws InvalidNameException {
		if (name.get(0).equals("")) {
			throw new InvalidNameException("An empty name cannot be passed to rebind");
		} else {
			Name fullName = (Name) prefix.clone();
			String comp = name.get(0);
			fullName.add(comp);

			Binding oldb = table.get(comp);
			Binding newb = setBinding(name, obj, className);
			// Notify event listeners
			if (listeners != null) {
				int type = NamingEvent.OBJECT_CHANGED;
				if (oldb == null)
					type = NamingEvent.OBJECT_ADDED;
				this.fireEvent(fullName, oldb, newb, type, "rebind");
			}
		}
	}

	private void rebindName(Name name, Object obj, String className) throws NamingException {
		Object ctx = getObject(name);
		if (ctx instanceof NamingServer) {
			((NamingServer) ctx).rebind(name.getSuffix(1), obj, className);
		} else if (ctx instanceof Reference) {
			throwSpecificExceptionsForReference(name, ctx);
		} else {
			throw new NotContextException();
		}
	}

	public synchronized void unbind(Name name) throws NamingException {
		if (name.isEmpty()) {
			// Empty names are not allowed
			throw new InvalidNameException();
		} else if (name.size() > 1) {
			// Recurse to find correct context
			Object ctx = getObject(name);
			if (ctx instanceof NamingServer) {
				((NamingServer) ctx).unbind(name.getSuffix(1));
			} else if (ctx instanceof Reference) {
				throwSpecificExceptionsForReference(name, ctx);
			} else {
				throw new NotContextException();
			}
		} else {
			// Unbind object
			if (name.get(0).equals("")) {
				throw new InvalidNameException();
			} else {
				getBinding(name);
				Name fullName = (Name) prefix.clone();
				fullName.addAll(name);

				Binding oldb = removeBinding(name);
				// Notify event listeners
				int type = NamingEvent.OBJECT_REMOVED;
				this.fireEvent(fullName, oldb, null, type, "unbind");
			}
		}
	}

	public Object lookup(Name name) throws NamingException {
		Object result;
		if (name.isEmpty()) {

			// Return this
			result = new NamingContext(null, (Name) (prefix.clone()), getRoot());
		} else if (name.size() > 1) {
			// Recurse to find correct context
			result = lookupInContext(name);
		} else {
			// Get object to return
			if (name.get(0).equals("")) {
				result = new NamingContext(null, (Name) (prefix.clone()), getRoot());
			} else {
				Name fullName = (Name) (prefix.clone());
				fullName.addAll(name);

				Object res = getObject(name);

				if (res instanceof NamingServer) {
					result = new NamingContext(null, fullName, getRoot());
				} else
					result = res;
			}
		}

		return result;
	}

	private Object lookupInContext(Name name) throws NamingException {
		Object ctx = getObject(name);
		if (ctx instanceof NamingServer) {
			return ((NamingServer) ctx).lookup(name.getSuffix(1));
		} else if (ctx instanceof Reference) {
			createAndThrowCanNotProceedExceptionForNnsReference(name, ctx);
			return new ResolveResult(ctx, name.getSuffix(1));
		} else {
			throw new NotContextException();
		}
	}

	public Collection<NameClassPair> list(Name name) throws NamingException {
		if (name.isEmpty()) {
			ArrayList<NameClassPair> list = new ArrayList<>();
			for (Binding b : table.values()) {
				NameClassPair ncp = new NameClassPair(b.getName(), b.getClassName(), true);
				list.add(ncp);
			}
			return list;
		} else {
			Object ctx = getObject(name);
			if (ctx instanceof NamingServer) {
				return ((NamingServer) ctx).list(name.getSuffix(1));
			} else if (ctx instanceof Reference) {
				throwSpecificExceptionsForReference(name, ctx);
				// never reached
				return Collections.emptyList();
			} else {
				throw new NotContextException();
			}
		}
	}

	public Collection<Binding> listBindings(Name name) throws NamingException {
		if (name.isEmpty()) {
			return listCurrentBindings();
		} else {
			Object ctx = getObject(name);
			if (ctx instanceof NamingServer) {
				return ((NamingServer) ctx).listBindings(name.getSuffix(1));
			} else if (ctx instanceof Reference) {
				throwSpecificExceptionsForReference(name, ctx);
				// never reached because of exception from throw...
				return Collections.emptyList();
			} else {
				throw new NotContextException();
			}
		}
	}

	private Collection<Binding> listCurrentBindings() throws NamingException {
		Collection<Binding> bindings = table.values();
		Collection<Binding> newBindings = new ArrayList<>(bindings.size());
		for (Binding b : bindings) {
			if (b.getObject() instanceof NamingServer) {
				Name n = (Name) prefix.clone();
				n.add(b.getName());
				newBindings.add(new Binding(b.getName(), b.getClassName(), new NamingContext(null, n, getRoot())));
			} else {
				newBindings.add(b);
			}
		}

		return newBindings;
	}

	public Context createSubcontext(Name name) throws NamingException {
		if (name.size() == 0)
			throw new InvalidNameException("Cannot pass an empty name to createSubcontext");

		NamingException ex;
		Context subCtx;
		if (name.size() > 1) {
			subCtx = createFurtherSubcontexts(name);
		} else {
			Object binding = table.get(name.get(0));
			if (binding != null) {
				ex = new NameAlreadyBoundException();
				ex.setResolvedName(prefix);
				ex.setRemainingName(name);
				throw ex;
			} else {
				Name fullName = (Name) prefix.clone();
				fullName.addAll(name);
				NamingServer subContext = createNamingServer(fullName, this);
				subCtx = new NamingContext(null, fullName, getRoot());
				setBinding(name, subContext, NamingContext.class.getName());
				// Return the NamingContext as the binding value
				Binding newb = new Binding(name.toString(), NamingContext.class.getName(), subCtx, true);
				// Notify event listeners
				if (listeners != null) {
					this.fireEvent(fullName, null, newb, NamingEvent.OBJECT_ADDED, "createSubcontext");
				}
			}
		}
		return subCtx;
	}

	private Context createFurtherSubcontexts(Name name) throws NamingException {
		NamingException ex;
		Object ctx = getObject(name);
		if (ctx != null) {
			Name subCtxName = name.getSuffix(1);
			if (ctx instanceof NamingServer) {
				return ((NamingServer) ctx).createSubcontext(subCtxName);
			} else if (ctx instanceof Reference) {
				// Federation
				createAndThrowCanNotProceedExceptionForNnsReference(name, ctx);
			}
			// all other cases
			ex = new NotContextException();
			ex.setResolvedName(name.getPrefix(0));
			ex.setRemainingName(subCtxName);

		} else {
			ex = new NameNotFoundException();
			ex.setRemainingName(name);
		}
		throw ex;
	}

	public Naming getRoot() {
		if (parent == null)
			return this;
		else
			return parent.getRoot();
	}

	// Y overrides ---------------------------------------------------

	// Package protected ---------------------------------------------

	// Protected -----------------------------------------------------

	protected Map<String, Binding> createTable() {
		return new ConcurrentHashMap<>();
	}

	/**
	 * Create sub naming.
	 *
	 * @param prefix the prefix
	 * @param parent the parent naming server
	 * @return new sub instance
	 * @throws NamingException for any error
	 */
	protected NamingServer createNamingServer(Name prefix, NamingServer parent) throws NamingException {
		return new NamingServer(prefix, parent, eventMgr);
	}

	protected void fireEvent(Name fullName, Binding oldb, Binding newb, int type, String changeInfo) {
		if (eventMgr == null) {
			if (debug)
				LOGGER.debug("Skipping event dispatch because there is no EventMgr");
			return;
		}

		if (listeners != null) {
			if (debug)
				LOGGER.debug("fireEvent, type: {}, fullName: {}", type, fullName);
			HashSet<Integer> scopes = new HashSet<>();
			scopes.add(EventContext.OBJECT_SCOPE);
			scopes.add(EventContext.ONELEVEL_SCOPE);
			scopes.add(EventContext.SUBTREE_SCOPE);
			eventMgr.fireEvent(fullName, oldb, newb, type, changeInfo, listeners, scopes);
		} else if (debug) {
			LOGGER.debug("fireEvent, type: {}, fullName: {}", type, fullName);
		}
		// Traverse to parent for SUBTREE_SCOPE
		HashSet<Integer> scopes = new HashSet<>();
		scopes.add(EventContext.SUBTREE_SCOPE);
		NamingServer nsparent = parent;
		while (nsparent != null) {
			if (nsparent.listeners != null) {
				eventMgr.fireEvent(fullName, oldb, newb, type, changeInfo, nsparent.listeners, scopes);
			}
			nsparent = nsparent.parent;
		}
	}

	// Private -------------------------------------------------------

	private Binding setBinding(Name name, Object obj, String className) {
		String n = name.toString();
		Binding b = new Binding(n, className, obj, true);
		table.put(n, b);
		if (debug) {
			StringBuilder tmp = new StringBuilder(super.toString());
			tmp.append(", setBinding: name=");
			tmp.append(name);
			tmp.append(", obj=");
			tmp.append(obj);
			tmp.append(", className=");
			tmp.append(className);
			if (LOGGER.isDebugEnabled())
				LOGGER.debug(tmp.toString());
		}
		return b;
	}

	private Binding getBinding(String key) throws NameNotFoundException {
		Binding b = table.get(key);
		if (b == null) {
			if (LOGGER.isDebugEnabled()) {
				StringBuilder tmp = new StringBuilder(super.toString());
				tmp.append(", No binding for: ");
				tmp.append(key);
				tmp.append(" in context ");
				tmp.append(this.prefix);
				tmp.append(", bindings:\n");
				Collection<Binding> bindings = table.values();
				for (Binding value : bindings) {
					tmp.append(value.getName());
					tmp.append('=');
					if (value.getObject() != null)
						tmp.append(value.getObject().toString());
					else
						tmp.append("null");
					tmp.append('\n');
				}
				LOGGER.debug(tmp.toString());
			}
			throw new NameNotFoundException(key + " not bound in " + prefix);
		}
		return b;
	}

	private Binding getBinding(Name key) throws NameNotFoundException {
		return getBinding(key.get(0));
	}

	private Object getObject(Name key) throws NameNotFoundException {
		return getBinding(key).getObject();
	}

	private Binding removeBinding(Name name) {
		return table.remove(name.get(0));
	}

	protected synchronized EventMgr getEventMgr() {
		return this.eventMgr;
	}

	protected synchronized void setEventMgr(EventMgr paramEventMgr) {
		this.eventMgr = paramEventMgr;
	}
}
