package com.heliosapm.unsafe;

import java.util.Map;

/**
 * <p>Title: UnsafeMemoryMBean</p>
 * <p>Description: JMX MBean interface for unsafe memory allocation trackers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapter.UnsafeMemoryMBean</code></p>
 */
public interface UnsafeMemoryMBean {
	
	/** The map key for the total memory allocation in bytes */
	public static final String ALLOC_MEM = "Memory";
	/** The map key for the total memory allocation in KB */
	public static final String ALLOC_MEMK = "MemoryKb";
	/** The map key for the total memory allocation in MB */
	public static final String ALLOC_MEMM = "MemoryMb";
	/** The map key for the total number of current allocations */
	public static final String ALLOC_COUNT = "Allocations";
	/** The map key for the pending phantom references */
	public static final String PENDING_COUNT = "Pending";
	
	/**
	 * Returns the size in bytes of a native pointer
	 * @return the size in bytes of a native pointer
	 */
	public int getAddressSize();
	
	/**
	 * Returns the size in bytes of a native memory page
	 * @return the size in bytes of a native memory page
	 */
	public int getPageSize();
	
	/**
	 * Returns a map of unsafe memory stats keyed by the stat name
	 * @return a map of unsafe memory stats
	 */
	public Map<String, Long> getState();
	
	/**
	 * Returns the total off-heap allocated memory in bytes, not including the base line defined in {@link UnsafeAdapter#BASELINE_MEM}
	 * @return the total off-heap allocated memory
	 */
	public long getTotalAllocatedMemory();
	
	/**
	 * Returns the total aligned memory overhead in bytes
	 * @return the total aligned memory overhead in bytes
	 */
	public long getAlignedMemoryOverhead();
	
	
	/**
	 * Returns the total off-heap allocated memory in Kb
	 * @return the total off-heap allocated memory
	 */
	public long getTotalAllocatedMemoryKb();
	
	/**
	 * Returns the total off-heap allocated memory in Mb
	 * @return the total off-heap allocated memory
	 */
	public long getTotalAllocatedMemoryMb();

	/**
	 * Returns the total number of existing allocations, not including the base line defined in {@link UnsafeAdapter#BASELINE_ALLOCS}
	 * @return the total number of existing allocations
	 */
	public int getTotalAllocationCount();
	    	
	/**
	 * Returns the number of retained phantom references to memory allocations
	 * @return the number of retained phantom references to memory allocations
	 */
	public int getPendingRefs();
	
}