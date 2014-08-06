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
 * array of memory block keyAddresses allocated for a common purpose 
 * and which will be deallocated when the referencing object becomes phantom reachable.</p> 
 * <p>Structure of AllocationPointer Header:
 * <pre>
 *                4 bytes     4 bytes     8 bytes               1 byte
 *             +----------++----------++-----------------------++----+
 *             | capacity || size     || reference id          || dim|
 *             +----------++----------++-----------------------++----+
 *                 int         int           long                byte
 *             +----------------------------------------------------->
 * </pre></p>
 * <p>Structure of AllocationPointer:
 * <pre>
 *                 17 bytes            8 bytes       8 bytes          8 bytes     
 *             +-------------------++------------++------------    +-------------+
 *             |  Header           || Slot 1     || Slot 2     ::::| Slot n      |
 *             +-------------------++------------++------------    +-------------+
 *                                     long          long             long        
 *             +------------------------------------------------------------------>                       
 * </pre></p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationPointerOperations</code></p>
 * TODO:  AP should contain size and overhead pointers within the base structure so the whole triplet can be referred to by one long.
 */
@SuppressWarnings("restriction")
public class AllocationPointerOperations {
	/** A reference to the Unsafe */	
	private static final Unsafe unsafe;	
	/** A zero byte const */
	public static final byte ZERO_BYTE = 0;
	/** A one byte const */
	public static final byte ONE_BYTE = 1;
	
	/** The byte size of a long */
	public static final long LONG_SIZE = 8;
	/** The size of the memory block header in bytes (2 ints, 1 byte and 1 long) */
	public static final int HEADER_SIZE = 8 + 4 + 4 + 1;
	
	/** The capacity offfset */
	public static final int CAP_OFFSET = 0;
	/** The size offfset */
	public static final int SIZE_OFFSET = 4;
	/** The ref id offfset */
	public static final int REFID_OFFSET = 8;
	/** The dimension offfset */
	public static final int DIM_OFFSET = 16;
	
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
	 * @param memTracking Indicates if memory tracking is enabled
	 * @param memAlignment Indicates if cache-line memory alignment is enabled 
	 * @param refId The UnsafeAdapter's ref manager assigned reference id
	 * @return the address of the created memory block. 
	 */
	static final long newAllocationPointer(boolean memTracking, boolean memAlignment, long refId) {
		byte dim = ONE_BYTE;
		if(memTracking) {
			dim++;
			if(memAlignment) {
				dim++;
			}
		}
		long address = unsafe.allocateMemory(ALLOC_SIZE * ADDRESS_SIZE + HEADER_SIZE);
		unsafe.putInt(address, ALLOC_SIZE);
		unsafe.putInt(address + SIZE_OFFSET, 0);
		unsafe.putLong(address + REFID_OFFSET, refId);
		unsafe.putByte(address + DIM_OFFSET, dim);
		unsafe.setMemory(address + HEADER_SIZE, ALLOC_MEM_SIZE, ZERO_BYTE);
		return address;
	}
	
	/**
	 * Creates an internal AllocationPointer with no mem tracking, alignment tracking or ref id.
	 * @return the new internal AllocationPointer
	 */
	static final long newAllocationPointer() {
		return newAllocationPointer(false, false, 0L);
	}

	/**
	 * Returns the dimension of the referenced AllocationPointer where the dimensions are:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @param address The address of the AllocationPointer
	 * @return the dimension of the AllocationPointer
	 */
	public static final byte getDimension(final long address) {
		return unsafe.getByte(address + DIM_OFFSET);
	}
	/**
	 * Returns the reference id of the referenced AllocationPointer
	 * @param address The address of the AllocationPointer
	 * @return the reference id of the AllocationPointer
	 */
	public static final long getReferenceId(final long address) {
		return unsafe.getLong(address + REFID_OFFSET);
	}
	
//	/**
//	 * Assigns the passed address to the next available slot in the referenced AllocationPointerOperations
//	 * @param address The address of the allocation pointer memory block
//	 * @param newAddress The address to assign to the next slot
//	 * @return the new address of the memory block if it changed, or the original passed <code>address</code> if 
//	 * the memory block had an available empty address slot
//	 */
//	public static final long assignSlot(final long address, final long newAddress, final long trackingAddress, final long size) {
//		long addr = assignSlot(address, newAddress);
//		
//		return addr;
//		
//	}
	
