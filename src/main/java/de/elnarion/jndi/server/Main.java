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
package de.elnarion.jndi.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ServerSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.elnarion.jndi.interfaces.MarshalledValuePair;
import de.elnarion.jndi.interfaces.Naming;

/**
 * A main() entry point for running the jnp naming service implementation as a
 * standalone process.
 * 
 * @author Rickard Oberg
 * @author Scott.Stark@jboss.org
 */
public class Main implements MainMBean {
	// Constants -----------------------------------------------------

	// Attributes ----------------------------------------------------
	/** The Naming interface server implementation. */
	protected NamingBean theServer;

	/** The server stub. */
	protected MarshalledObject serverStub;

	/** The is stub exported. */
	protected boolean isStubExported;

	/** The jnp server socket through which the NamingServer stub is vended. */
	protected ServerSocket serverSocket;

	/** An optional custom client socket factory. */
	protected RMIClientSocketFactory clientSocketFactory;

	/** An optional custom server socket factory. */
	protected RMIServerSocketFactory serverSocketFactory;

	/** An optional custom server socket factory. */
	protected ServerSocketFactory jnpServerSocketFactory;

	/** The class name of the optional custom client socket factory. */
	protected String clientSocketFactoryName;

	/** The class name of the optional custom server socket factory. */
	protected String serverSocketFactoryName;

	/** The class name of the optional custom JNP server socket factory. */
	protected String jnpServerSocketFactoryName;
	/**
	 * The interface to bind to for the lookup socket. This is useful for
	 * multi-homed hosts that want control over which interfaces accept connections
	 */
	protected InetAddress bindAddress;

	/** The bind addresses. */
	protected List<InetAddress> bindAddresses;

	/** The interface to bind to for the Naming RMI server. */
	protected InetAddress rmiBindAddress;
	/** Should the java.rmi.server.hostname property to rmiBindAddress */
	private boolean enableRmiServerHostname;

	/** The serverSocket listen queue depth. */
	protected int backlog = 50;
	/**
	 * The jnp protocol listening port. The default is 1099, the same as the RMI
	 * registry default port.
	 */
	protected int port = 1099;
	/**
	 * The RMI port on which the Naming implementation will be exported. The default
	 * is 0 which means use any available port.
	 */
	protected int rmiPort = 0;

	/** URLs that clients can use to connect to the bootstrap socket. */
	protected List<String> bootstrapURLs;
	/**
	 * A flag indicating if theServer will be set as the NamingContext.setLocal
	 * value
	 */
	protected boolean InstallGlobalService = true;
	/**
	 * A flag indicating if theServer will try to use the NamingContext.setLocal
	 * value
	 */
	protected boolean UseGlobalService = true;

	/** The log. */
	protected Logger log;

	/** The thread pool used to handle jnp stub lookup requests. */
	private Executor lookupExector;

	/** The exception seen when creating the lookup listening port. */
	private Exception lookupListenerException;

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws Exception the exception
	 */
	// Static --------------------------------------------------------
	public static void main(String[] args) throws Exception {
		new Main().start();
	}

	/**
	 * Instantiates a new main.
	 */
	// Constructors --------------------------------------------------
	public Main() {
		this("org.jboss.naming.Naming");
	}

	/**
	 * Instantiates a new main.
	 *
	 * @param categoryName the category name
	 */
	public Main(String categoryName) {
		// Load properties from properties file
		try {
			ClassLoader loader = getClass().getClassLoader();
			InputStream is = loader.getResourceAsStream("jnp.properties");
			System.getProperties().load(is);
		} catch (Exception e) {
			// Ignore
		}

		// Set configuration from the system properties
		setPort(Integer.getInteger("jnp.port", getPort()).intValue());
		setRmiPort(Integer.getInteger("jnp.rmiPort", getRmiPort()).intValue());
		log = LoggerFactory.getLogger(categoryName);
		log.debug("isDebugEnabled: " + log.isDebugEnabled());
	}

	/**
	 * Gets the naming info.
	 *
	 * @return the naming info
	 */
	// Public --------------------------------------------------------
	public NamingBean getNamingInfo() {
		return theServer;
	}

	/**
	 * Set the NamingBean/Naming implementation.
	 *
	 * @param info the new naming info
	 */
	public void setNamingInfo(NamingBean info) {
		this.theServer = info;
	}

	/**
	 * Gets the lookup exector.
	 *
	 * @return the lookup exector
	 */
	public Executor getLookupExector() {
		return lookupExector;
	}

