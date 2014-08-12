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
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import sun.misc.Unsafe;

/**
 * <p>Title: UnsafeAdapter</p>
 * <p>Description: A wrapper for {@link sun.misc.Unsafe} to provide enhanced functionality</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapter</code></p>
 */

@SuppressWarnings("restriction")
public class UnsafeAdapter {
	
	/** The system prop specifying the use of the safe memory management implementation */
	public static final String SAFE_MANAGER_PROP = "safe.allocations.manager";
	/** The system prop indicating that allocations should be tracked */
	public static final String TRACK_ALLOCS_PROP = "unsafe.allocations.track";
	
	/** The system prop indicating that allocations should be alligned */
	public static final String ALIGN_ALLOCS_PROP = "unsafe.allocations.align";
	/** The system prop indicating if safe allocations should be on heap */
	public static final String SAFE_ALLOCS_ONHEAP_PROP = "safe.allocations.onheap";	
	
    /** The unsafe instance */    
	static final Unsafe theUNSAFE;
	
    /** Indicates if the 5 param copy memory is supported */
    public static final boolean FIVE_COPY;
    /** Indicates if the 4 param set memory is supported */
    public static final boolean FOUR_SET;	
    /** The address size */
    public static final int ADDRESS_SIZE;
    /** The byte size of a <b><code>short</code></b> */
    public static final int SHORT_SIZE = 2;
    /** The byte size of a <b><code>char</code></b> */
    public static final int CHAR_SIZE = 2;    
    /** The byte size of a <b><code>int</code></b> */
    public static final int INT_SIZE = 4;
    /** The byte size of a <b><code>float</code></b> */
    public static final int FLOAT_SIZE = 4;
    /** The byte size of a <b><code>double</code></b> */
    public static final int DOUBLE_SIZE = 8;    
    /** The byte size of a <b><code>long</code></b> */
    public static final int LONG_SIZE = 8;
    /** The size of a <b><code>long[]</code></b> array offset */
    public final static int LONG_ARRAY_OFFSET;
    
    
    /** The maximum direct memory allocation size in bytes 
     * Can be overriden by the JVM launch option <b><code>-XX:MaxDirectMemorySize=&lt;size&gt;</code></b>.
     */
    public static final long MAX_DIRECT_MEMORY_SIZE;
    
    
    /** Byte array offset */
    public static final int BYTES_OFFSET;
    /** Object array offset */
    public static final long OBJECTS_OFFSET;
    
	/** The JMX ObjectName for the unsafe memory allocation JMX management interface MBean */
	public static final ObjectName UNSAFE_MEM_OBJECT_NAME = JMXHelper.objectName("com.heliosapm.unsafe:service=MemoryAllocationService,type=unsafe");
	/** The JMX ObjectName for the safe memory allocation JMX management interface MBean */
	public static final ObjectName SAFE_MEM_OBJECT_NAME = JMXHelper.objectName("com.heliosapm.unsafe:service=MemoryAllocationService,type=safe");
	/** The JMX ObjectName for the currently enabled memory allocation JMX management interface MBean */
	public static final ObjectName MEM_OBJECT_NAME = JMXHelper.objectName("com.heliosapm.unsafe:service=MemoryAllocationService");

	/** The max 32bit memory size that can be cache-line aligned */
	public static final int MAX_ALIGNED_MEM_32 = 1073741824;
	/** The max 64bit memory size that can be cache-line aligned */
	public static final long MAX_ALIGNED_MEM_64 = 4611686018427387904L;
	

	/** The configured adapter (default or safe) */
	private static final DefaultUnsafeAdapterImpl adapter;
	/** A map of cleaner threads keyed by the thread Id */
    /** A map of memory allocation references keyed by an internal counter */
    protected static final NonBlockingHashMapLong<Thread> cleanerThreads = new NonBlockingHashMapLong<Thread>();


	static {
		// =========================================================
		// Acquire the unsafe instance
		// =========================================================
		try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            theUNSAFE = (Unsafe) theUnsafe.get(null);			
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
		// Attempt to get the max direct memory size
		// =========================================================        
        long tmpSz = -1L;
        try {
        	tmpSz = (Long)ReflectionHelper.invoke(Class.forName("sun.misc.VM"), "maxDirectMemory");
        } catch (Throwable t) {
        	tmpSz = 64 * 1024 * 1024;
        }
        MAX_DIRECT_MEMORY_SIZE = tmpSz;
		// =========================================================
		// Get the sizes of commonly used references
		// =========================================================        
        ADDRESS_SIZE = theUNSAFE.addressSize();
        BYTES_OFFSET = theUNSAFE.arrayBaseOffset(byte[].class);
        OBJECTS_OFFSET = theUNSAFE.arrayBaseOffset(Object[].class);
        LONG_ARRAY_OFFSET = theUNSAFE.arrayBaseOffset(long[].class);
        adapter = getAdapter();
	}
	
	private static final DefaultUnsafeAdapterImpl getAdapter() {
		return System.getProperties().containsKey(SAFE_MANAGER_PROP) ? 
				SafeAdapterImpl.getInstance() : 
					DefaultUnsafeAdapterImpl.getInstance();
	}
	
	/**
	 * <b>TEST HOOK ONLY !</b>
	 * Don't use this unless you know what you're doing.
	 */
	@SuppressWarnings("unused")
	private static final void resetRefMgr() {
		ReflectionHelper.invoke(adapter, "resetRefMgr");
	}
	
	
	/**
	 * <b>TEST HOOK ONLY !</b>
	 * Don't use this unless you know what you're doing.
	 */
	@SuppressWarnings("unused")
	private static final void reset() {
		try {
			Field adapterField = ReflectionHelper.setFieldEditable(UnsafeAdapter.class, "adapter");
			if(adapter!=null) ReflectionHelper.invoke(adapter, "reset");
			adapterField.set(null, getAdapter());
		} catch (Throwable t) {
			loge("Failed to reset UnsafeAdapter", t);
		} finally {
			ReflectionHelper.setFieldReadOnly(UnsafeAdapter.class, "adapter");
		}
	}
	
	// =====================================================================================================
	// Static config
	// =====================================================================================================
	
	/**
	 * Indicates if allocated memory size tracking is enabled 
	 * @return true if allocated memory size tracking is enabled, false otherwise
	 */
	public static boolean isMemTrackingEnabled() {
		return adapter.trackMem;
	}
	
	/**
	 * Indicates if cache-line memory alignment overhead tracking is enabled 
	 * @return true if cache-line memory alignment overhead tracking is enabled, false otherwise
	 */
	public static boolean isAlignmentTrackingEnabled() {
		return adapter.alignMem;
	}
	
	
	// =====================================================================================================
	// AllocationPointer Requests
	// =====================================================================================================

	/**
	 * Returns a new {@link AllocationPointer} that is ref queue registered 
	 * and configured according to mem tracking and mem alignment settings. 
	 * @return a new AllocationPointer
	 */
	public static final AllocationPointer newAllocationPointer() {
		return adapter.refMgr.newAllocationPointer();
	}
	
	/**
	 * Returns a new {@link AllocationPointer} that is ref queue registered 
	 * and configured according to mem tracking and mem alignment settings. 
	 * @param onClearRunnable An optional on clear runnable
	 * @return a new AllocationPointer
	 */
	public static final AllocationPointer newAllocationPointer(final Runnable onClearRunnable) {
		return adapter.refMgr.newAllocationPointer(onClearRunnable);
	}
	
