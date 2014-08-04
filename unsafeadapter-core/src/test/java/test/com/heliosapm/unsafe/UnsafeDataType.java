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

import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: UnsafeDataType</p>
 * <p>Description: Test supporting enumeration of data types manipulatable through the UnsafeAdapter</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.UnsafeDataType</code></p>
 */

public enum UnsafeDataType {
	BOOLEAN,
	BYTE,
	CHAR,
	SHORT,
	INTEGER,
	FLOAT,
	LONG, 
	ADDRESS,
	DOUBLE,
	OBJECT;
	
	/**
	 * <p>Title: UnsafeDataTypeOps</p>
	 * <p>Description: Defines type parameterized unsafe data operations</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>test.com.heliosapm.unsafe.UnsafeDataType.UnsafeDataTypeOps</code></p>
	 * @param <T> The assumed unsafe data type
	 */
	public static interface UnsafeDataTypeOps<T> {
		/**
		 * Writes the passed value to the specified address
		 * @param value The value to write
		 * @param address The address to write to
		 */
		public void put(T value, long address);
		/**
		 * Writes the passed value to the specified offset of the passed object
		 * @param target The target object 
		 * @param value The value to write
		 * @param offset The offset beyond the address of the passed object
		 */
		public void put(Object target, T value, long offset);
		/**
		 * Reads the typed value from the passed address
		 * @param address The address to read from
		 * @return The read value
		 */
		public T get(long address); 
		/**
		 * Reads the typed value from the offset beyond the address of the target object
		 * @param target The target object 
		 * @param offset The offset beyond the address of the passed object to read from
		 * @return The read value
		 */
		public T get(Object target, long offset);		
	}
	
	/**
	 * <p>Title: BooleanUnsafeDataTypeOps</p>
	 * <p>Description: UnsafeDataTypeOps for Booleans</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>test.com.heliosapm.unsafe.UnsafeDataType.BooleanUnsafeDataTypeOps</code></p>
	 */
	public static class BooleanUnsafeDataTypeOps implements UnsafeDataTypeOps<Boolean> {

		/**
		 * {@inheritDoc}
		 * @see test.com.heliosapm.unsafe.UnsafeDataType.UnsafeDataTypeOps#put(java.lang.Object, long)
		 */
		@Override
		public void put(Boolean value, long address) {
			UnsafeAdapter.putBoolean(null, address, value.booleanValue());
		}

		/**
		 * {@inheritDoc}
		 * @see test.com.heliosapm.unsafe.UnsafeDataType.UnsafeDataTypeOps#put(java.lang.Object, java.lang.Object, long)
		 */
		@Override
		public void put(Object target, Boolean value, long offset) {
			UnsafeAdapter.putBoolean(target, offset, value.booleanValue());			
		}

		/**
		 * {@inheritDoc}
		 * @see test.com.heliosapm.unsafe.UnsafeDataType.UnsafeDataTypeOps#get(long)
		 */
		@SuppressWarnings("boxing")
		@Override
		public Boolean get(long address) {
			return UnsafeAdapter.getBoolean(null, address);
		}

		/**
		 * {@inheritDoc}
		 * @see test.com.heliosapm.unsafe.UnsafeDataType.UnsafeDataTypeOps#get(java.lang.Object, long)
		 */
		@SuppressWarnings("boxing")
		@Override
		public Boolean get(Object target, long offset) {
			return UnsafeAdapter.getBoolean(target, offset);
		}		
	}
}


