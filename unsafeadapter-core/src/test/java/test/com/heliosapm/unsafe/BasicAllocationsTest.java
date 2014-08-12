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



import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.heliosapm.unsafe.ReflectionHelper;
import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: BasicAllocationsTest</p>
 * <p>Description: Basic allocations test case</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.BasicAllocationsTest</code></p>
 */
@SuppressWarnings("restriction")
@UnsafeAdapterConfiguration()
public class BasicAllocationsTest extends BaseTest {
//	/** Keeps a count of raw (unmanaged) memory allocations through UnsafeAdapter */
//	protected final AtomicInteger rawAllocations = new AtomicInteger(0);
//	
//	/** A snapshot of the raw allocation count so we can verify it */
//	protected int rawCount = -1;

	
	
	/**
	 * Initializes the config and creates a new AllocationReferenceManager in accordance.
	 * We only need to enable this if we're debugging.
	 */
	@Before
	public void beforeTest() {
//		rawAllocations.set(0);
//		if(DEBUG_AGENT_LOADED) {
			if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
				//ReflectionHelper.invoke(UnsafeAdapter.class, "resetRefMgr");
			}
//		}
	}
	
	/**
	 * Validates the raw allocation count in the ref mgr
	 */
	@After
	public void afterTest() {
		if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
			Assert.assertEquals("Total Raw Allocations was unexpected. --> ", 0, UnsafeAdapter.getMemoryMBean().getTotalRawAllocationCount());			
		} else {
			Assert.assertEquals("Total Raw Allocations was unexpected. --> ", -1, UnsafeAdapter.getMemoryMBean().getTotalRawAllocationCount());
		}
	}
	
	
	/**
	 * Generic unsafe data type type allocation, write, read and deallocation test
	 * @param udt The type to test
	 * @param multiplier The multiplier on the base allocation size
	 * @param loops The number of allocation loops to run
	 * @return the total memory allocated during this call
	 */
	public long testAllocation(final UnsafeDataType udt, final int multiplier, final int loops) {
		final long allocSize = udt.size * multiplier;
		final long address[] = new long[loops];
		long totalAllocated = 0L;
		try {
			for(int loop = 0; loop < loops; loop ++) {				
				address[loop] = UnsafeAdapter.allocateMemory(allocSize);
				totalAllocated += allocSize;
//				rawCount = rawAllocations.incrementAndGet();
				long currentAddress = address[loop];
				for(int i = 0; i < multiplier; i++) {
					Object value = udt.randomValue();
					udt.adapterPut(currentAddress, value);
					Assert.assertEquals("Adapter Read Value was not [" + value + "]", value, udt.adapterGet(currentAddress));
					Assert.assertEquals("Unsafe Read Value was not [" + value + "]", value, udt.unsafeGet(currentAddress));	
					value = udt.randomValue();
					udt.adapterVolatilePut(currentAddress, value);
					Assert.assertEquals("Adapter Read Value was not [" + value + "]", value, udt.adapterGet(currentAddress));
					Assert.assertEquals("Unsafe Read Value was not [" + value + "]", value, udt.unsafeGet(currentAddress));
					currentAddress += udt.size;
				}
				validateAllocated(String.format("[testAllocation(%s) loop: %s]", udt.name(), loop), allocSize * (loop + 1), -1, loop + 1);
			}
			if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
				totalAllocated = UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory();
			}
		} finally {
			for(int loop = 0; loop < loops; loop ++) {	
				UnsafeAdapter.freeMemory(address[loop]);
			}
			validateDeallocated(String.format("[testAllocation(%s) Final]", udt.name()), 0, -1);
		}	
		return totalAllocated;
	}
	
	
	
	/**
	 * Generic unsafe data type type allocation, write, read, reallocation and deallocation test
	 * @param udt The type to test
	 * @param multiplier The multiplier on the base allocation size
	 * @param loops The number of allocation loops to run
	 * @return the total memory allocated during this call
	 */
	public long testReallocation(final UnsafeDataType udt, final int multiplier, final int loops) {
		final long allocSize = udt.size * multiplier;
		final long address[] = new long[loops];
		final long sizes[] = new long[loops];		
		long totalAllocated = 0L;
		try {
			for(int loop = 0; loop < loops; loop ++) {
				// Allocate
				address[loop] = UnsafeAdapter.allocateMemory(allocSize);
				totalAllocated += allocSize;
				sizes[loop] = allocSize;				
//				rawCount = rawAllocations.incrementAndGet();				
				final Object values[] = new Object[multiplier + 1];
				//  Write Values
				for(int mult = 0; mult < multiplier; mult++) {
					Object value = udt.randomValue();
					values[mult] = value;
					udt.adapterPut(address[loop] + (mult * udt.size), value);					
				}
				// Read values and verify
				for(int mult = 0; mult < multiplier; mult++) {
					Object adapterValue = udt.adapterGet(address[loop] + (mult * udt.size));
					Object directValue = udt.unsafeGet(address[loop] + (mult * udt.size));
					Object actualValue = values[mult];
					Assert.assertEquals("Adapter Read Value was not [" + actualValue + "]", adapterValue, actualValue);
					Assert.assertEquals("Unsafe Read Value was not [" + actualValue + "]", directValue, actualValue);
				}
				// =======================================================================================
				// Reallocate and add udt.size to size alloc
				// =======================================================================================
				final long newSize = (allocSize + udt.size);				
				address[loop] = UnsafeAdapter.reallocateMemory(address[loop], newSize);
				totalAllocated += udt.size;
				values[multiplier] = udt.randomValue();
				udt.adapterPut(address[loop] + (multiplier * udt.size), values[multiplier]);
				for(int mult = 0; mult < multiplier+1; mult++) {
					Object adapterValue = udt.adapterGet(address[loop] + (mult * udt.size));
					Object directValue = udt.unsafeGet(address[loop] + (mult * udt.size));
					Object actualValue = values[mult];
					Assert.assertEquals("Adapter Read Value was not [" + actualValue + "]", adapterValue, actualValue);
					Assert.assertEquals("Unsafe Read Value was not [" + actualValue + "]", directValue, actualValue);					
				}
				// long trackedValue, long untrackedValue, long allocationCoun
				validateAllocated(String.format("[testReallocation(%s) loop: %s]", udt.name(), loop), 
						UnsafeAdapter.getMemoryMBean().isTrackingEnabled() ? UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory() : totalAllocated,
						-1,
						loop+1);				
			}
			if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
				totalAllocated = UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory();
			}
			
		} finally {
			for(int loop = 0; loop < loops; loop ++) {	
				UnsafeAdapter.freeMemory(address[loop]);
			}
			validateDeallocated(String.format("[testAllocation(%s) Final]", udt.name()), 0, -1);
		}	
		return totalAllocated;
	}

	

	/**
	 * Tests a type allocation, write, read and deallocation
	 * @param udt The data type to test
	 * @throws Exception thrown on any error
	 */	
	public void testAllocated(final UnsafeDataType udt) throws Exception {
		final int multiplier = nextPosInt(100);
		final int loops = nextPosInt(100);		
		final long typeSize = testUnsafe.arrayIndexScale(Array.newInstance(udt.primitiveType, 1).getClass());
		final long expectedAllocation = (multiplier * loops * udt.size);
		log("Executing Simple Allocation Test: type: [%s], size: [%s], multiplier: [%s], loops: [%s], expected allocation: [%s]", udt.name(), typeSize,  multiplier, loops, expectedAllocation);
		final long actualAllocation = testAllocation(udt, multiplier, loops);
		Assert.assertEquals("Total Allocation was not [" + expectedAllocation + "]", expectedAllocation, actualAllocation);
	}
	
	/**
	 * Tests a type allocation, write, read, reallocation and deallocation
	 * @param udt The data type to test
	 * @throws Exception thrown on any error
	 */	
	public void testReallocated(final UnsafeDataType udt) throws Exception {
		final int multiplier = nextPosInt(100)+10;
		final int loops = nextPosInt(100)+10;		
		final long typeSize = testUnsafe.arrayIndexScale(Array.newInstance(udt.primitiveType, 1).getClass());
		final long expectedAllocation = (multiplier * loops * udt.size)  + (loops * udt.size);
		log("Executing Simple Allocation/Reallocation Test: type: [%s], size: [%s], multiplier: [%s], loops: [%s], expected allocation: [%s]", udt.name(), typeSize,  multiplier, loops, expectedAllocation);
		final long actualAllocation = testReallocation(udt, multiplier, loops);
		Assert.assertEquals("Total Allocation was not [" + expectedAllocation + "]", expectedAllocation, actualAllocation);
	}
	
	
	
