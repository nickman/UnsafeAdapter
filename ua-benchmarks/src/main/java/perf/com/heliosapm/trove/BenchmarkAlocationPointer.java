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
package perf.com.heliosapm.trove;

import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import com.heliosapm.unsafe.AllocationPointer;
import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: BenchmarkAlocationPointer</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>perf.com.heliosapm.trove.BenchmarkAlocationPointer</code></p>
 */
@State(Scope.Thread)
public class BenchmarkAlocationPointer {
	
	

	@Benchmark
	public int testFixedSizeAllocationPointer() {	
		final Random r = new Random(System.currentTimeMillis());
		int success = 0;
		AllocationPointer ap = new AllocationPointer();
		final int valueCount = 1000;;
		final long[] writeValues = new long[valueCount];
		for(short i = 0; i < writeValues.length; i++) {
			writeValues[i] = r.nextLong();
			final long address = UnsafeAdapter.allocateMemory(8, ap);
			UnsafeAdapter.putLong(address, writeValues[i]);
		}
		for(short i = 0; i < writeValues.length; i++) {
			final long readValue = UnsafeAdapter.getLong(ap.getAddress(i));
			if(readValue==writeValues[i]) success++;
		}
		final long expectedAllocation = ((long)valueCount << 3);
		ap = null;		
		return success;
	}
	
}
