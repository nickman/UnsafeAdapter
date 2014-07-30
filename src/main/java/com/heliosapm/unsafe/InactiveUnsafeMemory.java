package com.heliosapm.unsafe;

import java.util.Collections;
import java.util.Map;

/**
 * <p>Title: InactiveUnsafeMemory</p>
 * <p>Description: Stub for unsafe memory allocation tracking when tracking is not enabled</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapterOld.InactiveUnsafeMemory</code></p>
 */
public class InactiveUnsafeMemory implements MemoryMBean  {

	/**
	 * Returns a map of unsafe memory stats keyed by the stat name
	 * @return a map of unsafe memory stats
	 */
	@Override
	public Map<String, Long> getState() {
		return Collections.EMPTY_MAP;
	}

	/**
	 * Returns the total off-heap allocated memory in bytes
	 * @return the total off-heap allocated memory
	 */
	@Override
	public long getTotalAllocatedMemory() {
		return -1L;
	}

	/**
	 * Returns the total aligned memory overhead in bytes
	 * @return the total aligned memory overhead in bytes
	 */
	@Override
	public long getAlignedMemoryOverhead() {
		return -1L;
	}

	/**
	 * Returns the total off-heap allocated memory in Kb
	 * @return the total off-heap allocated memory
	 */
	@Override
	public long getTotalAllocatedMemoryKb() {
		return -1L;
	}

	/**
	 * Returns the total off-heap allocated memory in Mb
	 * @return the total off-heap allocated memory
	 */
	@Override
	public long getTotalAllocatedMemoryMb() {
		return -1L;
	}

	/**
	 * Returns the total number of existing allocations
	 * @return the total number of existing allocations
	 */
	@Override
	public int getTotalAllocationCount() {
		return -1;
	}


	/**
	 * Returns the number of retained phantom references to memory allocations
	 * @return the number of retained phantom references to memory allocations
	 */
	@Override
	public int getPendingRefs() {			
		return -1;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getAddressSize()
	 */
	@Override
	public int getAddressSize() {
		return UnsafeAdapterOld.addressSize();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getPageSize()
	 */
	@Override
	public int getPageSize() {
		return UnsafeAdapterOld.pageSize();
	}

	@Override
	public boolean isSafeMemory() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isTrackingEnabled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isFiveCopy() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isFourSet() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isAlignmentEnabled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long getReferenceQueueSize() {
		// TODO Auto-generated method stub
		return 0;
	}
	
}