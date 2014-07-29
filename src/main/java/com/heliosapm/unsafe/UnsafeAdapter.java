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

import sun.misc.Unsafe;

/**
 * <p>Title: UnsafeAdapter</p>
 * <p>Description: A wrapper for {@link sun.misc.Unsafe} to provide enhanced functionality</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapter</code></p>
 */

public class UnsafeAdapter {
	
	/** The system prop specifying the use of the safe memory management implementation */
	public static final String SAFE_MANAGER_PROP = "safe.allocations.manager";
    /** The unsafe instance */    
	static final Unsafe theUNSAFE;
	
    /** Indicates if the 5 param copy memory is supported */
    public static final boolean FIVE_COPY;
    /** Indicates if the 4 param set memory is supported */
    public static final boolean FOUR_SET;	
    /** The address size */
    public static final int ADDRESS_SIZE;
    /** Byte array offset */
    public static final int BYTES_OFFSET;
    /** Object array offset */
    public static final long OBJECTS_OFFSET;
    
	
	

	/** The configured adapter (default or safe) */
	private static final DefaultUnsafeAdapterImpl adapter;

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
		// Get the sizes of commonly used references
		// =========================================================        
        ADDRESS_SIZE = theUNSAFE.addressSize();
        BYTES_OFFSET = theUNSAFE.arrayBaseOffset(byte[].class);
        OBJECTS_OFFSET = theUNSAFE.arrayBaseOffset(Object[].class);        
		
