/**
 * 
 */
package com.heliosapm.unsafe;

/**
 * <p>Title: Deallocatable</p>
 * <p>Description: interface that designates a class's instances to be cleaned on enqueueing to release their 
 * unsafe allocated memory</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.unsafe.Deallocatable</code></b>
 */

public interface Deallocatable {
	/**
	 * Returns the keyAddresses to be deallocated
	 * @return the keyAddresses to be deallocated
	 */
	public long[][] getAddresses();
	
	/**
	 * Returns the reference id assigned to this deallocatable.
	 * <b>NOTE:</b> If a reference id has not been assigned (i.e. no call to {@link #getReferenceId()}
	 * the implementation should return <b><code>0</code></b>.
	 * @return the reference id or zero if one has not been assigned
	 */
	public long getReferenceId();
	
	/**
	 * Sets the reference id.
	 * <b>NOTE:</b> Should throw an exception if the ref id has already been set
	 * @param referenceId The reference id assigned 
	 */
	public void setReferenceId(long referenceId);
	
	
	
}
