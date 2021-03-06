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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicLong;

import jsr166e.LongAdder;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import sun.misc.Unsafe;

import com.heliosapm.unsafe.ReflectionHelper.ReferenceQueueLengthReader;

/**
 * <p>Title: AllocationReferenceManager</p>
 * <p>Description: Handles the creating and clearing of memory block allocation references.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationReferenceManager</code></p>
 */

public class AllocationReferenceManager implements Runnable {
	
    /** The unsafe instance */    
	static final Unsafe unsafe;

	static {
		try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to access sun.misc.Unsafe.theUnsafe", ex);
		}		
	}
	
	/** Indicates if memory allocation tracking is enabled */
	private final boolean memTracking;
	/** Indicates if cache-line memory alignment is enabled */
	private final boolean memAlignment;
	
	// =========================================================
	//  Reference Tracking
	// =========================================================
	/** A map of phantom refs */ 
	final NonBlockingHashMapLong<AllocationPointerPhantomRef> trackedRefs = new NonBlockingHashMapLong<AllocationPointerPhantomRef>(1024, true);
	/** The interface tracker to cache which classes implement "special" UnsafeAdapter interfaces */
	final InterfaceTracker ifaceTracker = new InterfaceTracker();
    /** Serial number factory for memory allocation references */
	protected final AtomicLong refSerial = new AtomicLong(0L);

	/** A map of memory allocation sizes and overhead keyed by the address for unmanaged memory allocations */ 
	final NonBlockingHashMapLong<long[]> trackedRaw;
	/** A map of runnables registered for AllocationPointers and fired when the AP is cleared keyed by the reference id */ 
	final NonBlockingHashMapLong<RunnableSequence> onRefClearRunnables = new NonBlockingHashMapLong<RunnableSequence>(256);
	
	
	// =========================================================
	//  Aggregate Allocation Tracking
	// =========================================================		
	/** The total native memory allocation */
	final LongAdder totalMemoryAllocated;
	/** The total number of native memory allocations */
	final LongAdder totalAllocationCount;
	/** The total native memory allocation overhead for alignment */
	final LongAdder totalAlignmentOverhead;
	
	// =========================================================
	//  Auto Deallocation
	// =========================================================			
	/** The reference queue where collected allocations go */
	final ReferenceQueue<Object> refQueue = new ReferenceQueue<Object>();
	/** The queue length reader for the reference queue */
	final ReferenceQueueLengthReader<?> refQueueLengthReader = ReflectionHelper.newReferenceQueueLengthReader(refQueue);
	/** The reference cleaner thread */
	final Thread cleanerThread;
	/** Serial number factory for cleaner threads */
	private static final AtomicLong cleanerSerial = new AtomicLong(0L);
	/** The total number of cleared references */
	final LongAdder refsCleared = new LongAdder();
	
	/**
	 * <b>TEST HOOK ONLY !</b>
	 * Don't use this unless you know what you're doing.
	 */
	@SuppressWarnings("unused")
	private final void reset() {
		refsCleared.reset();
		if(totalMemoryAllocated!=null) totalMemoryAllocated.reset();
		if(totalAllocationCount!=null) totalAllocationCount.reset();
		if(totalAlignmentOverhead!=null) totalAlignmentOverhead.reset();
		if(trackedRaw!=null) trackedRaw.clear();
		if(trackedRefs!=null) trackedRefs.clear();
	}
	
	
	/**
	 * Creates a new AllocationReferenceManager
	 * @param memTracking true if memory allocation tracking is enabled, false otherwise
	 * @param memAlignment true if cache-line memory alignment is enabled, false otherwise 
	 */
	public AllocationReferenceManager(final boolean memTracking, final boolean memAlignment) {
		this.memTracking = memTracking;
		this.memAlignment = memAlignment;
		if(this.memTracking) {
			totalMemoryAllocated = new LongAdder();
			totalAllocationCount = new LongAdder();
			totalAlignmentOverhead = new LongAdder();		
			trackedRaw = new NonBlockingHashMapLong<long[]>(1024, true);
		} else {
			totalMemoryAllocated = null;
			totalAllocationCount = null;
			totalAlignmentOverhead = null;
			trackedRaw = null;
		}		
		// =========================================================
		// Start the cleaner thread
		// =========================================================
		cleanerThread = new Thread(this, "UnsafeMemoryAllocationCleaner#" + cleanerSerial.incrementAndGet());
		cleanerThread.setDaemon(true);
		cleanerThread.setPriority(Thread.MAX_PRIORITY);
		cleanerThread.start();		
	}
	
	// =====================================================================================================
	// AllocationPointer Requests
	// =====================================================================================================
	
	private class RunnableSequence implements Runnable {
		/** An ordered set of runnables that will be run when this runnable runs */
		private final LinkedHashSet<Runnable> nestedRunnables = new LinkedHashSet<Runnable>();
		
		/**
		 * Adds a new nested runnable
		 * @param runnable The runnable to add
		 * @return this RunnableSequence
		 */
		public RunnableSequence registerRunnable(Runnable runnable) {
			if(runnable!=null) {
				nestedRunnables.add(runnable);
			}
			return this;
		}
		
		/**
		 * <p>Runs the nested runnables in the order they were registered</p>
		 * {@inheritDoc}
		 * @see java.lang.Runnable#run()
		 */
		public void run() {
			final Iterator<Runnable> iter = nestedRunnables.iterator();
			while(iter.hasNext()) {
				Runnable r = iter.next();
				try { r.run(); } catch (Exception x) {/* No Op */}
				iter.remove();
			}
		}
	}
	
	/**
	 * Registers an AllocationPointer on clear runnable.
	 * Throws a runime exception if the ref id does not belong to a registered AP.
	 * @param refId The reference id of the AllocationPointer
	 * @param runnable The runnable to register (ignored if null)
	 */
	public final void registerOnClearRunnable(final long refId, final Runnable runnable) {
		if(runnable!=null) {
			if(!trackedRefs.containsKey(refId)) throw new RuntimeException("The reference id [" + refId + "] is not registered");
			RunnableSequence rs = null;
			onRefClearRunnables.put(refId, ((
					rs = onRefClearRunnables.get(refId))==null ? 
							new RunnableSequence() : 
							rs)
						.registerRunnable(runnable)
					);
		}
	}
	
	/**
	 * Returns a new {@link AllocationPointer} that is ref queue registered 
	 * and configured according to mem tracking and mem alignment settings. 
	 * @return a new AllocationPointer
	 */
	public final AllocationPointer newAllocationPointer() {
		return newAllocationPointer(null);
	}
	
	/**
	 * Returns a new {@link AllocationPointer} that is ref queue registered 
	 * and configured according to mem tracking and mem alignment settings. 
	 * @param onClearRunnable An optional on clear runnable
	 * @return a new AllocationPointer
	 */
	public final AllocationPointer newAllocationPointer(final Runnable onClearRunnable) {
		final long refId = refSerial.incrementAndGet();
		final AllocationPointer ap = new AllocationPointer(memTracking, memAlignment, refId);
		AllocationPointerPhantomRef ref = ap.getReference(refQueue);
		trackedRefs.put(refId, ref);
		if(onClearRunnable!=null) {
			onRefClearRunnables.put(refId, new RunnableSequence().registerRunnable(onClearRunnable));
		}
		return ap;
	}
	


	/**
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		log("Starting Unsafe Cleaner Thread [%s]", Thread.currentThread().getName());
		UnsafeAdapter.registerCleanerThread(Thread.currentThread());
		boolean terminating = false;
		while(true) {
			try {
				Reference<?> ref = refQueue.remove(3000);
				if(ref==null) {
					//System.gc();
					continue;
				}
				log("Dequeued Reference [%s]", ref);
				if(ref instanceof AllocationPointerPhantomRef) {					
					AllocationPointerPhantomRef appr = (AllocationPointerPhantomRef)ref;
					final long refId = appr.getReferenceId();
					trackedRefs.remove(refId);	
					appr.clear();
//					log("Dequeued Appr [%s]", ref);
					long[][] cleared = appr.getClearedAddresses();
//					log("Cleared Appr [%s]", Arrays.deepToString(cleared));
					if(cleared!=null && cleared.length>0) {
						long[] totals = decrement(cleared);
						cleared = null;
						decrement(totals[0], totals[1]);
					}
					final RunnableSequence rs = onRefClearRunnables.remove(refId);
					if(rs != null) rs.run();  // FIXME:  hand this off to a pool ?
//					ref.clear();
					refsCleared.increment();
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
	 * Increments the memory and overhead counters if enabled in each case
	 * @param size The memory allocation size
	 * @param alignmentOverhead The cache-line memory alignment overhead
	 */
	final void increment(final long size, final long alignmentOverhead) {
		if(memTracking) {
			totalMemoryAllocated.add(size);
			totalAllocationCount.increment();			
		}
		if(memAlignment) {
			totalAlignmentOverhead.add(alignmentOverhead);
		}
	}
	
	/**
	 * Decrements the memory and overhead counters if enabled in each case
	 * @param size The memory allocation size
	 * @param alignmentOverhead The cache-line memory alignment overhead
	 */
	final void decrement(final long size, final long alignmentOverhead) {
		if(memTracking && size > 0) {
			totalMemoryAllocated.add(0-size);
			totalAllocationCount.decrement();			
		}
		if(memAlignment && alignmentOverhead > 0) {
			totalAlignmentOverhead.add(0-alignmentOverhead);
		}
	}
	
	/**
	 * Executes address freeing, total memory allocation and alignment overhead accounting for gc'ed AllocationPointers
	 * @param cleared The cleared array from the AllocationPointer phantom reference
	 * @return a long array with the total allocation and alignment overhead
	 * FIXME: this can be optimized
	 */
	final long[] decrement(final long[][] cleared) {
		final long[] totals = new long[2];
		if(cleared!=null && cleared.length>0) {
			for(long[] triplet: cleared) {
				long address = triplet[0];
				if(address>0) {
					unsafe.freeMemory(address);
					triplet[0] = 0;
				}
				if(memTracking) {
					totals[0] += triplet[1];
				}
				if(memAlignment) {
					totals[1] += triplet[2];
				}				
			}
		}
		return totals;
	}
	
	
	/**
	 * Tracks a new memory allocation
	 * @param allocatedAddress The allocated address
	 * @param size The size of the allocation
	 * @param alignmentOverhead The cache-line memory alignment overhead
	 * @param memoryManager The optional memory manager
	 */
	final void allocateMemory(final long allocatedAddress, final long size, final long alignmentOverhead, final Object memoryManager) {
		if(memoryManager!=null) {
			final int mask = ifaceTracker.getMask(memoryManager);
			if(mask==0)throw new IllegalArgumentException("The supplied memory manager of type [" + memoryManager.getClass().getName() + "] does not implement any known memory management interfaces");
			if(InterfaceTracker.isAllocationPointer(mask)) {
				((AllocationPointer)memoryManager).assignSlot(allocatedAddress, size, alignmentOverhead);				
			} else {
				if(InterfaceTracker.isDeallocatable(mask)) {
					Deallocatable dealloc = (Deallocatable)memoryManager;
					final long refId;
					final AllocationPointerPhantomRef apRef;
					if(dealloc.getReferenceId()==0) {
						apRef = newAllocationPointer().ingest(dealloc).getReference(refQueue);
						refId = apRef.getReferenceId();
					} else {
						refId = dealloc.getReferenceId();
						apRef = trackedRefs.get(refId);
						if(apRef==null) throw new RuntimeException("Failed to find AllocationPointerPhantomRef for reference id [" + refId + "]");					
					}
					apRef.add(allocatedAddress, size, alignmentOverhead);
				}
				if(InterfaceTracker.isAssignable(mask)) {
					((AddressAssignable)memoryManager).setAllocated(allocatedAddress, size, alignmentOverhead);
				}				
			}
			increment(size, alignmentOverhead);		
		} else {
			incrementUnmanaged(allocatedAddress, size, alignmentOverhead);
		}
		
	}

	/**
	 * Tracks a new memory re-allocation
	 * @param priorAddress The prior address will was deallocated
	 * @param allocatedAddress The allocated address
	 * @param size The size of the allocation
	 * @param alignmentOverhead The cache-line memory alignment overhead
	 * @param memoryManager The optional memory manager
	 */
	final void reallocateMemory(final long priorAddress, final long allocatedAddress, final long size, final long alignmentOverhead, final Object memoryManager) {
		if(memoryManager!=null) {
			final int mask = ifaceTracker.getMask(memoryManager);
			if(mask==0)throw new IllegalArgumentException("The supplied memory manager of type [" + memoryManager.getClass().getName() + "] does not implement any known memory management interfaces");
			if(InterfaceTracker.isAllocationPointer(mask)) {
				final AllocationPointer ap = (AllocationPointer)memoryManager;
				// ===========================================================================
				// handle decrements
				// ===========================================================================
				final int index = ap.findIndexForAddress(priorAddress);
				if(index != -1) {
					decrement(ap.getAllocationSize(index), ap.getAlignmentOverhead(index));
					ap.reassignSlot(index, allocatedAddress, size, alignmentOverhead);
				} else {
					// FIXME:  What the heck do we do now ?
					ap.assignSlot(allocatedAddress, size, alignmentOverhead);
				}
			} else {
				if(InterfaceTracker.isAssignable(mask)) {
					((AddressAssignable)memoryManager).setAllocated(allocatedAddress, size, alignmentOverhead);
					((AddressAssignable)memoryManager).removeAllocated(priorAddress);
				}
				if(InterfaceTracker.isDeallocatable(mask)) {
					Deallocatable dealloc = (Deallocatable)memoryManager;
					final long refId;
					final AllocationPointerPhantomRef apRef;
					if(dealloc.getReferenceId()==0) {
						apRef = newAllocationPointer().ingest(dealloc).getReference(refQueue);
						refId = apRef.getReferenceId();
					} else {
						refId = dealloc.getReferenceId();
						apRef = trackedRefs.get(refId);
						if(apRef==null) throw new RuntimeException("Failed to find AllocationPointerPhantomRef for reference id [" + refId + "]");					
					}
					decrement(apRef.getAllocationSize(priorAddress), apRef.getAlignmentOverhead(priorAddress));
					apRef.clearAddress(priorAddress);
					apRef.add(allocatedAddress, size, alignmentOverhead);
				}						
			}
			increment(size, alignmentOverhead);
		} else {
			incrementUnmanaged(priorAddress, allocatedAddress, size, alignmentOverhead);
		}					
	}
	
	
	/**
	 * Untracks a memory allocation
	 * @param freedAddress The address that was freed
	 * @param memoryManager The optional memory manager
	 */
	final void freeMemory(final long freedAddress, final Object memoryManager) {
		if(memoryManager!=null) {
			final int mask = ifaceTracker.getMask(memoryManager);
			if(mask==0)throw new IllegalArgumentException("The supplied memory manager of type [" + memoryManager.getClass().getName() + "] does not implement any known memory management interfaces");
			if(InterfaceTracker.isAllocationPointer(mask)) {
				final AllocationPointer ap = (AllocationPointer)memoryManager;
				final int index = ap.findIndexForAddress(freedAddress);
				if(index != -1) {
					decrement(ap.getAllocationSize(index), ap.getAlignmentOverhead(index));
					ap.clearAddress(index);
				}
			} else {
				if(InterfaceTracker.isAssignable(mask)) {					
					((AddressAssignable)memoryManager).removeAllocated(freedAddress);
				}
				if(InterfaceTracker.isDeallocatable(mask)) {
					Deallocatable dealloc = (Deallocatable)memoryManager;
					final long refId;
					final AllocationPointerPhantomRef apRef;
					if(dealloc.getReferenceId()!=0) {
						refId = dealloc.getReferenceId();
						apRef = trackedRefs.get(refId);
						if(apRef==null) throw new RuntimeException("Failed to find AllocationPointerPhantomRef for reference id [" + refId + "]");
						decrement(apRef.getAllocationSize(freedAddress), apRef.getAlignmentOverhead(freedAddress));
						apRef.clearAddress(freedAddress);
					}
				}
			}
		} else {
			decrementUnmanaged(freedAddress);
		}
	}
	
	/**
	 * Tracks the size and alignment overhead of an unmanaged allocation
	 * @param allocatedAddress The allocated address
	 * @param size The size of the allocation
	 * @param alignmentOverhead The alignment overhead
	 */
	private final void incrementUnmanaged(final long allocatedAddress, final long size, final long alignmentOverhead) {
		if(memTracking) {
			totalAllocationCount.increment();
			totalMemoryAllocated.add(size);
			if(memAlignment) totalAlignmentOverhead.add(alignmentOverhead);
			long[] prior = trackedRaw.put(allocatedAddress, memAlignment ? new long[]{size, alignmentOverhead} : new long[]{size});
			if(prior!=null) {
				// =======  COLLISION !!!  What do we do with it ?
			}
		}
	}
	
	/**
	 * Tracks the size and alignment overhead of an unmanaged re-allocation
	 * @param priorAddress The de-allocated address
	 * @param allocatedAddress The allocated address
	 * @param size The size of the allocation
	 * @param alignmentOverhead The alignment overhead
	 */
	private final void incrementUnmanaged(final long priorAddress, final long allocatedAddress, final long size, final long alignmentOverhead) {
		if(memTracking) {
			decrementUnmanaged(priorAddress);
			incrementUnmanaged(allocatedAddress, size, alignmentOverhead);
		}
	}
	
	/**
	 * Decrements the size and overhead for the freed memory address
	 * @param priorAddress The freed address
	 */
	private final void decrementUnmanaged(final long priorAddress) {
		if(memTracking) {			
			long[] prior = trackedRaw.remove(priorAddress);
			if(prior!=null) {
				if(prior.length==1) {
					decrement(prior[0], 0L);
				} else if(prior.length==2) {
					decrement(prior[0], prior[1]);
				}
			}
		}
	}
	
	
	/**
	 * Indicates if mem tracking is enabled
	 * @return true if mem tracking is enabled, false otherwise
	 */
	public final boolean isMemTracking() {
		return memTracking;
	}


	/**
	 * Indicates if cache-line mem alignment is enabled
	 * @return true if cache-line mem alignment, false otherwise
	 */
	public final boolean isMemAlignment() {
		return memAlignment;
	}


	/**
	 * Returns the number of pending allocation references
	 * @return the number of pending allocation references
	 */
	public int getPendingRefs() {
		return trackedRefs.size();
	}



	/**
	 * Returns the total tracked allocated memory allocated in bytes 
	 * @return the the total tracked allocated memory 
	 */
	public final long getTotalMemoryAllocated() {
		return memTracking ? totalMemoryAllocated.longValue() : -1L;
	}


	/**
	 * Returns the total number of memory allocations
	 * @return the total number of memory allocations
	 */
	public final long getTotalAllocationCount() {
		return memTracking ? totalAllocationCount.longValue() : -1L;
	}
	
	/**
	 * Returns the total number of tracked raw allocations (i.e. where no memory manager was provided to auto clear)
	 * @return the total number of tracked raw allocations
	 */
	public final int getTotalRawAllocationCount() {
		return memTracking ? trackedRaw.size() : -1;
	}

	
	/**
	 * Returns the total number of cleared allocation references
	 * @return the total number of cleared allocation references
	 */
	public final long getTotalClearedAllocations() {
		return refsCleared.longValue();
	}


	/**
	 * Returns the total cache-line memory alignment overhead
	 * @return the total cache-line memory alignment overhead
	 */
	public final long getTotalAlignmentOverhead() {
		return memAlignment ? totalAlignmentOverhead.longValue() : -1L;		
	}


	/**
	 * Returns the length of the reference queue
	 * @return the length of the reference queue
	 */
	public final long getRefQueuePending() {
		return refQueueLengthReader.getQueueLength();
	}
	
	/**
	 * Low maintenance out logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	void log(Object fmt, Object...args) {
		System.out.println(String.format("[ARM]" + fmt.toString(), args));
	}
	
	/**
	 * Low maintenance err logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	void loge(Object fmt, Object...args) {
		System.err.println(String.format("[ARM]" + fmt.toString(), args));
		if(args!=null && args.length>0 && args[args.length-1] instanceof Throwable) {
			System.err.println("Stack trace follows....");
			((Throwable)args[args.length-1]).printStackTrace(System.err);			
		}
	}
	

}
