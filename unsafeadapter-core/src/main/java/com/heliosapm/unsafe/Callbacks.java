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

import java.util.concurrent.Callable;

/**
 * <p>Title: Callbacks</p>
 * <p>Description: A collection of callbacks for use with the {@link UnsafeAdapterOld}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.Callbacks</code></p>
 */

public class Callbacks {
	/**
	 * <p>Title: LongCallable</p>
	 * <p>Description: The equivalent of a {@link Callable} but for a primitive long to avoid AutoBoxing</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.Callbacks.LongCallable</code></p>
	 */
	public interface LongCallable {
		    /**
		     * Computes a result, or throws an exception if unable to do so.
		     * @return computed result
		     */
		    long longCall();
	}
	
	
	/**
	 * <p>Title: DoubleCallable</p>
	 * <p>Description: The equivalent of a {@link Callable} but for a primitive double to avoid AutoBoxing</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.Callbacks.DoubleCallable</code></p>
	 */
	public interface DoubleCallable {
		    /**
		     * Computes a result, or throws an exception if unable to do so.
		     * @return computed result
		     */
		    double doubleCall();
	}
	
	/**
	 * <p>Title: IntCallable</p>
	 * <p>Description: The equivalent of a {@link Callable} but for a primitive int to avoid AutoBoxing</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.Callbacks.IntCallable</code></p>
	 */
	public interface IntCallable {
		    /**
		     * Computes a result, or throws an exception if unable to do so.
		     * @return computed result
		     */
		    int intCall();
	}
	
	/**
	 * <p>Title: ByteCallable</p>
	 * <p>Description: The equivalent of a {@link Callable} but for a primitive byte to avoid AutoBoxing</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.Callbacks.ByteCallable</code></p>
	 */
	public interface ByteCallable {
		    /**
		     * Computes a result, or throws an exception if unable to do so.
		     * @return computed result
		     */
		    byte byteCall();
	}
	
	/**
	 * <p>Title: BooleanCallable</p>
	 * <p>Description: The equivalent of a {@link Callable} but for a primitive boolean to avoid AutoBoxing</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.Callbacks.BooleanCallable</code></p>
	 */
	public interface BooleanCallable {
		    /**
		     * Computes a result, or throws an exception if unable to do so.
		     * @return computed result
		     */
		    boolean booleanCall();
	}

}
