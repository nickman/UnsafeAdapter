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

import java.lang.management.ManagementFactory;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import com.heliosapm.unsafe.UnsafeAdapterOld.MemoryAllocationReference;

import sun.misc.Unsafe;

/**
 * <p>Title: DefaultUnsafeAdapterImpl</p>
 * <p>Description: Provides the default {@link sun.misc.Unsafe} invocations for the {@link UnsafeAdapter}.
 * Memory allocations are to direct memory and unchecked.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.DefaultUnsafeAdapterImpl</code></p>
 */

class DefaultUnsafeAdapterImpl implements Runnable {
	// =========================================================
	//  Singleton
	// =========================================================
	/** The singleton instance */
	private static volatile DefaultUnsafeAdapterImpl instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	// =========================================================
	//  Statics
	// =========================================================
	
    /** The unsafe instance */    
	static final Unsafe UNSAFE = UnsafeAdapter.theUNSAFE;
    /** Indicates if the 5 param copy memory is supported */
    public static final boolean FIVE_COPY = UnsafeAdapter.FIVE_COPY;
    /** Indicates if the 4 param set memory is supported */
    public static final boolean FOUR_SET = UnsafeAdapter.FOUR_SET;	
	
	/** The system prop indicating that allocations should be tracked */
	public static final String TRACK_ALLOCS_PROP = "unsafe.allocations.track";
	/** The system prop indicating that allocations should be alligned */
	public static final String ALIGN_ALLOCS_PROP = "unsafe.allocations.align";
	/** The debug agent library signature */
	public static final String AGENT_LIB = "-agentlib:";	
	/** The legacy debug agent library signature */
	public static final String LEGACY_AGENT_LIB = "-Xrunjdwp:";
    /** Serial number factory for memory allocationreferences */
    private static final AtomicLong refIndexFactory = new AtomicLong(0L);
    /** Empty long[] array const */
    private static final long[][] EMPTY_ADDRESSES = {{}};
    /** Empty MemoryAllocationReference list const */
    private static final List<MemoryAllocationReference> EMPTY_ALLOC_LIST = Collections.unmodifiableList(new ArrayList<MemoryAllocationReference>(0));
    /** A map of memory allocation references keyed by an internal counter */
    protected static final NonBlockingHashMapLong<MemoryAllocationReference> deAllocs = new NonBlockingHashMapLong<MemoryAllocationReference>(1024, false);
	/** Serial number factory for cleaner threads */
	private static final AtomicLong cleanerSerial = new AtomicLong(0L); 

	// =========================================================
	//  Instance
	// =========================================================	
	
	/** The configured native memory tracking enablement  */
	public final boolean trackMem;
	/** The configured native memory alignment enablement  */
	public final boolean alignMem;
	/** The unsafe memory management MBean */
	final UnsafeMemoryMBean unsafeMemoryStats = null;
	/** A map of memory allocation sizes keyed by the address */
	final NonBlockingHashMapLong<long[]> memoryAllocations;
	/** The total native memory allocation */
	final AtomicLong totalMemoryAllocated;
	/** The total native memory allocation overhead for alignment */
	final AtomicLong totalAlignmentOverhead;
	
	/** The reference queue where collected allocations go */
	final ReferenceQueue<? super DeAllocateMe> refQueue;
	/** The reference cleaner thread */
	Thread cleanerThread;
	