	/**
	 * Set the Executor to use for bootstrap socket lookup handling. Note that this
	 * must support at least 2 thread to avoid hanging the AcceptHandler accept
	 * loop.
	 * 
	 * @param lookupExector - An Executor that supports at least 2 threads
	 */
	public void setLookupExector(Executor lookupExector) {
		this.lookupExector = lookupExector;
	}

	/**
	 * Get any exception seen during the lookup listening port creation.
	 *
	 * @return the lookup listener exception
	 */
	public Exception getLookupListenerException() {
		return lookupListenerException;
	}

	/**
	 * Get the call by value flag for jndi lookups.
	 * 
	 * @return true if all lookups are unmarshalled using the caller's TCL, false if
	 *         in VM lookups return the value by reference.
	 */
	public boolean getCallByValue() {
		return MarshalledValuePair.getEnableCallByReference() == false;
	}

	/**
	 * Set the call by value flag for jndi lookups.
	 *
	 * @param flag - true if all lookups are unmarshalled using the caller's TCL,
	 *             false if in VM lookups return the value by reference.
	 */
	public void setCallByValue(boolean flag) {
		boolean callByValue = !flag;
		MarshalledValuePair.setEnableCallByReference(callByValue);
	}

	/**
	 * Gets the naming proxy.
	 *
	 * @return the naming proxy
	 * @throws Exception the exception
	 */
	public Object getNamingProxy() throws Exception {
		return serverStub.get();
	}

	/**
	 * Sets the naming proxy.
	 *
	 * @param proxy the new naming proxy
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void setNamingProxy(Object proxy) throws IOException {
		serverStub = new MarshalledObject(proxy);
	}

	/**
	 * Sets the rmi port.
	 *
	 * @param p the new rmi port
	 */
	public void setRmiPort(int p) {
		rmiPort = p;
	}

	/**
	 * Gets the rmi port.
	 *
	 * @return the rmi port
	 */
	public int getRmiPort() {
		return rmiPort;
	}

	/**
	 * Sets the port.
	 *
	 * @param p the new port
	 */
	public void setPort(int p) {
		port = p;
	}

	/**
	 * Gets the port.
	 *
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Gets the bind address.
	 *
	 * @return the bind address
	 */
	public String getBindAddress() {
		String address = null;
		if (bindAddress != null)
			address = bindAddress.getHostAddress();
		return address;
	}

	/**
	 * Sets the bind address.
	 *
	 * @param host the new bind address
	 * @throws UnknownHostException the unknown host exception
	 */
	public void setBindAddress(String host) throws UnknownHostException {
		if (host == null || host.length() == 0)
			bindAddress = null;
		else
			bindAddress = InetAddress.getByName(host);
	}

	/**
	 * Gets the bind addresses.
	 *
	 * @return the bind addresses
	 */
	public List<InetAddress> getBindAddresses() {
		return bindAddresses;
	}

	/**
	 * Sets the bind addresses.
	 *
	 * @param bindAddresses the new bind addresses
	 */
	public void setBindAddresses(List<InetAddress> bindAddresses) {
		this.bindAddresses = bindAddresses;
	}

	/**
	 * Gets the rmi bind address.
	 *
	 * @return the rmi bind address
	 */
	public String getRmiBindAddress() {
		String address = null;
		if (rmiBindAddress != null)
			address = rmiBindAddress.getHostAddress();
		return address;
	}

	/**
	 * Sets the rmi bind address.
	 *
	 * @param host the new rmi bind address
	 * @throws UnknownHostException the unknown host exception
	 */
	public void setRmiBindAddress(String host) throws UnknownHostException {
		if (host == null || host.length() == 0)
			rmiBindAddress = null;
		else
			rmiBindAddress = InetAddress.getByName(host);
	}

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
	public String getBootstrapURL() {
		if (bootstrapURLs != null && bootstrapURLs.size() > 0) {
			return bootstrapURLs.get(0);
		}
		return null;
	}

	/**
	 * Returns a list of URLs suitable for use as a java.naming.provider.url value
	 * in a set of naming environment properties; i.e. ones that can be used to
	 * connect to the lookup socket. There will be one URL per configured
	 * {@link #getBindAddresses() bind address}.
	 * 
	 * @return the URLs, or <code>null</code> if no bound lookup socket exists
	 */
	public List<String> getBootstrapURLs() {
		return bootstrapURLs;
	}

	/**
	 * Checks if is enable rmi server hostname.
	 *
	 * @return true, if is enable rmi server hostname
	 */
	public boolean isEnableRmiServerHostname() {
		return enableRmiServerHostname;
	}

