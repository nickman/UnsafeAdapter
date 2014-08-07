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
package test.com.heliosapm.unsafe.ap;

import org.junit.Before;

import com.heliosapm.unsafe.AllocationPointerOperations;
import com.heliosapm.unsafe.ReflectionHelper;

import test.com.heliosapm.unsafe.UnsafeAdapterConfiguration;

/**
 * <p>Title: MemTrackingAllocationPointerTest</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.ap.MemTrackingAllocationPointerTest</code></p>
 */
@UnsafeAdapterConfiguration(memTracking=true, apAllocSize=10, apManaged=true)
public class MemTrackingAllocationPointerTest extends BasicAllocationPointerTest {

	/**
	 * Creates a new MemTrackingAllocationPointerTest
	 */
	public MemTrackingAllocationPointerTest() {
		super();
	}
	
	/**
	 * Resets the allocation tracking before each test
	 */
	@Before
	public void clearAllocations() {
		ReflectionHelper.invoke(AllocationPointerOperations.class, "resetAllocations");
	}

}
