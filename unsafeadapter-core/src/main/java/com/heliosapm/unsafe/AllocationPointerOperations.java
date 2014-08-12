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
import java.util.concurrent.atomic.AtomicLong;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

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
 * <p><b>NOTE !</b>  Managed memory is for TESTING only !  It will be SLOW ! </p>
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
	/** A two byte const */
	public static final byte TWO_BYTE = 2;
	
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
	
	/** The default allocation size */
	public static final int DEFAULT_ALLOC_SIZE_PROP = 1;
	
	/** The system prop to override the allocation size */
	public static final String ALLOC_SIZE_PROP = "allocation.pointer.alloc.size";	
	/** The system prop to override the raw memory allocation/reallocation/deallocation with managed operations using {@link UnsafeAdapter} */
	public static final String MANAGED_ALLOC_PROP = "allocation.pointer.managed";	
	/** The default override the raw memory allocation/reallocation/deallocation with managed operations using {@link UnsafeAdapter} */
	public static final boolean DEFAULT_MANAGED_ALLOC = false;	
	
	/** The managed memory allocation flag */
	public static final boolean MANAGED_ALLOC;	
	
	
	/** The size of a memory pointer in bytes */
	public static final int ADDRESS_SIZE;	
	/** 
	 * The size of the buffer expressed as the number of adress slots 
	 * (not including the initial slots for the capacity and size) 
	 */
	public static final int ALLOC_SIZE;	
//	/** 
//	 * The size of the buffer expressed as the number of allocated bytes
//	 * (not including the initial 8 bytes for the capacity and size) 
//	 */
//	public static final int ALLOC_MEM_SIZE;	
	/** The size of an <b><code>Object[]</code></b> array offset */
    public static final long OBJECTS_OFFSET;
    /** The size of a <b><code>long[]</code></b> array offset */
    public final static int LONG_ARRAY_OFFSET;
    /** The size of a <b><code>int[]</code></b> array offset */
    public final static int INT_ARRAY_OFFSET;
    
    /** A map of allocation sizes and overhead keyed by address */
    private static final NonBlockingHashMapLong<long[]> allocations;
    /** A counter of total allocation size */
    private static final AtomicLong totalAllocated;
    /** A counter of total alignment overhead */
    private static final AtomicLong totalOverhead;   
    
	/** Provides refIfds since we're not using a refMgr */
	private static final AtomicLong refIdSerial = new AtomicLong(0L);

    
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
			tmp = Integer.parseInt(System.getProperty(ALLOC_SIZE_PROP, "" + DEFAULT_ALLOC_SIZE_PROP));
		} catch (Exception ex) {
			tmp = 1;
		}
		ALLOC_SIZE = tmp;
