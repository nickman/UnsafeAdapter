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

}
