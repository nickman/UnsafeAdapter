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

import gnu.trove.procedure.*;
import gnu.trove.impl.HashFunctions;

import java.io.ObjectOutput;
import java.io.ObjectInput;
import java.io.IOException;

/**
 * <p>Title: UnsafeUnsafeTLongLongHash</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>gnu.trove.impl.hash.UnsafeUnsafeTLongLongHash</code></p>
 */

abstract public class UnsafeTLongLongHash extends UnsafeTPrimitiveHash {
	static final long serialVersionUID = 1L;

    /** the set of longs */
    protected long[][] keyAddresses;
    // public transient long[] _set;



    /**
     * key that represents null
     *
     * NOTE: should not be modified after the Hash is created, but is
     *       not final because of Externalization
     *
     */
    protected long no_entry_key;


    /**
     * value that represents null
     *
     * NOTE: should not be modified after the Hash is created, but is
     *       not final because of Externalization
     *
     */
    protected long no_entry_value;

    protected boolean consumeFreeSlot;

    /**
     * Creates a new <code>T#E#Hash</code> instance with the default
     * capacity and load factor.
     */
    public UnsafeTLongLongHash() {
        super();
        no_entry_key = ( long ) 0;
        no_entry_value = ( long ) 0;
    }


    /**
     * Creates a new <code>T#E#Hash</code> instance whose capacity
     * is the next highest prime above <tt>initialCapacity + 1</tt>
     * unless that value is already prime.
     *
     * @param initialCapacity an <code>int</code> value
     */
    public UnsafeTLongLongHash( int initialCapacity ) {
        super( initialCapacity );
        no_entry_key = ( long ) 0;
        no_entry_value = ( long ) 0;
    }


    /**
     * Creates a new <code>UnsafeTLongLongHash</code> instance with a prime
     * value at or near the specified capacity and load factor.
     *
     * @param initialCapacity used to find a prime capacity for the table.
     * @param loadFactor used to calculate the threshold over which
     * rehashing takes place.
     */
    public UnsafeTLongLongHash( int initialCapacity, float loadFactor ) {
        super(initialCapacity, loadFactor);
        no_entry_key = ( long ) 0;
        no_entry_value = ( long ) 0;
    }


    /**
     * Creates a new <code>UnsafeTLongLongHash</code> instance with a prime
     * value at or near the specified capacity and load factor.
     *
     * @param initialCapacity used to find a prime capacity for the table.
     * @param loadFactor used to calculate the threshold over which
     * rehashing takes place.
     * @param no_entry_value value that represents null
     */
    public UnsafeTLongLongHash( int initialCapacity, float loadFactor,
        long no_entry_key, long no_entry_value ) {
        super(initialCapacity, loadFactor);
        this.no_entry_key = no_entry_key;
        this.no_entry_value = no_entry_value;
    }

    public void free() {
    	super.free();
    	if(keyAddresses!=null && keyAddresses[0][0]>0) {
    		unsafe.freeMemory(keyAddresses[0][0]);
    		keyAddresses=null;
    	}
    }
    
    
    /**
     * Returns the long at the specified index
     * @param index The index to get the long at
     * @return the read long
     */
    public long keyAt(int index) {
    	return unsafe.getLong(keyAddresses[0][0] + 4 + (index << 3));
    }
    
    /**
     * Sets the long at the specified index
     * @param index The index to set the long at
     * @param value The value to set
     */
    public void keyAt(int index, long value) {
    	unsafe.putLong(keyAddresses[0][0] + 4 + (index << 3), value);
    }
    

    /**
     * Returns the value that is used to represent null as a key. The default
     * value is generally zero, but can be changed during construction
     * of the collection.
     *
     * @return the value that represents null
     */
    public long getNoEntryKey() {
        return no_entry_key;
    }


    /**
     * Returns the value that is used to represent null. The default
     * value is generally zero, but can be changed during construction
     * of the collection.
     *
     * @return the value that represents null
     */
    public long getNoEntryValue() {
        return no_entry_value;
    }


    /**
     * initializes the hashtable to a prime capacity which is at least
     * <tt>initialCapacity + 1</tt>.
     *
     * @param initialCapacity an <code>int</code> value
     * @return the actual capacity chosen
     */
    protected int setUp( int initialCapacity ) {
    	keyAddresses = new long[1][1];
    	int capacity;        
        capacity = super.setUp( initialCapacity );        
        keyAddresses[0][0] = unsafe.allocateMemory(4 + (capacity << 3));
        unsafe.putInt(keyAddresses[0][0], capacity);
        unsafe.setMemory(keyAddresses[0][0], capacity << 3, ZERO_BYTE);
        //_set = new long[capacity];
        return capacity;
    }


