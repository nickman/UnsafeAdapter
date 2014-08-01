/**
 * 
 */
package com.heliosapm.unsafe;

/**
 * <p>Title: DeAllocateMe</p>
 * <p>Description: interface that designates a class's instances to be cleaned on enqueueing to release their 
 * unsafe allocated memory</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.unsafe.DeAllocateMe</code></b>
 */

public interface DeAllocateMe {
	/**
	 * Returns the addresses to be deallocated
	 * @return the addresses to be deallocated
	 */
	public long[] getAddresses();
	
	
	
}
