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

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

/**
 * <p>Title: SafeAdapterImpl</p>
 * <p>Description: An extension of {@link DefaultUnsafeAdapterImpl} that implements safe memory management
 * when using null {@link Object} memory allocations, deallocations, re-allocations and memory data access.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.SafeAdapterImpl</code></p>
 */

public class SafeAdapterImpl extends DefaultUnsafeAdapterImpl implements SafeAdapterImplMBean {
	
	
	// =========================================================
	//  Singleton
	// =========================================================
	/** The singleton instance */
	private static volatile SafeAdapterImpl instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	

	
	/** A map of memory allocation sizes keyed by the address */
	final NonBlockingHashMapLong<ByteBuffer> safeMemoryAllocations;
	/** Indicates if safe allocations should be on heap */
	final boolean onHeap;
	/** The safe memory allocator */
	final SafeMemoryAllocator allocator;

	/**
	 * Acquires the singleton SafeAdapterImpl and initializes it on first access.
	 * @return the singleton SafeAdapterImpl
	 */
	public static SafeAdapterImpl getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new SafeAdapterImpl(); 
				}
			}
		}
		return instance;
	}

	
	/**
	 * Creates a new SafeAdapterImpl
	 */
	SafeAdapterImpl() {
		super();
		onHeap = System.getProperties().containsKey(UnsafeAdapter.SAFE_ALLOCS_ONHEAP_PROP);
		if(trackMem) {
			safeMemoryAllocations = new NonBlockingHashMapLong<ByteBuffer>(1024, true);
		} else {
			safeMemoryAllocations = null;
		}
		allocator = SafeMemoryAllocator.getInstance();				
	}
	
	/**
	 * Registers the Safe Memory Allocator's JMX MBean
	 */
	protected void registerJmx() {
		try {
			JMXHelper.registerMBean(this, UnsafeAdapter.SAFE_MEM_OBJECT_NAME);
		} catch (Exception ex) {
			loge("Failed to register JMX MemoryMBean", ex);
		}
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
				JMXHelper.unregisterMBean(UnsafeAdapter.SAFE_MEM_OBJECT_NAME);
				Field instanceField = ReflectionHelper.setFieldEditable(getClass(), "instance");
				instanceField.set(null, null);
			}
		} catch (Throwable t) {
			loge("Failed to reset SafeAdapter", t);
		}
	}
	
	
	/**
	 * Allocates a chunk of memory and returns its address
	 * @param size The number of bytes to allocate
	 * @param alignmentOverhead The number of bytes allocated in excess of requested for alignment
	 * @return The address of the allocated memory
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	long _allocateMemory(long size, long alignmentOverhead) {
		if(size > Integer.MAX_VALUE || size < 1) throw new IllegalArgumentException("Invalid Safe Memory Size [" + size + "]", new Throwable());
		ByteBuffer buff = onHeap ? ByteBuffer.allocate((int)size) : ByteBuffer.allocateDirect((int)size);
		long address = UnsafeAdapter.getAddressOf(buff);
		if(trackMem) {		
			if(trackMem) {		
				memoryAllocations.put(address, size);
				totalMemoryAllocated.addAndGet(size);
				totalAllocationCount.incrementAndGet();
				if(alignmentOverhead>0) {
					totalAlignmentOverhead.addAndGet(alignmentOverhead);
					alignmentOverheads.put(address, alignmentOverhead); 
				}
			}
		}
		return address;
	}
	
	/**
	 * Frees the memory allocated at the passed address
	 * @param address The address of the memory to free
	 */
	void freeMemory(long address) {
		if(address < 1) throw new IllegalArgumentException("Invalid Address [" + address + "]", new Throwable());
		allocator.removeAllocation(address);
		
		if(trackMem) {
			// ==========================================================
			//  Subtract pervious allocation
			// ==========================================================
			totalAllocationCount.decrementAndGet();
			long sz = memoryAllocations.remove(address);
			if(sz!=0) {								
				totalMemoryAllocated.addAndGet(-1L * sz);				
			}
			sz = alignmentOverheads.remove(address);
			if(sz!=0) {								
				totalAlignmentOverhead.addAndGet(-1L * sz);								
			}
		}		
		
	}	

	/**
	 * Low maintenance out logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	void log(Object fmt, Object...args) {
		System.out.println(String.format("[SafeAdapter]" + fmt.toString(), args));
	}
	
	/**
	 * Low maintenance err logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	void loge(Object fmt, Object...args) {
		System.err.println(String.format("[SafeAdapter]" + fmt.toString(), args));
		if(args!=null && args.length>0 && args[args.length-1] instanceof Throwable) {
			System.err.println("Stack trace follows....");
			((Throwable)args[args.length-1]).printStackTrace(System.err);			
		}
	}
	
	// ====================================================================================================================
	//  MemoryMBean Overrides
	// ====================================================================================================================
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.MemoryMBean#isSafeMemoryOffHeap()
	 */
	@Override
	public boolean isSafeMemoryOffHeap() {
		return !allocator.onHeap;
	}
	
	
	/**
	 * Returns the total off-heap allocated memory in bytes
	 * @return the total off-heap allocated memory
	 */
	@Override
	public long getTotalAllocatedMemory() {
		return allocator.getTotalMemoryAllocated();
	}
	
	/**
	 * Returns the total aligned memory overhead in bytes
	 * @return the total aligned memory overhead in bytes
	 */
	public long getAlignedMemoryOverhead() {
		return -1L; // TODO: Fixme
	}
	
	
	/**
	 * Returns the total off-heap allocated memory in Kb
	 * @return the total off-heap allocated memory
	 */
	public long getTotalAllocatedMemoryKb() {
		long mem = totalMemoryAllocated.get();
		return mem < 1 ? 0L : roundMem(mem, 1024);		
	}
	
	/**
	 * Returns the total off-heap allocated memory in Mb
	 * @return the total off-heap allocated memory
	 */
	public long getTotalAllocatedMemoryMb() {
		long mem = totalMemoryAllocated.get();
		return mem < 1 ? 0L : roundMem(mem, 1024);		
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
	 * Returns the total number of existing allocations, not including the base line defined in {@link UnsafeAdapterOld#BASELINE_ALLOCS}
	 * @return the total number of existing allocations
	 */
	public int getTotalAllocationCount() {
		return allocator.getTotalAllocations();
	}
	    	
	/**
	 * Returns the number of retained phantom references to memory allocations
	 * @return the number of retained phantom references to memory allocations
	 */
	public int getPendingRefs() {
		return allocator.getPending();
	}
	
	/**
	 * Returns the size of the reference queue
	 * @return the size of the reference queue
	 */
	public long getReferenceQueueSize() {
		return allocator.getRefQueuePending();
	}

	
}
