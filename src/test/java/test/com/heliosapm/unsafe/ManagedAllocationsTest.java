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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.heliosapm.unsafe.JMXHelper;
import com.heliosapm.unsafe.ReflectionHelper;
import com.heliosapm.unsafe.UnsafeAdapter;


/**
 * <p>Title: ManagedAllocationsTest</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.ManagedAllocationsTest</code></p>
 */

public class ManagedAllocationsTest extends BasicAllocationsTest {
	/** Indicates if the baseline has been set for this class */
	static final AtomicBoolean baselineSet = new AtomicBoolean(false);

	{
		if(baselineSet.compareAndSet(false, true)) {
			System.clearProperty(UnsafeAdapter.SAFE_MANAGER_PROP);
			System.setProperty(UnsafeAdapter.TRACK_ALLOCS_PROP, "true");
			ReflectionHelper.invoke(UnsafeAdapter.class, "reset");
			Assert.assertEquals("UnsafeAdapter is not in managed memory-tracking mode", true, UnsafeAdapter.getMemoryMBean().isTrackingEnabled());
			Assert.assertFalse("Adapter was not set to unsafe", UnsafeAdapter.isSafeAdapter());		
			Assert.assertTrue("Unsafe Adapter MBean Was Not Registered", JMXHelper.getDefaultMBeanServer().isRegistered(UnsafeAdapter.UNSAFE_MEM_OBJECT_NAME));
			Assert.assertFalse("Safe Adapter MBean Was Registered", JMXHelper.getDefaultMBeanServer().isRegistered(UnsafeAdapter.SAFE_MEM_OBJECT_NAME));
			log(UnsafeAdapter.printStatus());
		}
	}
	
	

	
}
