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

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import com.heliosapm.unsafe.DefaultUnsafeAdapterImpl.MemoryAllocationReference;

import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

/**
 * <p>Title: SafeMemoryAllocator</p>
 * <p>Description: Mimics true unsafe memory allocations through the use of ByteBuffers. The trick here is to map
 * the ranges of memory addresses that appear to be within the allocated buffer and rewrite/read them to/from
 * the ByteBuffer.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.SafeMemoryAllocator</code></p>
 */

public class SafeMemoryAllocator implements Runnable {
	/** The singleton instance */
	private static volatile SafeMemoryAllocator instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	/** Serial number factory for cleaner threads */
	private static final AtomicLong cleanerSerial = new AtomicLong(0L);
	
    /** A map of memory allocation references keyed by an internal counter */
    protected final NonBlockingHashMapLong<MemoryAllocationReference> deAllocs = new NonBlockingHashMapLong<MemoryAllocationReference>(1024, false);

    /** Serial number factory for memory allocationreferences */
    private static final AtomicLong refIndexFactory = new AtomicLong(0L);
    /** Empty long[] array const */
    private static final long[][] EMPTY_ADDRESSES = {{}};
    

	
	
	/** The field for accessing the pending queue length */
	private final Field queueLengthField;
	/** The object to synchronize on for accessing the pending queue length */
	private final Object queueLengthFieldLock;
	/** The configured native memory alignment enablement  */
	public final boolean alignMem;
	/** The configured safe memory direct-or-not designation */
	public final boolean onHeap;
	/** A map of allocation weak references */ 
	final Map<Range, SafeMemoryAllocationWeakReference> allocations;
	/** A weak ref key map of safe allocations keyed by the deallocation long array */
	final Map<LongArrayMapKey, Map<Long, SafeMemoryAllocation>> allocationByLongArr = 
				Collections.synchronizedMap(new WeakHashMap<LongArrayMapKey, Map<Long, SafeMemoryAllocation>>(1024));
	/** The reference queue where collected allocations go */
	final ReferenceQueue<? super Deallocatable> refQueue;
	/** The reference cleaner thread */
	Thread cleanerThread;
	/** The total memory allocated */
	final AtomicLong totalMemory = new AtomicLong(0L);
	/** The total alignment overhead */
	final AtomicLong totalAlignmentOverhead = new AtomicLong(0L);
	
