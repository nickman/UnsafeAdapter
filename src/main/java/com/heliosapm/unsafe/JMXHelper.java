/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
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
 *
 */
package com.heliosapm.unsafe;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

/**
 * <p>Title: JMXHelper</p>
 * <p>Description: Static unchecked JMX utility methods</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.JMXHelper</code></p>
 */

public class JMXHelper {

	/** The system property specifying the JMX domain of the MBeanServer to register mbeans with */
	public static final String MBEANSERVER_PROP = "com.heliosapm.jmx.domain";
	
	/** The configured or default JMX domain of the default MBeanServer */
	public static final String JMX_DOMAIN = System.getProperty(MBEANSERVER_PROP, "DefaultDomain").trim();
	
	private static volatile MBeanServer DEFAULT_MBEANSERVER = null;
	private static final Object mBeanServerLock = new Object();
	
	/**
	 * Returns the default MBeanServer. If the configured one cannot be found, returns the platform MBeanServer
	 * @return the default MBeanServer
	 */
	public static MBeanServer getDefaultMBeanServer() {
		if(DEFAULT_MBEANSERVER==null) {
			synchronized(mBeanServerLock) {
				if(DEFAULT_MBEANSERVER==null) {
					if(JMX_DOMAIN.equals("DefaultDomain")) {
						DEFAULT_MBEANSERVER = ManagementFactory.getPlatformMBeanServer();
						return DEFAULT_MBEANSERVER;
					}
					for(MBeanServer server: MBeanServerFactory.findMBeanServer(null)) {
						String domain = server.getDefaultDomain();
						if(JMX_DOMAIN.equals(domain)) {
							DEFAULT_MBEANSERVER = server;
							return DEFAULT_MBEANSERVER;
						}
					}
					return ManagementFactory.getPlatformMBeanServer();
				}
			}
		}
		return DEFAULT_MBEANSERVER==null ? ManagementFactory.getPlatformMBeanServer() : DEFAULT_MBEANSERVER; 
	}
	
	/**
	 * Creates a new JMX ObjectName
	 * @param name The stringy to build the ObjectName from
	 * @return the new ObjectName
	 */
	public static ObjectName objectName(CharSequence name) {
		try {
			return new ObjectName(name.toString().trim());
		} catch (Exception ex) {
			throw new RuntimeException("Failed to create ObjectName for [" + name + "]", ex);
		}
	}
	
	/**
	 * Registers the passed object MBean in the default MBeanServer under the passed ObjectName
	 * @param mbean The object to register
	 * @param objectName The ObjectName to register under
	 */
	public static void registerMBean(Object mbean, ObjectName objectName) {
		try {
			getDefaultMBeanServer().registerMBean(mbean, objectName);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to to register MBean for ObjectName [" + objectName + "]", ex);
		}
	}
	
	/**
	 * Attempts to unregister the MBean registered in the default MBeanServer under the passed ObjectName
	 * @param objectName the ObjectName of the MBean to unregister
	 */
	public static void unregisterMBean(ObjectName objectName) {
		try {
			getDefaultMBeanServer().unregisterMBean(objectName);
		} catch (Exception ex) {
			/* No Op */
		}		
	}
	
	/**
	 * Forces registration of the passed object MBean in the default MBeanServer under the passed ObjectName.
	 * If an MBean is already registered, it is removed
	 * @param mbean The object to register
	 * @param objectName The ObjectName to register under
	 */
	public static void forceRegisterMBean(Object mbean, ObjectName objectName) {
		final MBeanServer server = getDefaultMBeanServer();
		try {
			if(server.isRegistered(objectName)) {
				server.unregisterMBean(objectName);
			}
		} catch (Exception ex) {
			/* No Op */
		}		
		try {
			server.registerMBean(mbean, objectName);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to to force register MBean for ObjectName [" + objectName + "]", ex);
		}
	}
	

}