	/**
	 * Sets the enable rmi server hostname.
	 *
	 * @param enableRmiServerHostname the new enable rmi server hostname
	 */
	public void setEnableRmiServerHostname(boolean enableRmiServerHostname) {
		this.enableRmiServerHostname = enableRmiServerHostname;
	}

	/**
	 * Gets the backlog.
	 *
	 * @return the backlog
	 */
	public int getBacklog() {
		return backlog;
	}

	/**
	 * Sets the backlog.
	 *
	 * @param backlog the new backlog
	 */
	public void setBacklog(int backlog) {
		if (backlog <= 0)
			backlog = 50;
		this.backlog = backlog;
	}

	/**
	 * Gets the install global service.
	 *
	 * @return the install global service
	 */
	public boolean getInstallGlobalService() {
		return InstallGlobalService;
	}

	/**
	 * Sets the install global service.
	 *
	 * @param flag the new install global service
	 */
	public void setInstallGlobalService(boolean flag) {
		this.InstallGlobalService = flag;
	}

	/**
	 * Gets the use global service.
	 *
	 * @return the use global service
	 */
	public boolean getUseGlobalService() {
		return UseGlobalService;
	}

	/**
	 * Sets the use global service.
	 *
	 * @param flag the new use global service
	 */
	public void setUseGlobalService(boolean flag) {
		this.UseGlobalService = flag;
	}

	/**
	 * Gets the client socket factory.
	 *
	 * @return the client socket factory
	 */
	public String getClientSocketFactory() {
		return clientSocketFactoryName;
	}