    /**
     * Searches the set for <tt>val</tt>
     *
     * @param val an <code>long</code> value
     * @return a <code>boolean</code> value
     */
    public boolean contains( long val ) {
        return index(val) >= 0;
    }

    
//    protected void rehash( int newCapacity ) {
//    	System.out.println("Rehashing UnsafeTLongLongHash");
//    	super.rehash(newCapacity);
//    	try {
//    		keyAddresses[0][0] = unsafe.reallocateMemory(keyAddresses[0][0], 4 + (newCapacity << 3));
//    	} catch (IllegalArgumentException iae) {
//    		throw new Error("Failed to reallocate for new capacity [" + newCapacity + "] requiring memory of [" + (4 + (newCapacity << 3)) + "]");
//    	}
//    	unsafe.putInt(keyAddresses[0][0], newCapacity);
//    }
    
    public final void Arraysfill(final long address, final long value) {
    	final int len = unsafe.getInt(address);
    	for(int index = 0; index < len; index++) {
    		unsafe.putLong(address + 4 + (index << 3), value);
    	}
    }
    

    /**
     * Executes <tt>procedure</tt> for each key in the map.
     *
     * @param procedure a <code>TLongProcedure</code> value
     * @return false if the loop over the set terminated because
     * the procedure returned false for some value.
     */
    public boolean forEach( TLongProcedure procedure ) {
//        byte[] states = _states;
//        long[] set = _set;
//        for ( int i = set.length; i-- > 0; ) {
//            if ( states[i] == FULL && ! procedure.execute( set[i] ) ) {
//                return false;
//            }
//        }
    	int len = super.capacity();
    	for ( int i = len; i-- > 0; ) {
    		if ( stateAt(i) == FULL && ! procedure.execute( keyAt(i) ) ) {
    			return false;
    		}
    	}
        return true;
    }
    

    /**
     * Releases the element currently stored at <tt>index</tt>.
     *
     * @param index an <code>int</code> value
     */
    protected void removeAt( int index ) {
        keyAt(index, no_entry_key);
        super.removeAt( index );
    }

    public int length() {
    	return unsafe.getInt(keyAddresses[0][0]);
    }

    /**
     * Locates the index of <tt>val</tt>.
     *
     * @param key an <code>long</code> value
     * @return the index of <tt>val</tt> or -1 if it isn't in the set.
     */
    protected int index( long key ) {
        int hash, probe, index, length;

//        final byte[] states = _states;
//        final long[] set = _set;
        length = super.capacity();
        hash = HashFunctions.hash( key ) & 0x7fffffff;
        index = hash % length;
        byte state = stateAt(index);

        if (state == FREE)
            return -1;

        if (state == FULL && keyAt(index) == key)
            return index;

        return indexRehashed(key, index, hash, state);
    }

    int indexRehashed(long key, int index, int hash, byte state) {
        // see Knuth, p. 529
//        int length = _set.length;
    	int length = length();
        int probe = 1 + (hash % (length - 2));
        final int loopIndex = index;

        do {
            index -= probe;
            if (index < 0) {
                index += length;
            }
//            state = _states[index];
            state = stateAt(index);
            //
            if (state == FREE)
                return -1;

            //
//            if (key == _set[index] && state != REMOVED)
              if (key == keyAt(index) && state != REMOVED)
                return index;
        } while (index != loopIndex);

        return -1;
    }


    /**
     * Locates the index at which <tt>val</tt> can be inserted.  if
     * there is already a value equal()ing <tt>val</tt> in the set,
     * returns that value as a negative integer.
     *
     * @param key an <code>long</code> value
     * @return an <code>int</code> value
     */
         protected int insertKey( long val ) {
             int hash, index;

             hash = HashFunctions.hash(val) & 0x7fffffff;
//             index = hash % _states.length;
//             byte state = _states[index];
           index = hash % super.capacity();
           byte state = stateAt(index);

             consumeFreeSlot = false;

             if (state == FREE) {
                 consumeFreeSlot = true;
                 insertKeyAt(index, val);

                 return index;       // empty, all done
             }

//             if (state == FULL && _set[index] == val) {
             if (state == FULL && keyAt(index) == val) {             
                 return -index - 1;   // already stored
             }

             // already FULL or REMOVED, must probe
             return insertKeyRehash(val, index, hash, state);
         }

