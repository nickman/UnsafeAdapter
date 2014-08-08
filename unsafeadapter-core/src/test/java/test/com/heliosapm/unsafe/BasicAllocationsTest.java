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
	/** Keeps a count of raw (unmanaged) memory allocations through UnsafeAdapter */
	protected final AtomicInteger rawAllocations = new AtomicInteger(0);
	
	/** A snapshot of the raw allocation count so we can verify it */
	protected int rawCount = -1;
	/**
	 * Initializes the config and creates a new AllocationReferenceManager in accordance.
	 */
	@Before
	public void beforeTest() {
		if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
			ReflectionHelper.invoke(UnsafeAdapter.class, "resetRefMgr");
		}
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
				rawCount = rawAllocations.incrementAndGet();
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
	
	public static final long ADDRESS_SIZE = UnsafeAdapter.ADDRESS_SIZE;
	
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
				address[loop] = UnsafeAdapter.allocateMemory(allocSize);
				totalAllocated += allocSize;
				sizes[loop] = allocSize;				
				rawCount = rawAllocations.incrementAndGet();				
				final Object values[] = new Object[multiplier + 1];
				for(int mult = 0; mult < multiplier; mult++) {
					Object value = udt.randomValue();
					values[mult] = value;
					udt.adapterPut(address[loop] + (mult * udt.size), value);					
				}
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
		log("Executing Simple Allocation Test: type: [%s], size: [%s], multiplier: [%s], loops: [%s], expected allocation: [%s]", udt.name(), typeSize,  multiplier, loops, expectedAllocation);
		final long actualAllocation = testReallocation(udt, multiplier, loops);
		Assert.assertEquals("Total Allocation was not [" + expectedAllocation + "]", expectedAllocation, actualAllocation);
	}
	
	
	
	/**
	 * Tests data allocation, write, read and deallocation for all unsafe data types
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testSimpleAllocationDeallocation() throws Exception {
		for(UnsafeDataType udt: UnsafeDataType.values()) {
			testAllocated(udt);
		}
	}
	
	/**
	 * Tests data allocation, write, read and deallocation for all unsafe data types
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testSimpleReallocationDeallocation() throws Exception {
		for(UnsafeDataType udt: UnsafeDataType.values()) {
			testReallocated(udt);
			break;
		}
		log("Completed testSimpleReallocationDeallocation");
	}


	    

		/**
		 * Tests a long allocation, write, read, re-allocation and deallocation
		 * @throws Exception thrown on any error
		 */
		@Test
		public void testReallocatedLong() throws Exception {
			long address = -1;
			try {
				address = UnsafeAdapter.allocateMemory(8);
				rawCount = rawAllocations.incrementAndGet();
				long value = nextPosLong();
				long nextValue = nextPosLong();
				UnsafeAdapter.putLong(address, value);
				Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getLong(address));
				Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getLong(address));
				validateAllocated(8, -1);	
				address = UnsafeAdapter.reallocateMemory(address, 16);
				UnsafeAdapter.putLong(address + 8, nextValue);
				Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.getLong(address));
				Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.getLong(address));	
				Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.getLong(address + 8));
				Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.getLong(address + 8));
				validateAllocated(16, -1);
			} finally {
				if(address!=-1) UnsafeAdapter.freeMemory(address);
				validateDeallocated("testReallocatedLong", 0, -1);
			}
		}
	    

        /**
         * Tests a int allocation, write, read, re-allocation and deallocation
         * @throws Exception thrown on any error
         */
        @Test
        public void testReallocatedInteger() throws Exception {
            long address = -1;
            try {
                address = UnsafeAdapter.allocateMemory(4);
                rawCount = rawAllocations.incrementAndGet();
                int value = nextPosInteger();
                int nextValue = nextPosInteger();
                UnsafeAdapter.putInt(address, value);
                Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getInt(address));
                Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getInt(address));   
                validateAllocated(4, -1);
                address = UnsafeAdapter.reallocateMemory(address, 4*2);
                UnsafeAdapter.putInt(address + 4, nextValue);
                Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.getInt(address));
                Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.getInt(address));    
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.getInt(address + 4));
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.getInt(address + 4));
                validateAllocated(4*2, -1);
            } finally {
                if(address!=-1) UnsafeAdapter.freeMemory(address);
                rawCount = rawAllocations.decrementAndGet();
                validateDeallocated("testReallocatedInteger", 0, -1);
            }
        }


        
        

        /**
         * Tests a byte allocation, write, read, re-allocation and deallocation
         * @throws Exception thrown on any error
         */
        @Test
        public void testReallocatedByte() throws Exception {
            long address = -1;
            try {
                address = UnsafeAdapter.allocateMemory(1);    
                rawCount = rawAllocations.incrementAndGet();
                byte value = nextPosByte();
                byte nextValue = nextPosByte();
                UnsafeAdapter.putByte(address, value);
                Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getByte(address));
                Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getByte(address));   
                validateAllocated(1, -1);
                address = UnsafeAdapter.reallocateMemory(address, 1*2);
                UnsafeAdapter.putByte(address + 1, nextValue);
                Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.getByte(address));
                Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.getByte(address));    
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.getByte(address + 1));
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.getByte(address + 1));
                validateAllocated(1*2, -1);
            } finally {
                if(address!=-1) UnsafeAdapter.freeMemory(address);
                rawCount = rawAllocations.decrementAndGet();
                validateDeallocated("testReallocatedByte", 0, -1);
            }
        }


        /**
         * Tests a boolean allocation, write, read, re-allocation and deallocation
         * @throws Exception thrown on any error
         */
        @Test
        public void testReallocatedBoolean() throws Exception {
            long address = -1;
            try {
                address = UnsafeAdapter.allocateMemory(1);
                rawCount = rawAllocations.incrementAndGet();
                boolean value = nextBoolean();
                boolean nextValue = nextBoolean();
                UnsafeAdapter.putBoolean(address, value);
                Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getBoolean(address));
                Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getBoolean(null, address));    
                validateAllocated(1, -1);
                address = UnsafeAdapter.reallocateMemory(address, 1*2);
                UnsafeAdapter.putBoolean(address + 1, nextValue);
                Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.getBoolean(address));
                Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.getBoolean(null, address));    
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.getBoolean(address + 1));
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.getBoolean(null, address + 1));
                validateAllocated(1*2, -1);                
            } finally {
                if(address!=-1) UnsafeAdapter.freeMemory(address);
                rawCount = rawAllocations.decrementAndGet();
                validateDeallocated("testReallocatedBoolean", 0, -1);
            }
        }


        /**
         * Tests a char allocation, write, read, re-allocation and deallocation
         * @throws Exception thrown on any error
         */
        @Test
        public void testReallocatedCharacter() throws Exception {
            long address = -1;
            try {
                address = UnsafeAdapter.allocateMemory(2);
                rawCount = rawAllocations.incrementAndGet();
                char value = nextCharacter();
                char nextValue = nextCharacter();
                UnsafeAdapter.putChar(address, value);
                Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getChar(address));
                Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getChar(address));    
                validateAllocated(2, -1);
                address = UnsafeAdapter.reallocateMemory(address, 2*2);
                UnsafeAdapter.putChar(address + 2, nextValue);
                Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.getChar(address));
                Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.getChar(address));    
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.getChar(address + 2));
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.getChar(address + 2));
                validateAllocated(2*2, -1);                
            } finally {
                if(address!=-1) UnsafeAdapter.freeMemory(address);
                rawCount = rawAllocations.decrementAndGet();
                validateDeallocated("testReallocatedCharacter", 0, -1);
            }
        }


        /**
         * Tests a short allocation, write, read, re-allocation and deallocation
         * @throws Exception thrown on any error
         */
        @Test
        public void testReallocatedShort() throws Exception {
            long address = -1;
            try {
                address = UnsafeAdapter.allocateMemory(2);
                rawCount = rawAllocations.incrementAndGet();
                short value = nextPosShort();
                short nextValue = nextPosShort();
                UnsafeAdapter.putShort(address, value);
                Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getShort(address));
                Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getShort(address));   
                validateAllocated(2, -1);
                address = UnsafeAdapter.reallocateMemory(address, 2*2);
                UnsafeAdapter.putShort(address + 2, nextValue);
                Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.getShort(address));
                Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.getShort(address));    
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.getShort(address + 2));
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.getShort(address + 2));
                validateAllocated(2*2, -1);                
            } finally {
                if(address!=-1) UnsafeAdapter.freeMemory(address);
                rawCount = rawAllocations.decrementAndGet();
                validateDeallocated("testReallocatedShort", 0, -1);
            }
        }


        /**
         * Tests a float allocation, write, read, re-allocation and deallocation
         * @throws Exception thrown on any error
         */
        @Test
        public void testReallocatedFloat() throws Exception {
            long address = -1;
            try {
                address = UnsafeAdapter.allocateMemory(4);
                rawCount = rawAllocations.incrementAndGet();
                float value = nextPosFloat();
                float nextValue = nextPosFloat();
                UnsafeAdapter.putFloat(address, value);
                Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getFloat(address), 0f);
                Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getFloat(address), 0f);    
                validateAllocated(4, -1);                
                address = UnsafeAdapter.reallocateMemory(address, 4*2);
                UnsafeAdapter.putFloat(address + 4, nextValue);
                Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.getFloat(address), 0f);
                Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.getFloat(address), 0f);    
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.getFloat(address + 4), 0f);
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.getFloat(address + 4), 0f);
                validateAllocated(4*2, -1);
            } finally {
                if(address!=-1) UnsafeAdapter.freeMemory(address);
                rawCount = rawAllocations.decrementAndGet();
                validateDeallocated("testReallocatedFloat", 0, -1);
            }
        }


        /**
         * Tests a double allocation, write, read, re-allocation and deallocation
         * @throws Exception thrown on any error
         */
        @Test
        public void testReallocatedDouble() throws Exception {
            long address = -1;            
            try {
                address = UnsafeAdapter.allocateMemory(8);                
                rawCount = rawAllocations.incrementAndGet();
                double value = nextPosDouble();
                double nextValue = nextPosDouble();
                UnsafeAdapter.putDouble(address, value);
                Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getDouble(address), 0d);
                Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getDouble(address), 0d);   
                validateAllocated(8, -1);
                address = UnsafeAdapter.reallocateMemory(address, 8*2);
                UnsafeAdapter.putDouble(address + 8, nextValue);
                Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.getDouble(address), 0d);
                Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.getDouble(address), 0d);    
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.getDouble(address + 8), 0d);
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.getDouble(address + 8), 0d);
                validateAllocated(8*2, -1);                
            } finally {
                if(address!=-1) UnsafeAdapter.freeMemory(address);
                rawCount = rawAllocations.decrementAndGet();
                validateDeallocated("testReallocatedDouble", 0, -1);
            }
        }

        
	
}
