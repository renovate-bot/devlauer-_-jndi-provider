/*
* JBoss, Home of Professional Open Source
* Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package de.elnarion.jndi.test.support;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientSocketFactory implements RMIClientSocketFactory, Serializable {
	static final long serialVersionUID = 1L;

	static final Logger LOGGER = LoggerFactory.getLogger(ClientSocketFactory.class);

	public static boolean created;

	public Socket createSocket(String host, int port) throws IOException {
		Socket clientSocket = new Socket(host, port);
		LOGGER.info("createSocket -> " + clientSocket);
		created = true;
		return clientSocket;
	}
}