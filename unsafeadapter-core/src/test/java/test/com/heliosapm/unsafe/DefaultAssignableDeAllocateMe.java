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

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

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
	private final NonBlockingHashMapLong<long[]> addresses;
	/** The refrence id */
	private long refId = 0;
	
	/**
	 * Creates a new DefaultAssignableDeAllocateMe
	 * @param addressCount The number of address slots to create
	 */
	public DefaultAssignableDeAllocateMe(int addressCount) {
		addresses = new NonBlockingHashMapLong<long[]>(addressCount);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#getAddresses()
	 */
	@Override
	public long[][] getAddresses() {
		long[][] addrs = new long[1][addresses.size()];
		int cnt = 0;
		for(Long x: addresses.keySet()) {
			addrs[0][cnt] = x;
			cnt++;
		}
		return addrs;
	}
	


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#getReferenceId()
	 */
	@Override
	public long getReferenceId() {
		return refId;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.Deallocatable#setReferenceId(long)
	 */
	@Override
	public void setReferenceId(long referenceId) {
		refId = referenceId;		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AddressAssignable#setAllocated(long, long, long)
	 */
	@Override
	public void setAllocated(long address, long size, long alignmentOverhead) {
		addresses.put(address, new long[]{size, alignmentOverhead});
		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AddressAssignable#removeAllocated(long)
	 */
	@Override
	public void removeAllocated(long address) {
		addresses.remove(address);
		
	}
	

}