	/**
	 * Sets the client socket factory.
	 *
	 * @param factoryClassName the new client socket factory
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 */
	public void setClientSocketFactory(String factoryClassName)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		this.clientSocketFactoryName = factoryClassName;
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Class<?> clazz = loader.loadClass(clientSocketFactoryName);
		clientSocketFactory = (RMIClientSocketFactory) clazz.newInstance();
	}

	/**
	 * Gets the client socket factory bean.
	 *
	 * @return the client socket factory bean
	 */
	public RMIClientSocketFactory getClientSocketFactoryBean() {
		return clientSocketFactory;
	}

	/**
	 * Sets the client socket factory bean.
	 *
	 * @param factory the new client socket factory bean
	 */
	public void setClientSocketFactoryBean(RMIClientSocketFactory factory) {
		this.clientSocketFactory = factory;
	}

	/**
	 * Gets the server socket factory.
	 *
	 * @return the server socket factory
	 */
	public String getServerSocketFactory() {
		return serverSocketFactoryName;
	}

	/**
	 * Sets the server socket factory.
	 *
	 * @param factoryClassName the new server socket factory
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 */
	public void setServerSocketFactory(String factoryClassName)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		this.serverSocketFactoryName = factoryClassName;
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Class<?> clazz = loader.loadClass(serverSocketFactoryName);
		serverSocketFactory = (RMIServerSocketFactory) clazz.newInstance();
	}

	/**
	 * Gets the server socket factory bean.
	 *
	 * @return the server socket factory bean
	 */
	public RMIServerSocketFactory getServerSocketFactoryBean() {
		return serverSocketFactory;
	}

	/**
	 * Sets the server socket factory bean.
	 *
	 * @param factory the new server socket factory bean
	 */
	public void setServerSocketFactoryBean(RMIServerSocketFactory factory) {
		this.serverSocketFactory = factory;
	}

	/**
	 * Gets the JNP server socket factory.
	 *
	 * @return the JNP server socket factory
	 */
	public String getJNPServerSocketFactory() {
		return jnpServerSocketFactoryName;
	}

	/**
	 * Sets the JNP server socket factory.
	 *
	 * @param factoryClassName the new JNP server socket factory
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 */
	public void setJNPServerSocketFactory(String factoryClassName)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		this.jnpServerSocketFactoryName = factoryClassName;
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Class<?> clazz = loader.loadClass(jnpServerSocketFactoryName);
		jnpServerSocketFactory = (ServerSocketFactory) clazz.newInstance();
	}

	/**
	 * Gets the JNP server socket factory bean.
	 *
	 * @return the JNP server socket factory bean
	 */
	public ServerSocketFactory getJNPServerSocketFactoryBean() {
		return jnpServerSocketFactory;
	}

	/**
	 * Sets the JNP server socket factory bean.
	 *
	 * @param factory the new JNP server socket factory bean
	 */
	public void setJNPServerSocketFactoryBean(ServerSocketFactory factory) {
		this.jnpServerSocketFactory = factory;
	}

	/**
	 * Access the.
	 *
	 * @return the naming instance
	 */
	public Naming getNamingInstance() {
		return theServer.getNamingInstance();
	}

	/**
	 * Start.
	 *
	 * @throws Exception the exception
	 */
	public void start() throws Exception {
		log.debug("Begin start");
		// Set the java.rmi.server.hostname to the bind address if not set
		if (rmiBindAddress != null && System.getProperty("java.rmi.server.hostname") == null)
			System.setProperty("java.rmi.server.hostname", rmiBindAddress.getHostAddress());

		// Initialize the custom socket factories with any bind address
		initCustomSocketFactories();
		/*
		 * Only export server RMI interface and setup the listening socket if the port
		 * is >= 0 and an external proxy has not been installed. A value < 0 indicates
		 * no socket based access
		 */
		if (this.serverStub == null && port >= 0) {
			initJnpInvoker();
		}
		// Only bring up the bootstrap listener if there is a naming proxy
		if (this.serverStub != null) {
			initBootstrapListener();
		}
		log.debug("End start");
	}

	/**
	 * Stop.
	 */
	public void stop() {
		try {
			// Stop listener and unexport the RMI object
			if (serverSocket != null) {
				ServerSocket s = serverSocket;
				serverSocket = null;
				s.close();
			}
			if (isStubExported == true)
				UnicastRemoteObject.unexportObject(theServer.getNamingInstance(), false);
		} catch (Exception e) {
			log.error("Exception during shutdown", e);
		} finally {
			bootstrapURLs = null;
		}
	}

	/**
	 * This code should be moved to a seperate invoker in the org.jboss.naming
	 * package.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected void initJnpInvoker() throws IOException {
		log.debug("Creating NamingServer stub, theServer=" + theServer + ",rmiPort=" + rmiPort + ",clientSocketFactory="
				+ clientSocketFactory + ",serverSocketFactory=" + serverSocketFactory);
		Naming instance = getNamingInstance();
		Remote stub = UnicastRemoteObject.exportObject(instance, rmiPort, clientSocketFactory, serverSocketFactory);
		log.debug("NamingServer stub: " + stub);
		serverStub = new MarshalledObject(stub);
		isStubExported = true;
	}

	/**
	 * Bring up the bootstrap lookup port for obtaining the naming service proxy.
	 */
	protected void initBootstrapListener() {
		// Start listener
		try {
			// Get the default ServerSocketFactory is one was not specified
			if (jnpServerSocketFactory == null)
				jnpServerSocketFactory = ServerSocketFactory.getDefault();
			List<InetAddress> addresses = bindAddresses;
			if (addresses == null)
				addresses = Collections.singletonList(bindAddress);
			// Setup the exectuor with addresses + 1 threads
			if (lookupExector == null) {
				int count = addresses.size() + 1;
				log.debug("Using default newFixedThreadPool(" + count + ")");
				lookupExector = Executors.newFixedThreadPool(count, BootstrapThreadFactory.getInstance());
			}

			bootstrapURLs = new ArrayList<String>(addresses.size());
			for (InetAddress address : addresses) {
				serverSocket = jnpServerSocketFactory.createServerSocket(port, backlog, address);
				// If an anonymous port was specified get the actual port used
				if (port == 0)
					port = serverSocket.getLocalPort();

				bootstrapURLs.add(createBootstrapURL(serverSocket, port));

				String msg = "JNDI bootstrap JNP=" + address + ":" + port + ", RMI=" + address + ":" + rmiPort
						+ ", backlog=" + backlog;

				if (clientSocketFactory == null)
					msg += ", no client SocketFactory";
				else
					msg += ", Client SocketFactory=" + clientSocketFactory.toString();

				if (serverSocketFactory == null)
					msg += ", no server SocketFactory";
				else
					msg += ", Server SocketFactory=" + serverSocketFactory.toString();

				log.debug(msg);

				AcceptHandler handler = new AcceptHandler();
				lookupExector.execute(handler);
			}
		} catch (IOException e) {
			lookupListenerException = e;
			log.error("Could not start on port " + port, e);
			return;
		}

	}

	/**
	 * Init the clientSocketFactory, serverSocketFactory using the bind address.
	 */
	protected void initCustomSocketFactories() {
		// Use either the rmiBindAddress or bindAddress for the RMI service
		InetAddress addr = rmiBindAddress != null ? rmiBindAddress : bindAddress;

		if (clientSocketFactory != null && addr != null) {
			// See if the client socket supports setBindAddress(String)
			try {
				Class<?> csfClass = clientSocketFactory.getClass();
				Class<?>[] parameterTypes = { String.class };
				Method m = csfClass.getMethod("setBindAddress", parameterTypes);
				Object[] args = { addr.getHostAddress() };
				m.invoke(clientSocketFactory, args);
			} catch (NoSuchMethodException e) {
				log.warn("Socket factory does not support setBindAddress(String)");
				// Go with default address
			} catch (Exception e) {
				log.warn("Failed to setBindAddress=" + addr + " on socket factory", e);
				// Go with default address
			}
		}

		try {
			if (serverSocketFactory == null)
				serverSocketFactory = new DefaultSocketFactory(addr);
			else {
				if (addr != null) {
					// See if the server socket supports setBindAddress(String)
					try {
						Class<?> ssfClass = serverSocketFactory.getClass();
						Class<?>[] parameterTypes = { String.class };
						Method m = ssfClass.getMethod("setBindAddress", parameterTypes);
						Object[] args = { addr.getHostAddress() };
						m.invoke(serverSocketFactory, args);
					} catch (NoSuchMethodException e) {
						log.warn("Socket factory does not support setBindAddress(String)");
						// Go with default address
					} catch (Exception e) {
						log.warn("Failed to setBindAddress=" + addr + " on socket factory", e);
						// Go with default address
					}
				}
			}
		} catch (Exception e) {
			log.error("operation failed", e);
			serverSocketFactory = null;
		}
	}

	/**
	 * Creates the bootstrap URL.
	 *
	 * @param serverSocket the server socket
	 * @param port         the port
	 * @return the string
	 */
	private static String createBootstrapURL(ServerSocket serverSocket, int port) {
		if (serverSocket == null || serverSocket.getInetAddress() == null)
			return null;

		// Determine the bootstrap URL
		StringBuilder sb = new StringBuilder("jnp://");
		InetAddress addr = serverSocket.getInetAddress();
		if (addr instanceof Inet6Address) {
			sb.append('[');
			sb.append(addr.getHostAddress());
			sb.append(']');
		} else {
			sb.append(addr.getHostAddress());
		}
		sb.append(':');
		sb.append(port);
		return sb.toString();

	}

	/**
	 * The Class AcceptHandler.
	 */
	private class AcceptHandler implements Runnable {

		/**
		 * Run.
		 */
		public void run() {
			boolean debug = log.isDebugEnabled();
			while (serverSocket != null) {
				Socket socket = null;
				// Accept a connection
				try {
					if (debug)
						log.debug("Enter accept on: " + serverSocket);
					socket = serverSocket.accept();
					if (debug)
						log.debug("Accepted bootstrap client: " + socket);
					BootstrapRequestHandler handler = new BootstrapRequestHandler(socket);
					lookupExector.execute(handler);
				} catch (IOException e) {
					// Stopped by normal means
					if (serverSocket == null)
						return;
					log.error("Naming accept handler stopping", e);
				} catch (Throwable e) {
					log.error("Unexpected exception during accept", e);
				}
			}
		}
	}

	/**
	 * The Class BootstrapRequestHandler.
	 */
	private class BootstrapRequestHandler implements Runnable {

		/** The socket. */
		private Socket socket;

		/**
		 * Instantiates a new bootstrap request handler.
		 *
		 * @param socket the socket
		 */
		BootstrapRequestHandler(Socket socket) {
			this.socket = socket;
		}

		/**
		 * Run.
		 */
		public void run() {
			// Return the naming server stub
			try {
				if (log.isDebugEnabled())
					log.debug("BootstrapRequestHandler.run start");
				OutputStream os = socket.getOutputStream();
				ObjectOutputStream out = new ObjectOutputStream(os);
				out.writeObject(serverStub);
				out.close();
				if (log.isDebugEnabled())
					log.debug("BootstrapRequestHandler.run end");
			} catch (IOException ex) {
				log.debug("Error writing response to " + socket.getInetAddress(), ex);
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * A factory for creating BootstrapThread objects.
	 */
	private static class BootstrapThreadFactory implements ThreadFactory {

		/** The Constant tnumber. */
		private static final AtomicInteger tnumber = new AtomicInteger(1);

		/** The instance. */
		static BootstrapThreadFactory instance;

		/**
		 * Gets the single instance of BootstrapThreadFactory.
		 *
		 * @return single instance of BootstrapThreadFactory
		 */
		static synchronized ThreadFactory getInstance() {
			if (instance == null)
				instance = new BootstrapThreadFactory();
			return instance;
		}

		/**
		 * New thread.
		 *
		 * @param r the r
		 * @return the thread
		 */
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "Naming Bootstrap#" + tnumber.getAndIncrement());
			return t;
		}
	}
}
