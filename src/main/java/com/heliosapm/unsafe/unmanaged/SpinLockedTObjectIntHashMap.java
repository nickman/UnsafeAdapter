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
package com.heliosapm.unsafe.unmanaged;

import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

/**
 * <p>Title: SpinLockedTObjectIntHashMap</p>
 * <p>Description: {@link MemSpinLock} guarded version of {@link TObjectIntHashMap}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.unmanaged.SpinLockedTObjectIntHashMap</code></p>
 * @param <K> The assumed type of the map key
 */

public class SpinLockedTObjectIntHashMap<K> extends TObjectIntHashMap<K> {
	/** The spin lock to guard the map from concurrent access */
	protected final MemSpinLock lock = new MemSpinLock();
	
	/**
	 * Creates a new SpinLockedTObjectIntHashMap
	 * @param capacity used to find a prime capacity for the table.
	 * @param loadFactor used to calculate the threshold over which rehashing takes place.
	 * @param nullValue the value used to represent null.
	 */
	public SpinLockedTObjectIntHashMap(int capacity, float loadFactor, int nullValue) {
		super(capacity, loadFactor, nullValue);
	}
	
	
	
	
	/**
	 * <p>SpinLocked version of {@link TObjectIntHashMap#get(java.lang.Object)}</p>
	 * {@inheritDoc}
	 * @see gnu.trove.map.hash.TObjectIntHashMap#get(java.lang.Object)
	 */
	@Override
	public int get(Object key) {		
		try {
			lock.xlock();
			return super.get(key);
		} finally {
			lock.xunlock();
		}
	}
	
	/**
	 * <p>SpinLocked version of {@link TObjectIntHashMap#put(java.lang.Object, int)}</p>
	 * {@inheritDoc}
	 * @see gnu.trove.map.hash.TObjectIntHashMap#put(java.lang.Object, int)
	 */
	@Override
	public int put(K key, int value) {		
		try {
			lock.xlock();
			return super.put(key, value);
		} finally {
			lock.xunlock();
		}
	}

	

}
