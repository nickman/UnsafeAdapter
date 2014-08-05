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
	final NonBlockingHashMapLong<Reference<Object>> trackedRefs = new NonBlockingHashMapLong<Reference<Object>>(1024, true); 
	/** The interface tracker to cache which classes implement "special" UnsafeAdapter interfaces */
	final InterfaceTracker ifaceTracker = new InterfaceTracker();
    /** Serial number factory for memory allocation references */
	protected final AtomicLong refSerial = new AtomicLong(0L);

	// =========================================================
	//  Individual Allocation Tracking
	// =========================================================	
	/** A map of memory allocation sizes keyed by the memory block address */
//	final SpinLockedTLongLongHashMap memoryAllocations;
	final NonBlockingHashMapLong<Long> memoryAllocations;
	/** A map of memory allocation alignment overhead sizes keyed by the memory block address */
//	final SpinLockedTLongLongHashMap alignmentOverheads;
	final NonBlockingHashMapLong<Long> alignmentOverheads;
	
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
	 * Creates a new AllocationReferenceManager
	 * @param memTracking true if memory allocation tracking is enabled, false otherwise
	 * @param memAlignment true if cache-line memory alignment is enabled, false otherwise 
	 */
	public AllocationReferenceManager(final boolean memTracking, final boolean memAlignment) {
		this.memTracking = memTracking;
		this.memAlignment = memAlignment;
		if(this.memTracking) {
			memoryAllocations = new NonBlockingHashMapLong<Long>(1024, true);
//			memoryAllocations = new SpinLockedTLongLongHashMap(2048, .1f);
			totalMemoryAllocated = new AtomicLong();
			totalAllocationCount = new AtomicLong();
			totalAlignmentOverhead = new AtomicLong();			
		} else {
			memoryAllocations = null;
			totalMemoryAllocated = null;
			totalAllocationCount = null;
			totalAlignmentOverhead = null;			
		}		
		if(this.memAlignment) {
			alignmentOverheads = new NonBlockingHashMapLong<Long>(1024, true);
//			alignmentOverheads = new SpinLockedTLongLongHashMap(2048, .1f);			
		} else {
			alignmentOverheads = null;
		}
		// =========================================================
		// Start the cleaner thread
		// =========================================================
		cleanerThread = new Thread(this, "UnsafeMemoryAllocationCleaner#" + cleanerSerial.incrementAndGet());
		cleanerThread.setDaemon(true);
		cleanerThread.setPriority(Thread.MAX_PRIORITY);
		cleanerThread.start();		
		
	}


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
					decrementMemTracking(((AllocationPointerPhantomRef)ref).getClearedAddresses());					
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
				if(!memoryAllocations.contains(clearedAddress)) continue;
				final long allocSize = memoryAllocations.remove(clearedAddress);
				if(allocSize>0) {
					totalMemoryAllocated.addAndGet(allocSize * -1L);
					totalAllocationCount.decrementAndGet();
					if(memAlignment) {
						final long alignOverhead = alignmentOverheads.remove(clearedAddress);
						if(alignOverhead>0) {
							totalAlignmentOverhead.addAndGet(alignOverhead * -1L);
						}
					}
				}
			}
		}
	}
	
	
	public final void allocateMemory(long size, long alignmentOverhead, Object memoryManager) {
		
	}

	public final void reallocateMemory(long priorAddress, long newAddress, long size, long alignmentOverhead, Object memoryManager) {
		
	}
	
	
	/**
	 * Returns 
	 * @return the memTracking
	 */
	public final boolean isMemTracking() {
		return memTracking;
	}


	/**
	 * Returns 
	 * @return the memAlignment
	 */
	public final boolean isMemAlignment() {
		return memAlignment;
	}


	/**
	 * Returns the number of pending allocation references
	 * @return the number of pending allocation references
	 */
	public final long getPendingRefs() {
		return trackedRefs.size();
	}


	/**
	 * Returns 
	 * @return the ifaceTracker
	 */
	public final InterfaceTracker getIfaceTracker() {
		return ifaceTracker;
	}


	/**
	 * Returns 
	 * @return the refSerial
	 */
	public final AtomicLong getRefSerial() {
		return refSerial;
	}


	/**
	 * Returns 
	 * @return the memoryAllocations
	 */
	public final NonBlockingHashMapLong<Long> getMemoryAllocations() {
		return memoryAllocations;
	}


	/**
	 * Returns 
	 * @return the alignmentOverheads
	 */
	public final NonBlockingHashMapLong<Long> getAlignmentOverheads() {
		return alignmentOverheads;
	}


	/**
	 * Returns 
	 * @return the totalMemoryAllocated
	 */
	public final AtomicLong getTotalMemoryAllocated() {
		return totalMemoryAllocated;
	}


	/**
	 * Returns 
	 * @return the totalAllocationCount
	 */
	public final AtomicLong getTotalAllocationCount() {
		return totalAllocationCount;
	}


	/**
	 * Returns 
	 * @return the totalAlignmentOverhead
	 */
	public final AtomicLong getTotalAlignmentOverhead() {
		return totalAlignmentOverhead;
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