         int insertKeyRehash(long val, int index, int hash, byte state) {
             // compute the double hash
//             final int length = _set.length;
        	 final int length = unsafe.getInt(keyAddresses[0][0]);
             int probe = 1 + (hash % (length - 2));
             final int loopIndex = index;
             int firstRemoved = -1;

             /**
              * Look until FREE slot or we start to loop
              */
             do {
                 // Identify first removed slot
                 if (state == REMOVED && firstRemoved == -1)
                     firstRemoved = index;

                 index -= probe;
                 if (index < 0) {
                     index += length;
                 }
//                 state = _states[index];
                 state = stateAt(index);

                 // A FREE slot stops the search
                 if (state == FREE) {
                     if (firstRemoved != -1) {
                         insertKeyAt(firstRemoved, val);
                         return firstRemoved;
                     } 
                     consumeFreeSlot = true;
                     insertKeyAt(index, val);
                     return index;
                 }

                 if (state == FULL && stateAt(index) == val) {
                     return -index - 1;
                 }

                 // Detect loop
             } while (index != loopIndex);

             // We inspected all reachable slots and did not find a FREE one
             // If we found a REMOVED slot we return the first one found
             if (firstRemoved != -1) {
                 insertKeyAt(firstRemoved, val);
                 return firstRemoved;
             }

             // Can a resizing strategy be found that resizes the set?
             throw new IllegalStateException("No free or removed slots available. Key set full?!!");
         }
         

         void insertKeyAt(int index, long val) {
//             _set[index] = val;  // insert value
//             _states[index] = FULL;
           keyAt(index, val);  // insert value
           stateAt(index, FULL);
        	 
         }

    protected int XinsertKey( long key ) {
        int hash, probe, index, length;

//        final byte[] states = _states;
//        final long[] set = _set;
//        length = states.length;
        length = super.capacity();
        hash = HashFunctions.hash( key ) & 0x7fffffff;
        index = hash % length;
//        byte state = states[index];
        byte state = stateAt(index);

        consumeFreeSlot = false;

        if ( state == FREE ) {
            consumeFreeSlot = true;
//            set[index] = key;
//            states[index] = FULL;
          keyAt(index, key);
          stateAt(index, FULL);

            return index;       // empty, all done
//        } else if ( state == FULL && set[index] == key ) {
        } else if ( state == FULL && keyAt(index) == key ) {            
            return -index -1;   // already stored
        } else {                // already FULL or REMOVED, must probe
            // compute the double hash
            probe = 1 + ( hash % ( length - 2 ) );

            // if the slot we landed on is FULL (but not removed), probe
            // until we find an empty slot, a REMOVED slot, or an element
            // equal to the one we are trying to insert.
            // finding an empty slot means that the value is not present
            // and that we should use that slot as the insertion point;
            // finding a REMOVED slot means that we need to keep searching,
            // however we want to remember the offset of that REMOVED slot
            // so we can reuse it in case a "new" insertion (i.e. not an update)
            // is possible.
            // finding a matching value means that we've found that our desired
            // key is already in the table

            if ( state != REMOVED ) {
				// starting at the natural offset, probe until we find an
				// offset that isn't full.
				do {
					index -= probe;
					if (index < 0) {
						index += length;
					}
					state = stateAt(index);
				} while ( state == FULL && keyAt(index) != key );
            }

            // if the index we found was removed: continue probing until we
            // locate a free location or an element which equal()s the
            // one we have.
            if ( state == REMOVED) {
                int firstRemoved = index;
//                while ( state != FREE && ( state == REMOVED || set[index] != key ) ) {
                while ( state != FREE && ( state == REMOVED || keyAt(index) != key ) ) {
                    index -= probe;
                    if (index < 0) {
                        index += length;
                    }
//                    state = states[index];
                    state = stateAt(index);
                }

                if (state == FULL) {
                    return -index -1;
                } else {
//                    set[index] = key;
//                    states[index] = FULL;
                  keyAt(index, key);
                  stateAt(index, FULL);

                    return firstRemoved;
                }
            }
            // if it's full, the key is already stored
            if (state == FULL) {
                return -index -1;
            } else {
                consumeFreeSlot = true;
//                set[index] = key;
//                states[index] = FULL;
                keyAt(index, key);
                stateAt(index, FULL);

                return index;
            }
        }
    }


    /** {@inheritDoc} */
    public void writeExternal( ObjectOutput out ) throws IOException {
        // VERSION
    	out.writeByte( 0 );

        // SUPER
    	super.writeExternal( out );

    	// NO_ENTRY_KEY
    	out.writeLong( no_entry_key );

    	// NO_ENTRY_VALUE
    	out.writeLong( no_entry_value );
    }


    /** {@inheritDoc} */
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException {
        // VERSION
    	in.readByte();

        // SUPER
    	super.readExternal( in );

    	// NO_ENTRY_KEY
    	no_entry_key = in.readLong();

    	// NO_ENTRY_VALUE
    	no_entry_value = in.readLong();
    }
} // UnsafeTLongLongHash
