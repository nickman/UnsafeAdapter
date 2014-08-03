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
import java.lang.management.MemoryMXBean;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import sun.misc.Unsafe;

import com.heliosapm.unsafe.ReflectionHelper.ReferenceQueueLengthReader;
import com.heliosapm.unsafe.unmanaged.SpinLockedTLongLongHashMap;



/**
 * <p>Title: DefaultUnsafeAdapterImpl</p>
 * <p>Description: Provides the default {@link sun.misc.Unsafe} invocations for the {@link UnsafeAdapter}.
 * Memory allocations are to direct memory and unchecked.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.DefaultUnsafeAdapterImpl</code></p>
 */
@SuppressWarnings({"restriction", "static-method"})
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
	private static final Unsafe UNSAFE = UnsafeAdapter.theUNSAFE;
    /** Indicates if the 5 param copy memory is supported */
    public static final boolean FIVE_COPY = UnsafeAdapter.FIVE_COPY;
    /** Indicates if the 4 param set memory is supported */
    public static final boolean FOUR_SET = UnsafeAdapter.FOUR_SET;	
	
	/** The debug agent library signature */
	public static final String AGENT_LIB = "-agentlib:";	
	/** The legacy debug agent library signature */
	public static final String LEGACY_AGENT_LIB = "-Xrunjdwp:";
    /** Empty long[] array const */
    private static final long[][] EMPTY_ADDRESSES = {{}};
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
	final MemoryMBean unsafeMemoryStats = null;
	
	
	 

	
	// =========================================================
	//  Memory Tracking
	// =========================================================
	/** A map of phantom refs */
	final NonBlockingHashMapLong<DeallocationPhantomReference> phantomRefs = new NonBlockingHashMapLong<DeallocationPhantomReference>(1024, true); 
	/** The interface tracker to cache which classes implement "special" UnsafeAdapter interfaces */
	final InterfaceTracker ifaceTracker = new InterfaceTracker();
    /** Serial number factory for memory allocationreferences */
	protected final AtomicLong phantomSerial = new AtomicLong(0L);
	
	
	/** A map of memory allocation sizes keyed by the memory block address */
	final SpinLockedTLongLongHashMap memoryAllocations;
	/** A map of memory allocation alignment overhead sizes keyed by the memory block address */
	final SpinLockedTLongLongHashMap alignmentOverheads;
	
	/** The total native memory allocation */
	final AtomicLong totalMemoryAllocated;
	/** The total number of native memory allocations */
	final AtomicLong totalAllocationCount;
	/** The total native memory allocation overhead for alignment */
	final AtomicLong totalAlignmentOverhead;
	// =========================================================
	//  Auto Deallocation
	// =========================================================	
		
    /** A map of memory allocation references keyed by an internal counter */
    final NonBlockingHashMapLong<AllocationPointer> deAllocs = new NonBlockingHashMapLong<AllocationPointer>(1024, false);
	/** The reference queue where collected allocations go */
	final ReferenceQueue<?> refQueue = new ReferenceQueue<Object>();
	/** The queue length reader for the reference queue */
	final ReferenceQueueLengthReader<?> refQueueLengthReader = ReflectionHelper.newReferenceQueueLengthReader(refQueue);
	/** The reference cleaner thread */
	Thread cleanerThread;

    // =========================================================
	
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
				if(cleanerThread!=null) cleanerThread.interrupt(); 
			}
		} catch (Throwable t) {
			loge("Failed to reset UnsafeAdapter", t);
		}
	}
	
	
	
	/**
	 * Creates a new DeallocationPhantomReference
	 * @param referent The deallocatable to create the reference for
	 */
	
	final void newDeallocationPhantomReference(Deallocatable referent) {
		DeallocationPhantomReference phantom = new DeallocationPhantomReference(referent, refQueue);
		phantomRefs.put(phantom.id, phantom);
	}
	
	
	/**
	 * <p>Title: DeallocationPhantomReference</p>
	 * <p>Description: A phantom reference extension for tracking {@link Deallocatable} instance enqueueing </p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.DefaultUnsafeAdapterImpl.DeallocationPhantomReference</code></p>
	 */
	class DeallocationPhantomReference extends PhantomReference<Deallocatable> implements Deallocatable {
		/** The keyAddresses to deallocate when this reference is enqueued */
		final long[][] addresses;
		/** The unique serial number for this reference */
		final long id;
		
		/**
		 * Creates a new DeallocationPhantomReference
		 * @param referent The deallocatable to create the reference for
		 * @param refQueue The reference queue
		 */
		@SuppressWarnings("unchecked") DeallocationPhantomReference(Deallocatable referent, ReferenceQueue<?> refQueue) {
			super(referent, (ReferenceQueue<? super Deallocatable>) refQueue);
			this.addresses = referent.getAddresses();
			id = phantomSerial.incrementAndGet();
		}
		
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.ref.Reference#clear()
		 */
		@Override
		public void clear() {
			if(addresses!=null && addresses.length>0 && addresses[0].length>0) {
				for(int x = 0; x < addresses[0].length; x++) {
					freeMemory(addresses[0][x]);
					addresses[0][x] = 0;
				}
			}
			phantomRefs.remove(id);
			super.clear();
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.unsafe.Deallocatable#getAddresses()
		 */
		@Override
		public long[][] getAddresses() {
			return addresses;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.unsafe.Deallocatable#isReferenced()
		 */
		@Override
		public boolean isReferenced() {
			return true;
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
		// Read the system props to get the configuration
		// =========================================================        
    	trackMem = System.getProperties().containsKey(UnsafeAdapter.TRACK_ALLOCS_PROP) || isDebugAgentLoaded();   
    	alignMem = System.getProperties().containsKey(UnsafeAdapter.ALIGN_ALLOCS_PROP);
		// =========================================================
		// Initialize the memory allocation tracking if enabled.
    	// Otherwise, set to null.
		// =========================================================            	
    	if(trackMem) {
    		totalAllocationCount = new AtomicLong(0L);
    		totalMemoryAllocated = new AtomicLong(0L);
    		totalAlignmentOverhead = new AtomicLong(0L);
        	if(this.getClass()==DefaultUnsafeAdapterImpl.class) {
        		memoryAllocations = new SpinLockedTLongLongHashMap();
        		alignmentOverheads = new SpinLockedTLongLongHashMap();
        	} else {
        		memoryAllocations = null;
        		alignmentOverheads = null;
        	}
    	} else {
    		totalAllocationCount = null;
    		memoryAllocations = null;
    		totalMemoryAllocated = null;
    		totalAlignmentOverhead = null;
    		alignmentOverheads = null;
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
				Reference<?> ref = refQueue.remove();
				log("Dequeued Reference [%s]", ref);
				if(ref!=null) { 
					ref.clear();
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
		cleanerThread = null;
		log("Unsafe Cleaner Thread [%s] Terminated", Thread.currentThread().getName());
		UnsafeAdapter.removeCleanerThread(Thread.currentThread());
		
	}
	
	/**
	 * Returns the number of pending references in the reference queue
	 * @return the number of pending references
	 */
	public long getRefQueuePending() {
		return refQueueLengthReader.getQueueLength();
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
	public long allocateMemory(long size, Deallocatable dealloc) {
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
	public long allocateAlignedMemory(long size, Deallocatable dealloc) {
		if(alignMem) {
			long alignedSize = UnsafeAdapter.ADDRESS_SIZE==4 ? UnsafeAdapter.findNextPositivePowerOfTwo((int)size) : UnsafeAdapter.findNextPositivePowerOfTwo((int)size);
			return _allocateMemory(alignedSize, alignedSize-size, dealloc);
		}
		return _allocateMemory(size, 0L, dealloc);		
	}
	
    

	/**
	 * Allocates a chunk of memory and returns its address
	 * @param size The number of bytes to allocate
	 * @param alignmentOverhead The number of bytes allocated in excess of requested for alignment
	 * @param deallocator The reference to the object which when collected will deallocate the referenced keyAddresses
	 * @return The address of the allocated memory
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */		
	long _allocateMemory(long size, long alignmentOverhead, Deallocatable deallocator) {
		final long address = UNSAFE.allocateMemory(size);
		if(trackMem) {		
			memoryAllocations.put(address, size);
			totalMemoryAllocated.addAndGet(size);
			totalAllocationCount.incrementAndGet();
			if(alignmentOverhead>0) {
				totalAlignmentOverhead.addAndGet(alignmentOverhead);
				alignmentOverheads.put(address, alignmentOverhead); 
			}
		}
    	if(deallocator!=null) {
    		long[][] addresses = deallocator.getAddresses();
    		if(addresses!=null && addresses.length>0) {    			
    			newDeallocationPhantomReference(deallocator );
        		if(deallocator instanceof AddressAssignable) {
        			((AddressAssignable)deallocator).setAllocated(address);
        		}    			
    		}
    	}		
		return address;
	}
	
	
	
	/**
	 * Resizes a new block of native memory, to the given size in bytes.
	 * <b>NOTE:</b>If the caller implements {@link Deallocatable} and expects the allocations
	 * to be automatically cleared, the returned value should overwrite the index of 
	 * the {@link Deallocatable}'s array where the previous address was.    
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
	 * <b>NOTE:</b>If the caller implements {@link Deallocatable} and expects the allocations
	 * to be automatically cleared, the returned value should overwrite the index of 
	 * the {@link Deallocatable}'s array where the previous address was.   
	 * @param address The address of the existing allocation
	 * @param size The size of the new allocation in bytes
	 * @return The address of the new allocation
	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
	 */
	public long reallocateAlignedMemory(long address, long size) {
		if(alignMem) {
			long actual = UnsafeAdapter.findNextPositivePowerOfTwo(size);
			return _reallocateMemory(address, actual, actual-size);
		} 
		return _reallocateMemory(address, size, 0);
	}	
	
	/**
	 * Resizes a new block of native memory, to the given size in bytes.
	 * <b>NOTE:</b>If the caller implements {@link Deallocatable} and expects the allocations
	 * to be automatically cleared, the returned value should overwrite the index of 
	 * the {@link Deallocatable}'s array where the previous address was.  
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
			long sz = memoryAllocations.remove(address);
			if(sz!=0) {								
				totalMemoryAllocated.addAndGet(-1L * sz);				
			}
			if(alignMem) {
				sz = alignmentOverheads.remove(address);
				if(sz!=0) {								
					totalAlignmentOverhead.addAndGet(-1L * sz);								
				}
			}
			// ==========================================================
			//  Add new allocation
			// ==========================================================		
			memoryAllocations.put(newAddress, size);
			totalMemoryAllocated.addAndGet(size);			
			if(alignmentOverhead>0) {
				totalAlignmentOverhead.addAndGet(alignmentOverhead);
				alignmentOverheads.put(newAddress, alignmentOverhead); 
			}
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
		if(address<1) return;
		if(trackMem) {
			// ==========================================================
			//  Subtract pervious allocation
			// ==========================================================
			totalAllocationCount.decrementAndGet();
			long sz = memoryAllocations.remove(address);
			if(sz!=0) {								
				totalMemoryAllocated.addAndGet(-1L * sz);				
			}
			if(alignMem) {
				sz = alignmentOverheads.remove(address);
				if(sz!=0) {								
					totalAlignmentOverhead.addAndGet(-1L * sz);								
				}
			}
		}				
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
	 * by the address and length parameters.  If the effective keyAddresses and
	 * length are all even modulo 8, the transfer takes place in 'long' units.
	 * If the effective keyAddresses and length are (resp.) even modulo 4 or 2,
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
	
    
	//===========================================================================================================
	//	MemoryMBean Implementation
	//===========================================================================================================	
    
	private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean(); 
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getPendingFinalizationCount()
	 */
	@Override
	public int getPendingFinalizationCount() {
		return memoryMXBean.getObjectPendingFinalizationCount();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#getMaxDirectMemory()
	 */
	@Override
	public long getMaxDirectMemory() {
		return UnsafeAdapter.MAX_DIRECT_MEMORY_SIZE;
	}

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
