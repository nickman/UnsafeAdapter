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
package test.com.heliosapm.unsafe.ap;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import test.com.heliosapm.unsafe.BaseTest;
import test.com.heliosapm.unsafe.UnsafeAdapterConfiguration;
import test.com.heliosapm.unsafe.UnsafeAdapterConfigurator;

import com.heliosapm.unsafe.AllocationPointer;
import com.heliosapm.unsafe.AllocationPointerOperations;
import com.heliosapm.unsafe.AllocationReferenceManager;
import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: BasicAllocationPointerTest</p>
 * <p>Description: Test cases for {@link AllocationPointer} with no mem tracking or alignment overhead</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.ap.BasicAllocationPointerTest</code></p>
 */
@SuppressWarnings("restriction")
public class BasicAllocationPointerTest extends BaseTest {

	
	/** Indicates if allocated memory size tracking is enabled */
	protected boolean MEM_TRACKING = false;
	/** Indicates if cache-line memory alignment overhead tracking is enabled */
	protected boolean AL_TRACKING = false;
	/** The expected length of the AllocationPointer base address based on the config */
	protected int baseAddrLength = -1;
	/** Provides instances of AllocationPointers */
	protected AllocationReferenceManager refMgr = null;
	
	/** Indicates if this instance has been initialized */
	protected final AtomicBoolean inited = new AtomicBoolean(false);
	
	
	/**
	 * Initializes the config and creates a new AllocationReferenceManager in accordance.
	 */
	@Before
	public void beforeTest() {
		if(inited.compareAndSet(false, true)) {
			MEM_TRACKING = UnsafeAdapter.isMemTrackingEnabled();
			AL_TRACKING = UnsafeAdapter.isAlignmentTrackingEnabled();
			baseAddrLength = getBaseAddressExpectedLength();
			refMgr = new AllocationReferenceManager(MEM_TRACKING, AL_TRACKING);
			log("Config:\n\tMem Tracking: [%s]\n\tMem Alignment: [%s]", MEM_TRACKING, AL_TRACKING);
			UnsafeAdapterConfiguration uac = UnsafeAdapterConfigurator.getClassConfiguration(getClass());
			if(uac.memTracking()){
				Assert.assertTrue("Memory size tracking was not enabled", MEM_TRACKING);
			} else {
				Assert.assertFalse("Memory size tracking was enabled", MEM_TRACKING);
			}			
		}
	}
	
	/**
	 * Gets the expected length of an AllocationPoint base address array
	 * @return the expected length of an AllocationPoint base address array
	 */
	public int getBaseAddressExpectedLength() {
		int l = 1;
		if(MEM_TRACKING) {
			l++;
			if(AL_TRACKING) l++;
		}
		return l;
	}
	
	/**
	 * Tests the basic characteristics of a newly created AllocationPointer
	 */
	@Test
	public void testBasicAllocationSize() {
		AllocationPointer ap = refMgr.newAllocationPointer();
		try {
			final long rootAddress = ap.getAddressBase();
			final byte dim = ap.getDimension();			
			log("Root Address: [%s], Deep Byte Size: [%s], Dimension: [%s]", rootAddress, AllocationPointerOperations.getDeepByteSize(rootAddress), dim);
			Assert.assertEquals("AllocationPointer Dimension", getBaseAddressExpectedLength(), dim);
			int capacity = AllocationPointerOperations.getCapacity(rootAddress);
			int size = AllocationPointerOperations.getSize(rootAddress);
			Assert.assertEquals("AllocationPointer Capacity", AllocationPointerOperations.ALLOC_SIZE, capacity);
			Assert.assertEquals("AllocationPointer Size", 0, size);
			capacity = ap.getCapacity();
			size = ap.getSize();
			Assert.assertEquals("AllocationPointer Capacity", AllocationPointerOperations.ALLOC_SIZE, capacity);
			Assert.assertEquals("AllocationPointer Size", 0, size);
			
			
			int expectedSize = AllocationPointerOperations.HEADER_SIZE + (AllocationPointerOperations.ADDRESS_SIZE * AllocationPointerOperations.ALLOC_SIZE); 
			
			
			Assert.assertEquals("AllocationPointer Local Byte Size", expectedSize, AllocationPointerOperations.getEndOffset(rootAddress));
			long expectedDeepSize = (dim * expectedSize) + (dim * AllocationPointerOperations.ADDRESS_SIZE);
					
					
					
//					(AllocationPointerOperations.HEADER_SIZE * dim) +	// the header size X the number of headers 
//					(AllocationPointerOperations.ADDRESS_SIZE * AllocationPointerOperations.ALLOC_SIZE * dim) +  // the size of an address X the number of allocated slots X the number of slot arrays allocated 
//					//AllocationPointerOperations.ADDRESS_SIZE;		// the root address size
//					(AllocationPointerOperations.ADDRESS_SIZE * dim);
			
			log("Byte Size Estimates:\n\tOne Dim Expected: %s\n\tOne Dim API: %s\n\tExpected DBS: %s\n\tAPI DBS:%s\n\tManaged: %s\n", expectedSize, AllocationPointerOperations.getEndOffset(rootAddress), expectedDeepSize, AllocationPointerOperations.getDeepByteSize(rootAddress), AllocationPointerOperations.getTotalAllocatedMemory());
			
			
			Assert.assertEquals("AllocationPointer Deep Byte Size", expectedDeepSize, AllocationPointerOperations.getDeepByteSize(rootAddress));
			validateAPAllocated(expectedDeepSize, dim + 1);
		} finally {
			ap.free();
			validateAPAllocated(0, 0);
		}
	}
	
