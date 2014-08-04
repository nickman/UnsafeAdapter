package com.heliosapm.unsafe;

/**
 * <p>Title: MemSpinLock</p>
 * <p>Description: Unsafe memory based spin lock for use withing JVM only</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapter.MemSpinLock</code></p>
 */

public class MemSpinLock implements SpinLock, Deallocatable {
	/** The lock address */
	protected final long[][] address;

	/**
	 * Creates a new MemSpinLock
	 * @param address The address of the lock
	 */
	MemSpinLock(long address) {
		this.address = new long[1][1];		
		this.address[0][0] = NO_LOCK;
	}

	/**
	 * Returns the lock address
	 * @return the lock address
	 */
	public long address() {
		return address[0][0];
	}
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#getAddresses()
	 */
	@Override
	public long[][] getAddresses() {
		return address;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xlock()
	 */
	@Override
	public void xlock() {
		UnsafeAdapter.xlock(address[0][0]);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xlock(boolean)
	 */
	@Override
	public void xlock(boolean barge) {
		UnsafeAdapter.xlock(address[0][0], barge);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xunlock()
	 */
	@Override
	public void xunlock() {
		UnsafeAdapter.xunlock(address[0][0]);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#isLocked()
	 */
	@Override
	public boolean isLocked() {
		return UnsafeAdapter.xislocked(address[0][0]);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#isLockedByMe()
	 */
	@Override
	public boolean isLockedByMe() {
		return UnsafeAdapter.xislockedbyt(address[0][0]);
	}
	
	/** Indicates if this deallocatable has been refed */
	private boolean refed = false;
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#isReferenced()
	 */
	@Override
	public boolean isReferenced() {
		try {
			xlock();
			return refed;
		} finally {
			xunlock();
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#setReferenced()
	 */
	@Override
	public void setReferenced() {
		try {
			xlock();
			refed = true;
		} finally {
			xunlock();
		}			
	}	

}