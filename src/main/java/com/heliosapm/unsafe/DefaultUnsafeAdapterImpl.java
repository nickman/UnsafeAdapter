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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import sun.misc.Unsafe;

/**
 * <p>Title: DefaultUnsafeAdapterImpl</p>
 * <p>Description: Provides the default {@link sun.misc.Unsafe} invocations for the {@link UnsafeAdapter}.
 * Memory allocations are to direct memory and unchecked.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.DefaultUnsafeAdapterImpl</code></p>
 */
@SuppressWarnings("restriction")
public class DefaultUnsafeAdapterImpl implements Runnable, DefaultUnsafeAdapterImplMBean {
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
	
	/** The debug agent library signature */
	public static final String AGENT_LIB = "-agentlib:";	
	/** The legacy debug agent library signature */
	public static final String LEGACY_AGENT_LIB = "-Xrunjdwp:";
    /** Serial number factory for memory allocationreferences */
    private static final AtomicLong refIndexFactory = new AtomicLong(0L);
    /** Empty long[] array const */
    private static final long[][] EMPTY_ADDRESSES = {{}};
    /** A map of memory allocation references keyed by an internal counter */
    protected static final NonBlockingHashMapLong<MemoryAllocationReference> deAllocs = new NonBlockingHashMapLong<MemoryAllocationReference>(1024, false);
	/** Serial number factory for cleaner threads */
	private static final AtomicLong cleanerSerial = new AtomicLong(0L);
	
	/** The field for accessing the pending queue length */
	private static final Field queueLengthField;
	/** The field for accessing the pending queue lock */
	private static final Field queueLockField;


	// =========================================================
	//  Instance
	// =========================================================	
	
	/** The configured native memory tracking enablement  */
	public final boolean trackMem;
	/** The configured native memory alignment enablement  */
	public final boolean alignMem;
	/** The unsafe memory management MBean */
	final MemoryMBean unsafeMemoryStats = null;
	/** A map of memory allocation sizes keyed by the address */
	final NonBlockingHashMapLong<long[]> memoryAllocations;
	/** The total native memory allocation */
	final AtomicLong totalMemoryAllocated;
	/** The total native memory allocation overhead for alignment */
	final AtomicLong totalAlignmentOverhead;
	/** The object to synchronize on for accessing the pending queue length */
	private volatile Object queueLengthFieldLock;
	
	/** The reference queue where collected allocations go */
	final ReferenceQueue<? super DeAllocateMe> refQueue;
	/** The reference cleaner thread */
	Thread cleanerThread;
	
	
		
	static {
		try {
			queueLengthField = ReferenceQueue.class.getDeclaredField("queueLength");
			queueLengthField.setAccessible(true);
			queueLockField = ReferenceQueue.class.getDeclaredField("lock");
			queueLockField.setAccessible(true);
			//queueLengthFieldLock =f.get(refQueue); 
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}		
	}

	
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
	 * <b>TEST HOOK ONLY !</b>
	 * Don't use this unless you know what you're doing.
	 */
	@SuppressWarnings("unused")
	private final void reset() {
		log("***********  Resetting  ***********");		
		try {
			synchronized(lock) {
				JMXHelper.unregisterMBean(UnsafeAdapter.UNSAFE_MEM_OBJECT_NAME);
				Field instanceField = ReflectionHelper.setFieldEditable(getClass(), "instance");
				instanceField.set(null, null);
			}
		} catch (Throwable t) {
			loge("Failed to reset UnsafeAdapter", t);
		}
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
    	trackMem = System.getProperties().containsKey(UnsafeAdapter.TRACK_ALLOCS_PROP) || isDebugAgentLoaded();   
    	alignMem = System.getProperties().containsKey(UnsafeAdapter.ALIGN_ALLOCS_PROP);
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
    	registerJmx();
	}

