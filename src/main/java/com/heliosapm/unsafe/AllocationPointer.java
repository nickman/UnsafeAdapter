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
package com.heliosapm.unsafe;

import sun.misc.Unsafe;
import static com.heliosapm.unsafe.UnsafeAdapter.*;

/**
 * <p>Title: AllocationPointer</p>
 * <p>Description: Static methods for maniplulating an unsafe memory block containing an 
 * array of memory block addresses allocated for a common purpose 
 * and which will be deallocated when the referencing object becomes phantom reachable.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationPointer</code></p>
 */

public class AllocationPointer {
	/** A reference to the Unsafe */
	private static final Unsafe unsafe = theUNSAFE;
	
	/** A zero byte const */
	public static final byte ZERO_BYTE = 0;
	
	/** 
	 * The size of the buffer expressed as the number of adress slots 
	 * (not including the initial slots for the capacity and size) 
	 */
	public static final int ALLOC_SIZE = 7;
	
	/** The size of the memory block header in bytes (2 ints) */
	public static final int HEADER_SIZE = 8;
	
	/** 
	 * The size of the buffer expressed as the number of allocated bytes
	 * (not including the initial 8 bytes for the capacity and size) 
	 */
	public static final int ALLOC_MEM_SIZE = ALLOC_SIZE * ADDRESS_SIZE;
	
	
	
	/**
	 * Allocates a new AllocationPointer.
	 * @return the address of the created memory block. 
	 */
	public static long newAllocationPointer() {
		long address = unsafe.allocateMemory(ALLOC_SIZE * ADDRESS_SIZE + 8);
		unsafe.putInt(address, ALLOC_SIZE);
		unsafe.putInt(address + 4, 0);
		unsafe.setMemory(address + 8, ALLOC_MEM_SIZE, ZERO_BYTE);
		return address;
	}
	
	public static int assignSlot(final long address, final long newAddress) {
		if(isFull(address)) {
			extend();
		}
		int nextIndex = incrementSize(address);
		unsafe.putAddress(address + HEADER_SIZE + (nextIndex * ADDRESS_SIZE), newAddress);
		return nextIndex;
	}
	
	/**
	 * Returns the number of populated address slots in the AllocationPointer 
	 * resident at the passed memory address
	 * @param address the memory address of the AllocationPointer
	 * @return the number of populated slots
	 */
	public static int getSize(final long address) {
		return unsafe.getInt(address + 4);
	}
	
	/**
	 * Returns the address slot capacity of AllocationPointer 
	 * resident at the passed memory address
	 * @param address the memory address of the AllocationPointer
	 * @return the number of allocated slots
	 */
	public static int getCapacity(final long address) {
		return unsafe.getInt(address);
	}
	
	/**
	 * Determines if the AllocationPointer resident at the passed memory address is full.
	 * i.e. if the size equals the capacity.
	 * @param address the memory address of the AllocationPointer
	 * @return true if full, false otherwise
	 */
	public static boolean isFull(final long address) {
		return getSize(address) == getCapacity(address);
	}
	
	/**
	 * Returns the address at the specified index in the addressed AllocationPointer
	 * @param address the memory address of the AllocationPointer
	 * @param index the index of the AllocationPointer's address slots to retrieve
	 * @return the address as the specified index
	 */
	public static long getAddress(final long address, final int index) {
		if(index < 0) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		final int size = getSize(address);
		if(index > (size-1)) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		return unsafe.getAddress(address + HEADER_SIZE + (index * ADDRESS_SIZE));
	}
	
	/**
	 * Prints the summary state of this AllocationPointer
	 * @param address the memory address of the AllocationPointer
	 * @return a string describing the status of the AllocationPointer
	 */
	public static String print(final long address) {
		return String.format("AllocationPointer [size: %s, capacity: %s]", getSize(address), getCapacity(address));
	}
	
	/**
	 * Dumps the detailed state of this AllocationPointer
	 * @param address the memory address of the AllocationPointer
	 * @return a string describing the status and contents of the AllocationPointer
	 */
	public static String dump(final long address) {
		StringBuilder b = new StringBuilder(print(address));
		b.append("\n\tAddresses: [");
		final int size = getSize(address);
		if(size>0) {
			for(int i = 0; i < size; i++) {
				b.append(getAddress(address, i)).append(", ");
			}			
			b.deleteCharAt(b.length()-1);
			b.deleteCharAt(b.length()-1);
		}
		return b.append("]").toString();
	}
	
	
	/**
	 * Increments the size of the AllocationPointer to represent an added address.
	 * @param address The address of the AllocationPointer
	 * @return the index of the next open slot
	 */
	public static int incrementSize(final long address) {
		int nextSize = getSize(address); 
		unsafe.putInt(address + 4, nextSize + 1);
		return nextSize;
	}
	
	public static long getEndAddress(final long address) {
		final int size = getSize(address);
		return address + HEADER_SIZE + (size * ADDRESS_SIZE);
	}
	
	public static long getEndOffset(final long address) {
		return getEndAddress(address) - address;
	}
	
	public static long extend() {
		
		
		return -1;
	}
	
	private AllocationPointer() {}

}
