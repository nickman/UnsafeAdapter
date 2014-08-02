/**
 * 
 */
package com.heliosapm.unsafe.unmanaged;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

import com.heliosapm.unsafe.SpinLock;

/**
 * <p>Title: MemSpinLock</p>
 * <p>Description: An unmanaged spin lock</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.unsafe.unmanaged.MemSpinLock</code></b>
 */
@SuppressWarnings("restriction")
public class MemSpinLock implements SpinLock {
	/** A reference to the Unsafe */		
	private static final Unsafe unsafe;	

	/** The spin lock's address */
	private final long address = unsafe.allocateMemory(8);
	
	static {
		try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to access sun.misc.Unsafe.theUnsafe", ex);
		}
	}
	
	/**
	 * Creates a new MemSpinLock 
	 */
	public MemSpinLock() {
		unsafe.putAddress(address, NO_LOCK);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xlock()
	 */
	@Override
	public void xlock() {
		xlock(false);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xlock(boolean)
	 */
	@Override
	public void xlock(boolean barge) {
		final long tId = Thread.currentThread().getId();
		if(unsafe.getLong(address)==tId) return;
		while(!unsafe.compareAndSwapLong(null, address, SpinLock.NO_LOCK, tId)) {if(!barge) Thread.yield();}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xunlock()
	 */
	@Override
	public void xunlock() {
		final long tId = Thread.currentThread().getId();
		unsafe.compareAndSwapLong(null, address, tId, SpinLock.NO_LOCK);		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#isLocked()
	 */
	@Override
	public boolean isLocked() {
		return unsafe.getLong(address)!=SpinLock.NO_LOCK;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#isLockedByMe()
	 */
	@Override
	public boolean isLockedByMe() {
		final long tId = Thread.currentThread().getId();
		return unsafe.getLong(address)==tId;		
	}

}
