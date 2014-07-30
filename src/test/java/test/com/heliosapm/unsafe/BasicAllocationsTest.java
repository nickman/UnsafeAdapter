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
package test.com.heliosapm.unsafe;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.heliosapm.unsafe.JMXHelper;
import com.heliosapm.unsafe.ReflectionHelper;
import com.heliosapm.unsafe.UnsafeAdapter;
import com.heliosapm.unsafe.UnsafeAdapterOld;

/**
 * <p>Title: BasicAllocationsTest</p>
 * <p>Description: Basic allocations test case</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.BasicAllocationsTest</code></p>
 */
@SuppressWarnings("restriction")
public class BasicAllocationsTest extends BaseTest {
	
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
	 * Tests a long allocation, write, read and deallocation
	 * @throws Exception thrown on any error
	 */
	
	@Test
	public void testAllocatedLong() throws Exception {
		final long address = UnsafeAdapter.allocateMemory(8);
		try {
			long value = nextPosLong();
			UnsafeAdapter.putLong(address, value);
			Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getLong(address));
			Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getLong(address));	
			value = nextPosLong();
			UnsafeAdapter.putLongVolatile(address, value);
			Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getLong(address));
			Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getLong(address));	
			if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
				Assert.assertEquals("Mem Total Alloc was unexpected", 8, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
			} else {
				Assert.assertEquals("Mem Total Alloc was unexpected", -1, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
			}			
		} finally {
			UnsafeAdapter.freeMemory(address);
			if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
				Assert.assertEquals("Mem Total Alloc was unexpected", 0, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
			} else {
				Assert.assertEquals("Mem Total Alloc was unexpected", -1, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
			}
		}
	}
	
}
