package com.heliosapm.unsafe;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Title: UnsafeMemory</p>
 * <p>Description: Management stats for unsafe memory allocation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapter.UnsafeMemory</code></p>
 */
public class UnsafeMemory implements UnsafeMemoryMBean  {
	
	/** The map key for the total memory allocation in bytes */
	public static final String ALLOC_MEM = "Memory";
	/** The map key for the total memory alignedment overhead in bytes */
	public static final String ALLOC_OVER = "AllocationOverhead";
	
	/** The map key for the total memory allocation in KB */
	public static final String ALLOC_MEMK = "MemoryKb";
	/** The map key for the total memory allocation in MB */
	public static final String ALLOC_MEMM = "MemoryMb";
	/** The map key for the total number of current allocations */
	public static final String ALLOC_COUNT = "Allocations";
	/** The map key for the reference queue size */
	public static final String REFQ_SIZE= "RefQSize";
	/** The map key for the pending phantom references */
	public static final String PENDING_COUNT = "Pending";
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.UnsafeMemoryMBean#getState()
	 */
	@Override
	public Map<String, Long> getState() {
		Map<String, Long> map = new HashMap<String, Long>(6);
		map.put(ALLOC_MEM, getTotalAllocatedMemory());
		map.put(ALLOC_OVER, getAlignedMemoryOverhead());
		map.put(ALLOC_MEMK, getTotalAllocatedMemoryKb());
		map.put(ALLOC_MEMM, getTotalAllocatedMemoryMb());
		map.put(ALLOC_COUNT, (long)getTotalAllocationCount());
		map.put(PENDING_COUNT, (long)getPendingRefs());    		
		return map;
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
	
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.UnsafeMemoryMBean#getTotalAllocatedMemory()
	 */
	@Override
	public long getTotalAllocatedMemory() {
		if(!UnsafeAdapter.trackMem) return -1L;
		return UnsafeAdapter.totalMemoryAllocated.get()-UnsafeAdapter.BASELINE_MEM;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.UnsafeMemoryMBean#getAlignedMemoryOverhead()
	 */
	public long getAlignedMemoryOverhead() {
		if(!UnsafeAdapter.trackMem) return -1L;
		return UnsafeAdapter.totalAlignmentOverhead.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.UnsafeMemoryMBean#getTotalAllocationCount()
	 */
	@Override
	public int getTotalAllocationCount() {
		if(!UnsafeAdapter.trackMem) return -1;
		return UnsafeAdapter.memoryAllocations.size()-UnsafeAdapter.BASELINE_ALLOCS;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.UnsafeMemoryMBean#getTotalAllocatedMemoryKb()
	 */
	@Override
	public long getTotalAllocatedMemoryKb() {
		if(!UnsafeAdapter.trackMem) return -1L;
		long t = UnsafeAdapter.totalMemoryAllocated.get();
		if(t<1) return 0L;
		return t/1024;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.UnsafeMemoryMBean#getTotalAllocatedMemoryMb()
	 */
	@Override
	public long getTotalAllocatedMemoryMb() {
		if(!UnsafeAdapter.trackMem) return -1L;
		long t = UnsafeAdapter.totalMemoryAllocated.get();
		if(t<1) return 0L;
		return t/1024/1024;
	}
	
	/**
	 * Returns the number of retained phantom references to memory allocations
	 * @return the number of retained phantom references to memory allocations
	 */
	public int getPendingRefs() {
		return UnsafeAdapter.deAllocs.size();
	}
	
	
}