	/**
	 * Registers an AllocationPointer on clear runnable.
	 * Throws a runime exception if the ref id does not belong to a registered AP.
	 * @param refId The reference id of the AllocationPointer
	 * @param runnable The runnable to register (ignored if null)
	 */
	public final void registerOnClearRunnable(final long refId, final Runnable runnable) {
		adapter.refMgr.registerOnClearRunnable(refId, runnable);
	}
	
	// =====================================================================================================
	// Configuration reads
	// =====================================================================================================
	
	
	/**
	 * Indicates if the SafeMemoryAllocator adapter is installed
	 * @return true if the SafeMemoryAllocator adapter is installed, false otherwise
	 */
	public static final boolean isSafeAdapter() {
		return (adapter.getClass().equals(SafeAdapterImpl.class));
	}
	
	/**
	 * Returns the MemoryMBean for the currently installed adapter
	 * @return the MemoryMBean for the currently installed adapter
	 */
	public static MemoryMBean getMemoryMBean() {
		return adapter;
	}
	
	/**
	 * Registers a cleaner thread for tracking
	 * @param thread the cleaner thread to track
	 */
	static void registerCleanerThread(Thread thread) {
		if(thread!=null) {
			cleanerThreads.put(thread.getId(), thread);
		}
	}
	
	/**
	 * Removes a cleaner thread from tracking
	 * @param thread the cleaner thread to remove
	 */
	static void removeCleanerThread(Thread thread) {
		if(thread!=null) {
			cleanerThreads.remove(thread.getId());
		}		
	}
	
	/**
	 * Returns the number of registered cleaner threads
	 * @return the number of registered cleaner threads
	 */
	public static int getCleanerThreadCount() {
		return cleanerThreads.size();
	}
	
	// =====================================================================================================
	// Direct calls to theUNSAFE
	// =====================================================================================================
	
	/**
	 * Returns the largest memory base that can usefully be cache-line aligned
	 * @return the largest memory base that can usefully be cache-line aligned
	 */
	public static long getMaxCacheLineAlignBase() {
		if(ADDRESS_SIZE==4) return MAX_ALIGNED_MEM_32;
		return MAX_ALIGNED_MEM_64;
	}
	
	/**
     * Returns the address of the passed object
     * @param obj The object to get the address of 
     * @return the address of the passed object or zero if the passed object is null
     */
    public static long getAddressOf(Object obj) {
    	if(obj==null) return 0;
    	Object[] array = new Object[] {obj};
    	return UnsafeAdapter.ADDRESS_SIZE==4 ? theUNSAFE.getInt(array, UnsafeAdapter.OBJECTS_OFFSET) : theUNSAFE.getLong(array, UnsafeAdapter.OBJECTS_OFFSET);
    }	
	
	// =======================================================================================================================
	//	Sizing Operations Operations
	// =======================================================================================================================
	
    
    
	/**
	 * Report the offset of the first element in the storage allocation of a
	 * given array class.  If #arrayIndexScale  returns a non-zero value
	 * for the same class, you may use that scale factor, together with this
	 * base offset, to form new offsets to access elements of arrays of the
	 * given class.
	 * @param clazz The class to get the array base offset for
	 * @return thearray base offset
	 * @see sun.misc.Unsafe#arrayBaseOffset(java.lang.Class)
	 */
	public static int arrayBaseOffset(Class<?> clazz) {
		return theUNSAFE.arrayBaseOffset(clazz);
	}

	/**
	 * Report the scale factor for addressing elements in the storage
	 * allocation of a given array class.  However, arrays of "narrow" types
	 * will generally not work properly with accessors like #getByte(Object, int) , 
	 * so the scale factor for such classes is reported as zero.
	 * @param clazz The class to get the array index scale for
	 * @return the array index scale 
	 * @see sun.misc.Unsafe#arrayIndexScale(java.lang.Class)
	 */
	public static int arrayIndexScale(Class<?> clazz) {
		return theUNSAFE.arrayIndexScale(clazz);
	}

	/**
	 * Report the size in bytes of a native pointer, as stored via #putAddress.
	 * This value will be either 4 or 8.  Note that the sizes of other primitive 
	 * types (as stored in native memory blocks) is determined fully by their information content.
	 * @return The size in bytes of a native pointer
	 * @see sun.misc.Unsafe#addressSize()
	 */
	public static int addressSize() {
		return theUNSAFE.addressSize();
	}
	
	/**
	 * Report the size in bytes of a native memory page (whatever that is).
	 * This value will always be a power of two.
	 * @return the size in bytes of a native memory page
	 * @see sun.misc.Unsafe#pageSize()
	 */
	public static int pageSize() {
		return theUNSAFE.pageSize();
	}
	
	// =======================================================================================================
	// Utility/Misc operations
	// =======================================================================================================
	
	/**
	 * Gets the load average in the system run queue assigned
	 * to the available processors averaged over various periods of time.
	 * This method retrieves the given nelem samples and
	 * assigns to the elements of the given loadavg array.
	 * The system imposes a maximum of 3 samples, representing
	 * averages over the last 1,  5,  and  15 minutes, respectively.
	 * @param results The array to put the results into
	 * @param samples The number of samples to take. Max is 3. Min is 1. 
	 * @return The number of samples taken
	 * @see sun.misc.Unsafe#getLoadAverage(double[], int)
	 */
	public static int getLoadAverage(double[] results, int samples) {
		return theUNSAFE.getLoadAverage(results, samples);
	}
	
	/**
	 * Returns the most recent 1 minute system load average
	 * @return the most recent 1 minute system load average
	 * @see sun.misc.Unsafe#getLoadAverage(double[], int)
	 */
	public static double getLoad() {
		double[] loadavg = new double[1];
		if (theUNSAFE.getLoadAverage(loadavg, 1) == 1) {
			return loadavg[0];
		}
		return -1.0;		
	}
	
	/**
	 * Throw the exception without telling the verifier.
	 * @param throwable The throwable to throw
	 * @see sun.misc.Unsafe#throwException(java.lang.Throwable)
	 */
	public static void throwException(Throwable throwable) {
		theUNSAFE.throwException(throwable);
	}
	
	
	/** The array to return if {@link #getLoadAverage()} fails */
	private static final double[] FAILED_LOAD_AVG = new double[]{-1d, -1d, -1d};
	
	/**
	 * Returns the System load averages for the last 1,  5,  and  15 minutes, respectively.
	 * @return the System load averages 
	 * @see sun.misc.Unsafe#getLoadAverage(double[], int)
	 */
	public static double[] getLoadAverage() {
		double[] loadavg = new double[3];
		if(theUNSAFE.getLoadAverage(loadavg, 3)==3) {
			return loadavg;
		}
		return FAILED_LOAD_AVG.clone();
	}
	
	
	// =======================================================================================================================
	//	Unsafe Class Operations
	// =======================================================================================================================

	/**
	 * Allocate an instance but do not run an constructor. 
	 * Initializes the class if it has not yet been.
	 * @param clazz The class to allocate an instance of
	 * @return The created object instance
	 * @throws InstantiationException thrown on a failure to instantiate
	 * @see sun.misc.Unsafe#allocateInstance(java.lang.Class)
	 */
	public static Object allocateInstance(Class<?> clazz) throws InstantiationException {
		return theUNSAFE.allocateInstance(clazz);
	}
	
