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

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.heliosapm.unsafe.AllocationPointer;
import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: AutoDeallocationTest</p>
 * <p>Description: Tests for automated deallocation. We need mem tracking turned on to validate that allocations are cleared</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.AutoDeallocationTest</code></p>
 */
@UnsafeAdapterConfiguration(memTracking=true)
@SuppressWarnings("restriction")
public class AutoDeallocationTest extends BaseTest {
	
	@Test
//	@Ignore
	public void testAllocationPointer() throws Exception {
		AllocationPointer ap = new AllocationPointer();
		try {
			final int valueCount = Math.abs(RANDOM.nextInt(100)) + 100;
			final long[] writeValues = new long[valueCount];
			for(short i = 0; i < writeValues.length; i++) {
				writeValues[i] = nextPosLong();
				final long address = UnsafeAdapter.allocateMemory(8, ap);
				UnsafeAdapter.putLong(address, writeValues[i]);
			}
			for(short i = 0; i < writeValues.length; i++) {
				final long readValue = UnsafeAdapter.getLong(ap.getAddress(i));
				Assert.assertEquals("Incorrect value at index [" + i + "] ", writeValues[i], readValue);
			}
			final long expectedAllocation = ((long)valueCount << 3);
			validateAllocated(expectedAllocation, -1, valueCount);
			ap = null;
			System.gc();
			sleep(500);
			validateDeallocated(0, -1L);
		} finally {
			if(ap!=null) ap.free();
		}
	}
	
	/**
	 * Tests an auto de-allocated long memory allocation 
	 * @throws Exception thrown on any error
	 */	
	@Test	
	public void testAutoClearedAllocatedLong() throws Exception {
		DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
		final long address = UnsafeAdapter.allocateMemory(8, dealloc);
		long value = nextPosLong();
		UnsafeAdapter.putLong(address, value);
		Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getLong(address));
		Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getLong(address));
		validateAllocated(8, -1);
		log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
		dealloc = null;
		System.gc();
		sleep(100);
		log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
		validateDeallocated(0, -1);		
	}


    /**
     * Tests an auto de-allocated boolean memory allocation 
     * @throws Exception thrown on any error
     */    
    @Test    
    public void testAutoClearedAllocatedBoolean() throws Exception {
        DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
        final long address = UnsafeAdapter.allocateMemory(1, dealloc);
        boolean value = nextBoolean();
        UnsafeAdapter.putBoolean(address, value);
        Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getBoolean(address));
        Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getBoolean(null, address));
        validateAllocated(1, -1);
        log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
        dealloc = null;
        System.gc();
        sleep(100);
        log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
        validateDeallocated(0, -1);        
    }



    /**
     * Tests an auto de-allocated byte memory allocation 
     * @throws Exception thrown on any error
     */
    
    @Test    
    public void testAutoClearedAllocatedByte() throws Exception {
        DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
        final long address = UnsafeAdapter.allocateMemory(1, dealloc);
        byte value = nextPosByte();
        UnsafeAdapter.putByte(address, value);
        Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getByte(address));
        Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getByte(address));
        validateAllocated(1, -1);
        log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
        dealloc = null;
        System.gc();
        sleep(100);
        log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
        validateDeallocated(0, -1);        
    }



    /**
     * Tests an auto de-allocated char memory allocation 
     * @throws Exception thrown on any error
     */
    
    @Test    
    public void testAutoClearedAllocatedCharacter() throws Exception {
        DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
        final long address = UnsafeAdapter.allocateMemory(2, dealloc);
        char value = nextCharacter();
        UnsafeAdapter.putChar(address, value);
        Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getChar(address));
        Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getChar(address));
        validateAllocated(2, -1);
        log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
        dealloc = null;
        System.gc();
        sleep(100);
        log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
        validateDeallocated(0, -1);        
    }



    /**
     * Tests an auto de-allocated short memory allocation 
     * @throws Exception thrown on any error
     */
    
    @Test    
    public void testAutoClearedAllocatedShort() throws Exception {
        DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
        final long address = UnsafeAdapter.allocateMemory(2, dealloc);
        short value = nextPosShort();
        UnsafeAdapter.putShort(address, value);
        Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getShort(address));
        Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getShort(address));
        validateAllocated(2, -1);
        log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
        dealloc = null;
        System.gc();
        sleep(100);
        log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
        validateDeallocated(0, -1);        
    }



    /**
     * Tests an auto de-allocated int memory allocation 
     * @throws Exception thrown on any error
     */
    
    @Test    
    public void testAutoClearedAllocatedInteger() throws Exception {
        DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
        final long address = UnsafeAdapter.allocateMemory(4, dealloc);
        int value = nextPosInteger();
        UnsafeAdapter.putInt(address, value);
        Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getInt(address));
        Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getInt(address));
        validateAllocated(4, -1);
        log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
        dealloc = null;
        System.gc();
        sleep(100);
        log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
        validateDeallocated(0, -1);        
    }



    /**
     * Tests an auto de-allocated float memory allocation 
     * @throws Exception thrown on any error
     */
    
    @Test    
    public void testAutoClearedAllocatedFloat() throws Exception {
        DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
        final long address = UnsafeAdapter.allocateMemory(4, dealloc);
        float value = nextPosFloat();
        UnsafeAdapter.putFloat(address, value);
        Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getFloat(address), 0f);
        Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getFloat(address), 0f);
        validateAllocated(4, -1);
        log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
        dealloc = null;
        System.gc();
        sleep(100);
        log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
        validateDeallocated(0, -1);        
    }



    /**
     * Tests an auto de-allocated double memory allocation 
     * @throws Exception thrown on any error
     */
    
    @Test    
    public void testAutoClearedAllocatedDouble() throws Exception {
        DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
        final long address = UnsafeAdapter.allocateMemory(4, dealloc);
        double value = nextPosDouble();
        UnsafeAdapter.putDouble(address, value);
        Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getDouble(address), 0d);
        Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getDouble(address), 0d);
        validateAllocated(4, -1);
        log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
        dealloc = null;
        System.gc();
        sleep(100);
        log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
        validateDeallocated(0, -1);        
    }


	
}
