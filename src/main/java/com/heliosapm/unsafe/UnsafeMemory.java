package com.heliosapm.unsafe;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Title: UnsafeMemory</p>
 * <p>Description: Management stats for unsafe memory allocation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapterOld.UnsafeMemory</code></p>
 */
public class UnsafeMemory implements MemoryMBean  {
	
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
	 * @see com.heliosapm.unsafe.MemoryMBean#getState()
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
	
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getTotalAllocatedMemory()
	 */
	@Override
	public long getTotalAllocatedMemory() {
		if(!UnsafeAdapterOld.trackMem) return -1L;
		return UnsafeAdapterOld.totalMemoryAllocated.get()-UnsafeAdapterOld.BASELINE_MEM;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getAlignedMemoryOverhead()
	 */
	public long getAlignedMemoryOverhead() {
		if(!UnsafeAdapterOld.trackMem) return -1L;
		return UnsafeAdapterOld.totalAlignmentOverhead.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getTotalAllocationCount()
	 */
	@Override
	public int getTotalAllocationCount() {
		if(!UnsafeAdapterOld.trackMem) return -1;
		return UnsafeAdapterOld.memoryAllocations.size()-UnsafeAdapterOld.BASELINE_ALLOCS;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getTotalAllocatedMemoryKb()
	 */
	@Override
	public long getTotalAllocatedMemoryKb() {
		if(!UnsafeAdapterOld.trackMem) return -1L;
		long t = UnsafeAdapterOld.totalMemoryAllocated.get();
		if(t<1) return 0L;
		return t/1024;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getTotalAllocatedMemoryMb()
	 */
	@Override
	public long getTotalAllocatedMemoryMb() {
		if(!UnsafeAdapterOld.trackMem) return -1L;
		long t = UnsafeAdapterOld.totalMemoryAllocated.get();
		if(t<1) return 0L;
		return t/1024/1024;
	}
	
	/**
	 * Returns the number of retained phantom references to memory allocations
	 * @return the number of retained phantom references to memory allocations
	 */
	public int getPendingRefs() {
		return UnsafeAdapterOld.deAllocs.size();
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