	/**
	 * Define a class but do not make it known to the class loader or system dictionary.
	 * For each CP entry, the corresponding CP patch must either be null or have
	 * the a format that matches its tag:<ul>
	 * <li> Integer, Long, Float, Double: the corresponding wrapper object type from java.lang</li>
	 * <li> Utf8: a string (must have suitable syntax if used as signature or name)</li>
	 * <li> Class: any java.lang.Class object</li>
	 * <li> String: any object (not just a java.lang.String)</li>
	 * <li> InterfaceMethodRef: (NYI) a method handle to invoke on that call site's arguments</li>
	 * </ul>
	 * @param hostClass The class within which the anonymous class is defined
	 * @param byteCode The class definition byte code
	 * @param cpPatches Uncertain // TODO: Define this
	 * @return the defined class
	 * @see sun.misc.Unsafe#defineAnonymousClass(java.lang.Class, byte[], java.lang.Object[])
	 */
	public static Class<?> defineAnonymousClass(Class<?> hostClass, byte[] byteCode, Object[] cpPatches) {
		return theUNSAFE.defineAnonymousClass(hostClass, byteCode, cpPatches);
	}

	/**
	 * Tell the VM to define a class, without security checks.  
	 * By default, the class loader and protection domain come from the caller's class.
	 * @param name The fully qualified name of the class 
	 * @param byteCode The class definition byte code
	 * @param offset The offset in the byteCode array where the class definition bytes start
	 * @param length The length of the class definition bytes
	 * @param loader The classloader to load the class from
	 * @param protectionDomain The protection domain designated to the created class
	 * @return the created class
	 * @see sun.misc.Unsafe#defineClass(java.lang.String, byte[], int, int, java.lang.ClassLoader, java.security.ProtectionDomain)
	 */
	public static Class<?> defineClass(String name, byte[] byteCode, int offset, int length, ClassLoader loader, ProtectionDomain protectionDomain) {
		return theUNSAFE.defineClass(name, byteCode, offset, length, loader, protectionDomain);
	}

	/**
	 * Tell the VM to define a class, without security checks.  
	 * By default, the class loader and protection domain come from the caller's class.
	 * @param name The fully qualified name of the class 
	 * @param byteCode The class definition byte code
	 * @param offset The offset in the byteCode array where the class definition bytes start
	 * @param length The length of the class definition bytes
	 * @return The defined class
	 * @deprecated Use the method {@link #defineClass(String, byte[], int, int, ClassLoader, ProtectionDomain)}
	 * @see sun.misc.Unsafe#defineClass(java.lang.String, byte[], int, int)
	 */
	public static Class<?> defineClass(String name, byte[] byteCode, int offset, int length) {
		return theUNSAFE.defineClass(name, byteCode, offset, length);
	}

	/**
	 * Ensure the given class has been initialized. 
	 * This is often needed in conjunction with obtaining the static field base of a class.
	 * @param clazz The class to ensure the initialization of
	 * @see sun.misc.Unsafe#ensureClassInitialized(java.lang.Class)
	 */
	public static void ensureClassInitialized(Class<?> clazz) {
		theUNSAFE.ensureClassInitialized(clazz);
	}
	
	
	// =======================================================================================================================
	//	Compare and Swap Operations
	// =======================================================================================================================
	
	/**
	 * Atomically update Java variable to x if it is currently holding expected.
	 * @param object The target object. If null, the offset is assumed to be an absolute address
	 * @param offset  The offset of the base address of the object, or an absolute address if the object is null
	 * @param expected  The expected current value
	 * @param x the value to set if the expected evaluation was correct
	 * @return true if the expected evaluation was correct and the value was set, false otherwise
	 * @see sun.misc.Unsafe#compareAndSwapInt(java.lang.Object, long, int, int)
	 */
	public static final boolean compareAndSwapInt(Object object, long offset, int expected, int x) {
		return theUNSAFE.compareAndSwapInt(object, offset, expected, x);
	}

	/**
	 * Atomically update Java variable to x if it is currently holding expected.
	 * @param object The target object. If null, the offset is assumed to be an absolute address
	 * @param offset  The offset of the base address of the object, or an absolute address if the object is null
	 * @param expected  The expected current value
	 * @param x the value to set if the expected evaluation was correct
	 * @return true if the expected evaluation was correct and the value was set, false otherwise
	 * @see sun.misc.Unsafe#compareAndSwapLong(java.lang.Object, long, long, long)
	 */
	public static final boolean compareAndSwapLong(Object object, long offset, long expected, long x) {
		return theUNSAFE.compareAndSwapLong(object, offset, expected, x);
	}

	/**
	 * Atomically update Java variable to x if it is currently holding expected.
	 * @param object The target object. If null, the offset is assumed to be an absolute address
	 * @param offset  The offset of the base address of the object, or an absolute address if the object is null
	 * @param expected  The expected current value
	 * @param x the value to set if the expected evaluation was correct
	 * @return true if the expected evaluation was correct and the value was set, false otherwise
	 * @see sun.misc.Unsafe#compareAndSwapObject(java.lang.Object, long, java.lang.Object, java.lang.Object)
	 */
	public static final boolean compareAndSwapObject(Object object, long offset, long expected, long x) {
		return theUNSAFE.compareAndSwapObject(object, offset, expected, x);
	}
	
	// =======================================================================================================================
	//	Class Member Offset Operations
	// =======================================================================================================================
	


	/**
	 * Returns the offset of a field, truncated to 32 bits. This method is implemented as follows:
	 * <pre>
	 * 		public static int fieldOffset(Field f) {
	 * 		    if (Modifier.isStatic(f.getModifiers()))
	 * 		        return (int) staticFieldOffset(f);
	 * 		    else
	 * 		        return (int) objectFieldOffset(f);
	 * 		}
	 * </pre>
	 * @param field The field to get the offset of
	 * @return the offset of the field
	 * @deprecated As - of 1.4.1, use {@link #staticFieldOffset(Field)} for static fields and {@link #objectFieldOffset(Field)} for non-static fields.
	 * @see sun.misc.Unsafe#fieldOffset(java.lang.reflect.Field)
	 */
	public static int fieldOffset(Field field) {
		return theUNSAFE.fieldOffset(field);
	}
	
	/**
	 * Report the location of a given field in the storage allocation of its
	 * class.  Do not expect to perform any sort of arithmetic on this offset;
	 * it is just a cookie which is passed to the unsafe heap memory accessors.
	 * 
	 * Any given field will always have the same offset and base, and no
	 * two distinct fields of the same class will ever have the same offset
	 * and base.
	 * 
	 * As of 1.4.1, offsets for fields are represented as long values,
	 * although the Sun JVM does not use the most significant 32 bits.
	 * However, JVM implementations which store static fields at absolute
	 * keyAddresses can use long offsets and null base pointers to express
	 * the field locations in a form usable by #getInt(Object,long) . (TODO: Fill in this ref.)
	 * Therefore, code which will be ported to such JVMs on 64-bit platforms
	 * must preserve all bits of static field offsets.
	 * @param field The static field to get the offset of
	 * @return the offset of the field
	 * @see sun.misc.Unsafe#staticFieldOffset(java.lang.reflect.Field)
	 */
	public static long staticFieldOffset(Field field) {
		return theUNSAFE.staticFieldOffset(field);
	}
	
