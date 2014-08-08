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
import java.util.Arrays;
import java.util.Comparator;

/**
 * <p>Title: AllocationPointerPhantomRef</p>
 * <p>Description: A {@link PhantomReference} implementation to wrap AllocationPointers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationPointer.AllocationPointerPhantomRef</code></p>
 */
class AllocationPointerPhantomRef extends PhantomReference<AllocationPointer> implements AllocationTracker {
	/** The address that the referenced AllocationPointer pointed to */
	private long address;

	
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
	 */
	AllocationPointerPhantomRef(AllocationPointer referent, long address, ReferenceQueue<? super AllocationPointer> refQueue) {
		super(referent, refQueue);
		this.address = address;		
		referent.setAttached();
	}
	
	/**
	 * Indicates if the referenced AllocationPointer is attached
	 * @return true if the referenced AllocationPointer is attached or has been cleared
	 */
	public boolean isAttached() {
		if(address==0) return false;
		return AllocationPointerOperations.isAttached(address);
	}
	
	/**
	 * Marks the referenced AllocationPointer as attached
	 */
	public void setAttached() {
		if(address!=0) AllocationPointerOperations.setAttached(address);
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		if(clearedAddresses!=null) return String.format("AllocationPointerPhantomRef %s", Arrays.deepToString(clearedAddresses));
		return "AllocationPointerPhantomRef []";
	}
	
	/**
	 * Returns the UnsafeAdapter provided reference id
	 * @return the UnsafeAdapter provided reference id
	 */
	public long getReferenceId() {
		return AllocationPointerOperations.getReferenceId(address);
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
		System.out.println("CLEARING PHANTOM...");
		if(address > 0) {
			clearedAddresses = AllocationPointerOperations.free(address, true);
			Arrays.sort(clearedAddresses, CSORT);
			address = 0;
		} else {
			clearedAddresses = AllocationPointerOperations.EMPTY_DLONG_ARR;
		}
		super.clear();
	}
	
	/** A long arr arr sorter */
	public static final ClearedComparable CSORT = new ClearedComparable(); 
	
	/**
	 * <p>Title: ClearedComparable</p>
	 * <p>Description: Sorts the cleared array by the address (long[<index>][0])</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.AllocationPointerPhantomRef.ClearedComparable</code></p>
	 */
	public static class ClearedComparable implements Comparator<long[]> {
		@Override
		public int compare(long[] a, long[] b) {
			if(a[0]==b[0]) return 0;
			return a[0] < b[0] ? -1 : 1;
		}
	}
	
	public static void main(String[] args) {
		long[][] arr = new long[][]{
				{7,8,9},
				{1,2,3},
				{4,5,6}
		};
		System.out.println(Arrays.deepToString(arr));
		Arrays.sort(arr, new ClearedComparable());
		System.out.println(Arrays.deepToString(arr));
		int index = Arrays.binarySearch(arr, new long[] {7}, CSORT);
		System.out.println("Index of 7:" + index);
	}
	
	
	// ////////////////////////////////////////////////////////////////////////////////
	//  TODO:  These ops must be implemented in AllocationPointerOperations.
	// ////////////////////////////////////////////////////////////////////////////////

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#add(long, long, long)
	 */
	@Override
	public void add(final long newAddress, final long allocationSize, final long alignmentOverhead) {
		AllocationPointerOperations.assignSlot(address, newAddress, allocationSize, alignmentOverhead);		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#getAllocationSize(long)
	 */
	@Override
	public long getAllocationSize(final long trackedAddress) {
		if(address!=0) return AllocationPointerOperations.getAllocationSizeOf(address, trackedAddress);
		if(clearedAddresses==null || clearedAddresses.length==0 || clearedAddresses[0].length <2) return 0;
		final int index = Arrays.binarySearch(clearedAddresses, new long[] {trackedAddress}, CSORT); 
		if(index < 0) return 0;		
		return clearedAddresses[index][2];
	}

	/**
	 * Returns the total allocation size in bytes for the addresses tracked
	 * @return the total allocation size in bytes for the addresses tracked
	 */
	public long getTotalAllocationSize() {
		if(address!=0) return AllocationPointerOperations.getTotalTrackedAllocationBytes(address);
		if(clearedAddresses!=null) {
			if(clearedAddresses.length==0 || clearedAddresses[0].length <2) return 0;
			long total = 0;
			for(int i = 0; i < clearedAddresses.length; i++) {
				total += clearedAddresses[i][1];
			}
			return total;
		}
		return 0;
	}

	/**
	 * {@inheritDoc}trackedAddress
	 * @see com.heliosapm.unsafe.AllocationTracker#getAlignmentOverhead(long)
	 */
	@Override
	public long getAlignmentOverhead(long trackedAddress) {
		if(address!=0) return AllocationPointerOperations.getAllocationSizeOf(address, trackedAddress);
		if(clearedAddresses==null || clearedAddresses.length==0 || clearedAddresses[0].length <3) return 0;
		final int index = Arrays.binarySearch(clearedAddresses, new long[] {trackedAddress}, CSORT); 
		if(index < 0) return 0;		
		return clearedAddresses[index][3];
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#clearAddress(long)
	 */
	@Override
	public void clearAddress(long addressToClear) {
		AllocationPointerOperations.clearAddress(address, AllocationPointerOperations.findIndexForAddress(address, addressToClear));
	}

}