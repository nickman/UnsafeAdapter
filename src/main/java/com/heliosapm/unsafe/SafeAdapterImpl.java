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

public class SafeAdapterImpl extends DefaultUnsafeAdapterImpl {
	/** The system prop indicating if safe allocations should be on heap */
	public static final String SAFE_ALLOCS_ONHEAP_PROP = "safe.allocations.onheap";	
	/** The maximum safe memory allocation */
	public static final long MAX_SAFE_MEM_SIZE = Integer.MAX_VALUE;

	
	/** A map of memory allocation sizes keyed by the address */
	final NonBlockingHashMapLong<ByteBuffer> safeMemoryAllocations;
	/** Indicates if safe allocations should be on heap */
	final boolean onHeap;
	/** The safe memory allocator */
	final SafeMemoryAllocator allocator;

	static {
		
	}
	
	/**
	 * Creates a new SafeAdapterImpl
	 */
	SafeAdapterImpl() {
		super();
		onHeap = System.getProperties().containsKey(SAFE_ALLOCS_ONHEAP_PROP);
		if(trackMem) {
			safeMemoryAllocations = new NonBlockingHashMapLong<ByteBuffer>(1024, true);
		} else {
			safeMemoryAllocations = null;
		}
		allocator = SafeMemoryAllocator.getInstance();
	}
	
	/**
	 * Terminates this adapter.
	 * <b>TEST HOOK ONLY!</b>. Not intended for regular use.
	 */
	void shutdown() {
		
	}
	
	
	/**
	 * Allocates a chunk of memory and returns its address
	 * @param size The number of bytes to allocate
	 * @param alignmentOverhead The number of bytes allocated in excess of requested for alignment
	 * @return The address of the allocated memory
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	long _allocateMemory(long size, long alignmentOverhead) {
		if(size > MAX_SAFE_MEM_SIZE || size < 1) throw new IllegalArgumentException("Invalid Safe Memory Size [" + size + "]", new Throwable());
		ByteBuffer buff = onHeap ? ByteBuffer.allocate((int)size) : ByteBuffer.allocateDirect((int)size);
		long address = getAddressOf(buff);
		if(trackMem) {		
			memoryAllocations.put(address, new long[]{size, alignmentOverhead});
			totalMemoryAllocated.addAndGet(size);
			totalAlignmentOverhead.addAndGet(alignmentOverhead);
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
			long[] alloc = memoryAllocations.remove(address);
			if(alloc!=null) {				
				totalMemoryAllocated.addAndGet(-1L * alloc[0]);
				totalAlignmentOverhead.addAndGet(-1L * alloc[1]);
			}
		}		
		UNSAFE.freeMemory(address);
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
	
}