	/**
	 * Report the location of a given static field, in conjunction with #staticFieldBase .
	 * Do not expect to perform any sort of arithmetic on this offset;
	 * it is just a cookie which is passed to the unsafe heap memory accessors.
	 * 
	 * Any given field will always have the same offset, and no two distinct
	 * fields of the same class will ever have the same offset.
	 * 
	 * As of 1.4.1, offsets for fields are represented as long values,
	 * although the Sun JVM does not use the most significant 32 bits.
	 * It is hard to imagine a JVM technology which needs more than
	 * a few bits to encode an offset within a non-array object,
	 * However, for consistency with other methods in this class,
	 * this method reports its result as a long value.
	 * @param field The non-static field to get the offset of
	 * @return the offset of the field
	 * @see sun.misc.Unsafe#objectFieldOffset(java.lang.reflect.Field)
	 */
	public static long objectFieldOffset(Field field) {
		return theUNSAFE.objectFieldOffset(field);
	}
	
	/**
	 * Returns the base address for accessing some static field in the given class.
	 * This method works only for JVMs which store all statics for a given class in one place. 
	 * @param clazz The class to get the static field base for
	 * @return the base address
	 * @deprecated As - of 1.4.1, use #staticFieldBase(Field) to obtain the base pertaining to a specific Field .  
	 * @see sun.misc.Unsafe#staticFieldBase(java.lang.Class)
	 */
	public static Object staticFieldBase(Class<?> clazz) {
		return theUNSAFE.staticFieldBase(clazz);
	}

	/**
	 * Report the location of a given static field, in conjunction with #staticFieldOffset .
	 * Fetch the base "Object", if any, with which static fields of the
	 * given class can be accessed via methods like #getInt(Object, long).
	 * This value may be null.  This value may refer to an object
	 * which is a "cookie", not guaranteed to be a real Object, and it should
	 * not be used in any way except as argument to the get and put routines in this class. 
	 * @param field The field to get the offset for
	 * @return the offset of the given static field in the fields declaring class
	 * @see sun.misc.Unsafe#staticFieldBase(java.lang.reflect.Field)
	 */
	public static Object staticFieldBase(Field field) {
		return theUNSAFE.staticFieldBase(field);
	}
	
    /**
     * Finds the next <b><code>power of 2</code></b> higher or equal to than the passed value.
     * @param value The initial value
     * @return the next pow2 without overrunning the type size
     */
    public static long findNextPositivePowerOfTwo(final long value) {
    	if(ADDRESS_SIZE==4) {
        	if(value > MAX_ALIGNED_MEM_32) return value;
        	return  1 << (32 - Integer.numberOfLeadingZeros((int)value - 1));    		
    	}
    	if(value > MAX_ALIGNED_MEM_64) return value;
    	return  1 << (64 - Long.numberOfLeadingZeros(value - 1));    		
	}    
	
	
	//===========================================================================================================
	//	Concurrency Control Ops
	//===========================================================================================================	

	/**
	 * Lock the object.It must get unlocked via #monitorExit .
	 * @param object The object to lock
	 * @see sun.misc.Unsafe#monitorEnter(java.lang.Object)
	 */
	public static void monitorEnter(Object object) {
		theUNSAFE.monitorEnter(object);
	}

	/**
	 * Unlock the object. It must have been locked via #monitorEnter.
	 * @param object THe object to unlock
	 * @see sun.misc.Unsafe#monitorExit(java.lang.Object)
	 */
	public static void monitorExit(Object object) {
		theUNSAFE.monitorExit(object);
	}
	
	/**
	 * Tries to lock the object. Returns true or false to indicate
	 * whether the lock succeeded. If it did, the object must be
	 * unlocked via #monitorExit. 
	 * @param object The object to attempt to lock
	 * @return true if the object was successfully locked, false otherwise
	 * @see sun.misc.Unsafe#tryMonitorEnter(java.lang.Object)
	 */
	public static boolean tryMonitorEnter(Object object) {
		return theUNSAFE.tryMonitorEnter(object);
	}
	
	
	/**
	 * Block current thread, returning when a balancing
	 * unpark occurs, or a balancing unpark has
	 * already occurred, or the thread is interrupted, or, if not
	 * absolute and time is not zero, the given time nanoseconds have
	 * elapsed, or if absolute, the given deadline in milliseconds
	 * since Epoch has passed, or spuriously (i.e., returning for no
	 * "reason"). Note: This operation is in the Unsafe class only
	 * because unpark is, so it would be strange to place it elsewhere.
	 * 
	 * @param isAbsolute if true, the time is ms., otherwise it is ns.
	 * @param time the time to park for
	 * @see sun.misc.Unsafe#park(boolean, long)
	 */
	public static void park(boolean isAbsolute, long time) {
		theUNSAFE.park(isAbsolute, time);
	}	
	

	/**
	 * Unblock the given thread blocked on park, or, if it is
	 * not blocked, cause the subsequent call to park not to
	 * block.  Note: this operation is "unsafe" solely because the
	 * caller must somehow ensure that the thread has not been
	 * destroyed. Nothing special is usually required to ensure this
	 * when called from Java (in which there will ordinarily be a live
	 * reference to the thread) but this is not nearly-automatically
	 * so when calling from native code.
	 * @param thread The thread to unpark
	 * @see sun.misc.Unsafe#unpark(java.lang.Object)
	 */
	public static void unpark(Object thread) {
		theUNSAFE.unpark(thread);
	}
	
	
	
	
	// ======================
	// ==================================================
	// ==================================================================================
	// =====================================================================================================
	// Adapter redirected calls to theUNSAFE
	// =====================================================================================================
	// ==================================================================================
	// ==================================================
	// ======================
	
	
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
	public static long getAddress(long address) {
		return adapter.getAddress(address);
	}
	
	/**
	 * Stores a native pointer into a given memory address.  If the address is
	 * zero, or does not point into a block obtained from #allocateMemory , the results are undefined.
	 * 
 	 * The number of bytes actually written at the target address maybe
	 * determined by consulting #addressSize . 
	 * @param targetAddress The address to write the value to
	 * @param address The address value to write
	 * @see sun.misc.Unsafe#putAddress(long, long)
	 */
	public static void putAddress(long targetAddress, long address) {
		adapter.putAddress(targetAddress, address);
	}
	
	

	//===========================================================================================================
	//	Allocate/Free Memory Ops
	//===========================================================================================================
	