	/**
	 * Finds the index of the passed address in the slots of the referenced AllocationPointer
	 * @param address The address of the AllocationPointer
	 * @param addressToFind The address to find the index for
	 * @return the index of the passed address, or -1 if the address was not found
	 * TODO: implement hashing function here to speed up finding an address
	 */
	public static final int findIndexForAddress(final long address, final long addressToFind) {
		final int sz = getSize(address);
		long indexedValue = -1;
		for(int i = 0; i < sz; i++) {
			indexedValue = AllocationPointerOperations.getAddress(address, i);
			if(addressToFind==indexedValue) return i;
		}
		return -1;
	}
	
	
	
	/**
	 * Assigns the passed address to the next available slot in the referenced AllocationPointer
	 * @param address The address array of athe allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @param newAddress The address to assign to the next slot
	 * @param size The size of the memory block that the newAddress points to
	 * @param alignmentOverhead the cache-line memory alignment overhead of the memory block that the newAddress points to
	 * @return the new address of the memory block if it changed, or the original passed <code>address</code> if 
	 * the memory block had an available empty address slot
	 */
	public static final long assignSlot(final long address[], final long newAddress, final long size, final long alignmentOverhead) {		
		final byte dim = getDimension(address[0]);
		if(isFull(address[0])) {			
			address[0] = extend(address[0]);
			if(dim>1 && address.length>1) {
				address[1] = extend(address[1]);
				if(dim>2 && address.length>2) {
					address[2] = extend(address[2]);
				} else {
					loge("Assign: AP Dimension and address length mismatch: [%s] vs [%s]", dim, address.length);
				}				
			} else {
				loge("Assign: AP Dimension and address length mismatch: [%s] vs [%s]", dim, address.length);
			}
		}
		final int nextIndex = incrementSize(address);
		final long offset = HEADER_SIZE + (nextIndex * ADDRESS_SIZE);
		unsafe.putAddress(address[0] + offset, newAddress);
		if(dim>1 && address.length>1) {
			unsafe.putAddress(address[1] + offset, size);
			if(dim>2 && address.length>2) unsafe.putAddress(address[2] + offset, alignmentOverhead);
		}
		return address[0];  // could be re-allocated.
	}
	
