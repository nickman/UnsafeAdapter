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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

/**
 * <p>Title: ReferenceProvider</p>
 * <p>Description: Interface that defines an object as being to provide a {@link Reference} to itself.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.ReferenceProvider</code></p>
 * @param <T> The type of the phantom reference's referent and reference queue 
 */

public interface ReferenceProvider<T> {
	/**
	 * Returns a phantom reference to this object
	 * @param referenceQueue The reference queue the phantom reference will be constructed with
	 * @return a phantom reference to this object
	 */
	public Reference<? extends T> getReference(ReferenceQueue<? super T> referenceQueue);
}