	public static final int MAX_ALIGNED_MEM_32 = 1073741824;


	
	/**
	 * Acquires the singleton DefaultUnsafeAdapterImpl and initializes it on first access.
	 * @return the singleton DefaultUnsafeAdapterImpl
	 */
	public static DefaultUnsafeAdapterImpl getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new DefaultUnsafeAdapterImpl(); 
				}
			}
		}
		return instance;
	}

	

	
	/**
	 * Creates a new DefaultUnsafeAdapterImpl
	 */
	protected DefaultUnsafeAdapterImpl() {		
		// =========================================================
		// Start the cleaner thread
		// =========================================================
		cleanerThread = new Thread(this, "UnsafeMemoryAllocationCleaner#" + cleanerSerial.incrementAndGet());
		cleanerThread.setDaemon(true);
		cleanerThread.setPriority(Thread.MAX_PRIORITY);
		cleanerThread.start();

		// =========================================================
		// Create the reference queue for collected deallocation refs
		// =========================================================        
		refQueue = new ReferenceQueue<DeAllocateMe>();
		// =========================================================
		// Read the system props to get the configuration
		// =========================================================        
    	trackMem = System.getProperties().containsKey(TRACK_ALLOCS_PROP) || isDebugAgentLoaded();   
    	alignMem = System.getProperties().containsKey(ALIGN_ALLOCS_PROP);
		// =========================================================
		// Initialize the memory allocation tracking if enabled.
    	// Otherwise, set to null.
		// =========================================================            	
    	if(trackMem) {
    		
    		totalMemoryAllocated = new AtomicLong(0L);
    		totalAlignmentOverhead = new AtomicLong(0L);
        	if(this.getClass()==DefaultUnsafeAdapterImpl.class) {
        		memoryAllocations = new NonBlockingHashMapLong<long[]>(1024, true);
        	} else {
        		memoryAllocations = null;
        	}
    	} else {
    		memoryAllocations = null;
    		totalMemoryAllocated = null;
    		totalAlignmentOverhead = null;    		    		
    	}    	
	}

