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
package test.com.heliosapm.unsafe;

/**
 * <p>Title: UnsafeDataTypeInvoker</p>
 * <p>Description: Defines an {@link UnsafeDataType} invoker</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.UnsafeDataTypeInvoker</code></p>
 * @param <T> The unsafe data type
 */

public interface UnsafeDataTypeInvoker<T> {
	
	/**
	 * Puts a value to the specified address using the unsafe-adapter
	 * @param address The address to write to
	 * @param t The value to write
	 */
	public void adapterPut(long address, T t);
	
	/**
	 * Volatile puts a value to the specified address using the unsafe-adapter
	 * @param address The address to write to
	 * @param t The value to write
	 */
	public void adapterVolatilePut(long address, T t);
	
	/**
	 * Puts a value to the specified address using the direct unsafe
	 * @param address The address to write to
	 * @param t The value to write
	 */
	public void unsafePut(long address, T t);
	
	/**
	 * Volatile puts a value to the specified address using the direct unsafe
	 * @param address The address to write to
	 * @param t The value to write
	 */
	public void unsafeVolatilePut(long address, T t);
	
	
	/**
	 * Reads a value from the specified address using the unsafe-adapter
	 * @param address The address to read from
	 * @return the read value
	 */
	public T adapterGet(long address);
	/**
	 * Reads a value from the specified address using the direct unsafe
	 * @param address The address to read from
	 * @return the read value
	 */
	public T unsafeGet(long address);
	
	/**
	 * Returns a random value 
	 * @return a random value 
	 */
	public T randomValue();
	
	
	
//	public long allocateMemory(int multiplier, Object memoryManager);
//	
//	public long reallocateMemoryUp(long address, int multiplier, Object memoryManager);
	
	
	
	
	
}
