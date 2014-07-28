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
	static final Unsafe UNSAFE;
    /** Indicates if the 5 param copy memory is supported */
    public static final boolean FIVE_COPY;
    /** Indicates if the 4 param set memory is supported */
    public static final boolean FOUR_SET;	
	
	/** The system prop indicating that allocations should be tracked */
	public static final String TRACK_ALLOCS_PROP = "unsafe.allocations.track";
	/** The system prop indicating that allocations should be alligned */
	public static final String ALIGN_ALLOCS_PROP = "unsafe.allocations.align";
	/** The debug agent library signature */
	public static final String AGENT_LIB = "-agentlib:";	
	/** The legacy debug agent library signature */
	public static final String LEGACY_AGENT_LIB = "-Xrunjdwp:";
    /** The address size */
    public static final int ADDRESS_SIZE;
    /** Byte array offset */
    public static final int BYTES_OFFSET;
    /** Object array offset */
    public static final long OBJECTS_OFFSET;
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

	

	static {
		// =========================================================
		// Acquire the unsafe instance
		// =========================================================
		try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to access sun.misc.Unsafe.theUnsafe", ex);
		}
		// =========================================================
		// Determine which version of unsafe we're using
		// =========================================================
        int copyMemCount = 0;
        int setMemCount = 0;
        for(Method method: Unsafe.class.getDeclaredMethods()) {
        	if("copyMemory".equals(method.getName())) {
        		copyMemCount++;
        	}
        	if("setMemory".equals(method.getName())) {
        		setMemCount++;
        	}
        }
        FIVE_COPY = copyMemCount>1;
        FOUR_SET = setMemCount>1;
		// =========================================================
		// Get the sizes of commonly used references
		// =========================================================        
        ADDRESS_SIZE = UNSAFE.addressSize();
        BYTES_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        OBJECTS_OFFSET = UNSAFE.arrayBaseOffset(Object[].class);        
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
	
	/**
     * Returns the address of the passed object
     * @param obj The object to get the address of 
     * @return the address of the passed object or zero if the passed object is null
     */
    public static long getAddressOf(Object obj) {
    	if(obj==null) return 0;
    	Object[] array = new Object[] {obj};
    	return ADDRESS_SIZE==4 ? UNSAFE.getInt(array, OBJECTS_OFFSET) : UNSAFE.getLong(array, OBJECTS_OFFSET);
    }	
	

	/**
	 * Report the size in bytes of a native pointer, as stored via #putAddress.
	 * This value will be either 4 or 8.  Note that the sizes of other primitive 
	 * types (as stored in native memory blocks) are determined fully by their information content.
	 * @return The size in bytes of a native pointer
	 * @see sun.misc.Unsafe#addressSize()
	 */
	public int addressSize() {
		return UNSAFE.addressSize();
	}

	/**
	 * Allocate an instance but do not run an constructor. 
	 * Initializes the class if it has not yet been.
	 * @param clazz The class to allocate an instance of
	 * @return The created object instance
	 * @throws InstantiationException thrown on a failure to instantiate
	 * @see sun.misc.Unsafe#allocateInstance(java.lang.Class)
	 */
	public Object allocateInstance(Class<?> clazz) throws InstantiationException {
		return UNSAFE.allocateInstance(clazz);
	}

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
		return _allocateMemory(size, 0L);
	}
	
	/**
	 * Allocates a chunk of memory and returns its address
	 * @param size The number of bytes to allocate
	 * @param alignmentOverhead The number of bytes allocated in excess of requested for alignment
	 * @return The address of the allocated memory
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	long _allocateMemory(long size, long alignmentOverhead) {
		long address = UNSAFE.allocateMemory(size);
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
	
	

	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#arrayBaseOffset(java.lang.Class)
	 */
	public int arrayBaseOffset(Class arg0) {
		return UNSAFE.arrayBaseOffset(arg0);
	}

	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#arrayIndexScale(java.lang.Class)
	 */
	public int arrayIndexScale(Class arg0) {
		return UNSAFE.arrayIndexScale(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @return
	 * @see sun.misc.Unsafe#compareAndSwapInt(java.lang.Object, long, int, int)
	 */
	public final boolean compareAndSwapInt(Object arg0, long arg1, int arg2,
			int arg3) {
		return UNSAFE.compareAndSwapInt(arg0, arg1, arg2, arg3);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @return
	 * @see sun.misc.Unsafe#compareAndSwapLong(java.lang.Object, long, long, long)
	 */
	public final boolean compareAndSwapLong(Object arg0, long arg1, long arg2,
			long arg3) {
		return UNSAFE.compareAndSwapLong(arg0, arg1, arg2, arg3);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @return
	 * @see sun.misc.Unsafe#compareAndSwapObject(java.lang.Object, long, java.lang.Object, java.lang.Object)
	 */
	public final boolean compareAndSwapObject(Object arg0, long arg1,
			Object arg2, Object arg3) {
		return UNSAFE.compareAndSwapObject(arg0, arg1, arg2, arg3);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @see sun.misc.Unsafe#copyMemory(long, long, long)
	 */
	public void copyMemory(long arg0, long arg1, long arg2) {
		UNSAFE.copyMemory(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 * @see sun.misc.Unsafe#copyMemory(java.lang.Object, long, java.lang.Object, long, long)
	 */
	public void copyMemory(Object arg0, long arg1, Object arg2, long arg3,
			long arg4) {
		UNSAFE.copyMemory(arg0, arg1, arg2, arg3, arg4);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @return
	 * @see sun.misc.Unsafe#defineAnonymousClass(java.lang.Class, byte[], java.lang.Object[])
	 */
	public Class defineAnonymousClass(Class arg0, byte[] arg1, Object[] arg2) {
		return UNSAFE.defineAnonymousClass(arg0, arg1, arg2);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 * @param arg5
	 * @return
	 * @see sun.misc.Unsafe#defineClass(java.lang.String, byte[], int, int, java.lang.ClassLoader, java.security.ProtectionDomain)
	 */
	public Class defineClass(String arg0, byte[] arg1, int arg2, int arg3,
			ClassLoader arg4, ProtectionDomain arg5) {
		return UNSAFE.defineClass(arg0, arg1, arg2, arg3, arg4, arg5);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#defineClass(java.lang.String, byte[], int, int)
	 */
	public Class defineClass(String arg0, byte[] arg1, int arg2, int arg3) {
		return UNSAFE.defineClass(arg0, arg1, arg2, arg3);
	}

	/**
	 * @param arg0
	 * @see sun.misc.Unsafe#ensureClassInitialized(java.lang.Class)
	 */
	public void ensureClassInitialized(Class arg0) {
		UNSAFE.ensureClassInitialized(arg0);
	}

	/**
	 * @param obj
	 * @return
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return UNSAFE.equals(obj);
	}

	/**
	 * @param arg0
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#fieldOffset(java.lang.reflect.Field)
	 */
	public int fieldOffset(Field arg0) {
		return UNSAFE.fieldOffset(arg0);
	}

	
	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#getAddress(long)
	 */
	public long getAddress(long arg0) {
		return UNSAFE.getAddress(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getBoolean(java.lang.Object, int)
	 */
	public boolean getBoolean(Object arg0, int arg1) {
		return UNSAFE.getBoolean(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getBoolean(java.lang.Object, long)
	 */
	public boolean getBoolean(Object arg0, long arg1) {
		return UNSAFE.getBoolean(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getBooleanVolatile(java.lang.Object, long)
	 */
	public boolean getBooleanVolatile(Object arg0, long arg1) {
		return UNSAFE.getBooleanVolatile(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#getByte(long)
	 */
	public byte getByte(long arg0) {
		return UNSAFE.getByte(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getByte(java.lang.Object, int)
	 */
	public byte getByte(Object arg0, int arg1) {
		return UNSAFE.getByte(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getByte(java.lang.Object, long)
	 */
	public byte getByte(Object arg0, long arg1) {
		return UNSAFE.getByte(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getByteVolatile(java.lang.Object, long)
	 */
	public byte getByteVolatile(Object arg0, long arg1) {
		return UNSAFE.getByteVolatile(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#getChar(long)
	 */
	public char getChar(long arg0) {
		return UNSAFE.getChar(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getChar(java.lang.Object, int)
	 */
	public char getChar(Object arg0, int arg1) {
		return UNSAFE.getChar(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getChar(java.lang.Object, long)
	 */
	public char getChar(Object arg0, long arg1) {
		return UNSAFE.getChar(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getCharVolatile(java.lang.Object, long)
	 */
	public char getCharVolatile(Object arg0, long arg1) {
		return UNSAFE.getCharVolatile(arg0, arg1);
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
	 * @return
	 * @see sun.misc.Unsafe#getFloat(long)
	 */
	public float getFloat(long arg0) {
		return UNSAFE.getFloat(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getFloat(java.lang.Object, int)
	 */
	public float getFloat(Object arg0, int arg1) {
		return UNSAFE.getFloat(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getFloat(java.lang.Object, long)
	 */
	public float getFloat(Object arg0, long arg1) {
		return UNSAFE.getFloat(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getFloatVolatile(java.lang.Object, long)
	 */
	public float getFloatVolatile(Object arg0, long arg1) {
		return UNSAFE.getFloatVolatile(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#getInt(long)
	 */
	public int getInt(long arg0) {
		return UNSAFE.getInt(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getInt(java.lang.Object, int)
	 */
	public int getInt(Object arg0, int arg1) {
		return UNSAFE.getInt(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getInt(java.lang.Object, long)
	 */
	public int getInt(Object arg0, long arg1) {
		return UNSAFE.getInt(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getIntVolatile(java.lang.Object, long)
	 */
	public int getIntVolatile(Object arg0, long arg1) {
		return UNSAFE.getIntVolatile(arg0, arg1);
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
	 * @return
	 * @see sun.misc.Unsafe#getLong(long)
	 */
	public long getLong(long arg0) {
		return UNSAFE.getLong(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getLong(java.lang.Object, int)
	 */
	public long getLong(Object arg0, int arg1) {
		return UNSAFE.getLong(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getLong(java.lang.Object, long)
	 */
	public long getLong(Object arg0, long arg1) {
		return UNSAFE.getLong(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getLongVolatile(java.lang.Object, long)
	 */
	public long getLongVolatile(Object arg0, long arg1) {
		return UNSAFE.getLongVolatile(arg0, arg1);
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
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#getShort(long)
	 */
	public short getShort(long arg0) {
		return UNSAFE.getShort(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @deprecated
	 * @see sun.misc.Unsafe#getShort(java.lang.Object, int)
	 */
	public short getShort(Object arg0, int arg1) {
		return UNSAFE.getShort(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getShort(java.lang.Object, long)
	 */
	public short getShort(Object arg0, long arg1) {
		return UNSAFE.getShort(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 * @see sun.misc.Unsafe#getShortVolatile(java.lang.Object, long)
	 */
	public short getShortVolatile(Object arg0, long arg1) {
		return UNSAFE.getShortVolatile(arg0, arg1);
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
	 * @param arg0
	 * @return
	 * @see sun.misc.Unsafe#objectFieldOffset(java.lang.reflect.Field)
	 */
	public long objectFieldOffset(Field arg0) {
		return UNSAFE.objectFieldOffset(arg0);
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
	 * @return
	 * @see sun.misc.Unsafe#staticFieldOffset(java.lang.reflect.Field)
	 */
	public long staticFieldOffset(Field arg0) {
		return UNSAFE.staticFieldOffset(arg0);
	}

	/**
	 * @param arg0
	 * @see sun.misc.Unsafe#throwException(java.lang.Throwable)
	 */
	public void throwException(Throwable arg0) {
		UNSAFE.throwException(arg0);
	}

	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return UNSAFE.toString();
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
