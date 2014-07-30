/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2014, Helios Development Group and individual contributors
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
package test.com.heliosapm.unsafe;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.heliosapm.unsafe.JMXHelper;
import com.heliosapm.unsafe.ReflectionHelper;
import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: AdapterResetTest</p>
 * <p>Description: Tests the adapter reset functionality</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.AdapterResetTest</code></p>
 */

public class AdapterResetTest extends BaseTest {
	
	/**
	 * Sets the baseline state (unsafe) of the adapter for this test
	 */
	@BeforeClass
	public static void baselineState() {		
		System.clearProperty(UnsafeAdapter.SAFE_MANAGER_PROP);
		ReflectionHelper.invoke(UnsafeAdapter.class, "reset");
		Assert.assertFalse("Adapter was not set to unsafe", UnsafeAdapter.isSafeAdapter());		
		Assert.assertTrue("Unsafe Adapter MBean Was Not Registered", JMXHelper.getDefaultMBeanServer().isRegistered(UnsafeAdapter.UNSAFE_MEM_OBJECT_NAME));
		Assert.assertFalse("Safe Adapter MBean Was Registered", JMXHelper.getDefaultMBeanServer().isRegistered(UnsafeAdapter.SAFE_MEM_OBJECT_NAME));
	}


	/**
	 * Tests a reset then switch to safe
	 */
	@Test
	public void switchToSafe() {
		System.setProperty(UnsafeAdapter.SAFE_MANAGER_PROP, "true");
		ReflectionHelper.invoke(UnsafeAdapter.class, "reset");
		log("MemoryMBean: [%s]", UnsafeAdapter.getMemoryMBean().getClass().getName());
		Assert.assertTrue("Adapter was not set to safe", UnsafeAdapter.isSafeAdapter());		
		Assert.assertTrue("Safe Adapter MBean Was Not Registered", JMXHelper.getDefaultMBeanServer().isRegistered(UnsafeAdapter.SAFE_MEM_OBJECT_NAME));
		Assert.assertFalse("Unsafe Adapter MBean Was Registered", JMXHelper.getDefaultMBeanServer().isRegistered(UnsafeAdapter.UNSAFE_MEM_OBJECT_NAME));

	}
	
	/**
	 * Tests a reset then switch to unsafe
	 */
	@Test	
	public void switchToUnsafe() {		
		System.clearProperty(UnsafeAdapter.SAFE_MANAGER_PROP);
		ReflectionHelper.invoke(UnsafeAdapter.class, "reset");
		Assert.assertFalse("Adapter was not set to unsafe", UnsafeAdapter.isSafeAdapter());		
		Assert.assertTrue("Unsafe Adapter MBean Was Not Registered", JMXHelper.getDefaultMBeanServer().isRegistered(UnsafeAdapter.UNSAFE_MEM_OBJECT_NAME));
		Assert.assertFalse("Safe Adapter MBean Was Registered", JMXHelper.getDefaultMBeanServer().isRegistered(UnsafeAdapter.SAFE_MEM_OBJECT_NAME));
	}
	
}