	/**
	 * Assigns the passed address to an existing slot in the referenced AllocationPointerOperations
	 * @param address The address array of athe allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @param newAddress The address to assign to the next slot
	 * @param size The size of the memory block that the newAddress points to
	 * @param alignmentOverhead the cache-line memory alignment overhead of the memory block that the newAddress points to
	 * @param index The index of the slot to write the address to
	 */
	public static final void reassignSlot(final long address[], final long newAddress, final long size, final long alignmentOverhead, int index) {		
		if(index>=getSize(address[0])) throw new IllegalArgumentException("Invalid index [" + index + "]. Size is [" + getSize(address[0]) + "]");
		unsafe.putAddress(address[0] + HEADER_SIZE + (index * ADDRESS_SIZE), newAddress);
		final byte dim = getDimension(address[0]);
		if(dim>1 && address.length>1) {
			unsafe.putAddress(address[1] + HEADER_SIZE + (index * ADDRESS_SIZE), size);
			if(dim>2 && address.length>2) {
				unsafe.putAddress(address[2] + HEADER_SIZE + (index * ADDRESS_SIZE), alignmentOverhead);
			} else {
				loge("Reassign: AP Dimension and address length mismatch: [%s] vs [%s]", dim, address.length);
			}				
		} else {
			loge("Reassign: AP Dimension and address length mismatch: [%s] vs [%s]", dim, address.length);
		}		
	}
	

	
	/**
	 * Returns the keyAddresses of the allocated memory blocks referenced by the passed address as an array of longs
	 * @param address The address of the allocation pointer memory block
	 * @return an array of keyAddresses
	 */
	public static final long[] getAddresses(final long address) {
		final int size = getSize(address);
		long[] addresses = new long[size];
		addresses[0] = address;
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
		return unsafe.getInt(address + SIZE_OFFSET);
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
	 * @param address the memory address of the AllocationPointer
	 * @return the index of the most recently assigned address slot
	 */
	public static final int getLastIndex(final long address) {
		final int size = getSize(address);
		return size<1 ? -1 : size-1;
	}
	
	/**
	 * Determines if the memory block resident at the passed memory address is full.
	 * i.e. if the size equals the capacity.
	 * @param address the memory address of the AllocationPointer
	 * @return true if full, false otherwise
	 */
	public static final boolean isFull(final long address) {
		return getSize(address) == getCapacity(address);
	}
	
	/**
	 * Returns the address at the specified index in the addressed AllocationPointer
	 * @param address the memory address of the AllocationPointer
	 * @param index the index of the AllocationPointer's address slots to retrieve
	 * @return the address as the specified index
	 */
	public static final long getAddress(final long address, final int index) {
		if(index < 0) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		final int size = getSize(address);
		if(index > (size-1)) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		return unsafe.getAddress(address + HEADER_SIZE + (index * ADDRESS_SIZE));
	}
	
	/**
	 * Returns a long array containing the address, allocation size and alignment overhead 
	 * at the specified index in the addressed AllocationPointer. If mem tracking and/or 
	 * alignment overhead are not enabled, the corresponding arrray slots will be zero
	 * @param address The address array of athe allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @param index the index of the AllocationPointer's address slots to retrieve
	 * @return the address as the specified index
	 */
	public static final long[] getTriplet(final long address[], final int index) {
		if(address==null || address.length==0) throw new IllegalArgumentException("Address array was null or zero length");
		final long[] triplet = new long[3];		
		triplet[0] = getAddress(address[0], index);
		final byte dim = getDimension(address[0]); 
		if(dim != address.length) {
			loge("Triplet: AP Dimension and address length mismatch: [%s] vs [%s]", dim, address.length);
		} else {
			if(dim>1) {
				triplet[1] = getAddress(address[1], index);
				if(dim>2) triplet[2] = getAddress(address[2], index);
			}
		}
		return triplet;
	}
	
	/**
	 * Returns a long array containing the address, allocation size and alignment overhead 
	 * at the specified index in the addressed AllocationPointer. If mem tracking and/or 
	 * alignment overhead are not enabled, the arrray will be correspondingly smaller.
	 * @param address The address array of the allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @param index the index of the AllocationPointer's address slots to retrieve
	 * @return the address as the specified index
	 */
	public static final long[] getSizedTriplet(final long address[], final int index) {
		if(address==null || address.length==0) throw new IllegalArgumentException("Address array was null or zero length");
		final byte dim = getDimension(address[0]);
		if(dim != address.length) {
			loge("Triplet: AP Dimension and address length mismatch: [%s] vs [%s]", dim, address.length);
			return EMPTY_LONG_ARR;
		}
		final long[] triplet = new long[dim];		
		triplet[0] = getAddress(address[0], index);
		if(dim>1) {
			triplet[1] = getAddress(address[1], index);
			if(dim>2) triplet[2] = getAddress(address[2], index);
		}
		return triplet;
	}
	
	
	/**
	 * Clears the address at the specified index in the addressed AllocationPointerOperations
	 * @param address The address array of the allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @param index the index of the AllocationPointerOperations's address slots to retrieve
	 */
	public static final void clearAddress(final long address[], final int index) {
		if(address==null || address.length==0) throw new IllegalArgumentException("Address array was null or zero length");
		final byte dim = getDimension(address[0]);
		final int size = getSize(address[0]);
		if(index > (size-1)) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		unsafe.putAddress(address[0] + HEADER_SIZE + (index * ADDRESS_SIZE), 0L);
		if(dim != address.length) {
			loge("Triplet: AP Dimension and address length mismatch: [%s] vs [%s]", dim, address.length);
		} else {
			if(dim>1) {
				unsafe.putAddress(address[1] + HEADER_SIZE + (index * ADDRESS_SIZE), 0L);
				if(dim>2) unsafe.putAddress(address[2] + HEADER_SIZE + (index * ADDRESS_SIZE), 0L);
			}
		}		
	}
	
	
	/**
	 * Frees the memory block at the passed address
	 * @param address the address of the memory block to free
	 */
	public static final void freeAddress(long address) {
		unsafe.freeMemory(address);
	}
	
	/**
	 * Prints the summary state of an AllocationPointer
	 * @param address The address array of the allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @return a string describing the status of the AllocationPointer
	 */
	public static final String print(final long address[]) {
		if(address==null || address.length==0) throw new IllegalArgumentException("Address array was null or zero length");
		final byte dim = getDimension(address[0]);
		StringBuilder b = new StringBuilder(String.format("AllocationPointer >> [size: %s, capacity: %s, byteSize: %s]", getSize(address[0]), getCapacity(address[0]), getEndOffset(address[0])));
		if(dim>1) {
			b.append(String.format("\n\tAllocation Sizes >> [size: %s, capacity: %s, byteSize: %s]", getSize(address[1]), getCapacity(address[1]), getEndOffset(address[1])));
			if(dim>2) b.append(String.format("\n\tAllocation Alignment Overheads >> [size: %s, capacity: %s, byteSize: %s]", getSize(address[2]), getCapacity(address[2]), getEndOffset(address[2])));
		}
		return b.toString();
	}
	
	/**
	 * Prints the detailed state of an AllocationPointer
	 * @param address The address array of the allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @return a string describing the details of the AllocationPointer
	 * FIXME: implement this
	 */
	public static final String dump(final long address[]) {
		if(address==null || address.length==0) throw new IllegalArgumentException("Address array was null or zero length");
		final byte dim = getDimension(address[0]);		
		StringBuilder b = new StringBuilder(print(address));
//		b.append("\n\tAddresses: [");
//		final int size = getSize(address);
//		if(size>0) {
//			for(int i = 0; i < size; i++) {
//				b.append(getAddress(address, i)).append(", ");
//			}			
//			b.deleteCharAt(b.length()-1);
//			b.deleteCharAt(b.length()-1);
//		}
//		return b.append("]").toString();
		return b.toString();
	}
	
	/** Empty long arr const */
	public static final long[] EMPTY_LONG_ARR = {};
	/** Empty long[] arr const */
	public static final long[][] EMPTY_DLONG_ARR = {{}};
	
	/**
	 * Frees all memory allocated within the referenced AllocationPointerOperations.
	 * No includePostMortem is returned.
	 * @param address The address array of the allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 */
	public static final void free(final long address[]) {
		free(address, false);
	}

	
	/**
	 * Frees all memory allocated within the referenced AllocationPointerOperations
	 * @param address The address array of the allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @param includePostMortem If true, the returned array will have all the formerly allocated addresses,
	 * otherwise will be zero length.
	 * @return The [possibly empty] array of addresses just deallocated
	 */
	public static final long[][] free(final long address[], final boolean includePostMortem) {
		if(address==null || address.length==0) throw new IllegalArgumentException("Address array was null or zero length");
		final byte dim = getDimension(address[0]);				
		final int size = getSize(address[0]);
		long[][] deadAddresses = includePostMortem ? size>0 ? new long[size][dim] : EMPTY_DLONG_ARR : EMPTY_DLONG_ARR;
		if(size>0) {
			if(includePostMortem) {
				for(int i = 0; i < size; i++) {
					long[] triplet = getSizedTriplet(address, i);
					deadAddresses[i] = triplet;
					if(triplet[0]>0) {
						unsafe.freeMemory(triplet[0]);
					}
				}				
			} else {
				for(int i = 0; i < size; i++) {
					long addr = getAddress(address[0], i);
					if(addr>0) {
						unsafe.freeMemory(addr);
					}
				}
			}
		}
		unsafe.freeMemory(address[0]);
		if(dim>1) {
			unsafe.freeMemory(address[1]);
			if(dim>2) unsafe.freeMemory(address[2]);
		}		
		return deadAddresses;
	}
	
	
	/**
	 * Increments the size of the AllocationPointer to represent an added address.
	 * @param addresses The addresses of the AllocationPointer memory blocks to increment the size for
	 * @return the index of the next open slot from the first address
	 */
	public static final int incrementSize(final long...addresses) {
		int nextSize  = 0;
		if(addresses!=null && addresses.length > 0) {
			nextSize = getSize(addresses[0]);
			unsafe.putInt(addresses[0] + SIZE_OFFSET, nextSize + 1);
			for(int i = 1; i < addresses.length; i++) {
				unsafe.putInt(addresses[i] + SIZE_OFFSET, nextSize + 1);
			}
		}
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
	
//	/**
//	 * Command line quickie test
//	 * @param args None
//	 */
//	public static void main(String[] args) {
//		log("AllocationPointerOperations Test: Address Size: %s", ADDRESS_SIZE);
//		int warmup = Integer.parseInt(args[0]) * (ALLOC_SIZE+10);
//		int actual = Integer.parseInt(args[1]) * (ALLOC_SIZE+10);
//		int innerLoops = 100;
//		for(int i = 0; i < warmup; i++) {
//			long address = newAllocationPointer();
//			for(int x = 0; x < innerLoops; x++) {
//				address = assignSlot(address, unsafe.allocateMemory(8));
//			}
//			free(address);			
//		}
//		log("Warmup Complete");
//		long start = System.currentTimeMillis();
//		for(int i = 0; i < actual; i++) {
//			long address = newAllocationPointer();
//			for(int x = 0; x < innerLoops; x++) {
//				address = assignSlot(address, unsafe.allocateMemory(8));
//			}
//			free(address);			
//		}
//		long end = System.currentTimeMillis();
//		log("Elpased time: %s ms", (end-start));
////		log("Activity:\n\tAllocations: %s\n\tReallocations: %s", initialAllocations.get(), reAllocations.get());
//		
//	}
	
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
