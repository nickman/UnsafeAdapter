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

import java.lang.ref.ReferenceQueue;

/**
 * <p>Title: AllocationPointer</p>
 * <p>Description: A container for managing deallocatable memory block keyAddresses</p>
 * <p><b>Decidedly not thread safe.</b></p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationPointer</code></p>
 * TODO: <ol>
 * <li>AP needs a flag to indicate that addresses are "fake" when using SafeMemoryAllocations and the AP is dettached<li>
 * </ol>
 */

public class AllocationPointer implements ReferenceProvider<AllocationPointer>, AddressAssignable {
	/** The address of the memory block allocated for this AllocationPointer */
	private final long address;
	/** The phantom reference to this allocation pointer if one has been requested */
	private AllocationPointerPhantomRef phantomRef = null;

	// =====================================
	//  MUST BE CREATED BY UA
	// =====================================
	
	/**
	 * Returns the managed addresses for this AllocationPointer.
	 * <b>USE CAREFULLY</b> 
	 * @return the managed addresses for this AllocationPointer.
	 */
	public final long[] getManagedAddresses() {
		return AllocationPointerOperations.getAddressBase(address);
	}
	
	/**
	 * Returns the root address base for this AllocationPointer.
	 * <b>USE CAREFULLY</b> 
	 * @return the root address base for this AllocationPointer.
	 */
	public final long getAddressBase() {
		return address;
	}
	
	/**
	 * Returns this AllocationPointer's reference id
	 * @return the reference id
	 */
	public final long getReferenceId() {
		return AllocationPointerOperations.getReferenceId(address);
	}
	
	/**
	 * Returns the address of the slot at the passed dimension
	 * @param dim The dimension to get the address of
	 * @return the address
	 */
	public final long getAddressOfDim(final byte dim) {
		return AllocationPointerOperations.getAddressOfDim(address, dim);
	}

	
	/**
	 * Returns the dimension of this AllocationPointer where the return value indicates: <ol>
	 * 	<li>Managed addresses only</li>
	 *  <li>Managed addresses and the allocation size of each address</li>
	 *  <li>Managed addresses and the allocation size and cache-line alignment overhead of each address</li>
	 * </ol>
	 * @return the dimension of this AllocationPointer
	 */
	public final byte getDimension() {
		return AllocationPointerOperations.getDimension(address);
	}
	
	/**
	 * Creates a new AllocationPointer with the default capacity ({@link AllocationPointerOperations#ALLOC_SIZE})
	 * @param memTracking Indicates if memory tracking is enabled
	 * @param memAlignment Indicates if cache-line memory alignment is enabled 
	 * @param refId The UnsafeAdapter's ref manager assigned reference id
	 */
	AllocationPointer(boolean memTracking, boolean memAlignment, long refId) {
		address = AllocationPointerOperations.newAllocationPointer(memTracking, memAlignment, refId);
	}
	
	/**
	 * Ingests a {@link Deallocatable} and assigns it a ref id
	 * @param dealloc the Deallocatable to ingest
	 * @return this AllocationPointer
	 */
	final AllocationPointer ingest(Deallocatable dealloc) {
		AllocationPointerOperations.ingest(address, dealloc);
		return this;
	}
	
	/**
	 * Finds the index of the passed address in the slots of the referenced AllocationPointer
	 * @param addressToFind The address to find the index for
	 * @return the index of the passed address, or -1 if the address was not found
	 */
	public final int findIndexForAddress(final long addressToFind) {
		return AllocationPointerOperations.findIndexForAddress(address, addressToFind);
	}
	
	
	
	/**
	 * Assigns the passed address to the next available slot
	 * @param newAddress The address to assign to the next slot
	 * @param size The size of the allocation being registered in bytes
	 * @param alignmentOverhead  The alignment overhead being registered in bytes
	 * @return the index of the slot the address was inserted into
	 */
	public final int assignSlot(final long newAddress, final long size, final long alignmentOverhead) {
		AllocationPointerOperations.assignSlot(address, newAddress, size, alignmentOverhead);
		return AllocationPointerOperations.getLastIndex(address);
	}
	