	/**
	 * Acquires the singleton SafeMemoryAllocator and initializes it on first access.
	 * @return the singleton SafeMemoryAllocator
	 */
	public static SafeMemoryAllocator getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new SafeMemoryAllocator();
				}
			}
		}
		return instance;
	}

	
	
	
	/**
	 * Creates a new SafeMemoryAllocator
	 */
	private SafeMemoryAllocator() {
		allocations = new ConcurrentHashMap<Range, SafeMemoryAllocationWeakReference>();
		refQueue = new ReferenceQueue<Deallocatable>();
		cleanerThread = new Thread(this, "SafeMemoryAllocationCleaner#" + cleanerSerial.incrementAndGet());
		alignMem = System.getProperties().containsKey(UnsafeAdapter.ALIGN_ALLOCS_PROP);
		onHeap = System.getProperties().containsKey(UnsafeAdapter.SAFE_ALLOCS_ONHEAP_PROP);
		try {
			queueLengthField = ReferenceQueue.class.getDeclaredField("queueLength");
			queueLengthField.setAccessible(true);
			Field f = ReferenceQueue.class.getDeclaredField("lock");
			f.setAccessible(true);
			queueLengthFieldLock =f.get(refQueue); 
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
		cleanerThread.setDaemon(true);
		cleanerThread.setPriority(Thread.MAX_PRIORITY);
		cleanerThread.start();
	}
	
	
	
	/**
	 * Returns the total number of allocated bytes
	 * @return the total number of allocated bytes
	 */
	long getTotalMemoryAllocated() {
		return totalMemory.get();
	}
	
	/**
	 * Returns the total number of memory blocks allocated
	 * @return the total number of memory blocks allocated
	 */
	int getTotalAllocations() {
		return allocations.size();
	}
	
	/**
	 * Returns the number of pending references in the reference queue
	 * @return the number of pending references in the reference queue
	 */
	int getRefQueuePending() {
		try {
			synchronized(queueLengthFieldLock) {
				return queueLengthField.getInt(refQueue);
			}
		} catch (Exception x) {
			return -1;
		}
	}
	
	int getPending() {
		return -1; // FIXME
	}
	
	/**
	 * The entry point for the ref cleaner thread.
	 * The intent here is that when the allocator is terminated,
	 * the thread will keep running until all references are cleaned.
	 * However, tracking will be lost for the terminated reference.
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		boolean terminating = false;
		while(true) {
			try {
				SafeMemoryAllocationWeakReference ref = (SafeMemoryAllocationWeakReference)refQueue.remove();
				if(ref!=null) {
					ref.clear();
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
		log("MemoryAllocator Cleaner [%s] terminated", Thread.currentThread().getName());
	}

	/**
	 * Allocates a new safe memory block
	 * @param size The size of the memory block in bytes
	 * @return the notional address of the block
	 */
	public long allocate(long size) {
		SafeMemoryAllocation sma = new SafeMemoryAllocation(size, onHeap);
		Range range = sma.range();
		allocations.put(range, new SafeMemoryAllocationWeakReference(sma, range));
		totalMemory.addAndGet(size);
		return sma.startRange;
	}
	
	/**
	 * Allocates a new safe memory block, cache-line aligned for consistency if alignment is enabled
	 * @param size The size of the memory block in bytes
	 * @return the notional address of the block
	 */
	public long allocateAligned(long size) {
		final long alignedSize = UnsafeAdapter.findNextPositivePowerOfTwo(size);
		final long alignmentOverhead = alignedSize-size;
		SafeMemoryAllocation sma = new SafeMemoryAllocation(size, alignmentOverhead, onHeap);
		Range range = sma.range();
		allocations.put(range, new SafeMemoryAllocationWeakReference(sma, range));
		totalMemory.addAndGet(size);
		totalAlignmentOverhead.addAndGet(alignedSize-size);
		return sma.startRange;
	}
	
	
	/**
	 * Locates and returns the safe memory allocation at or containing the passed address
	 * @param address The address to get the allocation for
	 * @return The safe memory allocation
	 * @throws IllegalArgumentException thrown if the address is invalid
	 * @throws ExpiredMemoryAllocationException thrown if a safe memory allocation is not found
	 */
	public SafeMemoryAllocation getAllocation(long address) {
		if(address<1) throw new IllegalArgumentException("Invalid address [" + address + "]");
		SafeMemoryAllocationWeakReference ref = allocations.get(Range.range(address));
		if(ref==null) throw new ExpiredMemoryAllocationException(address);
		SafeMemoryAllocation sma = ref.get();
		if(sma==null) {			
			throw new ExpiredMemoryAllocationException(address);
		}
		return sma;
	}
	
	/**
	 * Low maintenance out logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	void log(Object fmt, Object...args) {
		System.out.println(String.format("[SafeMemoryAllocator]" + fmt.toString(), args));
	}
	
	/**
	 * Low maintenance err logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	void loge(Object fmt, Object...args) {
		System.err.println(String.format("[SafeMemoryAllocator]" + fmt.toString(), args));
		if(args!=null && args.length>0 && args[args.length-1] instanceof Throwable) {
			System.err.println("Stack trace follows....");
			((Throwable)args[args.length-1]).printStackTrace(System.err);			
		}
	}
	
	
	/**
	 * Locates, removes and returns the safe memory allocation at or containing the passed address
	 * @param address The address to remove the allocation for
	 * @throws IllegalArgumentException thrown if the address is invalid
	 * @throws ExpiredMemoryAllocationException thrown if a safe memory allocation is not found
	 */
	public void removeAllocation(long address) {
		if(address<1) throw new IllegalArgumentException("Invalid address [" + address + "]");
		SafeMemoryAllocationWeakReference ref = allocations.remove(Range.range(address));
		if(ref==null) throw new ExpiredMemoryAllocationException(address);
		SafeMemoryAllocation sma = ref.get();
		if(sma==null) {			
			throw new ExpiredMemoryAllocationException(address);
		}
		sma.destroy();
	}	
	
	
	public static void main(String[] args) {
//		log("Safe Test");
//		SafeMemoryAllocator sma = new SafeMemoryAllocator();
//		
//		long address = sma.allocate(100, false);
//		log("Address:" + address + "  Allocations:" + sma.allocations.size());
//		SafeMemoryAllocation alloc = sma.getAllocation(address);
//		log("Allocation:" + alloc);
//		alloc = sma.getAllocation(address + 2);
//		log("Allocation:" + alloc);
	}
	
	public static void log(Object msg) {
		System.out.println(msg);
	}
	

	
	class SafeMemoryAllocationWeakReference extends WeakReference<SafeMemoryAllocation> {
		/** The size of the allocation */
		final long size;
		/** The alignment overhead of the allocation */
		final long alignmentOverhead;
    	/** The index of this reference */
    	private final long index = refIndexFactory.incrementAndGet();
    	/** The memory addresses owned by this reference */
    	private final long[] addresses;
    	/** The Range for the referent's allocation */
    	private final Range range;
		
		
		public SafeMemoryAllocationWeakReference(SafeMemoryAllocation allocation, Range range, Deallocatable referent) {
			super(allocation, refQueue);
			this.range = range;
			size = allocation.size * -1L;
			alignmentOverhead = allocation.alignmentOverhead * -1L;
			addresses = referent==null ? new long[0] : referent.getAddresses();
		}

		public SafeMemoryAllocationWeakReference(SafeMemoryAllocation allocation, Range range) {
			this(allocation, range, null);
		}
		
		
		public void clear() {			
			allocations.remove(index);
			totalMemory.addAndGet(size);
			totalAlignmentOverhead.addAndGet(alignmentOverhead);
			super.clear();
		}
		
	}
	
	/**
	 * <p>Title: SafeMemoryAllocation</p>
	 * <p>Description: A memory allocation class intended to mimic dirct unsafe allocations in safe memory.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.SafeMemoryAllocator.SafeMemoryAllocation</code></p>
	 */
	static class SafeMemoryAllocation {
		/** The allocated block of memory */
		final ByteBuffer block;
		/** The size of the block */
		final long size;
		/** The alignment overhead */
		final long alignmentOverhead;		
		/** The notional address of the memory block */
		final long startRange;
		/** The notional end address of the memory block */
		final long endRange;



		/**
		 * Creates a new SafeMemoryAllocation
		 * @param size The size of the allocation in bytes
		 * @param alignmentOverhead The size of the alignment overhead
		 * @param onHeap true for on heap memory, false for direct
		 * @param referent optional The auto-tracking dealocation reference
		 */
		SafeMemoryAllocation(long size, long alignmentOverhead, boolean onHeap, Deallocatable referent) {
			if(size > Integer.MAX_VALUE || size < 1) throw new IllegalArgumentException("Invalid Safe Memory Size [" + size + "]", new Throwable());
			this.size = size;
			this.alignmentOverhead = alignmentOverhead;
			block = onHeap ? ByteBuffer.allocate((int)size) : ByteBuffer.allocateDirect((int)size);
			startRange = UnsafeAdapter.getAddressOf(block);
			endRange = startRange + size;			
		}
		
		/**
		 * Creates a memory range for this allocation
		 * @return a memory range 
		 */
		Range range() {
			return Range.range(startRange, endRange);
		}

		/**
		 * Creates a new SafeMemoryAllocation
		 * @param size The size of the allocation in bytes
		 * @param alignmentOverhead The size of the alignment overhead
		 * @param onHeap true for on heap memory, false for direct
		 */
		SafeMemoryAllocation(long size, long alignmentOverhead, boolean onHeap) {
			this(size, alignmentOverhead, onHeap, null);
		}


		/**
		 * Creates a new SafeMemoryAllocation with zero allocation overhead
		 * @param size The size of the allocation in bytes
		 * @param onHeap true for on heap memory, false for direct
		 */
		SafeMemoryAllocation(long size, boolean onHeap) {
			this(size, 0L, onHeap);
		}

		/**
		 * Creates a new SafeMemoryAllocation with zero allocation overhead
		 * @param size The size of the allocation in bytes
		 * @param onHeap true for on heap memory, false for direct
		 * @param referent optional The auto-tracking dealocation reference
		 */
		SafeMemoryAllocation(long size, boolean onHeap, Deallocatable referent) {
			this(size, 0L, onHeap, referent);
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return block.isDirect() ? String.format("DirectSafeMemory[size: %s, address: %s]", size, startRange) : String.format("HeapSafeMemory[size: %s, address: %s]", size, startRange); 
		}

		/**
		 * Returns the underlying ByteBuffer for this allocation 
		 * @return the underlying ByteBuffer
		 */
		ByteBuffer getBlock() {
			return block;
		}

		/**
		 * Destroys this allocation
		 */
		void destroy() {
			if(block instanceof DirectBuffer) {
				Cleaner cleaner = ((DirectBuffer)block).cleaner();
				if(cleaner!=null) cleaner.clean();
				// leave the accounting for the RefQueue processor
			}
		}

	}

	
	
	static class LongArrayMapKey {
		/** The long array serving as the key */
		final long[] key;

		/**
		 * Creates a new LongArrayMapKey
		 * @param key The long array serving as the key
		 */
		public LongArrayMapKey(long[] key) {
			super();
			this.key = key;
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(key);
			return result;
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			LongArrayMapKey other = (LongArrayMapKey) obj;
			if (!Arrays.equals(key, other.key))
				return false;
			return true;
		}
		
		
		
	}
	
	static class Range implements Comparable<Range> {
		/** The notional address of the memory block */
		final long startRange;
		/** The notional end address of the memory block */
		final long endRange;

		/**
		 * Creates a new memory range
		 * @param start The starting address
		 * @param end The end of the address range
		 * @return the created range
		 */
		public static Range range(long start, long end) {
			return new Range(start, end);
		}
		
		/**
		 * Creates a lookup range for a specific address
		 * @param address The address to find the range for
		 * @return the address range
		 */
		public static Range range(long address) {
			return new Range(address);
		}
		
		private Range(long start, long end) {
			startRange = start;
			endRange = end;
		}
		
		private Range(long start) {
			this(start, -1L);
		}
		
		
		public String toString() {
			if(endRange==-1L) return "Range[" + startRange + "]";
			return String.format("Range [%s,  %s]", startRange, endRange);
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(Range v) {
			return v.startRange < startRange ? -1 : 1;
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (startRange ^ (startRange >>> 32));
			return -1;
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			log("---Equals---  this:" + this + ",  that:" + obj);
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Range v = (Range) obj;
			if(v.endRange!=-1L && endRange!=-1L) {
				return v.startRange==startRange && v.endRange==endRange; 
			}
			if(endRange==-1) {
				return startRange >= v.startRange && startRange <= v.endRange; 
			}
			if (endRange != v.endRange)
				return false;
			if (startRange != v.startRange)
				return false;
			return true;
		}
	}
	
  /**
  * <p>Title: MemoryAllocationReference</p>
  * <p>Description: A phantom reference extension for tracking memory allocations without preventing them from being enqueued for de-allocation</p> 
  * <p>Company: Helios Development Group LLC</p>
  * @author Whitehead (nwhitehead AT heliosdev DOT org)
  * <p><code>com.heliosapm.unsafe.DefaultUnsafeAdapterImpl.MemoryAllocationReference</code></p>
  */
 class MemoryAllocationReference extends PhantomReference<Deallocatable> {
 	/** The index of this reference */
 	private final long index = refIndexFactory.incrementAndGet();
 	/** The memory addresses owned by this reference */
 	private final long[][] addresses;
 	
		/**
		 * Creates a new MemoryAllocationReference
		 * @param referent the memory address holder
		 */
		public MemoryAllocationReference(Deallocatable referent) {
			super(referent, refQueue);
			addresses = referent==null ? EMPTY_ADDRESSES : referent.getAddresses();
			deAllocs.put(index, this);
			referent = null;
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
				log("Freed memory at address [%s]", address[0]);
				deAllocs.remove(index);
			}
			super.clear();
		}



		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("MemoryAllocationReference [index=");
			builder.append(index);
			builder.append(", addresses=");
			builder.append(addresses != null ? Arrays.deepToString(addresses) : "[]");					
			builder.append("]");
			return builder.toString();
		}
 }
	
	
	
}