//	/**
//	 * Tests data allocation, write, read and deallocation for all unsafe data types
//	 * @throws Exception thrown on any error
//	 */
//	@Test
//	public void testSimpleAllocationDeallocation() throws Exception {
//		for(UnsafeDataType udt: UnsafeDataType.values()) {
//			testAllocated(udt);
//		}
//	}
//	
//	/**
//	 * Tests data allocation, write, read and deallocation for all unsafe data types
//	 * @throws Exception thrown on any error
//	 */
//	@Test
//	public void testSimpleReallocationDeallocation() throws Exception {
//		for(UnsafeDataType udt: UnsafeDataType.values()) {
//			testReallocated(udt);
//			break;
//		}		
//	}

	/**
	 * Tests data allocation, write, read, and deallocation for booleans
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testBooleanAllocationDeallocation() throws Exception {
		testAllocated(UnsafeDataType.BOOLEAN);
	}

	/**
	 * Tests data allocation, write, read, reallocation and deallocation for booleans
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testBooleanReallocationDeallocation() throws Exception {
		testReallocated(UnsafeDataType.BOOLEAN);
	}



	/**
	 * Tests data allocation, write, read, and deallocation for bytes
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testByteAllocationDeallocation() throws Exception {
		testAllocated(UnsafeDataType.BYTE);
	}

	/**
	 * Tests data allocation, write, read, reallocation and deallocation for bytes
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testByteReallocationDeallocation() throws Exception {
		testReallocated(UnsafeDataType.BYTE);
	}



	/**
	 * Tests data allocation, write, read, and deallocation for chars
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testCharacterAllocationDeallocation() throws Exception {
		testAllocated(UnsafeDataType.CHAR);
	}

	/**
	 * Tests data allocation, write, read, reallocation and deallocation for chars
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testCharacterReallocationDeallocation() throws Exception {
		testReallocated(UnsafeDataType.CHAR);
	}



	/**
	 * Tests data allocation, write, read, and deallocation for shorts
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testShortAllocationDeallocation() throws Exception {
		testAllocated(UnsafeDataType.SHORT);
	}

	/**
	 * Tests data allocation, write, read, reallocation and deallocation for shorts
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testShortReallocationDeallocation() throws Exception {
		testReallocated(UnsafeDataType.SHORT);
	}



	/**
	 * Tests data allocation, write, read, and deallocation for ints
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testIntegerAllocationDeallocation() throws Exception {
		testAllocated(UnsafeDataType.INTEGER);
	}

	/**
	 * Tests data allocation, write, read, reallocation and deallocation for ints
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testIntegerReallocationDeallocation() throws Exception {
		testReallocated(UnsafeDataType.INTEGER);
	}



	/**
	 * Tests data allocation, write, read, and deallocation for floats
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testFloatAllocationDeallocation() throws Exception {
		testAllocated(UnsafeDataType.FLOAT);
	}

	/**
	 * Tests data allocation, write, read, reallocation and deallocation for floats
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testFloatReallocationDeallocation() throws Exception {
		testReallocated(UnsafeDataType.FLOAT);
	}



	/**
	 * Tests data allocation, write, read, and deallocation for longs
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testLongAllocationDeallocation() throws Exception {
		testAllocated(UnsafeDataType.LONG);
	}

	/**
	 * Tests data allocation, write, read, reallocation and deallocation for longs
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testLongReallocationDeallocation() throws Exception {
		testReallocated(UnsafeDataType.LONG);
	}



	/**
	 * Tests data allocation, write, read, and deallocation for doubles
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testDoubleAllocationDeallocation() throws Exception {
		testAllocated(UnsafeDataType.DOUBLE);
	}

	/**
	 * Tests data allocation, write, read, reallocation and deallocation for doubles
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testDoubleReallocationDeallocation() throws Exception {
		testReallocated(UnsafeDataType.DOUBLE);
	}



	
	    

        
	
}
