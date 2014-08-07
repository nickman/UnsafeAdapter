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
import java.util.concurrent.atomic.AtomicLong;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import com.heliosapm.unsafe.ReflectionHelper.ReferenceQueueLengthReader;

/**
 * <p>Title: AllocationReferenceManager</p>
 * <p>Description: Handles the creating and clearing of memory block allocation references.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.AllocationReferenceManager</code></p>
 */

public class AllocationReferenceManager implements Runnable {
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
	
	
	// =========================================================
	//  Aggregate Allocation Tracking
	// =========================================================		
	/** The total native memory allocation */
	final AtomicLong totalMemoryAllocated;
	/** The total number of native memory allocations */
	final AtomicLong totalAllocationCount;
	/** The total native memory allocation overhead for alignment */
	final AtomicLong totalAlignmentOverhead;
	
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
	
	/**
	 * <b>TEST HOOK ONLY !</b>
	 * Don't use this unless you know what you're doing.
	 */
	@SuppressWarnings("unused")
	private final void reset() {
		if(totalMemoryAllocated!=null) totalMemoryAllocated.set(0L);
		if(totalAllocationCount!=null) totalAllocationCount.set(0L);
		if(totalAlignmentOverhead!=null) totalAlignmentOverhead.set(0L);
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
			totalMemoryAllocated = new AtomicLong();
			totalAllocationCount = new AtomicLong();
			totalAlignmentOverhead = new AtomicLong();		
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
	
	
	/**
	 * Returns a new {@link AllocationPointer} that is ref queue registered 
	 * and configured according to mem tracking and mem alignment settings. 
	 * @return a new AllocationPointer
	 */
	public final AllocationPointer newAllocationPointer() {
		final long refId = refSerial.incrementAndGet();
		final AllocationPointer ap = new AllocationPointer(memTracking, memAlignment, refId);
		AllocationPointerPhantomRef ref = ap.getReference(refQueue);
		trackedRefs.put(refId, ref);
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
					System.gc();
					continue;
				}
//				log("Dequeued Reference [%s]", ref);
				if(ref!=null) { 
					ref.clear();
				}
				if(ref instanceof AllocationPointerPhantomRef) {
					
				}
				if(memTracking && ref instanceof AllocationPointerPhantomRef) {
//					decrementMemTracking(((AllocationPointerPhantomRef)ref).getClearedAddresses());					
				} else {
					if(memTracking && InterfaceTracker.isDeallocatable(ifaceTracker.getMask(ref))) {
						final long[][] addrs = ((Deallocatable)ref).getAddresses();
						if(addrs!=null && addrs.length>0) {
							for(long[] clearedAddresses: addrs) {
								decrementMemTracking(clearedAddresses);
							}
						}
					}
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
	
	private final void decrementMemTracking(final long...clearedAddresses) {
		if(clearedAddresses!=null && clearedAddresses.length>0) {
			for(final long clearedAddress: clearedAddresses) {				
//				if(!memoryAllocations.contains(clearedAddress)) continue;
//				final long allocSize = memoryAllocations.remove(clearedAddress);
//				if(allocSize>0) {
//					totalMemoryAllocated.addAndGet(allocSize * -1L);
//					totalAllocationCount.decrementAndGet();
//					if(memAlignment) {
//						final long alignOverhead = alignmentOverheads.remove(clearedAddress);
//						if(alignOverhead>0) {
//							totalAlignmentOverhead.addAndGet(alignOverhead * -1L);
//						}
//					}
//				}
			}
		}
	}
	
	/**
	 * Increments the memory and overhead counters if enabled in each case
	 * @param size The memory allocation size
	 * @param alignmentOverhead The cache-line memory alignment overhead
	 */
	final void increment(final long size, final long alignmentOverhead) {
		if(memTracking) {
			totalMemoryAllocated.addAndGet(size);
			totalAllocationCount.incrementAndGet();			
		}
		if(memAlignment) {
			totalAlignmentOverhead.addAndGet(alignmentOverhead);
		}
	}
	
	/**
	 * Decrements the memory and overhead counters if enabled in each case
	 * @param size The memory allocation size
	 * @param alignmentOverhead The cache-line memory alignment overhead
	 */
	final void decrement(final long size, final long alignmentOverhead) {
		if(memTracking && size > 0) {
			totalMemoryAllocated.addAndGet(0-size);
			totalAllocationCount.decrementAndGet();			
		}
		if(memAlignment && alignmentOverhead > 0) {
			totalAlignmentOverhead.addAndGet(0-alignmentOverhead);
		}
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
				if(InterfaceTracker.isAssignable(mask)) {
					((AddressAssignable)memoryManager).setAllocated(allocatedAddress, size, alignmentOverhead);
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
					apRef.add(allocatedAddress, size, alignmentOverhead);
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
			totalAllocationCount.incrementAndGet();
			totalMemoryAllocated.addAndGet(size);
			if(memAlignment) totalAlignmentOverhead.addAndGet(alignmentOverhead);
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
		return memTracking ? totalMemoryAllocated.get() : -1L;
	}


	/**
	 * Returns the total number of memory allocations
	 * @return the total number of memory allocations
	 */
	public final long getTotalAllocationCount() {
		return memTracking ? totalAllocationCount.get() : -1L;
	}


	/**
	 * Returns the total cache-line memory alignment overhead
	 * @return the total cache-line memory alignment overhead
	 */
	public final long getTotalAlignmentOverhead() {
		return memAlignment ? totalAlignmentOverhead.get() : -1L;		
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
