package com.heliosapm.unsafe;

import java.util.Collections;
import java.util.Map;

/**
 * <p>Title: InactiveUnsafeMemory</p>
 * <p>Description: Stub for unsafe memory allocation tracking when tracking is not enabled</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapter.InactiveUnsafeMemory</code></p>
 */
public class InactiveUnsafeMemory implements UnsafeMemoryMBean  {

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
	 * @see com.heliosapm.unsafe.UnsafeMemoryMBean#getAddressSize()
	 */
	@Override
	public int getAddressSize() {
		return UnsafeAdapter.addressSize();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.UnsafeMemoryMBean#getPageSize()
	 */
	@Override
	public int getPageSize() {
		return UnsafeAdapter.pageSize();
	}
	
}