/*

	//===========================================================================================================
	//	Allocate Memory Ops
	//===========================================================================================================	

//	/**
//	 * Allocates a new block of native memory, of the given size in bytes. 
//	 * The contents of the memory are uninitialized; they will generally be garbage.
//	 * The resulting native pointer will never be zero, and will be aligned for all value types.  
//	 * Dispose of this memory by calling #freeMemory , or resize it with #reallocateMemory.
//	 * @param size The size of the block of memory to allocate in bytes
//	 * @return The address of the allocated memory block
//	 * @see sun.misc.Unsafe#allocateMemory(long)
//	 */
//	public long allocateMemory(long size) {
//		return _allocateMemory(size, 0L, null);
//	}
//	
//	/**
//	 * Allocates a chunk of memory and returns its address
//	 * @param size The number of bytes to allocate
//	 * @param dealloc The optional deallocatable to register for deallocation when it becomes phantom reachable
//	 * @return The address of the allocated memory
//	 * @see sun.misc.Unsafe#allocateMemory(long)
//	 */
//	public long allocateMemory(long size, Deallocatable dealloc) {
//		return _allocateMemory(size, 0L, dealloc);
//	}	
//	
//	
//	/**
//	 * Allocates a new block of cache-line aligned native memory, of the given size in bytes rounded up to the nearest power of 2. 
//	 * The contents of the memory are uninitialized; they will generally be garbage.
//	 * The resulting native pointer will never be zero, and will be aligned for all value types.  
//	 * Dispose of this memory by calling #freeMemory , or resize it with #reallocateMemory.
//	 * @param size The size of the block of memory to allocate in bytes
//	 * @return The address of the allocated memory block
//	 * @see sun.misc.Unsafe#allocateMemory(long)
//	 */
//	public long allocateAlignedMemory(long size) {
//		return allocateAlignedMemory(size, null);
//	}
//	
//	/**
//	 * Allocates a new block of cache-line aligned native memory, of the given size in bytes rounded up to the nearest power of 2. 
//	 * The contents of the memory are uninitialized; they will generally be garbage.
//	 * The resulting native pointer will never be zero, and will be aligned for all value types.  
//	 * Dispose of this memory by calling #freeMemory , or resize it with #reallocateMemory.
//	 * @param size The size of the block of memory to allocate in bytes
//	 * @param dealloc The optional deallocatable to register for deallocation when it becomes phantom reachable
//	 * @return The address of the allocated memory block
//	 * @see sun.misc.Unsafe#allocateMemory(long)
//	 */
//	public long allocateAlignedMemory(long size, Deallocatable dealloc) {
//		if(alignMem) {
//			long alignedSize = UnsafeAdapter.ADDRESS_SIZE==4 ? findNextPositivePowerOfTwo((int)size) : findNextPositivePowerOfTwo((int)size);
//			return _allocateMemory(alignedSize, alignedSize-size, dealloc);
//		}
//		return _allocateMemory(size, 0L, dealloc);		
//	}
//	
//    /**
//     * Finds the next <b><code>power of 2</code></b> higher or equal to than the passed value.
//     * @param value The initial value
//     * @return the next pow2 without overrunning the type size
//     */
//    public static long findNextPositivePowerOfTwo(final long value) {
//    	if(UnsafeAdapter.ADDRESS_SIZE==4) {
//        	if(value > MAX_ALIGNED_MEM_32) return value;
//        	return  1 << (32 - Integer.numberOfLeadingZeros((int)value - 1));    		
//    	}
//    	if(value > MAX_ALIGNED_MEM_64) return value;
//    	return  1 << (64 - Long.numberOfLeadingZeros(value - 1));    		
//	}    
//    
//
//	/**
//	 * Allocates a chunk of memory and returns its address
//	 * @param size The number of bytes to allocate
//	 * @param alignmentOverhead The number of bytes allocated in excess of requested for alignment
//	 * @param deallocator The reference to the object which when collected will deallocate the referenced addresses
//	 * @return The address of the allocated memory
//	 * @see sun.misc.Unsafe#allocateMemory(long)
//	 */
//	@SuppressWarnings("unused")
//	long _allocateMemory(long size, long alignmentOverhead, Deallocatable deallocator) {
//		long address = UNSAFE.allocateMemory(size);
//		if(trackMem) {		
//			memoryAllocations.put(address, new long[]{size, alignmentOverhead});
//			totalMemoryAllocated.addAndGet(size);
//			totalAlignmentOverhead.addAndGet(alignmentOverhead);
//		}
//    	if(deallocator!=null) {
//    		long[][] addresses = deallocator.getAddresses();
//    		if(addresses==null || addresses.length==0) {
//    			new MemoryAllocationReference(deallocator);
//    		}
//    	}		
//		return address;
//	}
//	
//	/**
//	 * Resizes a new block of native memory, to the given size in bytes.
//	 * <b>NOTE:</b>If the caller implements {@link Deallocatable} and expects the allocations
//	 * to be automatically cleared, the returned value should overwrite the index of 
//	 * the {@link Deallocatable}'s array where the previous address was.    
//	 * @param address The address of the existing allocation
//	 * @param size The size of the new allocation in bytes
//	 * @return The address of the new allocation
//	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
//	 */
//	public long reallocateMemory(long address, long size) {
//		return _reallocateMemory(address, size, 0);
//	}	
//	
//	/**
//	 * Resizes a new block of aligned (if enabled) native memory, to the given size in bytes.
//	 * <b>NOTE:</b>If the caller implements {@link Deallocatable} and expects the allocations
//	 * to be automatically cleared, the returned value should overwrite the index of 
//	 * the {@link Deallocatable}'s array where the previous address was.   
//	 * @param address The address of the existing allocation
//	 * @param size The size of the new allocation in bytes
//	 * @return The address of the new allocation
//	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
//	 */
//	public long reallocateAlignedMemory(long address, long size) {
//		if(alignMem) {
//			long actual = findNextPositivePowerOfTwo(size);
//			return _reallocateMemory(address, actual, actual-size);
//		} 
//		return _reallocateMemory(address, size, 0);
//	}	
//	
//	/**
//	 * Resizes a new block of native memory, to the given size in bytes.
//	 * <b>NOTE:</b>If the caller implements {@link Deallocatable} and expects the allocations
//	 * to be automatically cleared, the returned value should overwrite the index of 
//	 * the {@link Deallocatable}'s array where the previous address was.  
//	 * @param address The address of the existing allocation
//	 * @param size The size of the new allocation in bytes
//	 * @param alignmentOverhead The established overhead of the alignment
//	 * @return The address of the new allocation
//	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
//	 */
//	long _reallocateMemory(long address, long size, long alignmentOverhead) {
//		long newAddress = UNSAFE.reallocateMemory(address, size);
//		if(trackMem) {
//			// ==========================================================
//			//  Subtract pervious allocation
//			// ==========================================================				
//			long[] alloc = memoryAllocations.remove(address);
//			if(alloc!=null) {
//				totalMemoryAllocated.addAndGet(-1L * alloc[0]);
//				totalAlignmentOverhead.addAndGet(-1L * alloc[1]);
//			}
//			// ==========================================================
//			//  Add new allocation
//			// ==========================================================								
//			memoryAllocations.put(newAddress, new long[]{size, alignmentOverhead});
//			totalMemoryAllocated.addAndGet(size);
//			totalAlignmentOverhead.addAndGet(alignmentOverhead);
//		}
//		return newAddress;
//	}
//	