	/**
	 * Frees the memory allocated at the passed address
	 * @param address The address of the memory to free
	 * @see sun.misc.Unsafe#freeMemory(long)
	 */
	public static void freeMemory(long address) {
		adapter.freeMemory(address);
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
	public static long allocateMemory(long size) {
		return adapter.allocateMemory(size);
	}
	
	/**
	 * Allocates a chunk of memory and returns its address
	 * @param size The number of bytes to allocate
	 * @param memoryManager The object to handle memory management of the allocated memory block
	 * @return The address of the allocated memory
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	public static long allocateMemory(long size, Object memoryManager) {
		return adapter.allocateMemory(size, memoryManager);
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
	public static long allocateAlignedMemory(long size) {
		return adapter.allocateAlignedMemory(size);
	}
	
	/**
	 * Allocates a new block of cache-line aligned native memory, of the given size in bytes rounded up to the nearest power of 2. 
	 * The contents of the memory are uninitialized; they will generally be garbage.
	 * The resulting native pointer will never be zero, and will be aligned for all value types.  
	 * Dispose of this memory by calling #freeMemory , or resize it with #reallocateMemory.
	 * @param size The size of the block of memory to allocate in bytes
	 * @param memoryManager The object to handle memory management of the allocated memory block
	 * @return The address of the allocated memory block
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	public static long allocateAlignedMemory(long size, Object memoryManager) {
		return adapter.allocateAlignedMemory(size, memoryManager);
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
	public static long reallocateMemory(long address, long size) {
		return adapter.reallocateMemory(address, size);
	}
	
	/**
	 * Resizes a new block of native memory, to the given size in bytes. 
	 * <b>NOTE:</b>If the caller implements {@link Deallocatable} and expects the allocations
	 * to be automatically cleared, the returned value should overwrite the index of 
	 * the {@link Deallocatable}'s array where the previous address was.   
	 * @param address The address of the existing allocation
	 * @param size The size of the new allocation in bytes
	 * @param memoryManager The object to handle memory management of the allocated memory block
	 * @return The address of the new allocation
	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
	 */
	public static long reallocateMemory(long address, long size, Object memoryManager) {
		return adapter.reallocateMemory(address, size);
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
	public static long reallocateAlignedMemory(long address, long size) {
		return adapter.reallocateAlignedMemory(address, size);
	}
	
	/**
	 * Resizes a new block of aligned (if enabled) native memory, to the given size in bytes.
	 * @param address The address of the existing allocation
	 * @param size The size of the new allocation in bytes
	 * @param memoryManager The object to handle memory management of the allocated memory block
	 * @return The address of the new allocation
	 * @see sun.misc.Unsafe#reallocateMemory(long, long)
	 */
	public static long reallocateAlignedMemory(long address, long size, Object memoryManager) {
		return adapter.reallocateAlignedMemory(address, size, memoryManager);
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
	public static void copyMemory(long srcOffset, long destOffset, long bytes) {
		adapter.copyMemory(srcOffset, destOffset, bytes);
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
	public static void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
		adapter.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
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
	public static void setMemory(long address, long bytes, byte value) {
		adapter.setMemory(address, bytes, value);
	}

	/**
	 * Sets all bytes in a given block of memory to a fixed value (usually zero).
	 * @param object The object, the base address of which the offset is applied 
	 * @param offset The destination object offset, or an absolute adress if the object is null
	 * @param bytes The number of bytes to set
	 * @param value The value to write to each byte in the specified range
	 * @see sun.misc.Unsafe#setMemory(java.lang.Object, long, long, byte)
	 */
	public static void setMemory(Object object, long offset, long bytes, byte value) {
		if(object!=null) {
			theUNSAFE.setMemory(object, offset, bytes, value); 
		} else {
			adapter.setMemory(offset, bytes, value);
		}
	}
	
	
	//===========================================================================================================
	//	Byte Read Ops
	//===========================================================================================================	
	
	/**
	 * Reads and returns a byte from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See {@link #staticFieldOffset}.
	 * @see sun.misc.Unsafe#getByte(java.lang.Object, int)
	 */
	public static byte getByte(Object object, int offset) {		
		return object!=null ? theUNSAFE.getByte(object, offset) : adapter.getByte(offset);
	}

	/**
	 * Reads and returns a byte from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getByte(java.lang.Object, long)
	 */
	public static byte getByte(Object object, long offset) {
		return object!=null ? theUNSAFE.getByte(object, offset) : adapter.getByte(offset);
	}
	
	/**
	 * Reads and returns a byte from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByte(java.lang.Object, long)
	 */
	public static byte getByte(long address) {
		return adapter.getByte(address);
	}
		
	/**
	 * Volatile version of {@link UnsafeAdapter#getByte(Object, long)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getByteVolatile(java.lang.Object, long)
	 */
	public static byte getByteVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getByteVolatile(object, offset) : adapter.getByteVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getByte(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByteVolatile(java.lang.Object, long)
	 */
	public static byte getByteVolatile(long address) {
		return adapter.getByteVolatile(address);
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
	 * @see sun.misc.Unsafe#putByte(long, byte)
	 */
	public static void putByte(long address, byte value) {
		adapter.putByte(address, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#putByte(java.lang.Object, int, byte)
	 */
	public static void putByte(Object object, int offset, byte value) {
		theUNSAFE.putByte(object, offset, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putByte(java.lang.Object, long, byte)
	 */
	public static void putByte(Object object, long offset, byte value) {
		theUNSAFE.putByte(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putByte(Object, long, byte)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putByteVolatile(java.lang.Object, long, byte)
	 */
	public static void putByteVolatile(Object object, long offset, byte value) {
		theUNSAFE.putByteVolatile(object, offset, value);
	}
	
	/**
	 * Volatile version of {@link #putByte(long, byte)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putByteVolatile(java.lang.Object, long, byte)
	 */
	public static void putByteVolatile(long address, byte value) {
		adapter.putByteVolatile(address, value);
	}
	
	
	
	//===========================================================================================================
	//	Boolean Read Ops
	//===========================================================================================================	
	
	/**
	 * Reads and returns a boolean from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See {@link #staticFieldOffset}.
	 * @see sun.misc.Unsafe#getBoolean(java.lang.Object, int)
	 */
	public static boolean getBoolean(Object object, int offset) {		
		return object!=null ? theUNSAFE.getBoolean(object, offset) : adapter.getBoolean(offset);
	}

	/**
	 * Reads and returns a boolean from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getBoolean(java.lang.Object, long)
	 */
	public static boolean getBoolean(Object object, long offset) {
		return object!=null ? theUNSAFE.getBoolean(object, offset) : adapter.getBoolean(offset);
	}
	
	/**
	 * Reads and returns a boolean from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getBoolean(java.lang.Object, long)
	 */
	public static boolean getBoolean(long address) {
		return adapter.getBoolean(address);
	}
		
	/**
	 * Volatile version of {@link UnsafeAdapter#getBoolean(Object, long)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getBooleanVolatile(java.lang.Object, long)
	 */
	public static boolean getBooleanVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getBooleanVolatile(object, offset) : adapter.getBooleanVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getBoolean(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getBooleanVolatile(java.lang.Object, long)
	 */
	public static boolean getBooleanVolatile(long address) {
		return adapter.getBooleanVolatile(address);
	}
	
	//===========================================================================================================
	//	Boolean Write Ops
	//===========================================================================================================	
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putBoolean(Object, long, boolean)
	 */
	public static void putBoolean(long address, boolean value) {
		adapter.putBoolean(address, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#putBoolean(java.lang.Object, int, boolean)
	 */
	public static void putBoolean(Object object, int offset, boolean value) {
		theUNSAFE.putBoolean(object, offset, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putBoolean(java.lang.Object, long, boolean)
	 */
	public static void putBoolean(Object object, long offset, boolean value) {
		theUNSAFE.putBoolean(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putBoolean(Object, long, boolean)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putBooleanVolatile(java.lang.Object, long, boolean)
	 */
	public static void putBooleanVolatile(Object object, long offset, boolean value) {
		theUNSAFE.putBooleanVolatile(object, offset, value);
	}
	
	/**
	 * Volatile version of {@link #putBoolean(long, boolean)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putBooleanVolatile(java.lang.Object, long, boolean)
	 */
	public static void putBooleanVolatile(long address, boolean value) {
		adapter.putBooleanVolatile(address, value);
	}
	
	
	
	//===========================================================================================================
	//	Short Read Ops
	//===========================================================================================================		
	
	/**
	 * Fetches a value from a given memory address.  If the address is zero, or
	 * does not point into a block obtained from {@link #allocateMemory} , the
	 * results are undefined. 
	 * @param address The address to read the value from
	 * @return the read value
	 */
	public static short getShort(long address) {
		return adapter.getShort(address);
	}
	
	/**
	 * Volatile version of {@link #getShort(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByteVolatile(Object, long)
	 */
	public static short getShortVolatile(long address) {
		return adapter.getShortVolatile(address);
	}
	
	
	/**
	 * Reads and returns a short from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#getShort(java.lang.Object, int)
	 */
	public static short getShort(Object object, int offset) {
		return object!=null ? theUNSAFE.getShort(object, offset) : adapter.getShort(offset);
	}

	/**
	 * Reads and returns a short from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getShort(java.lang.Object, long)
	 */
	public static short getShort(Object object, long offset) {
		return object!=null ? theUNSAFE.getShort(object, offset) : adapter.getShort(offset);
	}

	/**
	 * Volatile version of {@link UnsafeAdapter#getShort(Object, long)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getShortVolatile(java.lang.Object, long)
	 */
	public static short getShortVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getShortVolatile(object, offset) : adapter.getShortVolatile(offset);
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
	 * @see sun.misc.Unsafe#putShort(long, short)
	 */
	public static void putShort(long address, short value) {
		adapter.putShort(address, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#putShort(java.lang.Object, int, short)
	 */
	public static void putShort(Object object, int offset, short value) {
		theUNSAFE.putShort(object, offset, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putShort(java.lang.Object, long, short)
	 */
	public static void putShort(Object object, long offset, short value) {
		theUNSAFE.putShort(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putShort(Object, long, short)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putShortVolatile(java.lang.Object, long, short)
	 */
	public static void putShortVolatile(Object object, long offset, short value) {
		theUNSAFE.putShortVolatile(object, offset, value);
	}
	
	/**
	 * Volatile version of {@link #putShort(long, short)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putShortVolatile(java.lang.Object, long, short)
	 */
	public static void putShortVolatile(long address, short value) {
		adapter.putShortVolatile(address, value);
	}
	
	
	
	//===========================================================================================================
	//	Char Read Ops
	//===========================================================================================================	
	
	/**
	 * Reads and returns a char from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See {@link #staticFieldOffset}.
	 * @see sun.misc.Unsafe#getChar(java.lang.Object, int)
	 */
	public static char getChar(Object object, int offset) {		
		return object!=null ? theUNSAFE.getChar(object, offset) : adapter.getChar(offset);
	}

	/**
	 * Reads and returns a char from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getChar(java.lang.Object, long)
	 */
	public static char getChar(Object object, long offset) {
		return object!=null ? theUNSAFE.getChar(object, offset) : adapter.getChar(offset);
	}
	
	/**
	 * Reads and returns a char from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getChar(java.lang.Object, long)
	 */
	public static char getChar(long address) {
		return adapter.getChar(address);
	}
		
	/**
	 * Volatile version of {@link UnsafeAdapter#getChar(Object, long)}
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getCharVolatile(java.lang.Object, long)
	 */
	public static char getCharVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getCharVolatile(object, offset) : adapter.getCharVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getChar(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getCharVolatile(java.lang.Object, long)
	 */
	public static char getCharVolatile(long address) {
		return adapter.getCharVolatile(address);
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
	 * @see sun.misc.Unsafe#putChar(long, char)
	 */
	public static void putChar(long address, char value) {
		adapter.putChar(address, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#putChar(java.lang.Object, int, char)
	 */
	public static void putChar(Object object, int offset, char value) {
		theUNSAFE.putChar(object, offset, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putChar(java.lang.Object, long, char)
	 */
	public static void putChar(Object object, long offset, char value) {
		theUNSAFE.putChar(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putChar(Object, long, char)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putCharVolatile(java.lang.Object, long, char)
	 */
	public static void putCharVolatile(Object object, long offset, char value) {
		theUNSAFE.putCharVolatile(object, offset, value);
	}
	
	/**
	 * Volatile version of {@link #putChar(long, char)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putCharVolatile(java.lang.Object, long, char)
	 */
	public static void putCharVolatile(long address, char value) {
		adapter.putCharVolatile(address, value);
	}
	
	
	
	//===========================================================================================================
	//	Int Read Ops
	//===========================================================================================================	
	
	/**
	 * Reads and returns a int from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See {@link #staticFieldOffset}.
	 * @see sun.misc.Unsafe#getInt(java.lang.Object, int)
	 */
	public static int getInt(Object object, int offset) {		
		return object!=null ? theUNSAFE.getInt(object, offset) : adapter.getInt(offset);
	}

	/**
	 * Reads and returns a int from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getInt(java.lang.Object, long)
	 */
	public static int getInt(Object object, long offset) {
		return object!=null ? theUNSAFE.getInt(object, offset) : adapter.getInt(offset);
	}
	
	/**
	 * Reads and returns a int from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getInt(java.lang.Object, long)
	 */
	public static int getInt(long address) {
		return adapter.getInt(address);
	}
		
	/**
	 * Volatile version of {@link UnsafeAdapter#getInt(Object, long)}
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getIntVolatile(java.lang.Object, long)
	 */
	public static int getIntVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getIntVolatile(object, offset) : adapter.getIntVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getInt(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getIntVolatile(java.lang.Object, long)
	 */
	public static int getIntVolatile(long address) {
		return adapter.getIntVolatile(address);
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
	public static void putInt(long address, int value) {
		adapter.putInt(address, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#putInt(java.lang.Object, int, int)
	 */
	public static void putInt(Object object, int offset, int value) {
		theUNSAFE.putInt(object, offset, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putInt(java.lang.Object, long, int)
	 */
	public static void putInt(Object object, long offset, int value) {
		theUNSAFE.putInt(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putInt(Object, long, int)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putIntVolatile(java.lang.Object, long, int)
	 */
	public static void putIntVolatile(Object object, long offset, int value) {
		theUNSAFE.putIntVolatile(object, offset, value);
	}
	
	/**
	 * Volatile version of {@link #putInt(long, int)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putIntVolatile(java.lang.Object, long, int)
	 */
	public static void putIntVolatile(long address, int value) {
		adapter.putIntVolatile(address, value);
	}
	
	/**
	 * Ordered/Lazy version of #putIntVolatile(Object, long, int) 
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putOrderedInt(java.lang.Object, long, int)
	 */
	public static void putOrderedInt(Object object, long offset, int value) {
		theUNSAFE.putOrderedInt(object, offset, value);
	}

	/**
	 * Ordered/Lazy version of #putIntVolatile(long, int) 
	 * @param offset The address to write to 
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putOrderedInt(java.lang.Object, long, int)
	 */
	public static void putOrderedInt(long offset, int value) {
		adapter.putOrderedInt(offset, value);
	}
	
	
	//===========================================================================================================
	//	Float Read Ops
	//===========================================================================================================	
	
	/**
	 * Reads and returns a float from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See {@link #staticFieldOffset}.
	 * @see sun.misc.Unsafe#getFloat(java.lang.Object, int)
	 */
	public static float getFloat(Object object, int offset) {		
		return object!=null ? theUNSAFE.getFloat(object, offset) : adapter.getFloat(offset);
	}

	/**
	 * Reads and returns a float from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloat(java.lang.Object, long)
	 */
	public static float getFloat(Object object, long offset) {
		return object!=null ? theUNSAFE.getFloat(object, offset) : adapter.getFloat(offset);
	}
	
	/**
	 * Reads and returns a float from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloat(java.lang.Object, long)
	 */
	public static float getFloat(long address) {
		return adapter.getFloat(address);
	}
		
	/**
	 * Volatile version of {@link UnsafeAdapter#getFloat(Object, long)}
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloatVolatile(java.lang.Object, long)
	 */
	public static float getFloatVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getFloatVolatile(object, offset) : adapter.getFloatVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getFloat(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloatVolatile(java.lang.Object, long)
	 */
	public static float getFloatVolatile(long address) {
		return adapter.getFloatVolatile(address);
	}
	
	//===========================================================================================================
	//	Float Write Ops
	//===========================================================================================================	
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putFloat(long, float)
	 */
	public static void putFloat(long address, float value) {
		adapter.putFloat(address, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#putFloat(java.lang.Object, int, float)
	 */
	public static void putFloat(Object object, int offset, float value) {
		theUNSAFE.putFloat(object, offset, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putFloat(java.lang.Object, long, float)
	 */
	public static void putFloat(Object object, long offset, float value) {
		theUNSAFE.putFloat(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putFloat(Object, long, float)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putFloatVolatile(java.lang.Object, long, float)
	 */
	public static void putFloatVolatile(Object object, long offset, float value) {
		theUNSAFE.putFloatVolatile(object, offset, value);
	}
	
	/**
	 * Volatile version of {@link #putFloat(long, float)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putFloatVolatile(java.lang.Object, long, float)
	 */
	public static void putFloatVolatile(long address, float value) {
		adapter.putFloatVolatile(address, value);
	}
	
		
	
	//===========================================================================================================
	//	Long Read Ops
	//===========================================================================================================	
	
	/**
	 * Reads and returns a long from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See {@link #staticFieldOffset}.
	 * @see sun.misc.Unsafe#getLong(java.lang.Object, int)
	 */
	public static long getLong(Object object, int offset) {		
		return object!=null ? theUNSAFE.getLong(object, offset) : adapter.getLong(offset);
	}

	/**
	 * Reads and returns a long from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getLong(java.lang.Object, long)
	 */
	public static long getLong(Object object, long offset) {
		return object!=null ? theUNSAFE.getLong(object, offset) : adapter.getLong(offset);
	}
	
	/**
	 * Reads and returns a long from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getLong(java.lang.Object, long)
	 */
	public static long getLong(long address) {
		return adapter.getLong(address);
	}
		
	/**
	 * Volatile version of {@link UnsafeAdapter#getLong(Object, long)}
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getLongVolatile(java.lang.Object, long)
	 */
	public static long getLongVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getLongVolatile(object, offset) : adapter.getLongVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getLong(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getLongVolatile(java.lang.Object, long)
	 */
	public static long getLongVolatile(long address) {
		return adapter.getLongVolatile(address);
	}
	
	//===========================================================================================================
	//	Long Write Ops
	//===========================================================================================================	
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putLong(long, long)
	 */
	public static void putLong(long address, long value) {
		adapter.putLong(address, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#putLong(java.lang.Object, int, long)
	 */
	public static void putLong(Object object, int offset, long value) {
		theUNSAFE.putLong(object, offset, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putLong(java.lang.Object, long, long)
	 */
	public static void putLong(Object object, long offset, long value) {
		theUNSAFE.putLong(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putLong(Object, long, long)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putLongVolatile(java.lang.Object, long, long)
	 */
	public static void putLongVolatile(Object object, long offset, long value) {
		theUNSAFE.putLongVolatile(object, offset, value);
	}
	
	/**
	 * Volatile version of {@link #putLong(long, long)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putLongVolatile(java.lang.Object, long, long)
	 */
	public static void putLongVolatile(long address, long value) {
		adapter.putLongVolatile(address, value);
	}
	
	/**
	 * Ordered/Lazy version of #putLongVolatile(Object, long, long) 
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putOrderedLong(java.lang.Object, long, long)
	 */
	public static void putOrderedLong(Object object, long offset, long value) {
		theUNSAFE.putOrderedLong(object, offset, value);
	}

	/**
	 * Ordered/Lazy version of #putLongVolatile(long, long) 
	 * @param offset The address to write to 
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putOrderedLong(java.lang.Object, long, long)
	 */
	public static void putOrderedLong(long offset, long value) {
		adapter.putOrderedLong(offset, value);
	}
	
		

	//===========================================================================================================
	//	Double Read Ops
	//===========================================================================================================	
	
	/**
	 * Reads and returns a double from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See {@link #staticFieldOffset}.
	 * @see sun.misc.Unsafe#getDouble(java.lang.Object, int)
	 */
	public static double getDouble(Object object, int offset) {		
		return object!=null ? theUNSAFE.getDouble(object, offset) : adapter.getDouble(offset);
	}

	/**
	 * Reads and returns a double from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getDouble(java.lang.Object, long)
	 */
	public static double getDouble(Object object, long offset) {
		return object!=null ? theUNSAFE.getDouble(object, offset) : adapter.getDouble(offset);
	}
	
	/**
	 * Reads and returns a double from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getDouble(java.lang.Object, long)
	 */
	public static double getDouble(long address) {
		return adapter.getDouble(address);
	}
		
	/**
	 * Volatile version of {@link UnsafeAdapter#getDouble(Object, long)}
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getDoubleVolatile(java.lang.Object, long)
	 */
	public static double getDoubleVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getDoubleVolatile(object, offset) : adapter.getDoubleVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getDouble(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getDoubleVolatile(java.lang.Object, long)
	 */
	public static double getDoubleVolatile(long address) {
		return adapter.getDoubleVolatile(address);
	}
	
	//===========================================================================================================
	//	Double Write Ops
	//===========================================================================================================	
	
	/**
	 * Stores a value into a given memory address.  If the address is zero, or
	 * does not point into a block obtained from #allocateMemory , the
	 * results are undefined.
	 * @param address the address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putDouble(long, double)
	 */
	public static void putDouble(long address, double value) {
		adapter.putDouble(address, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See #staticFieldOffset .
	 * @see sun.misc.Unsafe#putDouble(java.lang.Object, int, double)
	 */
	public static void putDouble(Object object, int offset, double value) {
		theUNSAFE.putDouble(object, offset, value);
	}

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putDouble(java.lang.Object, long, double)
	 */
	public static void putDouble(Object object, long offset, double value) {
		theUNSAFE.putDouble(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putDouble(Object, long, double)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putDoubleVolatile(java.lang.Object, long, double)
	 */
	public static void putDoubleVolatile(Object object, long offset, double value) {
		theUNSAFE.putDoubleVolatile(object, offset, value);
	}
	
	/**
	 * Volatile version of {@link #putDouble(long, double)}
	 * @param address The address to write to
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putDoubleVolatile(java.lang.Object, long, double)
	 */
	public static void putDoubleVolatile(long address, double value) {
		adapter.putDoubleVolatile(address, value);
	}
	
		
	
	//===========================================================================================================
	//	Object Read Ops
	//===========================================================================================================	
	
	/**
	 * Reads and returns a object from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @deprecated As - of 1.4.1, cast the 32-bit offset argument to a long. See {@link #staticFieldOffset}.
	 * @see sun.misc.Unsafe#getObject(java.lang.Object, int)
	 */
	public static Object getObject(Object object, int offset) {		
		return object!=null ? theUNSAFE.getObject(object, offset) : adapter.getObject(offset);
	}

	/**
	 * Reads and returns a object from the specified offset off the base address of the passed object.
	 * If the object is null, the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getObject(java.lang.Object, long)
	 */
	public static Object getObject(Object object, long offset) {
		return object!=null ? theUNSAFE.getObject(object, offset) : adapter.getObject(offset);
	}
	
	/**
	 * Reads and returns a object from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getObject(java.lang.Object, long)
	 */
	public static Object getObject(long address) {
		return adapter.getObject(address);
	}
		
	/**
	 * Volatile version of {@link UnsafeAdapter#getObject(Object, long)}
	 * @param object The target object, the base address of which the offset is based on
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to read from if the target object is null
	 * @return the read value
	 * @see sun.misc.Unsafe#getObjectVolatile(java.lang.Object, long)
	 */
	public static Object getObjectVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getObjectVolatile(object, offset) : adapter.getObjectVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getObject(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getObjectVolatile(java.lang.Object, long)
	 */
	public static Object getObjectVolatile(long address) {
		return adapter.getObjectVolatile(address);
	}
	
	//===========================================================================================================
	//	Object Write Ops
	//===========================================================================================================	

	/**
	 * Stores a value into a given offset off the base address of the passed object.
	 * If the object is null, then the offset is assumed to be an absolute address.
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putObject(java.lang.Object, long, Object)
	 */
	public static void putObject(Object object, long offset, Object value) {
		theUNSAFE.putObject(object, offset, value);
	}

	/**
	 * Volatile version of {@link #putObject(Object, long, Object)}
	 * @param object The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putObjectVolatile(java.lang.Object, long, Object)
	 */
	public static void putObjectVolatile(Object object, long offset, Object value) {
		theUNSAFE.putObjectVolatile(object, offset, value);
	}
	
	
	/**
	 * Version of #putObjectVolatile(Object, long, Object) 
	 * that does not guarantee immediate visibility of the store to
	 * other threads. This method is generally only useful if the
	 * underlying field is a Java volatile (or if an array cell, one
	 * that is otherwise only accessed using volatile accesses).
	 * @param targetObject The target object, the base address of which the offset is based off
	 * @param offset The offset off the base address of the target object, 
	 * or the absolute address to write to if the target object is null
	 * @param value The value to write
	 * @see sun.misc.Unsafe#putOrderedObject(java.lang.Object, long, java.lang.Object)
	 */
	public static void putOrderedObject(Object targetObject, long offset, Object value) {
		theUNSAFE.putOrderedObject(targetObject, offset, value);
	}
		
	
	
	// =======================================================================================================
	//			SpinLock Ops
	// =======================================================================================================	
	
	
	
	/**
	 * Allocates an initialized and initially unlocked memory based spin lock
	 * @return the spin lock
	 */
	public static final SpinLock allocateSpinLock() {
		long address = allocateAlignedMemory(UnsafeAdapter.LONG_SIZE);
		putLong(address, SpinLock.NO_LOCK);
		return new MemSpinLock(address);
	}
	
	/**
	 * Acquires the lock at the passed address exclusively
	 * @param address The address of the lock
	 * @param barge If true, does not yield between locking attempts. Should only be used by 
	 * a small number of high priority threads, otherwise has no effect.  
	 * @return true if the lock was acquired, false if it was already held by the calling thread
	 */
	public static final boolean xlock(final long address, final boolean barge) {
		final long tId = Thread.currentThread().getId();
		if(getLong(address)==tId) return false;
		while(!compareAndSwapLong(null, address, SpinLock.NO_LOCK, tId)) {if(!barge) Thread.yield();}
		return true;
	}
	
	/**
	 * Indicates if the spin lock at the specified address is currently held by any thread
	 * @param address The address of the spin lock
	 * @return true if the spin lock at the specified address is currently held by any thread, false otherwise
	 */
	public static final boolean xislocked(final long address) {
		return getLong(address)!=SpinLock.NO_LOCK;
	}
	
	/**
	 * Indicates if the spin lock at the specified address is held by the current thread
	 * @param address The address of the spin lock
	 * @return true if the spin lock at the specified address is held by the current thread, false otherwise
	 */
	public static final boolean xislockedbyt(final long address) {
		final long tId = Thread.currentThread().getId();
		return getLong(address)==tId;
	}
	
	/**
	 * Acquires the lock at the passed address exclusively with no barging
	 * @param address The address of the lock
	 * a small number of high priority threads, otherwise has no effect.  
	 * @return true if the lock was acquired, false if it was already held by the calling thread
	 */
	public static final boolean xlock(final long address) {
		return xlock(address, false);
	}
	
	/**
	 * Unlocks the exclusive lock at the passed address if it is held by the calling thread
	 * @param address The address of the lock
	 * @return true if the calling thread held the lock and unlocked it, false if the lock was not held by the calling thread
	 */
	public static final boolean xunlock(final long address) {
		final long tId = Thread.currentThread().getId();
		return compareAndSwapLong(null, address, tId, SpinLock.NO_LOCK);
	}
	
	
	// =======================================================================================================
	
	
	/**
	 * Returns a status and config summary for the Unsafe Adapter.
	 * @return a status and config summary
	 */
	public static String printStatus() {
		StringBuilder b = new StringBuilder("[UnsafeAdapter Status]");
		b.append("\n\tAdapter Type: ").append(adapter==null ? "None" : adapter.getClass().getName());		
		b.append("\n\tAllocation Model: ").append(isSafeAdapter() ? "SAFE" : "UNSAFE");
		b.append("\n\tAllocation Tracking: ").append(adapter==null ? "Unknown" : adapter.isTrackingEnabled() ? "Enabled" : "Disabled");
		b.append("\n\tCache-Line Alignment: ").append(adapter==null ? "Unknown" : adapter.isAlignmentEnabled() ? "Enabled" : "Disabled");
		b.append("\n\tCPU Model: ").append(ADDRESS_SIZE==4 ? "32" : "64");
		b.append("\n\tJVM 5 Copy: ").append(adapter==null ? "Unknown" : adapter.isFiveCopy() ? "Yes" : "No");
		b.append("\n\tJVM 4 Set: ").append(adapter==null ? "Unknown" : adapter.isFourSet() ? "Yes" : "No");
		b.append("\n\tCleaner Threads: ").append(cleanerThreads.toString());
		return b.append("\n").toString();
	}
		
	
	/**
	 * Prints information about the currently configured adapter.
	 * @param args None
	 */
	public static void main(String[] args) {
		System.out.println(printStatus());
	}

	/**
	 * Low maintenance out logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	public static void log(Object fmt, Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
	/**
	 * Low maintenance err logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	public static void loge(Object fmt, Object...args) {
		System.err.println(String.format(fmt.toString(), args));
		if(args!=null && args.length>0 && args[args.length-1] instanceof Throwable) {
			System.err.println("Stack trace follows....");
			((Throwable)args[args.length-1]).printStackTrace(System.err);			
		}
	}
	
	
}
