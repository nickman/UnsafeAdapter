package com.heliosapm.unsafe;

/**
 * <p>Title: SpinLock</p>
 * <p>Description: Defines a sping lock impl.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapterSpinLock</code></p>
 */
public interface SpinLock {
	/**
	 * Acquires the lock with the calling thread
	 */
	public void xlock();
	
	/**
	 * Acquires the lock with the calling thread
	 * @param barge  If true, does not yield between locking attempts. Should only be used by 
 	 * a small number of high priority threads, otherwise has no effect.  
	 */
	public void xlock(boolean barge);
	
	
	/**
	 * Releases the lock if it is held by the calling thread
	 */
	public void xunlock();
	
	/**
	 * Indicates if the spin lock is currently held by any thread
	 * @return true if the spin lock is currently held by any thread, false otherwise
	 */
	public boolean isLocked();
	
	/**
	 * Indicates if the spin lock is currently held by the calling thread
	 * @return true if the spin lock is currently held by the calling thread, false otherwise
	 */
	public boolean isLockedByMe();
	
	
}