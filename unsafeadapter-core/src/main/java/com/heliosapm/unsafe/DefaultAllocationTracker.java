/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2014, Helios Development Group and individual contributors
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
package com.heliosapm.unsafe;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

/**
 * <p>Title: DefaultAllocationTracker</p>
 * <p>Description: The default allocation tracker implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.DefaultAllocationTracker</code></p>
 */

public class DefaultAllocationTracker implements AllocationTracker {
	/** The allocation sizes and alignment overheads keyed by the address of the allocated memory block */
	private volatile NonBlockingHashMapLong<long[]> tracking = null;

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#add(long, long, long)
	 */
	@Override
	public void add(long address, long allocationSize, long alignmentOverhead) {
		if(tracking==null) {
			synchronized(this) {
				if(tracking==null) {
					tracking = new NonBlockingHashMapLong<long[]>(16, true);
				}
			}
		}
		tracking.put(address, new long[]{allocationSize, alignmentOverhead});		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#getAllocationSize(long)
	 */
	@Override
	public long getAllocationSize(long address) {
		if(tracking!=null) return 0;
		long[] track = tracking.get(address);
		if(track==null) return 0;
		return track[0];
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#getAlignmentOverhead(long)
	 */
	@Override
	public long getAlignmentOverhead(long address) {
		if(tracking!=null) return 0;
		long[] track = tracking.get(address);
		if(track==null) return 0;
		return track[0];		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.unsafe.AllocationTracker#clearAddress(long)
	 */
	@Override
	public void clearAddress(long address) {
		if(tracking!=null) tracking.remove(address);
	}

}
