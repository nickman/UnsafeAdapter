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
package gnu.trove.impl.hash;

import java.lang.reflect.Field;

import sun.misc.Unsafe;
import gnu.trove.impl.Constants;
import gnu.trove.impl.HashFunctions;

/**
 * <p>Title: UnsafeTPrimitiveHash</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>gnu.trove.impl.hash.UnsafeTPrimitiveHash</code></p>
 */

public abstract class UnsafeTPrimitiveHash extends THash {
	@SuppressWarnings( { "UnusedDeclaration" } )
	static final long serialVersionUID = 1L;
	
	/** A reference to the Unsafe */		
	protected static final Unsafe unsafe;
	
	/** A zero byte const */
	public static final byte ZERO_BYTE = 0;

	
	static {
		try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to access sun.misc.Unsafe.theUnsafe", ex);
		}		
	}
	

    /**
     * flags indicating whether each position in the hash is
     * FREE, FULL, or REMOVED
     */
    protected long[][] stateAddresses;
    //public transient byte[] _states;

    /* constants used for state flags */

    /** flag indicating that a slot in the hashtable is available */
    public static final byte FREE = 0;

    /** flag indicating that a slot in the hashtable is occupied */
    public static final byte FULL = 1;

    /**
     * flag indicating that the value of a slot in the hashtable
     * was deleted
     */
    public static final byte REMOVED = 2;


    /**
     * Creates a new <code>THash</code> instance with the default
     * capacity and load factor.
     */
    public UnsafeTPrimitiveHash() {
        super();
    }
    
    public void free() {    	
    	if(stateAddresses!=null && stateAddresses[0][0]>0) {
    		unsafe.freeMemory(stateAddresses[0][0]);
    		stateAddresses=null;
    	}
    }
    


    /**
     * Creates a new <code>TPrimitiveHash</code> instance with a prime
     * capacity at or near the specified capacity and with the default
     * load factor.
     *
     * @param initialCapacity an <code>int</code> value
     */
    public UnsafeTPrimitiveHash( int initialCapacity ) {
        this( initialCapacity, Constants.DEFAULT_LOAD_FACTOR );
    }


    /**
     * Creates a new <code>TPrimitiveHash</code> instance with a prime
     * capacity at or near the minimum needed to hold
     * <tt>initialCapacity<tt> elements with load factor
     * <tt>loadFactor</tt> without triggering a rehash.
     *
     * @param initialCapacity an <code>int</code> value
     * @param loadFactor      a <code>float</code> value
     */
    public UnsafeTPrimitiveHash( int initialCapacity, float loadFactor ) {
        this();
		initialCapacity = Math.max( 1, initialCapacity );
        _loadFactor = loadFactor;
        setUp( HashFunctions.fastCeil( initialCapacity / loadFactor ) );
    }

    protected void rehash( int newCapacity ) {
    	stateAddresses[0][0] = unsafe.reallocateMemory(stateAddresses[0][0], 4 + (newCapacity));
    	unsafe.putInt(stateAddresses[0][0], newCapacity);
    }
    
    public final void Arraysfill(final long address, final byte value) {
    	final int len = unsafe.getInt(address);
    	for(int index = 0; index < len; index++) {
    		unsafe.putLong(address + 4 + (index), value);
    	}
    }
    

    /**
     * Returns the capacity of the hash table.  This is the true
     * physical capacity, without adjusting for the load factor.
     *
     * @return the physical capacity of the hash table.
     */
    public int capacity() {
    	return unsafe.getInt(stateAddresses[0][0]);
        //return _states.length;
    }
    
    /**
     * Returns the state at the specified index
     * @param index The index to read the state from
     * @return the read state
     */
    public byte stateAt(int index) {
    	return unsafe.getByte(stateAddresses[0][0] + 4 + index);
    }
    
    /**
     * Sets the state at the specified index
     * @param index The index to write the state to
     * @param value The value to write
     */
    public void stateAt(int index, byte value) {
    	unsafe.putByte(stateAddresses[0][0] + 4 + index, value);
    }


    /**
     * Delete the record at <tt>index</tt>.
     *
     * @param index an <code>int</code> value
     */
    protected void removeAt( int index ) {
    	unsafe.putByte(stateAddresses[0][0] + index, REMOVED);  
        //_states[index] = REMOVED;
        super.removeAt( index );
    }

    
    
    /**
     * initializes the hashtable to a prime capacity which is at least
     * <tt>initialCapacity + 1</tt>.
     *
     * @param initialCapacity an <code>int</code> value
     * @return the actual capacity chosen
     */
    protected int setUp( int initialCapacity ) {
    	stateAddresses = new long[1][1];
    	int capacity;
        capacity = super.setUp( initialCapacity );        
        stateAddresses[0][0] = unsafe.allocateMemory(4 + capacity);
        unsafe.putInt(stateAddresses[0][0], capacity);
        unsafe.setMemory(stateAddresses[0][0]+4, capacity, ZERO_BYTE);
        //_states = new byte[capacity];
        return capacity;
    }

}
