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

import java.lang.reflect.Field;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import sun.misc.Unsafe;

import com.heliosapm.unsafe.AllocationPointer;
import com.heliosapm.unsafe.AllocationPointerOperations;
import com.heliosapm.unsafe.AllocationReferenceManager;
import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: BenchmarkAlocationPointer</p>
 * <p>Description: </p> 
 * <p>Run with:  <b><code>java -jar target\benchmarks.jar -wi 3 -i 3 ".*BenchmarkAlocationPointer.*"</code></b></p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>perf.com.heliosapm.trove.BenchmarkAlocationPointer</code></p>
 */
@State(Scope.Thread)
public class BenchmarkAlocationPointer {
	
	private static final Random r = new Random(System.currentTimeMillis());
	private static final int valueCount = 1000;
	/** A direct reference to the unsafe class instance */
	protected static final Unsafe testUnsafe;
	
	private static final AllocationReferenceManager refMgr = new AllocationReferenceManager(false, false);
	private static final AllocationReferenceManager refMgrMem = new AllocationReferenceManager(true, false);
	private static final AllocationReferenceManager refMgrMemAlign = new AllocationReferenceManager(true, false);
	
	static {
        try {        	
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            testUnsafe = (Unsafe) theUnsafe.get(null);
        } catch (Throwable t) {
        	throw new RuntimeException(t);
        }
	}
	
	
	protected int apTest(final AllocationPointer ap, final boolean free) {
		try {
			int success = 0;
			
			final long[] writeAddresses = new long[valueCount];
			for(short i = 0; i < writeAddresses.length; i++) {
				writeAddresses[i] = testUnsafe.allocateMemory(1);
				ap.assignSlot(writeAddresses[i]);				
			}
			for(short i = 0; i < writeAddresses.length; i++) {
				final long readValue = ap.getAddress(i);
				if(readValue==writeAddresses[i]) success++;
			}			
			return success;
		} finally {
			if(free) ap.free();
		}		
	}
	
	
	@Benchmark
	public int testAllocationPointer() {	
		return apTest(AllocationPointerOperations.newAllocationPointerInstance(false, false), true);
	}
	
	@Benchmark
	public int testAllocationPointerMemTracking() {	
		return apTest(AllocationPointerOperations.newAllocationPointerInstance(true, false), true);
	}
	
	@Benchmark
	public int testAllocationPointerMemAlignTracking() {	
		return apTest(AllocationPointerOperations.newAllocationPointerInstance(true, true), true);
	}
	
	@Benchmark
	public int testAllocationPointerAuto() {	
		return apTest(refMgr.newAllocationPointer(), false);
	}
	
	@Benchmark
	public int testAllocationPointerMemTrackingAuto() {	
		return apTest(refMgrMem.newAllocationPointer(), false);
	}
	
	@Benchmark
	public int testAllocationPointerMemAlignTrackingAuto() {	
		return apTest(refMgrMemAlign.newAllocationPointer(), false);
	}
	
	
	public static void main(String[] args) {
		BenchmarkAlocationPointer bap = new BenchmarkAlocationPointer();
		int x = bap.testAllocationPointerMemAlignTrackingAuto();
		if(x==valueCount) System.out.println("SUCCESS!");
		else System.out.println("FAIL! " + x + "vs expected " + valueCount);		 
	}
	
}