//	Memory : 136
//	Allocations : 2
//	Pending : 2
	
	
	/**
	 * The cleaner thread entry point.
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		log("Starting Unsafe Cleaner Thread [%s]", Thread.currentThread().getName());
		boolean terminating = false;
		while(true) {
			try {
				MemoryAllocationReference ref = (MemoryAllocationReference)refQueue.remove();
				if(ref!=null) {
					ref.close();
				}
				if(terminating) {
					if(getPending()==0) break;
				}
			} catch (InterruptedException e) {
				if(getPending()==0) break;
				terminating=true;
			} catch (Exception e) {
				loge("Unexpected exception [%s] in cleaner loop. Will Continue.", e);
			}
		}			
		log("Unsafe Cleaner Thread [%s] Terminated", Thread.currentThread().getName());
	}
	
	/**
	 * Returns the number of pending references in the reference queue
	 * @return the number of pending references
	 */
	public int getPending() {
		return -1;
	}
	
	/**
	 * Terminates this adapter.
	 * <b>TEST HOOK ONLY!</b>. Not intended for regular use.
	 */
	void shutdown() {
		
	}
	
	/**
	 * Determines if this JVM is running with the debug agent enabled
	 * @return true if this JVM is running with the debug agent enabled, false otherwise
	 */
	public static boolean isDebugAgentLoaded() {
		List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
		for(String s: inputArguments) {
			if(s.trim().startsWith(AGENT_LIB) || s.trim().startsWith(LEGACY_AGENT_LIB)) return true;
		}
		return false;
	}
	
	//===========================================================================================================
	//	Allocate Memory Ops
	//===========================================================================================================	

	/**
	 * Allocates a new block of native memory, of the given size in bytes. 
	 * The contents of the memory are uninitialized; they will generally be garbage.
	 * The resulting native pointer will never be zero, and will be aligned for all value types.  
	 * Dispose of this memory by calling #freeMemory , or resize it with #reallocateMemory.
	 * @param size The size of the block of memory to allocate in bytes
	 * @return The address of the allocated memory block
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	public long allocateMemory(long size) {
		return _allocateMemory(size, 0L, null);
	}
	
	/**
	 * Allocates a chunk of memory and returns its address
	 * @param size The number of bytes to allocate
	 * @param dealloc The optional deallocatable to register for deallocation when it becomes phantom reachable
	 * @return The address of the allocated memory
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	public long allocateMemory(long size, DeAllocateMe dealloc) {
		return _allocateMemory(size, 0L, dealloc);
	}	
	
	
	/**
	 * Allocates a new block of cache-line aligned native memory, of the given size in bytes rounded up to the nearest power of 2. 
	 * The contents of the memory are uninitialized; they will generally be garbage.
	 * The resulting native pointer will never be zero, and will be aligned for all value types.  
	 * Dispose of this memory by calling #freeMemory , or resize it with #reallocateMemory.
	 * @param size The size of the block of memory to allocate in bytes
	 * @return The address of the allocated memory block
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	public long allocateAlignedMemory(long size) {
		return allocateAlignedMemory(size, null);
	}
	
	/**
	 * Allocates a new block of cache-line aligned native memory, of the given size in bytes rounded up to the nearest power of 2. 
	 * The contents of the memory are uninitialized; they will generally be garbage.
	 * The resulting native pointer will never be zero, and will be aligned for all value types.  
	 * Dispose of this memory by calling #freeMemory , or resize it with #reallocateMemory.
	 * @param size The size of the block of memory to allocate in bytes
	 * @param dealloc The optional deallocatable to register for deallocation when it becomes phantom reachable
	 * @return The address of the allocated memory block
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	public long allocateAlignedMemory(long size, DeAllocateMe dealloc) {
		if(alignMem) {
			long alignedSize = UnsafeAdapter.ADDRESS_SIZE==4 ? findNextPositivePowerOfTwo((int)size) : findNextPositivePowerOfTwo((int)size);
			return _allocateMemory(alignedSize, alignedSize-size, dealloc);
		}
		return _allocateMemory(size, 0L, dealloc);		
	}
	
    /**
     * Finds the next <b><code>power of 2</code></b> higher or equal to than the passed value.
     * @param value The initial value
     * @return the pow2
     */
    public static int findNextPositivePowerOfTwo(final int value) {
    	return  1 << (32 - Integer.numberOfLeadingZeros(value - 1));
	}    
    
    /**
     * Finds the next <b><code>power of 2</code></b> higher or equal to than the passed value.
     * @param value The initial value
     * @return the pow2
     */
    public static long findNextPositivePowerOfTwo(final long value) {
    	return  1 << (64 - Long.numberOfLeadingZeros(value - 1));
	}    
    

	/**
	 * Allocates a chunk of memory and returns its address
	 * @param size The number of bytes to allocate
	 * @param alignmentOverhead The number of bytes allocated in excess of requested for alignment
	 * @param deallocator The reference to the object which when collected will deallocate the referenced addresses
	 * @return The address of the allocated memory
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	@SuppressWarnings("unused")
	long _allocateMemory(long size, long alignmentOverhead, DeAllocateMe deallocator) {
		long address = UNSAFE.allocateMemory(size);
		if(trackMem) {		
			memoryAllocations.put(address, new long[]{size, alignmentOverhead});
			totalMemoryAllocated.addAndGet(size);
			totalAlignmentOverhead.addAndGet(alignmentOverhead);
		}
    	if(deallocator!=null) {
    		long[][] addresses = deallocator.getAddresses();
    		if(addresses==null || addresses.length==0) {
    			new MemoryAllocationReference(deallocator);
    		}
    	}		
		return address;
	}
	
	//===========================================================================================================
	//	Free Memory Ops
	//===========================================================================================================	
	
	
	/**
	 * Frees the memory allocated at the passed address
	 * @param address The address of the memory to free
	 * @see sun.misc.Unsafe#freeMemory(long)
	 */
	void freeMemory(long address) {
		if(trackMem) {
			// ==========================================================
			//  Subtract pervious allocation
			// ==========================================================				
			long[] alloc = memoryAllocations.remove(address);
			if(alloc!=null) {				
				totalMemoryAllocated.addAndGet(-1L * alloc[0]);
				totalAlignmentOverhead.addAndGet(-1L * alloc[1]);
			}
		}		
		UNSAFE.freeMemory(address);
	}
	
	//===========================================================================================================
	//	Copy Memory Ops
	//===========================================================================================================	
	

	/**
	 * Sets all bytes in a given block of memory to a copy of another block. This provides a single-register addressing mode, 
	 * as discussed in #getInt(Object,long). 
	 * Equivalent to copyMemory(null, srcAddress, null, destAddress, bytes).
	 * @param srcOffset The source object offset, or an absolute adress if srcBase is null
	 * @param destOffset The destination object offset, or an absolute adress if destBase is null
	 * @param bytes The bytes to copy
	 * @see sun.misc.Unsafe#copyMemory(long, long, long)
	 */
	public void copyMemory(long srcOffset, long destOffset, long bytes) {
		UNSAFE.copyMemory(srcOffset, destOffset, bytes);
	}

	/**
	 * Sets all bytes in a given block of memory to a copy of another block.
	 * 
	 * This method determines each block's base address by means of two parameters,
	 * and so it provides (in effect) a double-register addressing mode,
	 * as discussed in #getInt(Object,long) .  When the object reference is null,
	 * the offset supplies an absolute base address.
	 * 
	 * The transfers are in coherent (atomic) units of a size determined
	 * by the address and length parameters.  If the effective addresses and
	 * length are all even modulo 8, the transfer takes place in 'long' units.
	 * If the effective addresses and length are (resp.) even modulo 4 or 2,
	 * the transfer takes place in units of 'int' or 'short'.
	 * @param srcBase The source object. Can be null, in which case srcOffset will be assumed to be an absolute address.
	 * @param srcOffset The source object offset, or an absolute adress if srcBase is null
	 * @param destBase The destination object. Can be null, in which case destOffset will be assumed to be an absolute address.
	 * @param destOffset The destination object offset, or an absolute adress if destBase is null
	 * @param bytes The bytes to copy
	 * @see sun.misc.Unsafe#copyMemory(java.lang.Object, long, java.lang.Object, long, long)
	 */
	public void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
    	if(UnsafeAdapter.FIVE_COPY) {
    		UNSAFE.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
    	} else {
    		UNSAFE.copyMemory(srcOffset + UnsafeAdapter.getAddressOf(srcBase), destOffset + UnsafeAdapter.getAddressOf(destBase), bytes);
    	}		
	}

	//===========================================================================================================
	//	Address Read Ops
	//===========================================================================================================	

	
	/**
	 * Fetches a native pointer from a given memory address.  If the address is
	 * zero, or does not point into a block obtained from #allocateMemory , the results are undefined.
	 * 
	 * If the native pointer is less than 64 bits wide, it is extended as
	 * an unsigned number to a Java long.  The pointer may be indexed by any
	 * given byte offset, simply by adding that offset (as a simple integer) to
	 * the long representing the pointer.  The number of bytes actually read
	 * from the target address maybe determined by consulting #addressSize .
	 * @param address The address to read the address from
	 * @return the address read 
	 * @see sun.misc.Unsafe#getAddress(long)
	 */
	public long getAddress(long address) {
		return UNSAFE.getAddress(address);
	}
	
	//===========================================================================================================
	//	Byte Read Ops
	//===========================================================================================================		

	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory} , the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 */
	public byte getByte(long address) {
		return UNSAFE.getByte(address);
	}
	
	/**
	 * Volatile version of {@link #getByte(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByteVolatile(Object, long)
	 */
	public byte getByteVolatile(long address) {
		return UNSAFE.getByteVolatile(null, address);
	}
	
	
	//===========================================================================================================
	//	Boolean Read Ops
	//===========================================================================================================	
	
	
	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory} , the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 */
	public boolean getBoolean(long address) {
		return UNSAFE.getBoolean(null, address);
	}

	/**
	 * Volatile version of {@link UnsafeAdapter#getBoolean(Object, long)} 
	 * @param address The address to read the boolean from
	 * @return the read boolean value
	 */
	public boolean getBooleanVolatile(long address) {
		return UNSAFE.getBooleanVolatile(null, address);
	}
	
	//===========================================================================================================
	//	Short Read Ops
	//===========================================================================================================		
	
	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory}, the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 */
	public short getShort(long address) {
		return UNSAFE.getShort(address);
	}
	
	/**
	 * Volatile version of {@link #getShort(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByteVolatile(Object, long)
	 */
	public short getShortVolatile(long address) {
		return UNSAFE.getShortVolatile(null, address);
	}
	
	//===========================================================================================================
	//	Char Read Ops
	//===========================================================================================================

	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory}, the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getChar(long)
	 */
	public char getChar(long address) {
		return UNSAFE.getChar(address);
	}


	/**
	 * Volatile version of {@link #getShort(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getCharVolatile(java.lang.Object, long)
	 */
	public char getCharVolatile(long address) {
		return UNSAFE.getCharVolatile(null, address);
	}
	
	
	//===========================================================================================================
	//	Int Read Ops
	//===========================================================================================================
	
	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory} , the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getInt(long)
	 */
	public int getInt(long address) {
		return UNSAFE.getInt(address);
	}


	/**
	 * Volatile version of {@link #getInt(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getIntVolatile(java.lang.Object, long)
	 */
	public int getIntVolatile(long address) {
		return UNSAFE.getIntVolatile(null, address);
	}
	
	//===========================================================================================================
	//	Float Read Ops
	//===========================================================================================================
	
	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory} , the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getInt(long)
	 */
	public float getFloat(long address) {
		return UNSAFE.getFloat(address);
	}


	/**
	 * Volatile version of {@link #getFloat(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloatVolatile(java.lang.Object, long)
	 */
	public float getFloatVolatile(long address) {
		return UNSAFE.getFloatVolatile(null, address);
	}
	
	//===========================================================================================================
	//	Long Read Ops
	//===========================================================================================================
	
	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory} , the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getLong(long)
	 */
	public long getLong(long address) {
		return UNSAFE.getLong(address);
	}


	/**
	 * Volatile version of {@link #getFloat(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloatVolatile(java.lang.Object, long)
	 */
	public long getLongVolatile(long address) {
		return UNSAFE.getLongVolatile(null, address);
	}
	
	//===========================================================================================================
	//	Double Read Ops
	//===========================================================================================================
	
	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory} , the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getLong(long)
	 */
	public long getLong(long address) {
		return UNSAFE.getLong(address);
	}


	/**
	 * Volatile version of {@link #getFloat(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloatVolatile(java.lang.Object, long)
	 */
	public long getLongVolatile(long address) {
		return UNSAFE.getLongVolatile(null, address);
	}

	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#getDouble(long)
	 */
	public double getDouble(long arg0) {
		return UNSAFE.getDouble(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getDouble(java.lang.Object, int)
	 */
	public double getDouble(Object arg0, int arg1) {
		return UNSAFE.getDouble(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getDouble(java.lang.Object, long)
	 */
	public double getDouble(Object arg0, long arg1) {
		return UNSAFE.getDouble(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getDoubleVolatile(java.lang.Object, long)
	 */
	public double getDoubleVolatile(Object arg0, long arg1) {
		return UNSAFE.getDoubleVolatile(arg0, arg1);
	}



	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getLoadAverage(double[], int)
	 */
	public int getLoadAverage(double[] arg0, int arg1) {
		return UNSAFE.getLoadAverage(arg0, arg1);
	}


	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getObject(java.lang.Object, int)
	 */
	public Object getObject(Object arg0, int arg1) {
		return UNSAFE.getObject(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getObject(java.lang.Object, long)
	 */
	public Object getObject(Object arg0, long arg1) {
		return UNSAFE.getObject(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getObjectVolatile(java.lang.Object, long)
	 */
	public Object getObjectVolatile(Object arg0, long arg1) {
		return UNSAFE.getObjectVolatile(arg0, arg1);
	}





	/**
	 * @return
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return UNSAFE.hashCode();
	}

	/**
	 * @param arg0
	 * @see sun.misc.Unsafe#monitorEnter(java.lang.Object)
	 */
	public void monitorEnter(Object arg0) {
		UNSAFE.monitorEnter(arg0);
	}

	/**
	 * @param arg0
	 * @see sun.misc.Unsafe#monitorExit(java.lang.Object)
	 */
	public void monitorExit(Object arg0) {
		UNSAFE.monitorExit(arg0);
	}


	/**
	 * @return
	 * @see sun.misc.Unsafe#pageSize()
	 */
	public int pageSize() {
		return UNSAFE.pageSize();
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#park(boolean, long)
	 */
	public void park(boolean arg0, long arg1) {
		UNSAFE.park(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#putAddress(long, long)
	 */
	public void putAddress(long arg0, long arg1) {
		UNSAFE.putAddress(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putBoolean(java.lang.Object, int, boolean)
	 */
	public void putBoolean(Object arg0, int arg1, boolean arg2) {
		UNSAFE.putBoolean(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putBoolean(java.lang.Object, long, boolean)
	 */
	public void putBoolean(Object arg0, long arg1, boolean arg2) {
		UNSAFE.putBoolean(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putBooleanVolatile(java.lang.Object, long, boolean)
	 */
	public void putBooleanVolatile(Object arg0, long arg1, boolean arg2) {
		UNSAFE.putBooleanVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#putByte(long, byte)
	 */
	public void putByte(long arg0, byte arg1) {
		UNSAFE.putByte(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putByte(java.lang.Object, int, byte)
	 */
	public void putByte(Object arg0, int arg1, byte arg2) {
		UNSAFE.putByte(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putByte(java.lang.Object, long, byte)
	 */
	public void putByte(Object arg0, long arg1, byte arg2) {
		UNSAFE.putByte(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putByteVolatile(java.lang.Object, long, byte)
	 */
	public void putByteVolatile(Object arg0, long arg1, byte arg2) {
		UNSAFE.putByteVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#putChar(long, char)
	 */
	public void putChar(long arg0, char arg1) {
		UNSAFE.putChar(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putChar(java.lang.Object, int, char)
	 */
	public void putChar(Object arg0, int arg1, char arg2) {
		UNSAFE.putChar(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putChar(java.lang.Object, long, char)
	 */
	public void putChar(Object arg0, long arg1, char arg2) {
		UNSAFE.putChar(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putCharVolatile(java.lang.Object, long, char)
	 */
	public void putCharVolatile(Object arg0, long arg1, char arg2) {
		UNSAFE.putCharVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#putDouble(long, double)
	 */
	public void putDouble(long arg0, double arg1) {
		UNSAFE.putDouble(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putDouble(java.lang.Object, int, double)
	 */
	public void putDouble(Object arg0, int arg1, double arg2) {
		UNSAFE.putDouble(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putDouble(java.lang.Object, long, double)
	 */
	public void putDouble(Object arg0, long arg1, double arg2) {
		UNSAFE.putDouble(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putDoubleVolatile(java.lang.Object, long, double)
	 */
	public void putDoubleVolatile(Object arg0, long arg1, double arg2) {
		UNSAFE.putDoubleVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#putFloat(long, float)
	 */
	public void putFloat(long arg0, float arg1) {
		UNSAFE.putFloat(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putFloat(java.lang.Object, int, float)
	 */
	public void putFloat(Object arg0, int arg1, float arg2) {
		UNSAFE.putFloat(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putFloat(java.lang.Object, long, float)
	 */
	public void putFloat(Object arg0, long arg1, float arg2) {
		UNSAFE.putFloat(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putFloatVolatile(java.lang.Object, long, float)
	 */
	public void putFloatVolatile(Object arg0, long arg1, float arg2) {
		UNSAFE.putFloatVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#putInt(long, int)
	 */
	public void putInt(long arg0, int arg1) {
		UNSAFE.putInt(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putInt(java.lang.Object, int, int)
	 */
	public void putInt(Object arg0, int arg1, int arg2) {
		UNSAFE.putInt(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putInt(java.lang.Object, long, int)
	 */
	public void putInt(Object arg0, long arg1, int arg2) {
		UNSAFE.putInt(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putIntVolatile(java.lang.Object, long, int)
	 */
	public void putIntVolatile(Object arg0, long arg1, int arg2) {
		UNSAFE.putIntVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#putLong(long, long)
	 */
	public void putLong(long arg0, long arg1) {
		UNSAFE.putLong(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putLong(java.lang.Object, int, long)
	 */
	public void putLong(Object arg0, int arg1, long arg2) {
		UNSAFE.putLong(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putLong(java.lang.Object, long, long)
	 */
	public void putLong(Object arg0, long arg1, long arg2) {
		UNSAFE.putLong(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putLongVolatile(java.lang.Object, long, long)
	 */
	public void putLongVolatile(Object arg0, long arg1, long arg2) {
		UNSAFE.putLongVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putObject(java.lang.Object, int, java.lang.Object)
	 */
	public void putObject(Object arg0, int arg1, Object arg2) {
		UNSAFE.putObject(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putObject(java.lang.Object, long, java.lang.Object)
	 */
	public void putObject(Object arg0, long arg1, Object arg2) {
		UNSAFE.putObject(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putObjectVolatile(java.lang.Object, long, java.lang.Object)
	 */
	public void putObjectVolatile(Object arg0, long arg1, Object arg2) {
		UNSAFE.putObjectVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putOrderedInt(java.lang.Object, long, int)
	 */
	public void putOrderedInt(Object arg0, long arg1, int arg2) {
		UNSAFE.putOrderedInt(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putOrderedLong(java.lang.Object, long, long)
	 */
	public void putOrderedLong(Object arg0, long arg1, long arg2) {
		UNSAFE.putOrderedLong(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putOrderedObject(java.lang.Object, long, java.lang.Object)
	 */
	public void putOrderedObject(Object arg0, long arg1, Object arg2) {
		UNSAFE.putOrderedObject(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @see sun.misc.Unsafe#putShort(long, short)
	 */
	public void putShort(long arg0, short arg1) {
		UNSAFE.putShort(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @deprecated
	 * @see sun.misc.Unsafe#putShort(java.lang.Object, int, short)
	 */
	public void putShort(Object arg0, int arg1, short arg2) {
		UNSAFE.putShort(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putShort(java.lang.Object, long, short)
	 */
	public void putShort(Object arg0, long arg1, short arg2) {
		UNSAFE.putShort(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#putShortVolatile(java.lang.Object, long, short)
	 */
	public void putShortVolatile(Object arg0, long arg1, short arg2) {
		UNSAFE.putShortVolatile(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
	 */
	public long reallocateMemory(long arg0, long arg1) {
		return UNSAFE.reallocateMemory(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#setMemory(long, long, byte)
	 */
	public void setMemory(long arg0, long arg1, byte arg2) {
		UNSAFE.setMemory(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @see sun.misc.Unsafe#setMemory(java.lang.Object, long, long, byte)
	 */
	public void setMemory(Object arg0, long arg1, long arg2, byte arg3) {
		UNSAFE.setMemory(arg0, arg1, arg2, arg3);
	}

	/**
	 * @param arg0
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#staticFieldBase(java.lang.Class)
	 */
	public Object staticFieldBase(Class arg0) {
		return UNSAFE.staticFieldBase(arg0);
	}

	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#staticFieldBase(java.lang.reflect.Field)
	 */
	public Object staticFieldBase(Field arg0) {
		return UNSAFE.staticFieldBase(arg0);
	}


	/**
	 * @param arg0
	 * @see sun.misc.Unsafe#throwException(java.lang.Throwable)
	 */
	public void throwException(Throwable arg0) {
		UNSAFE.throwException(arg0);
	}



	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#tryMonitorEnter(java.lang.Object)
	 */
	public boolean tryMonitorEnter(Object arg0) {
		return UNSAFE.tryMonitorEnter(arg0);
	}

	/**
	 * @param arg0
	 * @see sun.misc.Unsafe#unpark(java.lang.Object)
	 */
	public void unpark(Object arg0) {
		UNSAFE.unpark(arg0);
	}
	
	/**
	 * Low maintenance out logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	void log(Object fmt, Object...args) {
		System.out.println(String.format("[UnsafeAdapter]" + fmt.toString(), args));
	}
	
	/**
	 * Low maintenance err logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	void loge(Object fmt, Object...args) {
		System.err.println(String.format("[UnsafeAdapter]" + fmt.toString(), args));
		if(args!=null && args.length>0 && args[args.length-1] instanceof Throwable) {
			System.err.println("Stack trace follows....");
			((Throwable)args[args.length-1]).printStackTrace(System.err);			
		}
	}
	
    /**
     * <p>Title: MemoryAllocationReference</p>
     * <p>Description: A phantom reference extension for tracking memory allocations without preventing them from being enqueued for de-allocation</p> 
     * <p>Company: Helios Development Group LLC</p>
     * @author Whitehead (nwhitehead AT heliosdev DOT org)
     * <p><code>com.heliosapm.unsafe.DefaultUnsafeAdapterImpl.MemoryAllocationReference</code></p>
     */
    class MemoryAllocationReference extends PhantomReference<DeAllocateMe> {
    	/** The index of this reference */
    	private final long index = refIndexFactory.incrementAndGet();
    	/** The memory addresses owned by this reference */
    	private final long[][] addresses;
    	
		/**
		 * Creates a new MemoryAllocationReference
		 * @param referent the memory address holder
		 */
		public MemoryAllocationReference(final DeAllocateMe referent) {
			super(referent, refQueue);
			addresses = referent==null ? EMPTY_ADDRESSES : referent.getAddresses();
			deAllocs.put(index, this);
		}    	
		
		/**
		 * Deallocates the referenced memory blocks 
		 */
		public void close() {
			for(long[] address: addresses) {
				if(address[0]>0) {
					freeMemory(address[0]);
					address[0] = 0L;
				}
				deAllocs.remove(index);
			}
			super.clear();
		}
    }
	
    	
}
