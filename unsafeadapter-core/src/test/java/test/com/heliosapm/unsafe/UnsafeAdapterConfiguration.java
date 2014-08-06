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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.heliosapm.unsafe.AllocationPointerOperations;

/**
 * <p>Title: UnsafeAdapterConfiguration</p>
 * <p>Description: Annotation to configure the UnsafeAdapter configuration for the class under test</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.UnsafeAdapterConfiguration</code></p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UnsafeAdapterConfiguration {
	/**
	 * true if memory allocations should be unsafe, false if safe
	 */
	public boolean unsafe() default true;
	/**
	 * true to enable memory tracking, false otherwise
	 */
	public boolean memTracking() default false;	
	/**
	 * true to enable cache-line memory alignment, false otherwise
	 */
	public boolean memAlignment() default false;
	
	/**
	 * true if safe memory allocation should be off-heap, false otherwise.
	 * Only applicable if {@link #unsafe()} is false.
	 */
	public boolean offHeap() default false;
	
	/**
	 * true to enable managed memory allocation in the AP
	 */
	public boolean apManaged() default AllocationPointerOperations.DEFAULT_MANAGED_ALLOC;
	
	
	/**
	 * The initial and extend allocation size for the AllocationPonter
	 */
	public int apAllocSize() default AllocationPointerOperations.DEFAULT_ALLOC_SIZE_PROP;
}
