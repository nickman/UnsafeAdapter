package com.heliosapm.unsafe;

import java.util.Map;

/**
 * <p>Title: MemoryMBean</p>
 * <p>Description: JMX MBean interface for memory allocation trackers</p>
 * <p>Attributes referencing managed memory values will return <b><code>-1</code></b> 
 * if memory tracking is not enabled.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.MemoryMBean</code></p>
 */
public interface MemoryMBean {
	
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
	/** The map key for the total memory alignedment overhead in bytes */
	public static final String ALLOC_OVER = "AllocationOverhead";
	/** The map key for the reference queue size */
	public static final String REFQ_SIZE= "RefQSize";
	
	/**
	 * Indicates if the safe memory adapter is enabled
	 * @return true if the safe memory adapter is enabled, false otherwise
	 */
	public boolean isSafeMemory();
	
	/**
	 * Indicates if memory allocation tracking is enabled
	 * @return true if memory allocation tracking is enabled, false otherwise
	 */
	public boolean isTrackingEnabled();
	
	/**
	 * Indicates if the JVM supports the five parameter memory copy operation
	 * @return true if the JVM supports the five parameter memory copy operation, false otherwise
	 */
	public boolean isFiveCopy();
	
	/**
	 * Indicates if the JVM supports the four parameter memory set operation
	 * @return true if the JVM supports the four parameter memory set operation, false otherwise
	 */
	public boolean isFourSet();
	
	/**
	 * Indicates if cache-line memory alignment is enabled
	 * @return true if cache-line memory alignment is enabled, false otherwise
	 */
	public boolean isAlignmentEnabled();
	
	/**
	 * Returns the size of an address in bytes for the current JVM
	 * @return the size of an address
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
	 * Returns the total off-heap allocated memory in bytes, not including the base line defined in {@link UnsafeAdapterOld#BASELINE_MEM}
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
	 * Returns the total number of existing allocations, not including the base line defined in {@link UnsafeAdapterOld#BASELINE_ALLOCS}
	 * @return the total number of existing allocations
	 */
	public int getTotalAllocationCount();
	    	
	/**
	 * Returns the number of retained phantom references to memory allocations
	 * @return the number of retained phantom references to memory allocations
	 */
	public int getPendingRefs();
	
	/**
	 * Returns the size of the reference queue
	 * @return the size of the reference queue
	 */
	public long getReferenceQueueSize();
	
}