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
package com.heliosapm.unsafe;

/**
 * <p>Title: AllocationTracker</p>
 * <p>Description: Defines a class that can track memory block allocation sizes and alignment overhead</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationTracker</code></p>
 */

public interface AllocationTracker {
	/**
	 * Adds an allocation
	 * @param address The address of the allocation
	 * @param allocationSize The size of the allocation in bytes
	 * @param alignmentOverhead The alignment overhead in bytes
	 */
	public void add(long address, long allocationSize, long alignmentOverhead);
	
	/**
	 * Returns the allocation size of the memory block at the given address
	 * @param address The address of the allocation
	 * @return the allocation size
	 */
	public long getAllocationSize(long address);
	
	/**
	 * Returns the alignment overhead of the memory block at the given address
	 * @param address The address of the allocation
	 * @return the alignment overhead
	 */
	public long getAlignmentOverhead(long address);
	
	/**
	 * Clears the size and alignment overhead for the passed address
	 * @param address The address of the allocation
	 */
	public void clearAddress(long address);
}