	/**
	 * Registers the Unsafe Memory Allocator's JMX MBean
	 */
	protected void registerJmx() {
		try {
			JMXHelper.registerMBean(this, UnsafeAdapter.UNSAFE_MEM_OBJECT_NAME);
		} catch (Exception ex) {
			loge("Failed to register JMX MemoryMBean", ex);
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
		UnsafeAdapter.registerCleanerThread(Thread.currentThread());
		boolean terminating = false;
		while(true) {
			try {
				MemoryAllocationReference ref = (MemoryAllocationReference)refQueue.remove();
				if(ref!=null) {
					ref.close();
				}
				if(terminating) {
					if(getRefQueuePending()<1 && getPendingRefs()<1 ) break;
				}
			} catch (InterruptedException e) {
				if(getRefQueuePending()<1 && getPendingRefs()<1 ) break;
				terminating=true;
				Thread.currentThread().setName("[Terminating]" + Thread.currentThread().getName());
			} catch (Exception e) {
				loge("Unexpected exception [%s] in cleaner loop. Will Continue.", e);
			}
		}			
		log("Unsafe Cleaner Thread [%s] Terminated", Thread.currentThread().getName());
		UnsafeAdapter.removeCleanerThread(Thread.currentThread());
	}
	
	/**
	 * Returns the number of pending references in the reference queue
	 * @return the number of pending references
	 */
	public long getRefQueuePending() {
		if(queueLengthFieldLock==null) {
			synchronized(refQueue) {
				if(queueLengthFieldLock==null) {
					try {
						queueLengthFieldLock = queueLockField.get(refQueue);
					} catch (Exception x) {
						return -1L;
					}					
				}
			}
		}
		try {
			synchronized(queueLengthFieldLock) {
				return queueLengthField.getInt(refQueue);
			}
		} catch (Exception x) {
			return -1;
		}
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
     * @return the next pow2 without overrunning the type size
     */
    public static long findNextPositivePowerOfTwo(final long value) {
    	if(UnsafeAdapter.ADDRESS_SIZE==4) {
        	if(value > UnsafeAdapter.MAX_ALIGNED_MEM_32) return value;
        	return  1 << (32 - Integer.numberOfLeadingZeros((int)value - 1));    		
    	}
    	if(value > UnsafeAdapter.MAX_ALIGNED_MEM_64) return value;
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
	
	/**
	 * Resizes a new block of native memory, to the given size in bytes.
	 * <b>NOTE:</b>If the caller implements {@link DeAllocateMe} and expects the allocations
	 * to be automatically cleared, the returned value should overwrite the index of 
	 * the {@link DeAllocateMe}'s array where the previous address was.    
	 * @param address The address of the existing allocation
	 * @param size The size of the new allocation in bytes
	 * @return The address of the new allocation
	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
	 */
	public long reallocateMemory(long address, long size) {
		return _reallocateMemory(address, size, 0);
	}	
	
	/**
	 * Resizes a new block of aligned (if enabled) native memory, to the given size in bytes.
	 * <b>NOTE:</b>If the caller implements {@link DeAllocateMe} and expects the allocations
	 * to be automatically cleared, the returned value should overwrite the index of 
	 * the {@link DeAllocateMe}'s array where the previous address was.   
	 * @param address The address of the existing allocation
	 * @param size The size of the new allocation in bytes
	 * @return The address of the new allocation
	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
	 */
	public long reallocateAlignedMemory(long address, long size) {
		if(alignMem) {
			long actual = findNextPositivePowerOfTwo(size);
			return _reallocateMemory(address, actual, actual-size);
		} 
		return _reallocateMemory(address, size, 0);
	}	
	
	/**
	 * Resizes a new block of native memory, to the given size in bytes.
	 * <b>NOTE:</b>If the caller implements {@link DeAllocateMe} and expects the allocations
	 * to be automatically cleared, the returned value should overwrite the index of 
	 * the {@link DeAllocateMe}'s array where the previous address was.  
	 * @param address The address of the existing allocation
	 * @param size The size of the new allocation in bytes
	 * @param alignmentOverhead The established overhead of the alignment
	 * @return The address of the new allocation
	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
	 */
	long _reallocateMemory(long address, long size, long alignmentOverhead) {
		long newAddress = UNSAFE.reallocateMemory(address, size);
		if(trackMem) {
			// ==========================================================
			//  Subtract pervious allocation
			// ==========================================================				
			long[] alloc = memoryAllocations.remove(address);
			if(alloc!=null) {
				totalMemoryAllocated.addAndGet(-1L * alloc[0]);
				totalAlignmentOverhead.addAndGet(-1L * alloc[1]);
			}
			// ==========================================================
			//  Add new allocation
			// ==========================================================								
			memoryAllocations.put(newAddress, new long[]{size, alignmentOverhead});
			totalMemoryAllocated.addAndGet(size);
			totalAlignmentOverhead.addAndGet(alignmentOverhead);
		}
		return newAddress;
	}
	
	
	//===========================================================================================================
	//	Set Memory Ops
	//===========================================================================================================	
	

	/**
	 * Sets all bytes in a given block of memory to a fixed value (usually zero). 
	 * @param address The address to start the set memory at
	 * @param bytes The number of bytes to set
	 * @param value The value to write to each byte in the specified range
	 * @see sun.misc.Unsafe#setMemory(long, long, byte)
	 */
	public void setMemory(long address, long bytes, byte value) {
		UNSAFE.setMemory(address, bytes, value);
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
	//	Address Read/Write Ops
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
	
	/**
	 * Stores a native pointer into a given memory address.  If the address is
	 * zero, or does not point into a block obtained from #allocateMemory , the results are undefined.
	 * 
 	 * The number of bytes actually written at the target address maybe
	 * determined by consulting #addressSize . 
	 * @param targetAddress The address to write the address to
	 * @param address The address to write
	 * @see sun.misc.Unsafe#putAddress(long, long)
	 */
	public void putAddress(long targetAddress, long address) {
		UNSAFE.putAddress(targetAddress, address);
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
	 * Volatile version of {@link #getByte(long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByteVolatile(Object, long)
	 */
	public byte getByteVolatile(long address) {
		return UNSAFE.getByteVolatile(null, address);
	}
	
	
	//===========================================================================================================
	//	Byte Write  Ops
	//===========================================================================================================		
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putByte(long, byte)
	 */
	public void putByte(long address, byte value) {
		UNSAFE.putByte(address, value);
	}
	
	/**
	 * Volatile version of {@link #putByte(long, byte)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putByteVolatile(java.lang.Object, long, byte)
	 */
	public void putByteVolatile(long address, byte value) {
		UNSAFE.putByteVolatile(null, address, value);
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
	//	Byte Write Ops
	//===========================================================================================================		
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putBoolean(java.lang.Object, long, boolean)
	 */
	public void putBoolean(long address, boolean value) {
		UNSAFE.putBoolean(null, address, value);
	}
	
	/**
	 * Volatile version of {@link #putBoolean(long, boolean)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putBooleanVolatile(java.lang.Object, long, boolean)
	 */
	public void putBooleanVolatile(long address, boolean value) {
		UNSAFE.putBooleanVolatile(null, address, value);
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
	//	Short Write Ops
	//===========================================================================================================		
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putShort(java.lang.Object, long, short)
	 */
	public void putShort(long address, short value) {
		UNSAFE.putShort(address, value);
	}
	
	/**
	 * Volatile version of {@link #putShort(long, short)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putShortVolatile(java.lang.Object, long, short)
	 */
	public void putShortVolatile(long address, short value) {
		UNSAFE.putShortVolatile(null, address, value);
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
	//	Char Write Ops
	//===========================================================================================================		
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putChar(java.lang.Object, long, char)
	 */
	public void putChar(long address, char value) {
		UNSAFE.putChar(address, value);
	}
	
	/**
	 * Volatile version of {@link #putChar(long, char)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putCharVolatile(java.lang.Object, long, char)
	 */
	public void putCharVolatile(long address, char value) {
		UNSAFE.putCharVolatile(null, address, value);
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
	//	Int Write Ops
	//===========================================================================================================		
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putInt(long, int)
	 */
	public void putInt(long address, int value) {
		UNSAFE.putInt(address, value);
	}
	
	/**
	 * Volatile version of {@link #putInt(long, int)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putIntVolatile(java.lang.Object, long, int)
	 */
	public void putIntVolatile(long address, int value) {
		UNSAFE.putIntVolatile(null, address, value);
	}

	/**
	 * Ordered/Lazy version of #putIntVolatile(long, int) 
	 * @param offset The address to write to 
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putOrderedInt(java.lang.Object, long, int)
	 */
	public void putOrderedInt(long offset, int value) {
		UNSAFE.putOrderedInt(null, offset, value);
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
	//	Float Write Ops
	//===========================================================================================================		
	
	/**
	 * Stores a value to a given memory address.  If the address is zero, or
	 * does not point to a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putFloat(long, float)
	 */
	public void putFloat(long address, float value) {
		UNSAFE.putFloat(address, value);
	}
	
	/**
	 * Volatile version of {@link #putFloat(long, float)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putFloatVolatile(java.lang.Object, long, float)
	 */
	public void putFloatVolatile(long address, float value) {
		UNSAFE.putFloatVolatile(null, address, value);
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
	 * Volatile version of {@link #getLong(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getLongVolatile(java.lang.Object, long)
	 */
	public long getLongVolatile(long address) {
		return UNSAFE.getLongVolatile(null, address);
	}
	
	//===========================================================================================================
	//	Long Write Ops
	//===========================================================================================================		
	
	/**
	 * Stores a value to a given memory address.  If the address is zero, or
	 * does not point to a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putLong(long, long)
	 */
	public void putLong(long address, long value) {
		UNSAFE.putLong(address, value);
	}
	
	/**
	 * Volatile version of {@link #putLong(long, long)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putLongVolatile(java.lang.Object, long, long)
	 */
	public void putLongVolatile(long address, long value) {
		UNSAFE.putLongVolatile(null, address, value);
	}
	
	/**
	 * Ordered/Lazy version of #putIntVolatile(long, long) 
	 * @param offset The address to write to 
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putOrderedLong(java.lang.Object, long, long)
	 */
	public void putOrderedLong(long offset, long value) {
		UNSAFE.putOrderedLong(null, offset, value);
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
	 * @see sun.misc.Unsafe#getDouble(long)
	 */	
	public double getDouble(long address) {
		return UNSAFE.getDouble(address);
	}


	/**
	 * Volatile version of {@link #getDouble(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getDoubleVolatile(java.lang.Object, long)
	 */
	public double getDoubleVolatile(long address) {
		return UNSAFE.getDoubleVolatile(null, address);
	}
	
	//===========================================================================================================
	//	Double Write Ops
	//===========================================================================================================		
	
	/**
	 * Stores a value to a given memory address.  If the address is zero, or
	 * does not point to a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putDouble(long, double)
	 */
	public void putDouble(long address, double value) {
		UNSAFE.putDouble(address, value);
	}
	
	/**
	 * Volatile version of {@link #putDouble(long, double)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putDoubleVolatile(java.lang.Object, long, double)
	 */
	public void putDoubleVolatile(long address, double value) {
		UNSAFE.putDoubleVolatile(null, address, value);
	}
	
	
	//===========================================================================================================
	//	Object Read Ops
	//===========================================================================================================
	
	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory} , the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getObject(java.lang.Object, long)
	 */	
	public Object getObject(long address) {
		return UNSAFE.getObject(null, address);
	}


	/**
	 * Volatile version of {@link #getObject(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getObjectVolatile(java.lang.Object, long)
	 */
	public Object getObjectVolatile(long address) {
		return UNSAFE.getObjectVolatile(null, address);
	}
	



	//===========================================================================================================
	
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
    
	//===========================================================================================================
	//	MemoryMBean Implementation
	//===========================================================================================================	
    

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#isSafeMemory()
	 */
	@Override
	public boolean isSafeMemory() {
		return UnsafeAdapter.isSafeAdapter();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#isTrackingEnabled()
	 */
	@Override
	public boolean isTrackingEnabled() {
		return this.trackMem;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#isFiveCopy()
	 */
	@Override
	public boolean isFiveCopy() {
		return UnsafeAdapter.FIVE_COPY;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#isFourSet()
	 */
	@Override
	public boolean isFourSet() {
		return UnsafeAdapter.FOUR_SET;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#isAlignmentEnabled()
	 */
	@Override
	public boolean isAlignmentEnabled() {
		return this.alignMem;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getAddressSize()
	 */
	@Override
	public int getAddressSize() {
		return UnsafeAdapter.ADDRESS_SIZE;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getPageSize()
	 */
	@Override
	public int getPageSize() {
		return UnsafeAdapter.pageSize();
	}

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
		map.put(REFQ_SIZE, getRefQueuePending());    		
		map.put(PENDING_COUNT, (long)getPendingRefs());
		return map;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getTotalAllocatedMemory()
	 */
	@Override
	public long getTotalAllocatedMemory() {
		return trackMem ? totalMemoryAllocated.get() : -1L;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getAlignedMemoryOverhead()
	 */
	@Override
	public long getAlignedMemoryOverhead() {
		return alignMem ? totalAlignmentOverhead.get() : -1L;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getTotalAllocatedMemoryKb()
	 */
	@Override
	public long getTotalAllocatedMemoryKb() {
		if(trackMem) {
			long mem = totalMemoryAllocated.get();
			return mem < 1 ? 0L : roundMem(mem, 1024);
		}
		return -1L;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getTotalAllocatedMemoryMb()
	 */
	@Override
	public long getTotalAllocatedMemoryMb() {
		if(trackMem) {
			long mem = totalMemoryAllocated.get();
			return mem < 1 ? 0L : roundMem(mem, 1024*1024);
		}
		return -1L;
	}
	
	/**
	 * Rounds calculated memory sizes when converting from bytes to Kb and Mb.
	 * @param total The total memory allocated in bytes
	 * @param div The divisor
	 * @return the rounded memory in Kb or Mb.
	 */
	private long roundMem(double total, double div) {
		if(total<1) return 0L;
		double d = total / div;
		return Math.round(d);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getTotalAllocationCount()
	 */
	@Override
	public int getTotalAllocationCount() {
		if(!trackMem) return -1;
		return memoryAllocations.size();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getPendingRefs()
	 */
	@Override
	public int getPendingRefs() {
		return deAllocs.size();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getReferenceQueueSize()
	 */
	@Override
	public long getReferenceQueueSize() {		
		return getRefQueuePending();
	}
	
    	
}
