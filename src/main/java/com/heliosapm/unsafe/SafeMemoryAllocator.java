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
	
	
	
	/** The field for accessing the pending queue length */
	private final Field queueLengthField;
	/** The object to synchronize on for accessing the pending queue length */
	private final Object queueLengthFieldLock;
	
	
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
	
	/** A map of allocation weak references */ 
	final Map<Range, SafeMemoryAllocationWeakReference> allocations;
	/** A weak ref key map of safe allocations keyed by the deallocation long array */
	final Map<LongArrayMapKey, Map<Long, SafeMemoryAllocation>> allocationByLongArr = 
				Collections.synchronizedMap(new WeakHashMap<LongArrayMapKey, Map<Long, SafeMemoryAllocation>>(1024));
	/** The reference queue where collected allocations go */
	final ReferenceQueue<? super SafeMemoryAllocation> refQueue;
	/** The reference cleaner thread */
	Thread cleanerThread;
	/** The total memory allocated */
	final AtomicLong totalMemory = new AtomicLong(0L);
	
//	BaselineMemory : 136
//	BaselineAllocations : 2
//	BaselinePending : 2
//	BaselineOverhead : 52

	
	
	
	/**
	 * Creates a new SafeMemoryAllocator
	 */
	private SafeMemoryAllocator() {
		allocations = new ConcurrentHashMap<Range, SafeMemoryAllocationWeakReference>();
		refQueue = new ReferenceQueue<SafeMemoryAllocation>();
		cleanerThread = new Thread(this, "SafeMemoryAllocationCleaner#" + cleanerSerial.incrementAndGet());
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
	int getPending() {
		try {
			synchronized(queueLengthFieldLock) {
				return queueLengthField.getInt(refQueue);
			}
		} catch (Exception x) {
			return -1;
		}
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
		log("MemoryAllocator Cleaner [%s] terminated", Thread.currentThread().getName());
	}

	/**
	 * Allocates a new safe memory block
	 * @param size The size of the memory block in bytes
	 * @param onHeap true for heap memory, false for direct memory
	 * @return the notional address of the block
	 */
	public long allocate(long size, boolean onHeap) {
		SafeMemoryAllocation sma = new SafeMemoryAllocation(size, onHeap);
		allocations.put(sma.range, new SafeMemoryAllocationWeakReference(sma));
		totalMemory.addAndGet(size);
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
		log("Safe Test");
		SafeMemoryAllocator sma = new SafeMemoryAllocator();
		
		long address = sma.allocate(100, false);
		log("Address:" + address + "  Allocations:" + sma.allocations.size());
		SafeMemoryAllocation alloc = sma.getAllocation(address);
		log("Allocation:" + alloc);
		alloc = sma.getAllocation(address + 2);
		log("Allocation:" + alloc);
	}
	
	public static void log(Object msg) {
		System.out.println(msg);
	}
	
	class SafeMemoryAllocationWeakReference extends WeakReference<SafeMemoryAllocation> {
		final long size;
		final Range range;
		public SafeMemoryAllocationWeakReference(SafeMemoryAllocation allocation) {
			super(allocation, refQueue);
			size = allocation.size * -1;
			range = allocation.range;
		}		
		public void close() { 
			allocations.remove(range);
			totalMemory.addAndGet(size);
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
//			if(v.endRange==-1L && endRange==-1L) {
//				return startRange==v.startRange ? 0 : startRange<v.startRange ? -1 : 1; 
//			}
//			if(v.endRange==-1) {
//				return v.startRange>=startRange && v.startRange<=startRange ? 0 : v.startRange > startRange ? -1 : 1;
//			}
//			if(endRange==-1) {
//				return startRange>=v.startRange && startRange<=v.startRange ? 0 : startRange < v.startRange ? -1 : 1; 
//			}
//			
			
			
			
			return v.startRange < startRange ? -1 : 1;
//			if(endRange==-1L) {
//				if(v.startRange == startRange) return 0;
//				return startRange < v.startRange ? -1 : 1;
//			}
//			if(v.startRange>=startRange && v.startRange<=endRange) return 0;
//			return v.startRange < startRange ? -1 : 1;
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
//			if(v.endRange==-1L && endRange==-1L) {
//			return startRange==v.startRange ? 0 : startRange<v.startRange ? -1 : 1; 
//		}
//		if(v.endRange==-1) {
//			return v.startRange>=startRange && v.startRange<=startRange ? 0 : v.startRange > startRange ? -1 : 1;
//		}
//		if(endRange==-1) {
//			return startRange>=v.startRange && startRange<=v.startRange ? 0 : startRange < v.startRange ? -1 : 1; 
//		}
			
			if (endRange != v.endRange)
				return false;
			if (startRange != v.startRange)
				return false;
			return true;
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
		/** The notional address of the memory block */
		final long startRange;
		/** The notional end address of the memory block */
		final long endRange;
		/** The search range */
		final Range range;
		
		
		
		/**
		 * Creates a new SafeMemoryAllocation
		 * @param size The size of the allocation in bytes
		 * @param onHeap true for on heap memory, false for direct
		 */
		SafeMemoryAllocation(long size, boolean onHeap) {
			if(size > Integer.MAX_VALUE || size < 1) throw new IllegalArgumentException("Invalid Safe Memory Size [" + size + "]", new Throwable());
			this.size = size;
			block = onHeap ? ByteBuffer.allocate((int)size) : ByteBuffer.allocateDirect((int)size);
			startRange = DefaultUnsafeAdapterImpl.getAddressOf(block);
			endRange = startRange + size;
			range = Range.range(startRange, endRange);
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
}
