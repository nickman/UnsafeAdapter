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
package test.com.heliosapm.unsafe;

import java.util.concurrent.atomic.AtomicInteger;

import com.heliosapm.unsafe.AddressAssignable;
import com.heliosapm.unsafe.Deallocatable;

/**
 * <p>Title: DefaultAssignableDeAllocateMe</p>
 * <p>Description: A default {@link AddressAssignable} and {@link Deallocatable} implementation. Not too useful, but helpful for testing or extending.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.DefaultAssignableDeAllocateMe</code></p>
 */

public class DefaultAssignableDeAllocateMe implements Deallocatable, AddressAssignable {
	/** The address slots */
	private final long[][] addresses;
	/** the assignment count */
	private final AtomicInteger assigned = new AtomicInteger(-1);
	
	/**
	 * Creates a new DefaultAssignableDeAllocateMe
	 * @param addressCount The number of address slots to create
	 */
	public DefaultAssignableDeAllocateMe(int addressCount) {
		addresses = new long[2][];
		addresses[0] = new long[addressCount];
		addresses[1] = new long[] {0};
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#getAddresses()
	 */
	@Override
	public long[][] getAddresses() {
		return addresses;
	}
	

	/**
	 * Returns the index of the next unallocated address slot
	 * @return the index of the next unallocated address slot
	 */
	public int getNextIndex() {
		final int index = assigned.incrementAndGet();
		if(index > addresses[0].length) throw new RuntimeException("Invalid state. Address slots [" + index +  "] are full");
		return index;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#getReferenceId()
	 */
	@Override
	public long getReferenceId() {
		return addresses[1][0];
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#setReferenceId(long)
	 */
	@Override
	public void setReferenceId(long referenceId) {
		addresses[1][0] = referenceId;
		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AddressAssignable#setAllocated(long, long, long)
	 */
	@Override
	public void setAllocated(long address, long size, long alignmentOverhead) {
		// TODO Auto-generated method stub
		
	}
	

}
