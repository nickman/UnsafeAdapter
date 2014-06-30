package com.heliosapm.unsafe;

/**
 * <p>Title: MemSpinLock</p>
 * <p>Description: Unsafe memory based spin lock for use withing JVM only</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapter.MemSpinLock</code></p>
 */

public class MemSpinLock implements SpinLock, DeAllocateMe {
	/** The lock address */
	protected final long address;

	/**
	 * Creates a new MemSpinLock
	 * @param address The address of the lock
	 */
	MemSpinLock(long address) {
		this.address = address;
		UnsafeAdapter.registerForDeAlloc(this);
	}

	/**
	 * Returns the lock address
	 * @return the lock address
	 */
	public long address() {
		return address;
	}
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.DeAllocateMe#getAddresses()
	 */
	@Override
	public long[][] getAddresses() {
		return new long[][] {{address}};
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xlock()
	 */
	@Override
	public void xlock() {
		UnsafeAdapter.xlock(address);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xlock(boolean)
	 */
	@Override
	public void xlock(boolean barge) {
		UnsafeAdapter.xlock(address, barge);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#xunlock()
	 */
	@Override
	public void xunlock() {
		UnsafeAdapter.xunlock(address);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#isLocked()
	 */
	@Override
	public boolean isLocked() {
		return UnsafeAdapter.xislocked(address);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.SpinLock#isLockedByMe()
	 */
	@Override
	public boolean isLockedByMe() {
		return UnsafeAdapter.xislockedbyt(address);
	}

}