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

import static org.junit.Assume.assumeTrue;

import javax.management.MBeanServerInvocationHandler;

import org.junit.BeforeClass;
import org.junit.Test;

import com.heliosapm.unsafe.UnsafeAdapterOld;
import com.heliosapm.unsafe.MemoryMBean;

/**
 * <p>Title: ManagedAllocationsTest</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.ManagedAllocationsTest</code></p>
 */

public class ManagedAllocationsTest extends BaseTest {
	/** A proxy to the unsafe memory stats */
	protected static MemoryMBean unsafeMemory = null;
	/**
	 * Tests the assumption that 
	 * @throws Exception Thrown on any error
	 */
	@BeforeClass
	public static void testIfManaged() throws Exception {
		testManaged();
		unsafeMemory = MBeanServerInvocationHandler.newProxyInstance(PLATFORM_AGENT, UnsafeAdapterOld.UNSAFE_OBJECT_NAME, MemoryMBean.class, false);
		log("Acquired MemoryMBean");
	}
	
	private static void testManaged() throws Exception {
		assumeTrue("", PLATFORM_AGENT.isRegistered(UnsafeAdapterOld.UNSAFE_OBJECT_NAME));
	}
	
	/**
	 * Validates that the baseline memory is correctly set
	 */
	@Test
	public void testBaseline() {
		log("Baselines:  Allocs:%s,  Mem:%s", UnsafeAdapterOld.BASELINE_ALLOCS, UnsafeAdapterOld.BASELINE_MEM);
	}
	
	
}