	/**
	 * Tests AllocationPointer set address and clear
	 */	
	@Test
	public void testAllocationWriteRead() {
		AllocationPointer ap = refMgr.newAllocationPointer();
		long[] managedAddresses = new long[AllocationPointerOperations.ALLOC_SIZE];
		try {
			Assert.assertEquals("AllocationPointer Capacity", AllocationPointerOperations.ALLOC_SIZE, ap.getCapacity());
			Assert.assertEquals("AllocationPointer Size", 0, ap.getSize());
			long[][] testData = new long[AllocationPointerOperations.ALLOC_SIZE][3];			
			for(int outer = 0; outer < AllocationPointerOperations.ALLOC_SIZE; outer++) {
				testData[outer][0] = testUnsafe.allocateMemory(1);
				managedAddresses[outer] = testData[outer][0]; 
				for(int inner = 1; inner < 3; inner++) {
					testData[outer][inner] = nextPosInt();
				}
				ap.assignSlot(testData[outer][0], testData[outer][1], testData[outer][2]);
				Assert.assertEquals("AllocationPointer Size", outer+1, ap.getSize());
			}
			for(int outer = 0; outer < AllocationPointerOperations.ALLOC_SIZE; outer++) {
				Assert.assertEquals("Address at index " + outer, testData[outer][0] , ap.getAddress(outer));
				if(AllocationPointerOperations.MANAGED_ALLOC) {
					Assert.assertEquals("Size at index " + outer, testData[outer][1],  ap.getAllocationSize(outer));
//					Assert.assertEquals("Overhead at index " + outer, testData[outer][2], ap.getAlignmentOverhead(outer));
				} else {
					Assert.assertEquals("Size at index " + outer, 0,  ap.getAllocationSize(outer));
//					Assert.assertEquals("Overhead at index " + outer, 0, ap.getAlignmentOverhead(outer));					
				}
			}
		} finally {
			log("Will free Managed Addresses %s", Arrays.toString(managedAddresses));
			ap.free();
			validateAPAllocated(0, 0);
		}
	}
	
	
	/**
	 * Tests AllocationPointer incrementing size extension
	 */
	@Test
	public void testIncrementingAllocationSize() {
		AllocationPointer ap = refMgr.newAllocationPointer();
		try {
			/* No Op */
		} finally {
			ap.free();
			validateAPAllocated(0, 0);
		}
	}
	
	
	
	
	/**
	 * Validates that mem tracking reports correct values after memory is allocated via the AllocationPointerOperations when tracking is turned on, 
	 * or that disabled mem tracking is reporting the disabled values.
	 * @param mem The expected value when mem tracking is enabled
	 * @param count The number of expected allocations
	 */
	public void validateAPAllocated(long mem, int count) {
		if(AllocationPointerOperations.MANAGED_ALLOC) {
			Assert.assertEquals("Mem Total Alloc was unexpected. --> ", mem, AllocationPointerOperations.getTotalAllocatedMemory());
			Assert.assertEquals("Mem Total Allocation Count was unexpected. --> ", count, AllocationPointerOperations.getTotalAllocationCount());
		} else {
			Assert.assertEquals("Mem Total Alloc was unexpected. --> ", -1, AllocationPointerOperations.getTotalAllocationCount());
			Assert.assertEquals("Mem Total Allocation Count was unexpected. --> ", -1, AllocationPointerOperations.getTotalAllocationCount());
		}
	}
	

}