//		ALLOC_MEM_SIZE = ALLOC_SIZE * ADDRESS_SIZE;
		MANAGED_ALLOC = System.getProperties().containsKey(MANAGED_ALLOC_PROP);
		if(MANAGED_ALLOC) {
			totalAllocated = new AtomicLong(0L); 
			totalOverhead = new AtomicLong(0L);
			allocations = new NonBlockingHashMapLong<long[]>(1024, true);
		} else {
			totalAllocated = null;  
			totalOverhead = null;
			allocations = null;
		}
	}
	
	/**
	 * Ingests a {@link Deallocatable} and assigns it a ref id
	 * @param rootAddress The root address of the AllocationPointer
	 * @param dealloc the Deallocatable to ingest
	 */
	static final void ingest(final long rootAddress, Deallocatable dealloc) {
		if(dealloc==null) throw new IllegalArgumentException("The passed Deallocatable was null");
		final long myRefId = getReferenceId(rootAddress);
		long dRefId = dealloc.getReferenceId();
		if(dRefId != 0 && dRefId != myRefId) throw new IllegalArgumentException("The passed Deallocatable has already been assigned a RefId");
		if(dRefId == myRefId) return;
		if(dealloc.getAddresses()==null || dealloc.getAddresses().length==0) throw new IllegalStateException("The passed Deallocatable's getAddresses() op returned a null or zero length address array");
		dealloc.setReferenceId(myRefId);
		for(long address: dealloc.getAddresses()[0]) {
			if(address > 0) {
				assignSlot(rootAddress, address, 0L, 0L);
			}
		}
	}
	
	/**
	 * <p>Creates a new AllocationPointer which is <b>NOT</b> hooked up to a RefManager to auto clear the reference.
	 * <b>Only</b> use this when you're going to call {@link AllocationPointer#free()} yourself, otherwise you will 
	 * be leaking native memory allocations all over the place.</p>
	 * <p>The preferred way to create an AllocationPointer (i.e. one which will be cleared when the instance becomes phantom reachable)
	 * is: <pre>
	 * 			AllocationReferenceManager refMgr = new AllocationReferenceManager(TRUE, FALSE);
	 * 			AllocationPointer ap = refMgr.newAllocationPointer();
	 *  </pre></p>
	 * <p>This call is intended as a testing hook or for special circumstances</p>
	 * @param memTracking Indicates if memory tracking is enabled
	 * @param memAlignment Indicates if cache-line memory alignment is enabled 
	 * @return a unattached AllocationPointer
	 */
	public static final AllocationPointer newAllocationPointerInstance(boolean memTracking, boolean memAlignment) {
		return new AllocationPointer(memTracking, memAlignment, refIdSerial.incrementAndGet());		
	}
    
    
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
		long address = _allocateMemory(dim * ADDRESS_SIZE);
		unsafe.putAddress(address, _allocateMemory(ALLOC_SIZE * ADDRESS_SIZE + HEADER_SIZE));
		initAllocationPointer(getAddressOfDim(address, ZERO_BYTE), refId, dim);
		if(dim>1) {
			unsafe.putAddress(address + ADDRESS_SIZE, _allocateMemory(ALLOC_SIZE * ADDRESS_SIZE + HEADER_SIZE));
			initAllocationPointer(getAddressOfDim(address, ONE_BYTE), 0L, ZERO_BYTE);
			if(dim>2) {
				unsafe.putAddress(address + ADDRESS_SIZE + ADDRESS_SIZE, _allocateMemory(ALLOC_SIZE * ADDRESS_SIZE + HEADER_SIZE));
				initAllocationPointer(getAddressOfDim(address, TWO_BYTE), 0L, ZERO_BYTE);
			}
		}
		return address;
	}
	
	/**
	 * Allocates a memory block and returns the address, recording the allocation size is mem tracking is on
	 * @param size The size of the memory block to allocate
	 * @return the address of the allocated memory block
	 */
	private static final long _allocateMemory(final long size) {
		long addr = unsafe.allocateMemory(size);
		if(MANAGED_ALLOC) {
			allocations.put(addr, new long[]{size});
			totalAllocated.addAndGet(size);
		}
		return addr;
	}
	
	/**
	 * Reallocates a memory block and returns the address of the new block, recording the allocation size is mem tracking is on
	 * @param address The address of the memory block to reallocate 
	 * @param size The size of the memory block to allocate
	 * @return the address of the new allocated memory block
	 */
	private static final long _reallocateMemory(final long address, final long size) {
		final long addr = unsafe.reallocateMemory(address, size);
		if(MANAGED_ALLOC) {
			allocations.put(addr, new long[]{size});
			final long[] prior = allocations.remove(address);
			totalAllocated.addAndGet(size - ((prior!=null && prior.length>0) ? prior[0] : 0));
		}
		return addr;
	}
	
	/**
	 * Frees the memory block allocated at the specified address, recording the allocation size is mem tracking is on
	 * @param address The address of the memory block to free
	 */
	private static final void _freeMemory(final long address) {
		unsafe.freeMemory(address);
		if(MANAGED_ALLOC) {
			final long[] prior = allocations.remove(address);
			totalAllocated.addAndGet(((prior!=null && prior.length>0) ? 0-prior[0] : 0));
		}		
	}
	
	/**
	 * Returns the address of the slot at the passed dimension
	 * @param rootAddress The root address of the AllocationPointer
	 * @param dim The dimension to get the address of
	 * @return the address
	 */
	public static final long getAddressOfDim(final long rootAddress, final byte dim) {
		return unsafe.getAddress(rootAddress + (dim * ADDRESS_SIZE));
	}

	/**
	 * Resets the allocation tracking
	 */
	@SuppressWarnings("unused")
	private static void resetAllocations() {
		if(allocations!=null) allocations.clear();
		if(totalAllocated!=null) totalAllocated.set(0L);
		if(totalOverhead!=null) totalOverhead.set(0L);
	}
	
	/**
	 * Returns a long array with the dimension addresses
	 * @param address The AllocationPointer root address
	 * @return an array of addresses, one for each active dimension
	 */
	private static long[] getDimAddresses(final long address) {
		final byte dim = getDimension(address);
		long[] dimAddresses = new long[dim];
		switch (dim) {
			case 3:
				dimAddresses[TWO_BYTE] = getAddressOfDim(address, TWO_BYTE);
				//$FALL-THROUGH$
			case 2:
				dimAddresses[ONE_BYTE] = getAddressOfDim(address, ONE_BYTE);
				//$FALL-THROUGH$
			case 1:
				dimAddresses[ZERO_BYTE] = getAddressOfDim(address, ZERO_BYTE);
				break;
			default:
				loge("Invalid AP Dimension: [%s]", dim);
		}
		return dimAddresses;
	}
	
	
	/**
	 * Reassigns the address of a dim in the root space. Called after an extend.
	 * @param rootAddress The root address of the AllocationPointer
	 * @param dim The dimension to update
	 * @param newAddress The new address of the dim, assigned during the extend call.
	 */
	private static final void setAddressOfDim(final long rootAddress, final byte dim, final long newAddress) {
		unsafe.putAddress(rootAddress + (dim * ADDRESS_SIZE), newAddress);
	}
	
	
	/**
	 * Initializes a dim address space
	 * @param actualAddress The actual dim address (not the root address)
	 * @param refId The ref Id to write into the space
	 * @param dim The dim to initialize
	 */
	private static final void initAllocationPointer(final long actualAddress, final long refId, final byte dim) {
		unsafe.putInt(actualAddress, ALLOC_SIZE);
		unsafe.putInt(actualAddress + SIZE_OFFSET, 0);
		unsafe.putLong(actualAddress + REFID_OFFSET, refId);
		unsafe.putByte(actualAddress + DIM_OFFSET, dim);
		unsafe.setMemory(actualAddress + HEADER_SIZE, (ALLOC_SIZE * ADDRESS_SIZE), ZERO_BYTE);		
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
	 * @param rootAddress The root address of the AllocationPointer
	 * @return the dimension of the AllocationPointer
	 */
	public static final byte getDimension(final long rootAddress) {
		return unsafe.getByte(getAddressOfDim(rootAddress, ZERO_BYTE) + DIM_OFFSET);
	}
	/**
	 * Returns the reference id of the referenced AllocationPointer
	 * @param rootAddress The root address of the AllocationPointer
	 * @return the reference id of the AllocationPointer
	 */
	public static final long getReferenceId(final long rootAddress) {
		return unsafe.getLong(getAddressOfDim(rootAddress, ZERO_BYTE) + REFID_OFFSET);
	}
	
	/**
	 * Sets the reference id of the referenced AllocationPointer
	 * @param rootAddress The root address of the AllocationPointer
	 * @param newRefId The ref id to set the reference to
	 */
	private static final void setReferenceId(final long rootAddress, final long newRefId) {
		unsafe.putLong(getAddressOfDim(rootAddress, ZERO_BYTE) + REFID_OFFSET, newRefId);
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
	 * @param rootAddress The root address of the AllocationPointer
	 * @param addressToFind The address to find the index for
	 * @return the index of the passed address, or -1 if the address was not found
	 * TODO: implement hashing function here to speed up finding an address
	 */
	public static final int findIndexForAddress(final long rootAddress, final long addressToFind) {		
		final int sz = getSize(rootAddress);
		if(sz==0) return -1;
		long indexedValue = -1;
		for(int i = 0; i < sz; i++) {
			indexedValue = AllocationPointerOperations.getAddress(rootAddress, i);
			if(addressToFind==indexedValue) return i;
		}
		return -1;
	}
	
	
	/**
	 * Assigns the passed address to the next available slot in the referenced AllocationPointer
	 * @param rootAddress The root address of the AllocationPointer
	 * @param newAddress The address to assign to the next slot
	 * @param size The size of the memory block that the newAddress points to
	 * @param alignmentOverhead the cache-line memory alignment overhead of the memory block that the newAddress points to
	 */
	public static final void assignSlot(final long rootAddress, final long newAddress, final long size, final long alignmentOverhead) {		
		final byte dim = getDimension(rootAddress);
		if(isFull(rootAddress)) {			
			extend(rootAddress);
		}
		if(findIndexForAddress(rootAddress, newAddress)!=-1) return;
		final int nextIndex = incrementSize(rootAddress);		
		final long offset = HEADER_SIZE + (nextIndex * ADDRESS_SIZE);
		put(rootAddress, ZERO_BYTE, offset, newAddress);
		if(dim>1) {
			put(rootAddress, ONE_BYTE, offset, size);
			if(dim>2) {
				put(rootAddress, TWO_BYTE, offset, alignmentOverhead);
			}
		}
	}
	
	private static final void put(final long address, final byte dim, final long offset, final long value) {
		unsafe.putAddress(getAddressOfDim(address, dim) + offset, value);
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
	public static final void reassignSlot(final long address, final long newAddress, final long size, final long alignmentOverhead, final int index) {
		if(index >= getSize(address)) throw new IllegalArgumentException("Invalid index [" + index + "]. Size is [" + getSize(address) + "]");
		final long offset = HEADER_SIZE + (index * ADDRESS_SIZE);
		final byte dim = getDimension(address);
		put(address, ZERO_BYTE, offset, newAddress);
		if(dim>1) {
			put(address, ONE_BYTE, offset, size);
			if(dim>2) {
				put(address, TWO_BYTE, offset, alignmentOverhead);
			}
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
		return unsafe.getInt(getAddressOfDim(address, ZERO_BYTE) + SIZE_OFFSET);
	}
	
	/**
	 * Returns the address slot capacity of AllocationPointerOperations 
	 * resident at the passed memory address
	 * @param address the memory address of the AllocationPointerOperations
	 * @return the number of allocated slots
	 */
	public static final int getCapacity(final long address) {
		return unsafe.getInt(getAddressOfDim(address, ZERO_BYTE));
	}
	
	// =================================================================================================
	// Attached / Dettached Management
	// =================================================================================================
	
	/**
	 * Indicates if the referenced AllocationPointer is attached
	 * @param rootAddress The root address of the AllocationPointer 
	 * @return true if the referenced AllocationPointer is attached, false otherwise
	 */
	public static final boolean isAttached(long rootAddress) {
		return getReferenceId(rootAddress) > 0;
	}
	
	/**
	 * Marks the referenced AllocationPointer as attached
	 * @param rootAddress the root address of the AllocationPointer 
	 */
	public static final void setAttached(long rootAddress) {
		setReferenceId(rootAddress, Math.abs(getReferenceId(rootAddress)));
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
	 * @param dim THe dimension to get the value from
	 * @return the address as the specified index
	 */
	public static final long getAddress(final long address, final int index, final byte dim) {
		if(index < 0) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		final int size = getSize(address);
		if(index > (size-1)) throw new IllegalArgumentException("Invalid index: [" + index + "]");
		return unsafe.getAddress(getAddressOfDim(address, dim) + HEADER_SIZE + (index * ADDRESS_SIZE));
	}
	
	/**
	 * Returns the address at the specified index in the addressed AllocationPointer
	 * @param address the memory address of the AllocationPointer
	 * @param index the index of the AllocationPointer's address slots to retrieve
	 * @return the address as the specified index
	 */
	public static final long getAddress(final long address, final int index) {
		return getAddress(address, index, ZERO_BYTE);
	}

	/**
	 * Retrieves the total number of bytes allocated on behalf of the addresses being tracked 
	 * @param address The root address of the AllocationPointer
	 * @return the total number of bytes allocated or zero if mem tracking is not enabled 
	 */
	public static final long getTotalTrackedAllocationBytes(final long address) {
		if(getDimension(address)<2) return 0; 
		long size = 0;
		final int sz = getSize(address);
		for(int i = 0; i < sz; i++) {
			size += getAddress(address, i, ONE_BYTE);
		}
		return size;
	}
	
	/**
	 * Retrieves the total number of bytes of cache-line memory alignment bytes overhead on behalf of the addresses being tracked 
	 * @param rootAddress The root address of the AllocationPointer
	 * @return the total number of cache-line memory alignment bytes overhead 
	 */
	public static final long getTotalAlignmentOverheadSize(final long rootAddress) {
		if(getDimension(rootAddress)<3) return 0; 
		long size = 0;
		final int sz = getSize(rootAddress);
		for(int i = 0; i < sz; i++) {
			size += getAddress(rootAddress, i, TWO_BYTE);
		}
		return size;
	}
	
	/**
	 * Returns the size of the memory block in bytes at the specified tracked address
	 * @param rootAddress The root address of the AllocationPointer
	 * @param trackedAddress The address to get the allocation size for
	 * @return the size of the memory block referenced 
	 */
	public static long getAllocationSizeOf(final long rootAddress, final long trackedAddress) {
		if(getDimension(rootAddress)<2) return 0; 
		final int index = findIndexForAddress(rootAddress, trackedAddress);
		if(index==-1) return 0;
		return getAddress(rootAddress, index, ZERO_BYTE);
	}
	
	/**
	 * Returns the cache-line alignment overhead of the memory block in bytes at the specified tracked address
	 * @param rootAddress The root address of the AllocationPointer
	 * @param trackedAddress The address to get the cache-line alignment overhead for
	 * @return the cache-line alignment overhead of the memory block referenced 
	 */
	public static long getAlignmentOverheadOf(final long rootAddress, final long trackedAddress) {
		if(getDimension(rootAddress)<2) return 0; 
		final int index = findIndexForAddress(rootAddress, trackedAddress);
		if(index==-1) return 0;
		return getAddress(rootAddress, index, TWO_BYTE);
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
	public static final long[] getTriplet(final long address, final int index) {		
		final long[] triplet = new long[3];		
		triplet[0] = getAddress(address, index);
		final byte dim = getDimension(address); 
		if(dim>1) {
			triplet[1] = getAddress(address, index, ONE_BYTE);
			if(dim>2) {
				triplet[2] = getAddress(address, index, TWO_BYTE);
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
	public static final long[] getSizedTriplet(final long address, final int index) {
		final byte dim = getDimension(address);
		final long[] triplet = new long[dim];		
		triplet[0] = getAddress(address, index);		 
		if(dim>1) {
			triplet[1] = getAddress(address, index, ONE_BYTE);
			if(dim>2) {
				triplet[2] = getAddress(address, index, TWO_BYTE);
			}
		}
		return triplet;		
	}
	
	
	/**
	 * Clears the address at the specified index in the addressed AllocationPointerOperations
	 * @param rootAddress The address of the AllocationPointer
	 * @param index the index of the AllocationPointer's address slot to clear
	 */
	public static final void clearAddress(final long rootAddress, final int index) {
		if(index==-1) return;
		if(index >= getSize(rootAddress)) throw new IllegalArgumentException("Invalid index [" + index + "]. Size is [" + getSize(rootAddress) + "]");
		final long offset = HEADER_SIZE + (index * ADDRESS_SIZE);
		final byte dim = getDimension(rootAddress);
		put(rootAddress, ZERO_BYTE, offset, 0L);
		if(dim>1) {
			put(rootAddress, ONE_BYTE, offset, 0L);
			if(dim>2) {
				put(rootAddress, TWO_BYTE, offset, 0L);
			}
		}
	}
	
	
	/**
	 * Frees the memory block at the passed address
	 * @param address the address of the memory block to free
	 */
	public static final void freeAddress(long address) {
		_freeMemory(address);
	}
	
	/**
	 * Returns the total allocated memory
	 * @return the total allocated memory
	 */
	public static final long getTotalAllocatedMemory() {
		if(!MANAGED_ALLOC) return -1;
//		long total = 0;
//		for(Enumeration<long[]> e = allocations.elements(); e.hasMoreElements();) {
//			total += e.nextElement()[0];
//		}
//		return total;
		return totalAllocated.get();
	}
	
	/**
	 * Returns the total number of current memory allocations
	 * @return the total number of current memory allocations
	 */
	public static final int getTotalAllocationCount() {
		if(!MANAGED_ALLOC) return -1;
		return allocations.size();
	}
	
	
	/**
	 * Returns the total allocated memory for the passed addresses
	 * @param addresses The addresses to sum the memory allocation for
	 * @return the total allocated memory for the passed addresses
	 */
	public static final long getAggregateAllocatedMemory(long[] addresses) {
		if(!MANAGED_ALLOC) return -1;
		long total = 0;
		for(long address : addresses) {
			long[] mem = allocations.get(address);
			if(mem!=null) total += mem[0];
		}
		return total;
	}
	
	
	
	/**
	 * Prints the summary state of an AllocationPointer
	 * @param rootAddress of this AllocationPointer
	 * @return a string describing the status of the AllocationPointer
	 */
	public static final String print(final long rootAddress) {
		StringBuilder b = new StringBuilder(String.format("AllocationPointer >> [size: %s, capacity: %s, byteSize: %s, attached: %s]", getSize(rootAddress), getCapacity(rootAddress), getEndOffset(rootAddress), isAttached(rootAddress)));
		return b.toString();
	}
	
	/**
	 * Prints the detailed state of an AllocationPointer
	 * @param rootAddress of this AllocationPointer
	 * @return a string describing the details of the AllocationPointer
	 * FIXME: implement this
	 */
	public static final String dump(final long rootAddress) {
		return print(rootAddress);
	}
	
	/** Empty long arr const */
	public static final long[] EMPTY_LONG_ARR = {};
	/** Empty long[] arr const */
	public static final long[][] EMPTY_DLONG_ARR = {{}};
	
	/**
	 * Frees all memory allocated within the referenced AllocationPointerOperations.
	 * No includePostMortem is returned.
	 * @param rootAddress of this AllocationPointer
	 */
	public static final void free(final long rootAddress) {
		free(rootAddress, false);
	}

	
	/**
	 * Frees all memory allocated within the referenced AllocationPointerOperations
	 * @param rootAddress of this AllocationPointer
	 * @param includePostMortem If true, the returned array will have all the formerly allocated addresses,
	 * otherwise will be zero length.
	 * @return The [possibly empty] array of addresses just deallocated
	 */
	public static final long[][] free(final long rootAddress, final boolean includePostMortem) {
//		log("Starting full AP Free for root address [%s]", address);
		final byte dim = getDimension(rootAddress);				
		final int size = getSize(rootAddress);
		final boolean dettached = !isAttached(rootAddress);
		long[][] deadAddresses = includePostMortem ? size>0 ? new long[size][dim] : EMPTY_DLONG_ARR : EMPTY_DLONG_ARR;
		if(size>0) {
			if(includePostMortem) {
				for(int i = 0; i < size; i++) {
					long[] triplet = getSizedTriplet(rootAddress, i);
					deadAddresses[i] = triplet;
					if(triplet[0]>0) {
						if(dettached) _freeMemory(triplet[0]);						
					}
				}				
			} else {
				for(int i = 0; i < size; i++) {
					long addr = getAddress(rootAddress, i);
					if(addr>0) {
						if(dettached) _freeMemory(addr);
					}
				}
			}
		}
		for(final long dimAddress : getDimAddresses(rootAddress)) {
			_freeMemory(dimAddress);
		}
		_freeMemory(rootAddress);
		return deadAddresses;
	}
	
	/**
	 * Dumps the values of the AllocationPointer
	 * @param rootAddress The root address of the AllocationPointer
	 * @return a one, two or three length long array within an array keyed by by index.
	 */
	public static final long[][] dumpValues(final long rootAddress) {
		final byte dim = getDimension(rootAddress);				
		final int size = getSize(rootAddress);
		long[][] matrix  = new long[size][dim];
		if(size>0) {
			for(int i = 0; i < size; i++) {
				matrix[i] = getSizedTriplet(rootAddress, i);
			}			
		}
		return matrix;
	}
	
	/**
	 * Increments the size of the AllocationPointer to represent an added address.
	 * @param address The address of the AllocationPointer memory blocks to increment the size for
	 * @return the index of the next open slot from the first address
	 */
	public static final int incrementSize(final long address) {
		final byte dim = getDimension(address);
		final int nextSlot  = getSize(address);
		incrementSize(address, ZERO_BYTE, nextSlot + 1);
		if(dim>1) {
			incrementSize(address, ONE_BYTE, nextSlot + 1);
			if(dim>2) {
				incrementSize(address, TWO_BYTE, nextSlot + 1);
			}
		}		
		return nextSlot;
	}
	
	private static final void incrementSize(final long address, final byte dim, final int newSize) {
		unsafe.putInt(getAddressOfDim(address, dim) + SIZE_OFFSET, newSize);
	}
	
	/**
	 * Returns the address of the last byte of the referenced AllocationPointer
	 * @param address The address of the allocation pointer memory block
	 * @return the end address of the AllocationPointerOperations
	 */
	public static final long getEndAddress(final long address) {
		final int size = getCapacity(address);
		final long za = getAddressOfDim(address, ZERO_BYTE);
		return za + HEADER_SIZE + (size * ADDRESS_SIZE);
	}
	
	/**
	 * Returns the total byte size of the referenced AllocationPointer
	 * @param address The address of the allocation pointer memory block
	 * @return the total byte size of the AllocationPointer
	 */
	public static final long getEndOffset(final long address) {
		return getEndAddress(address) - getAddressOfDim(address, ZERO_BYTE);
	}
	
	/**
	 * Returns the total byte size of the AllocationPointer
	 * @param address The address array of the allocation pointer memory block which could be a length of:<ol>
	 * 	<li>Simple address management</li>
	 *  <li>Address management with memory tracking</li>
	 *  <li>Address management with memory tracking and cache-line alignment overhead</li>
	 * </ol>
	 * @return the total byte size of the AllocationPointer
	 */
	public static final long getDeepByteSize(long address) {
		final byte dim = getDimension(address);
		final int cap = getCapacity(address);
		return 
				(
						(HEADER_SIZE + (ADDRESS_SIZE * cap))	  // the size of one dim
						 * dim									  // multiplied by the number of dims
						 + (ADDRESS_SIZE * dim)                   // plus the dim addresses
				);
				
	}
	
	
	/**
	 * Extends the capacity of the referenced memory block
	 * @param address The address of the allocation pointer memory block
	 */
	public static final void extend(final long address) {
		final byte dim = getDimension(address);
		final long endOffset = getEndOffset(address);
		final int currentCap = getCapacity(address);
		
		extend(address, getAddressOfDim(address, ZERO_BYTE), endOffset, currentCap, ZERO_BYTE);
		if(dim>1) {
			extend(address, getAddressOfDim(address, ONE_BYTE), endOffset, currentCap, ONE_BYTE);
			if(dim>2) {
				extend(address, getAddressOfDim(address, TWO_BYTE), endOffset, currentCap, TWO_BYTE);
			}
		}		
	}
	
	private static void extend(final long rootAddress, final long actualAddress, final long endOffset, final int currentCap, final byte dim) {
		final long newAddress = _reallocateMemory(actualAddress, endOffset + (ALLOC_SIZE * ADDRESS_SIZE));
		unsafe.setMemory(newAddress + endOffset, (ALLOC_SIZE * ADDRESS_SIZE), ZERO_BYTE);
		unsafe.putInt(newAddress, currentCap + ALLOC_SIZE);
		setAddressOfDim(rootAddress, dim, newAddress);
	}
	
	
	/**
	 * Returns the managed addresses
	 * @param address The root address of the AllocationPointer
	 * @return an array of longs
	 */
	public static final long[] getAddressBase(final long address) {
		final int size = getSize(address);
		long[] addrs = new long[size];
		unsafe.copyMemory(address, getAddressOf(addrs) + LONG_ARRAY_OFFSET, size * LONG_SIZE);
		return addrs;
		
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
	 * Resets the AllocationPointerOperations.
	 * TESTING ONLY. DON'T USE FOR **ANYTHING** ELSE !
	 */
	@SuppressWarnings("unused")
	private static void enableManagement() {
		if(MANAGED_ALLOC) return;
		ReflectionHelper.setFieldValue(AllocationPointerOperations.class, "MANAGED_ALLOC", true);
		ReflectionHelper.setFieldValue(AllocationPointerOperations.class, "allocations", new NonBlockingHashMapLong<long[]>(1024, true));
		ReflectionHelper.setFieldValue(AllocationPointerOperations.class, "totalAllocated", new AtomicLong(0));
		ReflectionHelper.setFieldValue(AllocationPointerOperations.class, "totalOverhead", new AtomicLong(0));
	}
	
	/**
	 * Resets the AllocationPointerOperations.
	 * TESTING ONLY. DON'T USE FOR **ANYTHING** ELSE !
	 */
	@SuppressWarnings("unused")
	private static void disableManagement() {
		if(!MANAGED_ALLOC) return;
		ReflectionHelper.setFieldValue(AllocationPointerOperations.class, "MANAGED_ALLOC", false);
		if(allocations!=null) allocations.clear();
		ReflectionHelper.setFieldValue(AllocationPointerOperations.class, "allocations", null);
		ReflectionHelper.setFieldValue(AllocationPointerOperations.class, "totalAllocated", null);
		ReflectionHelper.setFieldValue(AllocationPointerOperations.class, "totalOverhead", null);

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