		adapter = System.getProperties().containsKey(SAFE_MANAGER_PROP) ? SafeAdapterImpl.getInstance() : DefaultUnsafeAdapterImpl.getInstance();
	}
	
	// =====================================================================================================
	// Direct calls to theUNSAFE
	// =====================================================================================================
	
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
	 * Allocate an instance but do not run an constructor. 
	 * Initializes the class if it has not yet been.
	 * @param clazz The class to allocate an instance of
	 * @return The created object instance
	 * @throws InstantiationException thrown on a failure to instantiate
	 * @see sun.misc.Unsafe#allocateInstance(java.lang.Class)
	 */
	public Object allocateInstance(Class<?> clazz) throws InstantiationException {
		return theUNSAFE.allocateInstance(clazz);
	}
	

	/**
	 * Atomically update Java variable to x if it is currently holding expected.
	 * @param object The target object. If null, the offset is assumed to be an absolute address
	 * @param offset  The offset of the base address of the object, or an absolute address if the object is null
	 * @param expected  The expected current value
	 * @param x the value to set if the expected evaluation was correct
	 * @return true if the expected evaluation was correct and the value was set, false otherwise
	 * @see sun.misc.Unsafe#compareAndSwapInt(java.lang.Object, long, int, int)
	 */
	public final boolean compareAndSwapInt(Object object, long offset, int expected, int x) {
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
	public final boolean compareAndSwapLong(Object object, long offset, long expected, long x) {
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
	public final boolean compareAndSwapObject(Object object, long offset, long expected, long x) {
		return theUNSAFE.compareAndSwapObject(object, offset, expected, x);
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
	public Class<?> defineAnonymousClass(Class<?> hostClass, byte[] byteCode, Object[] cpPatches) {
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
	public Class<?> defineClass(String name, byte[] byteCode, int offset, int length, ClassLoader loader, ProtectionDomain protectionDomain) {
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
	public Class<?> defineClass(String name, byte[] byteCode, int offset, int length) {
		return theUNSAFE.defineClass(name, byteCode, offset, length);
	}

	/**
	 * Ensure the given class has been initialized. 
	 * This is often needed in conjunction with obtaining the static field base of a class.
	 * @param clazz The class to ensure the initialization of
	 * @see sun.misc.Unsafe#ensureClassInitialized(java.lang.Class)
	 */
	public void ensureClassInitialized(Class<?> clazz) {
		theUNSAFE.ensureClassInitialized(clazz);
	}


	/**
	 * Returns the offset of a field, truncated to 32 bits. This method is implemented as follows:
	 * <pre>
	 * 		public int fieldOffset(Field f) {
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
	public int fieldOffset(Field field) {
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
	 * addresses can use long offsets and null base pointers to express
	 * the field locations in a form usable by #getInt(Object,long) . (TODO: Fill in this ref.)
	 * Therefore, code which will be ported to such JVMs on 64-bit platforms
	 * must preserve all bits of static field offsets.
	 * @param field The static field to get the offset of
	 * @return the offset of the field
	 * @see sun.misc.Unsafe#staticFieldOffset(java.lang.reflect.Field)
	 */
	public long staticFieldOffset(Field field) {
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
	public long objectFieldOffset(Field field) {
		return theUNSAFE.objectFieldOffset(field);
	}
	
	
	
	// =====================================================================================================
	// Adapter redirected calls to theUNSAFE
	// =====================================================================================================
	
	/**
	 * Frees the memory allocated at the passed address
	 * @param address The address of the memory to free
	 * @see sun.misc.Unsafe#freeMemory(long)
	 */
	public static void freeMemory(long address) {
		adapter.freeMemory(address);
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
	public static long allocateMemory(long size) {
		return adapter.allocateMemory(size);
	}
	
	/**
	 * Allocates a chunk of memory and returns its address
	 * @param size The number of bytes to allocate
	 * @param dealloc The optional deallocatable to register for deallocation when it becomes phantom reachable
	 * @return The address of the allocated memory
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	public static long allocateMemory(long size, DeAllocateMe dealloc) {
		return adapter.allocateMemory(size, dealloc);
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
	 * @param dealloc The optional deallocatable to register for deallocation when it becomes phantom reachable
	 * @return The address of the allocated memory block
	 * @see sun.misc.Unsafe#allocateMemory(long)
	 */
	public static long allocateAlignedMemory(long size, DeAllocateMe dealloc) {
		return adapter.allocateAlignedMemory(size, dealloc);
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
	 * by the address and length parameters.  If the effective addresses and
	 * length are all even modulo 8, the transfer takes place in 'long' units.
	 * If the effective addresses and length are (resp.) even modulo 4 or 2,
	 * the transfer takes place in units of 'int' or 'short'.
	 * @param srcBase The source object. Can be null, in which case srcOffset will be assumed to be an absolute address.
	 * @param srcOffset The source object offset, or an absolute adress if srcBase is null
	 * @param destBase The destination object. Can be null, in which case destOffset will be assumed to be an absolute address.
	 * @param destOffset The destination object offset, or an absolute adress if destBase is null
	 * @param bytes The bytes to copy
	 * @see sun.misc.Unsafe#copyMemory(java.lang.Object, long, java.lang.Object, long, long)
	 */
	public void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
		adapter.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
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
	public byte getByte(Object object, int offset) {		
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
	public byte getByte(Object object, long offset) {
		return object!=null ? theUNSAFE.getByte(object, offset) : adapter.getByte(offset);
	}
	
	/**
	 * Reads and returns a byte from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByte(java.lang.Object, long)
	 */
	public byte getByte(long address) {
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
	public byte getByteVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getByteVolatile(object, offset) : adapter.getByteVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getByte(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByteVolatile(java.lang.Object, long)
	 */
	public byte getByteVolatile(long address) {
		return adapter.getByteVolatile(address);
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
	public boolean getBoolean(Object object, int offset) {		
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
	public boolean getBoolean(Object object, long offset) {
		return object!=null ? theUNSAFE.getBoolean(object, offset) : adapter.getBoolean(offset);
	}
	
	/**
	 * Reads and returns a boolean from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getBoolean(java.lang.Object, long)
	 */
	public boolean getBoolean(long address) {
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
	public boolean getBooleanVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getBooleanVolatile(object, offset) : adapter.getBooleanVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getBoolean(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getBooleanVolatile(java.lang.Object, long)
	 */
	public boolean getBooleanVolatile(long address) {
		return adapter.getBooleanVolatile(address);
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
	public short getShort(long address) {
		return adapter.getShort(address);
	}
	
	/**
	 * Volatile version of {@link #getShort(long)}.
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getByteVolatile(Object, long)
	 */
	public short getShortVolatile(long address) {
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
	public short getShort(Object object, int offset) {
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
	public short getShort(Object object, long offset) {
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
	public short getShortVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getShortVolatile(object, offset) : adapter.getShortVolatile(offset);
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
	public char getChar(Object object, int offset) {		
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
	public char getChar(Object object, long offset) {
		return object!=null ? theUNSAFE.getChar(object, offset) : adapter.getChar(offset);
	}
	
	/**
	 * Reads and returns a char from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getChar(java.lang.Object, long)
	 */
	public char getChar(long address) {
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
	public char getCharVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getCharVolatile(object, offset) : adapter.getCharVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getChar(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getCharVolatile(java.lang.Object, long)
	 */
	public char getCharVolatile(long address) {
		return adapter.getCharVolatile(address);
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
	public int getInt(Object object, int offset) {		
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
	public int getInt(Object object, long offset) {
		return object!=null ? theUNSAFE.getInt(object, offset) : adapter.getInt(offset);
	}
	
	/**
	 * Reads and returns a int from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getInt(java.lang.Object, long)
	 */
	public int getInt(long address) {
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
	public int getIntVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getIntVolatile(object, offset) : adapter.getIntVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getInt(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getIntVolatile(java.lang.Object, long)
	 */
	public int getIntVolatile(long address) {
		return adapter.getIntVolatile(address);
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
	public float getFloat(Object object, int offset) {		
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
	public float getFloat(Object object, long offset) {
		return object!=null ? theUNSAFE.getFloat(object, offset) : adapter.getFloat(offset);
	}
	
	/**
	 * Reads and returns a float from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloat(java.lang.Object, long)
	 */
	public float getFloat(long address) {
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
	public float getFloatVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getFloatVolatile(object, offset) : adapter.getFloatVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getFloat(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getFloatVolatile(java.lang.Object, long)
	 */
	public float getFloatVolatile(long address) {
		return adapter.getFloatVolatile(address);
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
	public long getLong(Object object, int offset) {		
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
	public long getLong(Object object, long offset) {
		return object!=null ? theUNSAFE.getLong(object, offset) : adapter.getLong(offset);
	}
	
	/**
	 * Reads and returns a long from the specified address 
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getLong(java.lang.Object, long)
	 */
	public long getLong(long address) {
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
	public long getLongVolatile(Object object, long offset) {
		return object!=null ? theUNSAFE.getLongVolatile(object, offset) : adapter.getLongVolatile(offset);
	}
	
	/**
	 * Volatile version of {@link UnsafeAdapter#getLong(Object, long)}
	 * @param address The address to read the value from
	 * @return the read value
	 * @see sun.misc.Unsafe#getLongVolatile(java.lang.Object, long)
	 */
	public long getLongVolatile(long address) {
		return adapter.getLongVolatile(address);
	}

	
	// =======================================================================================================
	
	
	/**
	 * Prints information about the currently configured adapter.
	 * @param args None
	 */
	public static void main(String[] args) {

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