	/**
	 * Assigns the passed address to the next available slot
	 * @param newAddress The address to assign to the next slot
	 * @return the index of the slot the address was inserted into
	 */
	public final int assignSlot(final long newAddress) {
		AllocationPointerOperations.assignSlot(address, newAddress, 0L, 0L);
		return AllocationPointerOperations.getLastIndex(address);
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
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AddressAssignable#removeAllocated(long)
	 */
	@Override
	public final void removeAllocated(final long addressToRemove) {
		AllocationPointerOperations.clearAddress(address, findIndexForAddress(addressToRemove));
	}
	
	
	/**
	 * Clears the address at the specified index
	 * @param index the index of the AllocationPointer's address slot to clear
	 */
	public final void clearAddress(final int index) {
		AllocationPointerOperations.clearAddress(address, index);
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
		if(index==-1) throw new RuntimeException("Address [" + oldAddress + "] not registered for AllocationPointer [" + AllocationPointerOperations.getReferenceId(address) + "]");
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
		AllocationPointerOperations.reassignSlot(address, newAddress, size, alignmentOverhead, index);
	}
	
	/**
	 * Assigns the passed address to an existing slot. 
	 * @param index The index of the slot
	 * @param newAddress The address to assign to the next slot
	 */
	public final void reassignSlot(final int index, final long newAddress) {		
		AllocationPointerOperations.reassignSlot(address, newAddress, 0L, 0L, index);
	}
	
	/**
	 * Indicates if this AllocationPointer is attached
	 * @return true if this AllocationPointer is attached, false otherwise
	 */
	public final boolean isAttached() {
		return AllocationPointerOperations.isAttached(address);
	}
	
	/**
	 * Marks this AllocationPointer as attached
	 */
	public final void setAttached() {
		AllocationPointerOperations.setAttached(address);
	}
	
	/**
	 * Returns the number of populated address slots 
	 * @return the number of populated slots
	 */
	public final int getSize() {
		return AllocationPointerOperations.getSize(address);
	}
	
	/**
	 * Returns the address slot capacity 
	 * @return the number of allocated slots
	 */
	public final int getCapacity() {
		return AllocationPointerOperations.getCapacity(address);
	}
	
	/**
	 * Returns the index of the most recently assigned address slot, or -1 if none are assigned
	 * @return the index of the most recently assigned address slot
	 */
	public final int getLastIndex() {
		return AllocationPointerOperations.getLastIndex(address);
	}
	
	/**
	 * Determines if the address slots are full.
	 * i.e. if the size equals the capacity.
	 * @return true if full, false otherwise
	 */
	public final boolean isFull() {
		return AllocationPointerOperations.isFull(address);
	}
	
	
	/**
	 * Returns the address at the specified index slot
	 * @param index the index of the slot to read the address from
	 * @return the address at the specified index
	 */
	public final long getAddress(final int index) {
		return AllocationPointerOperations.getAddress(address, index);
	}
	
	/**
	 * Returns the allocation size at the specified index slot
	 * @param index the index of the slot to read the allocation size from
	 * @return the allocation size at the specified index or zero if mem tracking is not enabled
	 */
	public final long getAllocationSize(final int index) {
		if(AllocationPointerOperations.getDimension(address)<2) return 0;
		return AllocationPointerOperations.getAddress(address, index, AllocationPointerOperations.ONE_BYTE);
	}
	
	/**
	 * Returns the alignment overhead at the specified index slot
	 * @param index the index of the slot to read the alignment overhead from
	 * @return the alignment overhead at the specified index or zero if alignment overhead tracking is not enabled
	 */
	public final long getAlignmentOverhead(final int index) {
		if(AllocationPointerOperations.getDimension(address)<3) return 0;
		return AllocationPointerOperations.getAddress(address, index, AllocationPointerOperations.TWO_BYTE);
	}
	

	
	
	
	/**
	 * Returns the summary state of this AllocationPointer
	 * @return a string describing the status of the AllocationPointer
	 */
	public final String toString() {
		return AllocationPointerOperations.print(address);
	}
	
	/**
	 * Returns the detailed state of this AllocationPointer
	 * @return a string outlining the summary and each of the keyAddresses
	 */
	public final String dump() {
		return AllocationPointerOperations.dump(address);
	}
	
	
	/**
	 * Frees all memory allocated within this AllocationPointer
	 */
	public final void free() {
		AllocationPointerOperations.free(address);
		
	}
	
	/**
	 * Frees the memory block referred to by the passed address and zeros out the slots where the address was.
	 * @param addressToFree the address to free
	 */
	public final void freeAddress(long addressToFree) {
		final int index = findIndexForAddress(addressToFree);		
		if(index==-1) throw new RuntimeException("Address [" + addressToFree + "] not registered for AllocationPointer [" + AllocationPointerOperations.getReferenceId(address) + "]");
		freeIndex(index);		
	}
	
	/**
	 * Frees the memory block at the passed index and zeros out the slots.
	 * @param index the index of the slot to free
	 */
	public final void freeIndex(int index) {
		AllocationPointerOperations.clearAddress(address, index);
	}
	
	
	/**
	 * Returns the total byte size of this AllocationPointer
	 * @return the total byte size of this AllocationPointer
	 */
	public final long getByteSize() {
		return AllocationPointerOperations.getDeepByteSize(address);
	}

	
	
	/**
	 * Acquires a phantom reference to this AllocationPointer
	 * @param refQueue The reference queue the phantom reference will be registered with
	 * @return the phantom reference
	 */
	public final synchronized AllocationPointerPhantomRef getReference(ReferenceQueue<? super AllocationPointer> refQueue) {
		if(phantomRef==null) {
			phantomRef = new AllocationPointerPhantomRef(this, address, refQueue);
	}
		return phantomRef;
	}






}
