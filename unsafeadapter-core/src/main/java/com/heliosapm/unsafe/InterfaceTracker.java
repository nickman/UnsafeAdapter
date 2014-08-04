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

import com.heliosapm.unsafe.unmanaged.SpinLockedTObjectIntHashMap;

/**
 * <p>Title: InterfaceTracker</p>
 * <p>Description: Tracks and caches which classes implement "special" UnsafeAdapter interfaces.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.InterfaceTracker</code></p>
 */

public class InterfaceTracker {
	/** Cache of interface bit masks keyed by the class implementing */
	private final SpinLockedTObjectIntHashMap<Class<?>> classCache = new SpinLockedTObjectIntHashMap<Class<?>>(1024, 0.1f, NULL);

	/** The bit mask for {@link Deallocatable}s */
	public static final int DEALLOCATABLE = 1;
	/** The bit mask for {@link AddressAssignable}s */
	public static final int ASSIGNABLE = 2;
	/** The bit mask for {@link ReferenceProvider}s */
	public static final int REFPROVIDER = 4;
	/** The bit mask for {@link AllocationPointer}s */
	public static final int ALLOCPTR = 4;

	/** The int token for a null value */
	public static final int NULL = -1;

	/**
	 * Creates a new InterfaceTracker
	 */
	public InterfaceTracker() {
		getMask(AllocationPointer.class);
	}
	
	/**
	 * Determines if the passed mask is enabled for the {@link Deallocatable} interface.
	 * @param mask The mask to test
	 * @return true if enabled, false otherwise
	 */
	public static final boolean isDeallocatable(int mask) {
		return mask == (mask | DEALLOCATABLE);
	}
	
	/**
	 * Determines if the passed mask is enabled for the {@link AddressAssignable} interface.
	 * @param mask The mask to test
	 * @return true if enabled, false otherwise
	 */
	public static final boolean isAssignable(int mask) {
		return mask == (mask | ASSIGNABLE);
	}

	/**
	 * Determines if the passed mask is enabled for the {@link ReferenceProvider} interface.
	 * @param mask The mask to test
	 * @return true if enabled, false otherwise
	 */
	public static final boolean isReferenceProvider(int mask) {
		return mask == (mask | REFPROVIDER);
	}

	/**
	 * Determines if the passed mask is enabled for the {@link AllocationPointer} class.
	 * @param mask The mask to test
	 * @return true if enabled, false otherwise
	 */
	public static final boolean isAllocationPointer(int mask) {
		return mask == (mask | ALLOCPTR);
	}
	
	/**
	 * Returns the implementation mask for the passed class
	 * @param clazz The class to get the implementation mask for 
	 * @return the implementation mask for the class
	 */
	public final int getMask(Class<?> clazz) {
		if(clazz==null) return 0; //throw new IllegalArgumentException("The passed class was null");
		int mask = classCache.get(clazz);
		if(mask==NULL) {
			synchronized(classCache) {
				mask = classCache.get(clazz);
				if(mask==NULL) {
					mask = computeMask(clazz);
					classCache.put(clazz, mask);
				}
			}
		}
		return mask;
	}
	
	/**
	 * Returns the implementation mask for the passed object
	 * @param object The object to get the implementation mask for 
	 * @return the implementation mask for the object
	 */
	public final int getMask(Object object) {
		if(object==null) return 0; //throw new IllegalArgumentException("The passed object was null");
		return getMask(object.getClass());
	}
	
	
	
	/**
	 * Computes the implementation mask for the passed class.
	 * @param clazz The class to test
	 * @return the computed mask
	 */
	public static final int computeMask(Class<?> clazz) {
		int mask = 0;
		if(AllocationPointer.class.isAssignableFrom(clazz)) {
			return mask | DEALLOCATABLE | ASSIGNABLE | REFPROVIDER | ASSIGNABLE;
		}
		if(Deallocatable.class.isAssignableFrom(clazz)) {
			mask = mask | DEALLOCATABLE;
		}
		if(AddressAssignable.class.isAssignableFrom(clazz)) {
			mask = mask | ASSIGNABLE;
		}
		if(ReferenceProvider.class.isAssignableFrom(clazz)) {
			mask = mask | REFPROVIDER;
		}
		return mask;
	}

}
