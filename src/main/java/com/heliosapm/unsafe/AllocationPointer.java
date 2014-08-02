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
 * <p>Description: A container for managing deallocatable memory block addresses</p>
 * <p><b>Decidedly not thread safe.</b></p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationPointer</code></p>
 */

public class AllocationPointer implements ReferenceProvider<AllocationPointer>, Deallocatable {
	/** The address of the memory block allocated for this AllocationPointer */
	private final long[][] _address;
	/** The phantom reference to this allocation pointer if one has been requested */
	private PhantomReference<AllocationPointer> phantomRef = null;

	/**
	 * Creates a new AllocationPointer with no allocation tracking and the default capacity ({@link AllocationPointerOperations#ALLOC_SIZE})
	 */
	public AllocationPointer() {
		this(false);
	}

	
	/**
	 * Creates a new AllocationPointer with the default capacity ({@link AllocationPointerOperations#ALLOC_SIZE})
	 * @param trackAllocations true to enable allocation tracking, false otherwise
	 */
	public AllocationPointer(final boolean trackAllocations) {
		if(trackAllocations) {
			_address = new long[1][2];
			_address[0][1] = AllocationPointerOperations.newAllocationPointer();
		} else {
			_address = new long[1][1];
		}		 
		_address[0][0] = AllocationPointerOperations.newAllocationPointer();
	}
	
	/**
	 * Creates a new AllocationPointer with no allocation tracking and loads the passed address.
	 * @param address The address to load into this AllocationPointer.
	 */
	public AllocationPointer(long address) {
		this(address, false);
	}

	
	/**
	 * Creates a new AllocationPointer and loads the passed address.
	 * @param address The address to load into this AllocationPointer.
	 * @param trackAllocations true to enable allocation tracking, false otherwise
	 */
	public AllocationPointer(long address, final boolean trackAllocations) {
		this(trackAllocations);
		if(address>0) {
			assignSlot(address);
		}		
	}
	
	/**
	 * Creates a new AllocationPointer with no allocation tracking and loads the passed addresses.
	 * @param addresses The addresses to load into this AllocationPointer.
	 */
	public AllocationPointer(final long[] addresses) {
		this(addresses, false);
	}
	
	
	/**
	 * Creates a new AllocationPointer and loads the passed addresses.
	 * @param addresses The addresses to load into this AllocationPointer.
	 * @param trackAllocations true to enable allocation tracking, false otherwise
	 */
	public AllocationPointer(final long[] addresses, final boolean trackAllocations) {
		this();
		if(addresses!=null) {
			for(long address: addresses) {
				if(address>0) {
					assignSlot(address);
				}
			}
		}
	
	}	
	
	/**
	 * Assigns the passed address to the next available slot
	 * @param newAddress The address to assign to the next slot
	 * @return the index of the slot the address was inserted into
	 */
	public final int assignSlot(final long newAddress) {
		_address[0][0] = AllocationPointerOperations.assignSlot(_address[0][0], newAddress);
		return AllocationPointerOperations.getLastIndex(_address[0][0]);
	}
	
	/**
	 * Assigns the passed address to an existing slot
	 * @param newAddress The address to assign to the next slot
	 * @param index The index of the slot to write the address to
	 */
	public final void reassignSlot(final long newAddress, final int index) {
		AllocationPointerOperations.reassignSlot(_address[0][0], newAddress, index);
	}
	
	/**
	 * Returns the slotted addresses as a {@link Deallocatable} array of longs
	 * @return an array of addresses
	 */
	public final long[][] getAddresses() {
		return _address;
	}
	
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
	 * @return a string outlining the summary and each of the addresses
	 */
	public final String dump() {
		return AllocationPointerOperations.dump(_address[0][0]);
	}
	
	
	/**
	 * Frees all memory allocated within this AllocationPointer
	 */
	public final void free() {
		AllocationPointerOperations.free(_address[0][0]);
	}
	
	
	/**
	 * Returns the total byte size of this AllocationPointer
	 * @return the total byte size of this AllocationPointer
	 */
	public final long getByteSize() {
		return AllocationPointerOperations.getEndOffset(_address[0][0]);
	}
	
	
	/**
	 * Acquires a phantom reference to this AllocationPointer
	 * @param refQueue The reference queue the phantom reference will be registered with
	 * @return the phantom reference
	 */
	public final synchronized PhantomReference<AllocationPointer> getReference(ReferenceQueue<? super AllocationPointer> refQueue) {
		if(phantomRef==null) {
			phantomRef = new AllocationPointerPhantomRef(this, _address, refQueue);
		}
		return phantomRef;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#isReferenced()
	 */
	@Override
	public boolean isReferenced() {
		return phantomRef != null;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#setReferenced()
	 */
	@Override
	public void setReferenced() {
		/* No Op */		
	}

}
