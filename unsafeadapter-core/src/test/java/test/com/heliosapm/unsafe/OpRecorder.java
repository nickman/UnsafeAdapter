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

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Title: OpRecorder</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.OpRecorder</code></p>
 */

public class OpRecorder {
	
	private final List<Op> ops = new ArrayList<Op>();
	
	/**
	 * Creates a new OpRecorder
	 */
	public OpRecorder() {

	}
	
	public void recordPut(UnsafeDataType type, Object value, boolean adapter, long address) {
		ops.add(new PutOp(type, value, adapter, address));
	}
	
	public interface Op {
		
	}

	public class PutOp implements Op {
		public final UnsafeDataType type;
		public final Object value;
		public final boolean adapter;
		public final long address;
		/**
		 * Creates a new PutOp
		 * @param type
		 * @param value
		 * @param adapter
		 * @param address 
		 */
		public PutOp(UnsafeDataType type, Object value, boolean adapter, long address) {			
			this.type = type;
			this.value = value;
			this.adapter = adapter;
			this.address = address;
		}
		
		
	}
	
}
