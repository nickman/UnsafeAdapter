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



import org.junit.Assert;
import org.junit.Test;

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


	/**
	 * Tests a long allocation, write, read and deallocation
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testAllocatedLong() throws Exception {
		final long address = UnsafeAdapter.allocateMemory(8);
		try {
			long value = nextPosLong();
			UnsafeAdapter.putLong(address, value);
			Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getLong(address));
			Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getLong(address));	
			value = nextPosLong();
			UnsafeAdapter.putLongVolatile(address, value);
			Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getLong(address));
			Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getLong(address));	
			validateAllocated(8, -1);			
		} finally {
			UnsafeAdapter.freeMemory(address);
			validateDeallocated(0, -1);
		}
	}


	
	
	    /**
	     * Tests a int allocation, write, read and deallocation
	     * @throws Exception thrown on any error
	     */
	    
	    @Test
	    public void testAllocatedInteger() throws Exception {
	        final long address = UnsafeAdapter.allocateMemory(4);
	        try {
	            int value = nextPosInteger();
	            UnsafeAdapter.putInt(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getInt(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getInt(address));    
	            value = nextPosInteger();
	            UnsafeAdapter.putIntVolatile(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getInt(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getInt(address));  
	            validateAllocated(4, -1);	            
	        } finally {
	            UnsafeAdapter.freeMemory(address);
	            validateDeallocated(0, -1);
	        }
	    }
	    

	    /**
	     * Tests a float allocation, write, read and deallocation
	     * @throws Exception thrown on any error
	     */
	    
	    @Test
	    public void testAllocatedFloat() throws Exception {
	        final long address = UnsafeAdapter.allocateMemory(4);
	        try {
	            float value = nextPosFloat();
	            UnsafeAdapter.putFloat(address, value);
	            
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getFloat(address), 0f);
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getFloat(address), 0f);    
	            value = nextPosFloat();
	            UnsafeAdapter.putFloatVolatile(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getFloat(address), 0f);
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getFloat(address), 0f);
	            validateAllocated(4, -1);	            
	        } finally {
	            UnsafeAdapter.freeMemory(address);
	            validateDeallocated(0, -1);	            
	        }
	    }


	    /**
	     * Tests a double allocation, write, read and deallocation
	     * @throws Exception thrown on any error
	     */
	    
	    @Test
	    public void testAllocatedDouble() throws Exception {
	        final long address = UnsafeAdapter.allocateMemory(8);
	        try {
	            double value = nextPosDouble();
	            UnsafeAdapter.putDouble(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getDouble(address), 0d);
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getDouble(address), 0d);    
	            value = nextPosDouble();
	            UnsafeAdapter.putDoubleVolatile(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getDouble(address), 0d);
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getDouble(address), 0d);    
	            validateAllocated(8, -1);
	        } finally {
	            UnsafeAdapter.freeMemory(address);
	            validateDeallocated(0, -1);	            
	        }
	    }


	    /**
	     * Tests a short allocation, write, read and deallocation
	     * @throws Exception thrown on any error
	     */
	    
	    @Test
	    public void testAllocatedShort() throws Exception {
	        final long address = UnsafeAdapter.allocateMemory(2);
	        try {
	            short value = nextPosShort();
	            UnsafeAdapter.putShort(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getShort(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getShort(address));    
	            value = nextPosShort();
	            UnsafeAdapter.putShortVolatile(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getShort(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getShort(address));   
	            validateAllocated(2, -1);	            
	        } finally {
	            UnsafeAdapter.freeMemory(address);
	            validateDeallocated(0, -1);
	        }
	    }


	    /**
	     * Tests a byte allocation, write, read and deallocation
	     * @throws Exception thrown on any error
	     */	    
	    @Test
	    public void testAllocatedByte() throws Exception {
	        final long address = UnsafeAdapter.allocateMemory(1);
	        try {
	            byte value = nextPosByte();
	            UnsafeAdapter.putByte(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getByte(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getByte(address));    
	            value = nextPosByte();
	            UnsafeAdapter.putByteVolatile(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getByte(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getByte(address));    
	            validateAllocated(1, -1);	            
	        } finally {
	            UnsafeAdapter.freeMemory(address);
	            validateDeallocated(0, -1);
	        }
	    }
	    

	    /**
	     * Tests a boolean allocation, write, read and deallocation
	     * @throws Exception thrown on any error
	     */
	    
	    @Test
	    public void testAllocatedBoolean() throws Exception {
	        final long address = UnsafeAdapter.allocateMemory(1);
	        try {
	            boolean value = nextBoolean();
	            UnsafeAdapter.putBoolean(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getBoolean(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getBoolean(null, address));    
	            value = nextBoolean();
	            UnsafeAdapter.putBooleanVolatile(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getBoolean(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getBoolean(null, address));   
	            validateAllocated(1, -1);	
	        } finally {
	            UnsafeAdapter.freeMemory(address);
	            validateDeallocated(0, -1);
	        }
	    }


	    /**
	     * Tests a char allocation, write, read and deallocation
	     * @throws Exception thrown on any error
	     */
	    
	    @Test
	    public void testAllocatedCharacter() throws Exception {
	        final long address = UnsafeAdapter.allocateMemory(2);
	        try {
	            char value = nextCharacter();
	            UnsafeAdapter.putChar(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getChar(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getChar(address));    
	            value = nextCharacter();
	            UnsafeAdapter.putCharVolatile(address, value);
	            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getChar(address));
	            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getChar(address));    
	            validateAllocated(2, -1);		            
	        } finally {
	            UnsafeAdapter.freeMemory(address);
	            validateDeallocated(0, -1);
	        }
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
				validateDeallocated(0, -1);
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
                validateDeallocated(0, -1);
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
                validateDeallocated(0, -1);
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
                validateDeallocated(0, -1);
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
                validateDeallocated(0, -1);
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
                validateDeallocated(0, -1);
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
                validateDeallocated(0, -1);
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
                validateDeallocated(0, -1);
            }
        }

        
	
}
