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

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

/**
 * <p>Title: AllocationPointerPhantomRef</p>
 * <p>Description: A {@link PhantomReference} implementation to wrap AllocationPointers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationPointer.AllocationPointerPhantomRef</code></p>
 */
class AllocationPointerPhantomRef extends PhantomReference<Object> implements AllocationTracker {
	/** The address that the referenced AllocationPointer pointed to */
	private final long address;
	
	/** The UnsafeAdapter provided reference id */
	private final long refId;

	
	/** 
	 * A copy of the original addresses once the allocations have been cleared.
	 * The format of the array is: <ul>
	 * 	<li><b>long[0]</b> : The cleared addresses</li>
	 *  <li><b>long[1]</b> : The size of the memory allocation formerly at the address (Optional)</li>
	 *  <li><b>long[2]</b> : The alignment overhead of the memory allocation formerly at the address (Optional)</li>
	 * </ul>
	 * The size and alignment overhead array entries may not be present, so the length of the returned array should be tested.
	 * If alignment overhead is enabled, size will be too, so possible lengths are: <ol>
	 * 	<li>The cleared addresses only.</li>
	 *  <li>The cleared addresses and the memory allocation sizes</li>
	 *  <li>The cleared addresses, the memory allocation sizes and the alignment overheads</li>
	 * </ol>
	 * Copied for allocation tracking
	 */
	private long[][] clearedAddresses = null;
	
	/**
	 * Creates a new AllocationPointerPhantomRef
	 * @param referent The AllocationPointer to be referenced
	 * @param address The actual reference to the {@link AllocationPointer}'s address long array.
	 * @param refQueue The reference queue to register with
	 * @param refId The UnsafeAdapter provided reference id
	 */
	AllocationPointerPhantomRef(AllocationPointer referent, long[][] address, ReferenceQueue<Object> refQueue, long refId) {
		super(referent, refQueue);
		this.address = address;
		this.refId = refId;
	}
	
	/**
	 * Returns the UnsafeAdapter provided reference id
	 * @return the UnsafeAdapter provided reference id
	 */
	public long getRefId() {
		return refId;
	}
	
	/**
	 * Returns the cleared addresses (and possibly the memory allocation sizes and alignment overheads)</li>
	 * @return the cleared addresses
	 */
	public long[][] getClearedAddresses() {
		return clearedAddresses;
	}
	
	/**
	 * <p>Clears the referenced AllocationPointer and associated tracking subsidiaries and frees all the allocated memory.
	 * {@inheritDoc}
	 * @see java.lang.ref.Reference#clear()
	 */
	public void clear() {
		if(address[0][0] > 0) {
			clearedAddresses = AllocationPointerOperations.free(address[0], true);
			address[0][0] = 0;
		} else {
			clearedAddresses = AllocationPointerOperations.EMPTY_DLONG_ARR;
		}
		super.clear();
	}
	
	
	// ////////////////////////////////////////////////////////////////
	//  TODO:  These ops must be implemented in Ops.
	// ////////////////////////////////////////////////////////////////

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#add(long, long, long)
	 */
	@Override
	public void add(long newAddress, long allocationSize, long alignmentOverhead) {
		AllocationPointerOperations.assignSlot(address[0], newAddress, allocationSize, alignmentOverhead);		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#getAllocationSize(long)
	 */
	@Override
	public long getAllocationSize(long address) {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#getAlignmentOverhead(long)
	 */
	@Override
	public long getAlignmentOverhead(long address) {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#clearAddress(long)
	 */
	@Override
	public void clearAddress(long address) {
		// TODO Auto-generated method stub
		
	}

}