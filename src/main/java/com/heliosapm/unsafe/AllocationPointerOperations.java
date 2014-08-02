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

import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * <p>Title: AllocationPointerOperations</p>
 * <p>Description: Static methods for maniplulating an unsafe memory block containing an 
 * array of memory block addresses allocated for a common purpose 
 * and which will be deallocated when the referencing object becomes phantom reachable.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationPointerOperations</code></p>
 */
@SuppressWarnings("restriction")
public class AllocationPointerOperations {
	/** A reference to the Unsafe */	
	private static final Unsafe unsafe;	
	/** A zero byte const */
	public static final byte ZERO_BYTE = 0;
	/** The byte size of a long */
	public static final long LONG_SIZE = 8;
	/** The size of the memory block header in bytes (2 ints) */
	public static final int HEADER_SIZE = 8;
	
	/** The system prop to override the allocation size */
	public static final String ALLOC_SIZE_PROP = "allocation.pointer.alloc.size";	
	
	static {
		try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);			
            ADDRESS_SIZE = unsafe.addressSize();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to access sun.misc.Unsafe.theUnsafe", ex);
		}
		OBJECTS_OFFSET = unsafe.arrayBaseOffset(Object[].class);
		LONG_ARRAY_OFFSET = unsafe.arrayBaseOffset(long[].class);
		INT_ARRAY_OFFSET = unsafe.arrayBaseOffset(int[].class);
		int tmp = -1;
		try { 
			tmp = Integer.parseInt(System.getProperty(ALLOC_SIZE_PROP, "1"));
		} catch (Exception ex) {
			tmp = 1;
		}
		ALLOC_SIZE = tmp;
		
	}
	
	/** The size of a memory pointer in bytes */
	public static final int ADDRESS_SIZE;	
	/** 
	 * The size of the buffer expressed as the number of adress slots 
	 * (not including the initial slots for the capacity and size) 
	 */
	public static final int ALLOC_SIZE;	
	/** 
	 * The size of the buffer expressed as the number of allocated bytes
	 * (not including the initial 8 bytes for the capacity and size) 
	 */
	public static final int ALLOC_MEM_SIZE = ALLOC_SIZE * ADDRESS_SIZE;	
	/** The size of an <b><code>Object[]</code></b> array offset */
    public static final long OBJECTS_OFFSET;
    /** The size of a <b><code>long[]</code></b> array offset */
    public final static int LONG_ARRAY_OFFSET;
    /** The size of a <b><code>int[]</code></b> array offset */
    public final static int INT_ARRAY_OFFSET;
    
	/**
	 * Allocates a new AllocationPointerOperations.
	 * @return the address of the created memory block. 
	 */
	public static final long newAllocationPointer() {
		long address = unsafe.allocateMemory(ALLOC_SIZE * ADDRESS_SIZE + 8);
		unsafe.putInt(address, ALLOC_SIZE);
		unsafe.putInt(address + 4, 0);
		unsafe.setMemory(address + 8, ALLOC_MEM_SIZE, ZERO_BYTE);
		return address;
	}
	
	
	/**
	 * Assigns the passed address to the next available slot in the referenced AllocationPointerOperations
	 * @param address The address of the allocation pointer memory block
	 * @param newAddress The address to assign to the next slot
	 * @return the new address of the memory block if it changed, or the original passed <code>address</code> if 
	 * the memory block had an available empty address slot
	 */
	public static final long assignSlot(final long address, final long newAddress) {
		long addr = address;
		if(isFull(address)) {
			addr = extend(address);
		}
		final int nextIndex = incrementSize(addr);
		final long offset = HEADER_SIZE + (nextIndex * ADDRESS_SIZE);
		unsafe.putAddress(addr + offset, newAddress);
		return addr;
	}
	
	/**
	 * Assigns the passed address to the next available slot in the referenced AllocationPointerOperations
	 * @param address The address of the allocation pointer memory block
	 * @param newAddress The address to assign to the next slot
	 * @return the new address of the memory block if it changed, or the original passed <code>address</code> if 
	 * the memory block had an available empty address slot
	 */
	public static final long assignSlot(final long address, final long newAddress, final long trackingAddress, final long size) {
		long addr = assignSlot(address, newAddress);
		
		return addr;
		
	}
	
	
	/**
	 * Assigns the passed address to an existing slot in the referenced AllocationPointerOperations
	 * @param address The address of the allocation pointer memory block
	 * @param newAddress The address to assign to the next slot
	 * @param index The index of the slot to write the address to
	 */
	public static final void reassignSlot(final long address, final long newAddress, int index) {		
		if(index>=getSize(address)) throw new IllegalArgumentException("Invalid index [" + index + "]. Size is [" + getSize(address) + "]");
		unsafe.putAddress(address + HEADER_SIZE + (index * ADDRESS_SIZE), newAddress);		
	}
	
	/**
	 * Returns the addresses of the allocated memory blocks referenced by the passed address as an array of longs
	 * @param address The address of the allocation pointer memory block
	 * @return an array of addresses
	 */
	public static final long[] getAddresses(final long address) {
		final int size = getSize(address);
		long[] addresses = new long[size];
		for(int i = 0; i < size; i++) {
			addresses[i] = getAddress(address, i);
		}		
		return addresses;
	}
	
	/**
     * Returns the address of the passed object
     * @param obj The object to get the address of 
     * @return the address of the passed object or zero if the passed object is null
     */
    public static final long getAddressOf(Object obj) {
    	if(obj==null) return 0;
    	Object[] array = new Object[] {obj};
    	return ADDRESS_SIZE==4 ? unsafe.getInt(array, OBJECTS_OFFSET) : unsafe.getLong(array, OBJECTS_OFFSET);
    }		

	
	/**
	 * Returns the number of populated address slots in the AllocationPointerOperations 
	 * resident at the passed memory address
	 * @param address the memory address of the AllocationPointerOperations
	 * @return the number of populated slots
	 */
	public static final int getSize(final long address) {
		return unsafe.getInt(address + 4);
	}
	
	/**
	 * Returns the address slot capacity of AllocationPointerOperations 
	 * resident at the passed memory address
	 * @param address the memory address of the AllocationPointerOperations
	 * @return the number of allocated slots
	 */
	public static final int getCapacity(final long address) {
		return unsafe.getInt(address);
	}
	
	/**
	 * Returns the index of the most recently assigned address slot, or -1 if none are assigned
	 * @param address the memory address of the AllocationPointerOperations
	 * @return the index of the most recently assigned address slot
	 */
	public static final int getLastIndex(final long address) {
		final int size = getSize(address);
		return size<1 ? -1 : size-1;
	}
	
	/**
	 * Determines if the memory block resident at the passed memory address is full.
	 * i.e. if the size equals the capacity.
	 * @param address the memory address of the AllocationPointerOperations
	 * @return true if full, false otherwise
	 */
	public static final boolean isFull(final long address) {
		return getSize(address) == getCapacity(address);
	}
	
	/**
	 * Returns the address at the specified index in the addressed AllocationPointerOperations
	 * @param address the memory address of the AllocationPointerOperations
	 * @param index the index of the AllocationPointerOperations's address slots to retrieve
	 * @return the address as the specified index
	 */
	public static final long getAddress(final long address, final int index) {
		if(index < 0) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		final int size = getSize(address);
		if(index > (size-1)) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		return unsafe.getAddress(address + HEADER_SIZE + (index * ADDRESS_SIZE));
	}
	
	/**
	 * Clears the address at the specified index in the addressed AllocationPointerOperations
	 * @param address the memory address of the AllocationPointerOperations
	 * @param index the index of the AllocationPointerOperations's address slots to retrieve
	 */
	@SuppressWarnings("unused")
	private static final void clearAddress(final long address, final int index) {
		if(index < 0) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		final int size = getSize(address);
		if(index > (size-1)) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		unsafe.putAddress(address + HEADER_SIZE + (index * ADDRESS_SIZE), 0L);
	}
	
	
	/**
	 * Prints the summary state of this AllocationPointerOperations
	 * @param address the memory address of the AllocationPointerOperations
	 * @return a string describing the status of the AllocationPointerOperations
	 */
	public static final String print(final long address) {
		return String.format("AllocationPointerOperations >> [size: %s, capacity: %s, byteSize: %s]", getSize(address), getCapacity(address), getEndOffset(address));
	}
	
	/**
	 * Dumps the detailed state of the referenced AllocationPointerOperations
	 * @param address the memory address of the AllocationPointerOperations
	 * @return a string describing the status and contents of the AllocationPointerOperations
	 */
	public static final String dump(final long address) {
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
	 * Frees all memory allocated within the referenced AllocationPointerOperations
	 * @param address the memory address of the AllocationPointerOperations
	 */
	public static final void free(final long address) {
		final int size = getSize(address);
		if(size>0) {
			for(int i = 0; i < size; i++) {
				long addr = getAddress(address, i);
				if(addr>0) {
					unsafe.freeMemory(addr);
				}
			}
		}
		unsafe.freeMemory(address);
	}
	
	
	/**
	 * Increments the size of the AllocationPointerOperations to represent an added address.
	 * @param address The address of the allocation pointer memory block
	 * @return the index of the next open slot
	 */
	public static final int incrementSize(final long address) {
		int nextSize = getSize(address); 
		unsafe.putInt(address + 4, nextSize + 1);
		return nextSize;
	}
	
	/**
	 * Returns the address of the last byte of the referenced AllocationPointerOperations
	 * @param address The address of the allocation pointer memory block
	 * @return the end address of the AllocationPointerOperations
	 */
	public static final long getEndAddress(final long address) {
		final int size = getCapacity(address);
		return address + HEADER_SIZE + (size * ADDRESS_SIZE);
	}
	
	/**
	 * Returns the total byte size of the referenced AllocationPointerOperations
	 * @param address The address of the allocation pointer memory block
	 * @return the total byte size of the AllocationPointerOperations
	 */
	public static final long getEndOffset(final long address) {
		return getEndAddress(address) - address;
	}
	
	/**
	 * Extends the capacity of the referenced memory block
	 * @param address The address of the allocation pointer memory block
	 * @return The new address of the memory block
	 */
	public static final long extend(final long address) {
		final long endOffset = getEndOffset(address);
		final int currentCap = getCapacity(address);
//		log("Reallocating from %s ---- to -----> %s bytes", endOffset,  endOffset + ALLOC_MEM_SIZE);
		final long newAddress = unsafe.reallocateMemory(address, endOffset + ALLOC_MEM_SIZE);
//		log("Initializing new slot space of %s bytes at offset %s", ALLOC_MEM_SIZE, endOffset);
		unsafe.setMemory(newAddress + endOffset, ALLOC_MEM_SIZE, ZERO_BYTE);
		unsafe.putInt(newAddress, currentCap + ALLOC_SIZE);
//		reAllocations.incrementAndGet();
		return newAddress;
	}
	
	private AllocationPointerOperations() {}
	
	/**
	 * Command line quickie test
	 * @param args None
	 */
	public static void main(String[] args) {
		log("AllocationPointerOperations Test: Address Size: %s", ADDRESS_SIZE);
		int warmup = Integer.parseInt(args[0]) * (ALLOC_SIZE+10);
		int actual = Integer.parseInt(args[1]) * (ALLOC_SIZE+10);
		int innerLoops = 100;
		for(int i = 0; i < warmup; i++) {
			long address = newAllocationPointer();
			for(int x = 0; x < innerLoops; x++) {
				address = assignSlot(address, unsafe.allocateMemory(8));
			}
			free(address);			
		}
		log("Warmup Complete");
		long start = System.currentTimeMillis();
		for(int i = 0; i < actual; i++) {
			long address = newAllocationPointer();
			for(int x = 0; x < innerLoops; x++) {
				address = assignSlot(address, unsafe.allocateMemory(8));
			}
			free(address);			
		}
		long end = System.currentTimeMillis();
		log("Elpased time: %s ms", (end-start));
//		log("Activity:\n\tAllocations: %s\n\tReallocations: %s", initialAllocations.get(), reAllocations.get());
		
	}
	
	/**
	 * Low maintenance out logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	static final void log(Object fmt, Object...args) {
		System.out.println(String.format("[AllocationPointerOperations] " + fmt.toString(), args));
	}
	
	/**
	 * Low maintenance err logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	static final void loge(Object fmt, Object...args) {
		System.err.println(String.format("[AllocationPointerOperations] " + fmt.toString(), args));
		if(args!=null && args.length>0 && args[args.length-1] instanceof Throwable) {
			System.err.println("Stack trace follows....");
			((Throwable)args[args.length-1]).printStackTrace(System.err);			
		}
	}
	

}
