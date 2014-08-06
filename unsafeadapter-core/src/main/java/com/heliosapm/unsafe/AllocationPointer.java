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
 * <p>Title: AllocationPointer</p>
 * <p>Description: A container for managing deallocatable memory block keyAddresses</p>
 * <p><b>Decidedly not thread safe.</b></p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationPointer</code></p>
 */

public class AllocationPointer implements ReferenceProvider<Object>, AddressAssignable {
	/** The address of the memory block allocated for this AllocationPointer */
	private final long[][] _address;
	/** The phantom reference to this allocation pointer if one has been requested */
	private PhantomReference<Object> phantomRef = null;
	
	/** The UnsafeAdapter provided reference id */
	private final long[] refId;

	// =====================================
	//  MUST BE CREATED BY UA
	// =====================================
	
	/**
	 * Creates a new AllocationPointer with the default capacity ({@link AllocationPointerOperations#ALLOC_SIZE})
	 * @param memTracking Indicates if memory tracking is enabled
	 * @param memAlignment Indicates if cache-line memory alignment is enabled 
	 * @param refId The UnsafeAdapter's ref manager assigned reference id
	 */
	AllocationPointer(boolean memTracking, boolean memAlignment, long refId) {
		final int config = 1 + (memTracking ? (1 + (memAlignment ? 1 : 0)) : 0);		
		this.refId = new long[]{refId, config};
		_address = new long[config][1];
		_address[0][0] = AllocationPointerOperations.newAllocationPointer();
		if(memTracking) {
			_address[1][0] = AllocationPointerOperations.newAllocationPointer();
			if(memAlignment) {
				_address[2][0] = AllocationPointerOperations.newAllocationPointer();
			}
		}
	}
	
	
	/**
	 * Assigns the passed address to the next available slot
	 * @param newAddress The address to assign to the next slot
	 * @param size The size of the allocation being registered in bytes
	 * @param alignmentOverhead  The alignment overhead being registered in bytes
	 * @return the index of the slot the address was inserted into
	 */
	public final int assignSlot(final long newAddress, final long size, final long alignmentOverhead) {
		_address[0][0] = AllocationPointerOperations.assignSlot(_address[0][0], newAddress, size, alignmentOverhead);
		if(refId[2]>1) {
			_address[1][0] = AllocationPointerOperations.assignSlot(_address[1][0], size, 0, 0);
			if(refId[2]>2) _address[2][0] = AllocationPointerOperations.assignSlot(_address[2][0], alignmentOverhead, 0, 0);
		}		
		return AllocationPointerOperations.getLastIndex(_address[0][0]);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AddressAssignable#setAllocated(long, long, long)
	 */
	@Override
	public final void setAllocated(final long address, final long size, final long alignmentOverhead) {
		assignSlot(address, size, alignmentOverhead);		
	}
	
	
	/**
	 * Assigns the passed address to an existing slot
	 * @param oldAddress The old address to replace
	 * @param newAddress The address to assign to the next slot
	 * @param size The new size of the allocation being registered in bytes
	 * @param alignmentOverhead The new alignment overhead being registered in bytes
	 */
	public final void reassignSlot(final long oldAddress, final long newAddress, final long size, final long alignmentOverhead) { // final int index
		final int index = findIndexForAddress(oldAddress);		
		if(index==-1) throw new RuntimeException("Address [" + oldAddress + "] not registered for AllocationPointer [" + refId + "]");
		reassignSlot(index, newAddress, size, alignmentOverhead);		
	}
	
	/**
	 * Assigns the passed address to an existing slot. 
	 * @param index The index of the slot
	 * @param newAddress The address to assign to the next slot
	 * @param size The new size of the allocation being registered in bytes
	 * @param alignmentOverhead The new alignment overhead being registered in bytes
	 */
	public final void reassignSlot(final int index, final long newAddress, final long size, final long alignmentOverhead) {		
		AllocationPointerOperations.reassignSlot(_address[0][0], newAddress, index);
		if(refId[2]>1){
			AllocationPointerOperations.reassignSlot(_address[1][0], size, index);
			if(refId[2]>2) AllocationPointerOperations.reassignSlot(_address[2][0], alignmentOverhead, index);
		}
	}
	
	
	
	/**
	 * Finds the index of the passed address
	 * @param address The address to get the index
	 * @return the index of the passed address, or -1 if the address was not found
	 * TODO: implement hashing function here to speed up finding an address
	 */
	private final int findIndexForAddress(final long address) {
		final int sz = getSize();
		long indexedValue = -1;
		for(int i = 0; i < sz; i++) {
			indexedValue = AllocationPointerOperations.getAddress(_address[0][0], i);
			if(address==indexedValue) return i;
		}
		return -1;
	}
	
//	/**
//	 * Returns the slotted keyAddresses as a {@link Deallocatable} array of longs
//	 * @return an array of keyAddresses
//	 */
//	public final long[][] getAddresses() {
//		return _address;
//	}
	
	/**
	 * Returns the number of populated address slots 
	 * @return the number of populated slots
	 */
	public final int getSize() {
		return AllocationPointerOperations.getSize(_address[0][0]);
	}
	
	/**
	 * Returns the address slot capacity 
	 * @return the number of allocated slots
	 */
	public final int getCapacity() {
		return AllocationPointerOperations.getCapacity(_address[0][0]);
	}
	
	/**
	 * Returns the index of the most recently assigned address slot, or -1 if none are assigned
	 * @return the index of the most recently assigned address slot
	 */
	public final int getLastIndex() {
		return AllocationPointerOperations.getLastIndex(_address[0][0]);
	}
	
	/**
	 * Determines if the address slots are full.
	 * i.e. if the size equals the capacity.
	 * @return true if full, false otherwise
	 */
	public final boolean isFull() {
		return AllocationPointerOperations.isFull(_address[0][0]);
	}
	
	/**
	 * Returns the address at the specified index slot
	 * @param index the index of the slot to read the address from
	 * @return the address at the specified index
	 */
	public final long getAddress(final int index) {
		return AllocationPointerOperations.getAddress(_address[0][0], index);
	}
	
	
	
	/**
	 * Returns the summary state of this AllocationPointer
	 * @return a string describing the status of the AllocationPointer
	 */
	public final String toString() {
		return AllocationPointerOperations.print(_address[0][0]);
	}
	
	/**
	 * Returns the detailed state of this AllocationPointer
	 * @return a string outlining the summary and each of the keyAddresses
	 */
	public final String dump() {
		return AllocationPointerOperations.dump(_address[0][0]);
	}
	
	
	/**
	 * Frees all memory allocated within this AllocationPointer
	 */
	public final void free() {
		if(_address[0][0]>0) {
			AllocationPointerOperations.free(_address[0][0]);
		}
		if(refId[2]>1) {
			if(_address[1][0]>0) {
				AllocationPointerOperations.free(_address[1][0]);
			}
			if(refId[2]>2) {
				if(_address[2][0]>0) {
					AllocationPointerOperations.free(_address[2][0]);
				}
			}			
		}		
	}
	
	/**
	 * Frees the memory block referred to by the passed address and zeros out the slots where the address was.
	 * @param addressToFree the address to free
	 */
	public final void freeAddress(long addressToFree) {
		final int index = findIndexForAddress(addressToFree);		
		if(index==-1) throw new RuntimeException("Address [" + addressToFree + "] not registered for AllocationPointer [" + refId + "]");
		freeIndex(index);		
	}
	
	/**
	 * Frees the memory block at the passed index and zeros out the slots.
	 * @param index the index of the slot to free
	 */
	public final void freeIndex(int index) {
		final long addressToFree = AllocationPointerOperations.getAddress(_address[0][0], index); 
		if(addressToFree > 0) {
			AllocationPointerOperations.freeAddress(addressToFree);
			reassignSlot(index, 0L, 0L, 0L);
		}
	}
	
	
	/**
	 * Returns the total byte size of this AllocationPointer
	 * @return the total byte size of this AllocationPointer
	 */
	public final long getByteSize() {
		return AllocationPointerOperations.getEndOffset(_address[0][0]) + 
			refId[2]>1 ? (
				AllocationPointerOperations.getEndOffset(_address[1][0]) + 
					refId[2]>2 ? (
						AllocationPointerOperations.getEndOffset(_address[2][0])
					) : 0
			) : 0
		; 
	}

	
	
	/**
	 * Acquires a phantom reference to this AllocationPointer
	 * @param refQueue The reference queue the phantom reference will be registered with
	 * @return the phantom reference
	 */
	public final synchronized PhantomReference<Object> getReference(ReferenceQueue<Object> refQueue) {
		if(phantomRef==null) {
			phantomRef = new AllocationPointerPhantomRef(this, _address, refQueue, refId[0]);
		}
		return phantomRef;
	}





}
