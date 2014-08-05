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
class AllocationPointerPhantomRef extends PhantomReference<Object> {
	/** The address that the referenced AllocationPointer pointed to */
	private final long[][] address;
	
	/** A copy of the original addresses once the allocations have been cleared.
	 *   Copied for allocation tracking
	 */
	private long[] clearedAddresses = null;
	
	/**
	 * Creates a new AllocationPointerPhantomRef
	 * @param referent The AllocationPointer to be referenced
	 * @param address The actual reference to the {@link AllocationPointer}'s address long array.
	 * @param refQueue The reference queue to register with
	 */
	AllocationPointerPhantomRef(AllocationPointer referent, long[][] address, ReferenceQueue<Object> refQueue) {
		super(referent, refQueue);
		this.address = address;
	}
	
	/**
	 * Returns the cleared addresses
	 * @return the cleared addresses
	 */
	public long[] getClearedAddresses() {
		return clearedAddresses;
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.ref.Reference#clear()
	 */
	public void clear() {
		if(address!=null && address.length!=0) {
			if(address[0][0] > 0) {
				clearedAddresses = AllocationPointerOperations.free(address[0][0], true);
				address[0][0] = 0;
			} else {
				clearedAddresses = AllocationPointerOperations.EMPTY_LONG_ARR;
			}
		} else {
			clearedAddresses = AllocationPointerOperations.EMPTY_LONG_ARR;
		}
		super.clear();
	}
}