/**
 * 
 */
package com.heliosapm.unsafe.unmanaged;

import gnu.trove.map.hash.TLongLongHashMap;

/**
 * <p>Title: SpinLockedTLongLongHashMap</p>
 * <p>Description: {@link MemSpinLock} guarded version of {@link TLongLongHashMap}</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.unsafe.unmanaged.SpinLockedTLongLongHashMap</code></b>
 */

public class SpinLockedTLongLongHashMap extends TLongLongHashMap {
	/** The spin lock to guard the map from concurrent access */
	protected final MemSpinLock lock = new MemSpinLock();
	
	/** Represents a null key or value */
	public static final long NULL = -1L;
	
	/**
	 * Creates a new SpinLockedTLongLongHashMap
	 */
	public SpinLockedTLongLongHashMap() {
		super(1024, 0.1f, NULL, NULL);
	}
	
	/**
	 * Creates a new SpinLockedTLongLongHashMap
	 * @param initialCapacity The initial capacity of the map
	 * @param loadFactor The load factor of the map
	 */
	public SpinLockedTLongLongHashMap(int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor, NULL, NULL);
	}
	
	/**
	 * <p>SpinLock guarded {@link TLongLongHashMap#get(long)}.</p>
	 * <p>Returns <b><code>-1L</code></b> if the lookup does not find the specified key.</p>
	 * {@inheritDoc}
	 * @see gnu.trove.map.hash.TLongLongHashMap#get(long)
	 */
	@Override
	public long get(long key) {		
		try {
			lock.xlock();
			return super.get(key);
		} finally {
			lock.xunlock();
		}
	}
	
	/**
	 * <p>SpinLock guarded {@link TLongLongHashMap#put(long, long)}.</p>
	 * {@inheritDoc}
	 * @see gnu.trove.map.hash.TLongLongHashMap#put(long, long)
	 */
	@Override
	public long put(long key, long value) {
		try {
			lock.xlock();
			return super.put(key, value);
		} finally {
			lock.xunlock();
		}		
	}
	
	/**
	 * <p>SpinLock guarded {@link TLongLongHashMap#remove(long)}.</p>
	 * <p>Returns <b><code>-1L</code></b> if the lookup does not find the specified key.</p>
	 * {@inheritDoc}
	 * @see gnu.trove.map.hash.TLongLongHashMap#remove(long)
	 */
	@Override
	public long remove(long key) {
		try {
			lock.xlock();
			return super.remove(key);
		} finally {
			lock.xunlock();
		}		
	}
	
	/**
	 * <p>SpinLock guarded {@link TLongLongHashMap#size()}.</p>
	 * {@inheritDoc}
	 * @see gnu.trove.impl.hash.THash#size()
	 */
	@Override
	public int size() {
		try {
			lock.xlock();
			return super.size();
		} finally {
			lock.xunlock();
		}		
	}
	
	/**
	 * <p>SpinLock guarded {@link TLongLongHashMap#clear()}.</p>
	 * {@inheritDoc}
	 * @see gnu.trove.map.hash.TLongLongHashMap#clear()
	 */
	@Override
	public void clear() {
		try {
			lock.xlock();
			super.clear();
		} finally {
			lock.xunlock();
		}		
	}
	

}
