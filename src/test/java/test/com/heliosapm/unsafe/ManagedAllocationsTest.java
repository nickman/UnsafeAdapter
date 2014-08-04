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

import org.junit.BeforeClass;

/**
 * <p>Title: ManagedAllocationsTest</p>
 * <p>Description: The same tests executed in {@link BasicAllocationsTest} but with mem-tracking enabled.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.ManagedAllocationsTest</code></p>
 */
@UnsafeAdapterConfiguration(memTracking=true)
public class ManagedAllocationsTest extends BasicAllocationsTest {
	// Enables memory tracking 
//	{
//		UnsafeAdapterConfigurator.setConfiguration(this);
//	}
	/**
	 * Enables memory tracking 
	 */
	@BeforeClass
	public static void enableMemTracking() {
		UnsafeAdapterConfigurator.setConfiguration(ManagedAllocationsTest.class);
	}
}
