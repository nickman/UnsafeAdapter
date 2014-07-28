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
package com.heliosapm.unsafe;

/**
 * <p>Title: UnsafeAdapter</p>
 * <p>Description: A wrapper for {@link sun.misc.Unsafe} to provide enhanced functionality</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.UnsafeAdapter</code></p>
 */

public class UnsafeAdapter {
	
	/** The system prop specifying the use of the safe memory management implementation */
	public static final String SAFE_MANAGER_PROP = "safe.allocations.manager";

	/** The configured adapter (default or safe) */
	private static final DefaultUnsafeAdapterImpl adapter;

	static {
		adapter = System.getProperties().containsKey(SAFE_MANAGER_PROP) ? SafeAdapterImpl.getInstance() : DefaultUnsafeAdapterImpl.getInstance();
	}
	
	
	/**
	 * Report the size in bytes of a native pointer, as stored via #putAddress.
	 * This value will be either 4 or 8.  Note that the sizes of other primitive 
	 * types (as stored in native memory blocks) is determined fully by their information content.
	 * @return The size in bytes of a native pointer
	 * @see sun.misc.Unsafe#addressSize()
	 */
	public static int addressSize() {
		return adapter.addressSize();
	}
	
	
	
	/**
	 * Prints information about the currently configured adapter.
	 * @param args None
	 */
	public static void main(String[] args) {

	}

	/**
	 * Low maintenance out logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	public static void log(Object fmt, Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
	/**
	 * Low maintenance err logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	public static void loge(Object fmt, Object...args) {
		System.err.println(String.format(fmt.toString(), args));
		if(args!=null && args.length>0 && args[args.length-1] instanceof Throwable) {
			System.err.println("Stack trace follows....");
			((Throwable)args[args.length-1]).printStackTrace(System.err);			
		}
	}
	
	
}
