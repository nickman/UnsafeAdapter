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
package gnu.trove.map.hash;

import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.UnsafeTLongLongHashMap.TKeyView;
import gnu.trove.map.hash.UnsafeTLongLongHashMap.TValueView;
import gnu.trove.function.TLongFunction;
import gnu.trove.procedure.*;
import gnu.trove.set.*;
import gnu.trove.iterator.*;
import gnu.trove.iterator.hash.*;
import gnu.trove.impl.hash.*;
import gnu.trove.impl.HashFunctions;
import gnu.trove.*;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

import com.heliosapm.unsafe.UnsafeAdapter;

import sun.misc.Unsafe;

/**
 * <p>Title: UnsafeTLongLongHashMap</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>gnu.trove.map.hash.UnsafeTLongLongHashMap</code></p>
 */

public class UnsafeTLongLongHashMap extends UnsafeTLongLongHash implements TLongLongMap, Externalizable {
    static final long serialVersionUID = 1L;

    /** the values of the map */
    protected long[][] addresses;
//    protected transient long[] _values;
    
    
    public static void main(String[] args) {
    	log("Test UnsafeTLongLongHashMap");
    	
    	
    	final int sz = 4214397;
    	UnsafeTLongLongHashMap map = new UnsafeTLongLongHashMap(sz/3, 0.75f, -1L, -1L);
    	for(int i = 0; i < sz; i++) {
    		map.put(i, i);
    	}
    	for(int i = 0; i < sz; i++) {
    		long x = map.get(i);
    		if(x!=i) {
    			log("Err. Got [%s] but expected [%s]", x, i);
    		}
    	}
    	
    	log("Done: %s", map.size());
    }
    
    public static void log(Object fmt, Object...args) {
    	System.out.println(String.format(fmt.toString(), args));
    }


    /**
     * Creates a new <code>UnsafeTLongLongHashMap</code> instance with the default
     * capacity and load factor.
     */
    public UnsafeTLongLongHashMap() {
        super();
    }


    /**
     * Creates a new <code>UnsafeTLongLongHashMap</code> instance with a prime
     * capacity equal to or greater than <tt>initialCapacity</tt> and
     * with the default load factor.
     *
     * @param initialCapacity an <code>int</code> value
     */
    public UnsafeTLongLongHashMap( int initialCapacity ) {
        super( initialCapacity );
    }


    /**
     * Creates a new <code>UnsafeTLongLongHashMap</code> instance with a prime
     * capacity equal to or greater than <tt>initialCapacity</tt> and
     * with the specified load factor.
     *
     * @param initialCapacity an <code>int</code> value
     * @param loadFactor a <code>float</code> value
     */
    public UnsafeTLongLongHashMap( int initialCapacity, float loadFactor ) {
        super( initialCapacity, loadFactor );
    }


    /**
     * Creates a new <code>UnsafeTLongLongHashMap</code> instance with a prime
     * capacity equal to or greater than <tt>initialCapacity</tt> and
     * with the specified load factor.
     *
     * @param initialCapacity an <code>int</code> value
     * @param loadFactor a <code>float</code> value
     * @param noEntryKey a <code>long</code> value that represents
     *                   <tt>null</tt> for the Key set.
     * @param noEntryValue a <code>long</code> value that represents
     *                   <tt>null</tt> for the Value set.
     */
    public UnsafeTLongLongHashMap( int initialCapacity, float loadFactor,
        long noEntryKey, long noEntryValue ) {
        super( initialCapacity, loadFactor, noEntryKey, noEntryValue );
    }


    /**
     * Creates a new <code>UnsafeTLongLongHashMap</code> instance containing
     * all of the entries in the map passed in.
     *
     * @param keys a <tt>long</tt> array containing the keys for the matching values.
     * @param values a <tt>long</tt> array containing the values.
     */
    public UnsafeTLongLongHashMap( long[] keys, long[] values ) {
        super( Math.max( keys.length, values.length ) );

        int size = Math.min( keys.length, values.length );
        for ( int i = 0; i < size; i++ ) {
            this.put( keyAt(i), valueAt(i) );
        }
    }


    /**
     * Creates a new <code>UnsafeTLongLongHashMap</code> instance containing
     * all of the entries in the map passed in.
     *
     * @param map a <tt>TLongLongMap</tt> that will be duplicated.
     */
    public UnsafeTLongLongHashMap( TLongLongMap map ) {
        super( map.size() );
        if ( map instanceof UnsafeTLongLongHashMap ) {
            UnsafeTLongLongHashMap hashmap = ( UnsafeTLongLongHashMap ) map;
            this._loadFactor = hashmap._loadFactor;
            this.no_entry_key = hashmap.no_entry_key;
            this.no_entry_value = hashmap.no_entry_value;
            //noinspection RedundantCast
            if ( this.no_entry_key != ( long ) 0 ) {
//                Arrays.fill( _set, this.no_entry_key );
            	Arraysfill( keyAddresses[0][0], this.no_entry_key );
            }
            //noinspection RedundantCast
            if ( this.no_entry_value != ( long ) 0 ) {
//                Arrays.fill( _values, this.no_entry_value );
            	Arraysfill( addresses[0][0], this.no_entry_key );
            }
            setUp( (int) Math.ceil( DEFAULT_CAPACITY / _loadFactor ) );
        }
        putAll( map );
    }
    
    public void free() {
    	super.free();
    	if(addresses!=null && addresses[0][0]>0) {
    		unsafe.freeMemory(addresses[0][0]);
    		addresses=null;
    	}
    }
    
    
    /**
     * Returns the long at the specified index
     * @param index The index to get the long at
     * @return the read long
     */
    public long valueAt(int index) {
    	return unsafe.getLong(addresses[0][0] + 4 + (index << 3));
    }
    
    /**
     * Sets the long at the specified index
     * @param index The index to set the long at
     * @param value The value to set
     * @return the set value
     */
    public long valueAt(int index, long value) {
    	unsafe.putLong(addresses[0][0] + 4 + (index << 3), value);
    	return value;
    }
    


    /**
     * initializes the hashtable to a prime capacity which is at least
     * <tt>initialCapacity + 1</tt>.
     *
     * @param initialCapacity an <code>int</code> value
     * @return the actual capacity chosen
     */
    protected int setUp( int initialCapacity ) {
    	addresses = new long[1][1];
    	int capacity;

        capacity = super.setUp( initialCapacity );
//        _values = new long[capacity];
        addresses[0][0] = unsafe.allocateMemory(4 + (capacity << 3));
        unsafe.putInt(addresses[0][0], initialCapacity);
        return capacity;
    }

    protected int rehashes = 0;
    
    /**
     * rehashes the map to the new capacity.
     *
     * @param newCapacity an <code>int</code> value
     */
     /** {@inheritDoc} */
    @SuppressWarnings("restriction")
	protected void rehash( int newCapacity ) {
    	rehashes++;
    	System.out.println("Rehashing: #" + rehashes);
//        int oldCapacity = _set.length;
//        
//        long oldKeys[] = _set;
//        long oldVals[] = _values;
//        byte oldStates[] = _states;
//
//        _set = new long[newCapacity];
//        _values = new long[newCapacity];
//        _states = new byte[newCapacity];
//
//        for ( int i = oldCapacity; i-- > 0; ) {
//            if( oldStates[i] == FULL ) {
//                long o = oldKeys[i];
//                int index = insertKey( o );
//                _values[index] = oldVals[i];
//            }
//        }
    	
    	final int oldCapacity = unsafe.getInt(addresses[0][0]);
    	final long newLongSpaceOffset = (oldCapacity << 3) + 4;
    	final long newLongSpaceSize = (newCapacity - oldCapacity) << 3; 

    	final long newByteSpaceOffset = (oldCapacity) + 4;
    	final long newByteSpaceSize = (newCapacity - oldCapacity); 

    	
    	addresses[0][0] = unsafe.reallocateMemory(addresses[0][0], 4 + (newCapacity << 3));
    	keyAddresses[0][0] = unsafe.reallocateMemory(keyAddresses[0][0], 4 + (newCapacity << 3));
    	stateAddresses[0][0] = unsafe.reallocateMemory(stateAddresses[0][0], 4 + newCapacity);
    	
    	
    	unsafe.putInt(addresses[0][0], newCapacity);
    	unsafe.putInt(keyAddresses[0][0], newCapacity);
    	unsafe.putInt(stateAddresses[0][0], newCapacity);
    	
    	unsafe.setMemory(addresses[0][0] + newLongSpaceOffset, newLongSpaceSize, ZERO_BYTE);
    	unsafe.setMemory(keyAddresses[0][0] + newLongSpaceOffset, newLongSpaceSize, ZERO_BYTE);
    	unsafe.setMemory(stateAddresses[0][0] + newByteSpaceOffset, newByteSpaceSize, ZERO_BYTE);
    	System.out.println("Sizes  [ values: " +  unsafe.getInt(addresses[0][0]) + ", keys: " + unsafe.getInt(keyAddresses[0][0]) + ", state: " + unsafe.getInt(stateAddresses[0][0]) + "]");
    }


    /** {@inheritDoc} */
    public long put( long key, long value ) {
        int index = insertKey( key );
        return doPut( key, value, index );
    }


    /** {@inheritDoc} */
    public long putIfAbsent( long key, long value ) {
        int index = insertKey( key );
        if (index < 0)
            return valueAt(-index - 1);
        return doPut( key, value, index );
    }


    private long doPut( long key, long value, int index ) {
        long previous = no_entry_value;
        boolean isNewMapping = true;
        if ( index < 0 ) {
            index = -index -1;
            previous = valueAt(index);
            isNewMapping = false;
        }
        valueAt(index,  value);

        if (isNewMapping) {
            postInsertHook( consumeFreeSlot );
        }

        return previous;
    }


    /** {@inheritDoc} */
    public void putAll( Map<? extends Long, ? extends Long> map ) {
        ensureCapacity( map.size() );
        // could optimize this for cases when map instanceof THashMap
        for ( Map.Entry<? extends Long, ? extends Long> entry : map.entrySet() ) {
            this.put( entry.getKey().longValue(), entry.getValue().longValue() );
        }
    }
    

    /** {@inheritDoc} */
    public void putAll( TLongLongMap map ) {
        ensureCapacity( map.size() );
        TLongLongIterator iter = map.iterator();
        while ( iter.hasNext() ) {
            iter.advance();
            this.put( iter.key(), iter.value() );
        }
    }


    /** {@inheritDoc} */
    public long get( long key ) {
        int index = index( key );
        return index < 0 ? no_entry_value : valueAt(index);
    }


    /** {@inheritDoc} */
    public void clear() {
        super.clear();
//        Arrays.fill( _set, 0, _set.length, no_entry_key );
//        Arrays.fill( _values, 0, _values.length, no_entry_value );
//        Arrays.fill( _states, 0, _states.length, FREE );
	      Arraysfill( keyAddresses[0][0], no_entry_key );
	      Arraysfill( addresses[0][0], no_entry_value );
	      Arraysfill( stateAddresses[0][0], FREE );
        
    }


    /** {@inheritDoc} */
    public boolean isEmpty() {
        return 0 == _size;
    }


    /** {@inheritDoc} */
    public long remove( long key ) {
        long prev = no_entry_value;
        int index = index( key );
        if ( index >= 0 ) {
            prev = valueAt(index);
            removeAt( index );    // clear key,state; adjust size
        }
        return prev;
    }


    /** {@inheritDoc} */
    protected void removeAt( int index ) {
        valueAt(index,  no_entry_value);
        super.removeAt( index );  // clear key, state; adjust size
    }


    /** {@inheritDoc} */
    public TLongSet keySet() {
        return new TKeyView();
    }


    /** {@inheritDoc} */
    public long[] keys() {
//        long[] keys = new long[size()];
//        long[] k = _set;
//        byte[] states = _states;
//
//        for ( int i = k.length, j = 0; i-- > 0; ) {
//          if ( stateAt(i) == FULL ) {
//            keys[j++] = k[i];
//          }
//        }
    	final int sz = size();
    	final long[] keys = new long[sz];
    	unsafe.copyMemory(keyAddresses[0][0] + 4, UnsafeAdapter.getAddressOf(keys) + UnsafeAdapter.LONG_ARRAY_OFFSET,  sz << 3);
        return keys;
    }


    /** {@inheritDoc} */
    public long[] keys( long[] array ) {
//        int size = size();
//        if ( array.length < size ) {
//            array = new long[size];
//        }
//
//        long[] keys = _set;
//        byte[] states = _states;
//
//        for ( int i = keys.length, j = 0; i-- > 0; ) {
//          if ( stateAt(i) == FULL ) {
//            array[j++] = keyAt(i);
//          }
//        }
//        return array;
    	final int sz = size();
    	final long[] keys = (array!=null && array.length >= sz) ? array : new long[sz];
    	unsafe.copyMemory(keyAddresses[0][0] + 4, UnsafeAdapter.getAddressOf(keys) + UnsafeAdapter.LONG_ARRAY_OFFSET,  sz << 3);
        return keys;
    	
    }


    /** {@inheritDoc} */
    public TLongCollection valueCollection() {
        return new TValueView();
    }


    /** {@inheritDoc} */
    public long[] values() {
//        long[] vals = new long[size()];
//        long[] v = _values;
//        byte[] states = _states;
//
//        for ( int i = v.length, j = 0; i-- > 0; ) {
//          if ( stateAt(i) == FULL ) {
//            vals[j++] = v[i];
//          }
//        }
//        return vals;
    	final int sz = size();
    	final long[] values = new long[sz];
    	unsafe.copyMemory(addresses[0][0] + 4, UnsafeAdapter.getAddressOf(values) + UnsafeAdapter.LONG_ARRAY_OFFSET,  sz << 3);
        return values;
    	
    }


    /** {@inheritDoc} */
    public long[] values( long[] array ) {
//        int size = size();
//        if ( array.length < size ) {
//            array = new long[size];
//        }
//
//        long[] v = _values;
//        byte[] states = _states;
//
//        for ( int i = v.length, j = 0; i-- > 0; ) {
//          if ( stateAt(i) == FULL ) {
//            array[j++] = v[i];
//          }
//        }
//        return array;
    	final int sz = size();
    	final long[] values = (array!=null && array.length >= sz) ? array : new long[sz];
    	unsafe.copyMemory(addresses[0][0] + 4, UnsafeAdapter.getAddressOf(values) + UnsafeAdapter.LONG_ARRAY_OFFSET,  sz << 3);
        return values;
    	
    }


    /** {@inheritDoc} */
    public boolean containsValue( long val ) {
//        byte[] states = _states;
//        long[] vals = _values;

//        for ( int i = vals.length; i-- > 0; ) {
//            if ( stateAt(i) == FULL && val == vals[i] ) {
//                return true;
//            }
//        }
      for ( int i = unsafe.getInt(addresses[0][0]); i-- > 0; ) {
    	  if ( stateAt(i) == FULL && val == valueAt(i) ) {
    		  return true;
    	  }
      }
    	    	
        return false;
    }


    /** {@inheritDoc} */
    public boolean containsKey( long key ) {
        return contains( key );
    }


    /** {@inheritDoc} */
    public TLongLongIterator iterator() {
        return new UnsafeTLongLongHashIterator( this );
    }


    /** {@inheritDoc} */
    public boolean forEachKey( TLongProcedure procedure ) {
        return forEach( procedure );
    }


    /** {@inheritDoc} */
    public boolean forEachValue( TLongProcedure procedure ) {
//        byte[] states = _states;
//        long[] values = _values;
//        for ( int i = values.length; i-- > 0; ) {
//            if ( stateAt(i) == FULL && ! procedure.execute( valueAt(i) ) ) {
//                return false;
//            }
//        }
    	final int len = unsafe.getInt(addresses[0][0]);
        for ( int i = len; i-- > 0; ) {
            if ( stateAt(i) == FULL && ! procedure.execute( valueAt(i) ) ) {
                return false;
            }
        }
    	
        return true;
    }


    /** {@inheritDoc} */
    public boolean forEachEntry( TLongLongProcedure procedure ) {
//        byte[] states = _states;
//        long[] keys = _set;
//        long[] values = _values;
//        for ( int i = keys.length; i-- > 0; ) {
//            if ( stateAt(i) == FULL && ! procedure.execute( keyAt(i), valueAt(i) ) ) {
//                return false;
//            }
//        }
    	int len = unsafe.getInt(keyAddresses[0][0]);
    	for ( int i = len; i-- > 0; ) {
            if ( stateAt(i) == FULL && ! procedure.execute( keyAt(i), valueAt(i) ) ) {
                return false;
            }
        }
    	
        return true;
    }


    /** {@inheritDoc} */
    public void transformValues( TLongFunction function ) {
//        byte[] states = _states;
//        long[] values = _values;
//        for ( int i = values.length; i-- > 0; ) {
//            if ( stateAt(i) == FULL ) {
//                valueAt(i) = function.execute( valueAt(i) );
//            }
//        }
    	int len = unsafe.getInt(addresses[0][0]);
    	for ( int i = len; i-- > 0; ) {
            if ( stateAt(i) == FULL ) {
                valueAt(i, function.execute( valueAt(i)));
            }
        }
    	
    }


    /** {@inheritDoc} */
    public boolean retainEntries( TLongLongProcedure procedure ) {
        boolean modified = false;
//        byte[] states = _states;
//        long[] keys = _set;
//        long[] values = _values;


        // Temporarily disable compaction. This is a fix for bug #1738760
        tempDisableAutoCompaction();
        try {
        	final int len = unsafe.getInt(keyAddresses[0][0]);
            for ( int i = len; i-- > 0; ) {
                if ( stateAt(i) == FULL && ! procedure.execute( keyAt(i), valueAt(i) ) ) {
                    removeAt( i );
                    modified = true;
                }
            }
        }
        finally {
            reenableAutoCompaction( true );
        }

        return modified;
    }


    /** {@inheritDoc} */
    public boolean increment( long key ) {
        return adjustValue( key, ( long ) 1 );
    }


    /** {@inheritDoc} */
    public boolean adjustValue( long key, long amount ) {
        int index = index( key );
        if (index < 0) {
            return false;
        } else {
            valueAt(index, valueAt(index) + amount);
            return true;
        }
    }


    /** {@inheritDoc} */
    public long adjustOrPutValue( long key, long adjust_amount, long put_amount ) {
        int index = insertKey( key );
        final boolean isNewMapping;
        final long newValue;
        if ( index < 0 ) {
            index = -index -1;
            newValue = valueAt(index, valueAt(index) + adjust_amount);
            isNewMapping = false;
        } else {
            newValue = valueAt(index,  put_amount);
            isNewMapping = true;
        }

        byte previousState = stateAt(index);

        if ( isNewMapping ) {
            postInsertHook(consumeFreeSlot);
        }

        return newValue;
    }


    /** a view onto the keys of the map. */
    protected class TKeyView implements TLongSet {

        /** {@inheritDoc} */
        public TLongIterator iterator() {
            return new UnsafeTLongLongKeyHashIterator( UnsafeTLongLongHashMap.this );
        }


        /** {@inheritDoc} */
        public long getNoEntryValue() {
            return no_entry_key;
        }


        /** {@inheritDoc} */
        public int size() {
            return _size;
        }


        /** {@inheritDoc} */
        public boolean isEmpty() {
            return 0 == _size;
        }


        /** {@inheritDoc} */
        public boolean contains( long entry ) {
            return UnsafeTLongLongHashMap.this.contains( entry );
        }


        /** {@inheritDoc} */
        public long[] toArray() {
            return UnsafeTLongLongHashMap.this.keys();
        }


        /** {@inheritDoc} */
        public long[] toArray( long[] dest ) {
            return UnsafeTLongLongHashMap.this.keys( dest );
        }


        /**
         * Unsupported when operating upon a Key Set view of a TLongLongMap
         * <p/>
         * {@inheritDoc}
         */
        public boolean add( long entry ) {
            throw new UnsupportedOperationException();
        }


        /** {@inheritDoc} */
        public boolean remove( long entry ) {
            return no_entry_value != UnsafeTLongLongHashMap.this.remove( entry );
        }


        /** {@inheritDoc} */
        public boolean containsAll( Collection<?> collection ) {
            for ( Object element : collection ) {
                if ( element instanceof Long ) {
                    long ele = ( ( Long ) element ).longValue();
                    if ( ! UnsafeTLongLongHashMap.this.containsKey( ele ) ) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        }


        /** {@inheritDoc} */
        public boolean containsAll( TLongCollection collection ) {
            TLongIterator iter = collection.iterator();
            while ( iter.hasNext() ) {
                if ( ! UnsafeTLongLongHashMap.this.containsKey( iter.next() ) ) {
                    return false;
                }
            }
            return true;
        }


        /** {@inheritDoc} */
        public boolean containsAll( long[] array ) {
            for ( long element : array ) {
                if ( ! UnsafeTLongLongHashMap.this.contains( element ) ) {
                    return false;
                }
            }
            return true;
        }


        /**
         * Unsupported when operating upon a Key Set view of a TLongLongMap
         * <p/>
         * {@inheritDoc}
         */
        public boolean addAll( Collection<? extends Long> collection ) {
            throw new UnsupportedOperationException();
        }


        /**
         * Unsupported when operating upon a Key Set view of a TLongLongMap
         * <p/>
         * {@inheritDoc}
         */
        public boolean addAll( TLongCollection collection ) {
            throw new UnsupportedOperationException();
        }


        /**
         * Unsupported when operating upon a Key Set view of a TLongLongMap
         * <p/>
         * {@inheritDoc}
         */
        public boolean addAll( long[] array ) {
            throw new UnsupportedOperationException();
        }


        /** {@inheritDoc} */
        @SuppressWarnings({"SuspiciousMethodCalls"})
        public boolean retainAll( Collection<?> collection ) {
            boolean modified = false;
            TLongIterator iter = iterator();
            while ( iter.hasNext() ) {
                if ( ! collection.contains( Long.valueOf ( iter.next() ) ) ) {
                    iter.remove();
                    modified = true;
                }
            }
            return modified;
        }


        /** {@inheritDoc} */
        public boolean retainAll( TLongCollection collection ) {
            if ( this == collection ) {
                return false;
            }
            boolean modified = false;
            TLongIterator iter = iterator();
            while ( iter.hasNext() ) {
                if ( ! collection.contains( iter.next() ) ) {
                    iter.remove();
                    modified = true;
                }
            }
            return modified;
        }


        /** {@inheritDoc} */
        public boolean retainAll( long[] array ) {
            boolean changed = false;
            Arrays.sort( array );
//            long[] set = _set;
//            byte[] states = _states;

//            for ( int i = set.length; i-- > 0; ) {
//                if ( stateAt(i) == FULL && ( Arrays.binarySearch( array, set[i] ) < 0) ) {
//                    removeAt( i );
//                    changed = true;
//                }
//            }
            final int len = unsafe.getInt(keyAddresses[0][0]);
            for ( int i = len; i-- > 0; ) {
                if ( stateAt(i) == FULL && ( Arrays.binarySearch( array, keyAt(i) ) < 0) ) {
                    removeAt( i );
                    changed = true;
                }
            }
            
            return changed;
        }


        /** {@inheritDoc} */
        public boolean removeAll( Collection<?> collection ) {
            boolean changed = false;
            for ( Object element : collection ) {
                if ( element instanceof Long ) {
                    long c = ( ( Long ) element ).longValue();
                    if ( remove( c ) ) {
                        changed = true;
                    }
                }
            }
            return changed;
        }


        /** {@inheritDoc} */
        public boolean removeAll( TLongCollection collection ) {
            if ( this == collection ) {
                clear();
                return true;
            }
            boolean changed = false;
            TLongIterator iter = collection.iterator();
            while ( iter.hasNext() ) {
                long element = iter.next();
                if ( remove( element ) ) {
                    changed = true;
                }
            }
            return changed;
        }


        /** {@inheritDoc} */
        public boolean removeAll( long[] array ) {
            boolean changed = false;
            for ( int i = array.length; i-- > 0; ) {
                if ( remove( array[i] ) ) {
                    changed = true;
                }
            }
            return changed;
        }


        /** {@inheritDoc} */
        public void clear() {
            UnsafeTLongLongHashMap.this.clear();
        }


        /** {@inheritDoc} */
        public boolean forEach( TLongProcedure procedure ) {
            return UnsafeTLongLongHashMap.this.forEachKey( procedure );
        }


        @Override
        public boolean equals( Object other ) {
            if (! (other instanceof TLongSet)) {
                return false;
            }
            final TLongSet that = ( TLongSet ) other;
            if ( that.size() != this.size() ) {
                return false;
            }
            final int len = unsafe.getInt(stateAddresses[0][0]);
            for ( int i = len; i-- > 0; ) {
                if ( stateAt(i) == FULL ) {
                    if ( ! that.contains( keyAt(i) ) ) {
                        return false;
                    }
                }
            }
            return true;
        }


        @Override
        public int hashCode() {
            int hashcode = 0;
            final int len = unsafe.getInt(stateAddresses[0][0]);
            for ( int i = len; i-- > 0; ) {
                if ( stateAt(i) == FULL ) {
                    hashcode += HashFunctions.hash( keyAt(i) );
                }
            }
            return hashcode;
        }


        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder( "{" );
            forEachKey( new TLongProcedure() {
                private boolean first = true;


                public boolean execute( long key ) {
                    if ( first ) {
                        first = false;
                    } else {
                        buf.append( ", " );
                    }

                    buf.append( key );
                    return true;
                }
            } );
            buf.append( "}" );
            return buf.toString();
        }
    }


    /** a view onto the values of the map. */
    protected class TValueView implements TLongCollection {

        /** {@inheritDoc} */
        public TLongIterator iterator() {
            return new UnsafeTLongLongValueHashIterator( UnsafeTLongLongHashMap.this );
        }


        /** {@inheritDoc} */
        public long getNoEntryValue() {
            return no_entry_value;
        }


        /** {@inheritDoc} */
        public int size() {
            return _size;
        }


        /** {@inheritDoc} */
        public boolean isEmpty() {
            return 0 == _size;
        }


        /** {@inheritDoc} */
        public boolean contains( long entry ) {
            return UnsafeTLongLongHashMap.this.containsValue( entry );
        }


        /** {@inheritDoc} */
        public long[] toArray() {
            return UnsafeTLongLongHashMap.this.values();
        }


        /** {@inheritDoc} */
        public long[] toArray( long[] dest ) {
            return UnsafeTLongLongHashMap.this.values( dest );
        }



        public boolean add( long entry ) {
            throw new UnsupportedOperationException();
        }


        /** {@inheritDoc} */
        public boolean remove( long entry ) {
//            long[] values = _values;
//            long[] set = _set;

        	final int len = unsafe.getInt(addresses[0][0]);
            for ( int i = len; i-- > 0; ) {
                if ( ( keyAt(i) != FREE && keyAt(i) != REMOVED ) && entry == valueAt(i) ) {
                    removeAt( i );
                    return true;
                }
            }
            return false;
        }


        /** {@inheritDoc} */
        public boolean containsAll( Collection<?> collection ) {
            for ( Object element : collection ) {
                if ( element instanceof Long ) {
                    long ele = ( ( Long ) element ).longValue();
                    if ( ! UnsafeTLongLongHashMap.this.containsValue( ele ) ) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        }


        /** {@inheritDoc} */
        public boolean containsAll( TLongCollection collection ) {
            TLongIterator iter = collection.iterator();
            while ( iter.hasNext() ) {
                if ( ! UnsafeTLongLongHashMap.this.containsValue( iter.next() ) ) {
                    return false;
                }
            }
            return true;
        }


        /** {@inheritDoc} */
        public boolean containsAll( long[] array ) {
            for ( long element : array ) {
                if ( ! UnsafeTLongLongHashMap.this.containsValue( element ) ) {
                    return false;
                }
            }
            return true;
        }


        /** {@inheritDoc} */
        public boolean addAll( Collection<? extends Long> collection ) {
            throw new UnsupportedOperationException();
        }


        /** {@inheritDoc} */
        public boolean addAll( TLongCollection collection ) {
            throw new UnsupportedOperationException();
        }


        /** {@inheritDoc} */
        public boolean addAll( long[] array ) {
            throw new UnsupportedOperationException();
        }


        /** {@inheritDoc} */
        @SuppressWarnings({"SuspiciousMethodCalls"})
        public boolean retainAll( Collection<?> collection ) {
            boolean modified = false;
            TLongIterator iter = iterator();
            while ( iter.hasNext() ) {
                if ( ! collection.contains( Long.valueOf ( iter.next() ) ) ) {
                    iter.remove();
                    modified = true;
                }
            }
            return modified;
        }


        /** {@inheritDoc} */
        public boolean retainAll( TLongCollection collection ) {
            if ( this == collection ) {
                return false;
            }
            boolean modified = false;
            TLongIterator iter = iterator();
            while ( iter.hasNext() ) {
                if ( ! collection.contains( iter.next() ) ) {
                    iter.remove();
                    modified = true;
                }
            }
            return modified;
        }


        /** {@inheritDoc} */
        public boolean retainAll( long[] array ) {
            boolean changed = false;
            Arrays.sort( array );
//            long[] values = _values;
//            byte[] states = _states;
            final int len = unsafe.getInt(addresses[0][0]);
            for ( int i = len; i-- > 0; ) {
                if ( stateAt(i) == FULL && ( Arrays.binarySearch( array, valueAt(i) ) < 0) ) {
                    removeAt( i );
                    changed = true;
                }
            }
            return changed;
        }


        /** {@inheritDoc} */
        public boolean removeAll( Collection<?> collection ) {
            boolean changed = false;
            for ( Object element : collection ) {
                if ( element instanceof Long ) {
                    long c = ( ( Long ) element ).longValue();
                    if ( remove( c ) ) {
                        changed = true;
                    }
                }
            }
            return changed;
        }


        /** {@inheritDoc} */
        public boolean removeAll( TLongCollection collection ) {
            if ( this == collection ) {
                clear();
                return true;
            }
            boolean changed = false;
            TLongIterator iter = collection.iterator();
            while ( iter.hasNext() ) {
                long element = iter.next();
                if ( remove( element ) ) {
                    changed = true;
                }
            }
            return changed;
        }


        /** {@inheritDoc} */
        public boolean removeAll( long[] array ) {
            boolean changed = false;
            for ( int i = array.length; i-- > 0; ) {
                if ( remove( array[i] ) ) {
                    changed = true;
                }
            }
            return changed;
        }


        /** {@inheritDoc} */
        public void clear() {
            UnsafeTLongLongHashMap.this.clear();
        }


        /** {@inheritDoc} */
        public boolean forEach( TLongProcedure procedure ) {
            return UnsafeTLongLongHashMap.this.forEachValue( procedure );
        }


        /** {@inheritDoc} */
        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder( "{" );
            forEachValue( new TLongProcedure() {
                private boolean first = true;

                public boolean execute( long value ) {
                    if ( first ) {
                        first = false;
                    } else {
                        buf.append( ", " );
                    }

                    buf.append( value );
                    return true;
                }
            } );
            buf.append( "}" );
            return buf.toString();
        }
    }


    class UnsafeTLongLongKeyHashIterator extends UnsafeTHashPrimitiveIterator implements TLongIterator {

        /**
         * Creates an iterator over the specified map
         *
         * @param hash the <tt>TPrimitiveHash</tt> we will be iterating over.
         */
    	UnsafeTLongLongKeyHashIterator( UnsafeTPrimitiveHash hash ) {
            super( hash );
        }

        /** {@inheritDoc} */
        public long next() {
            moveToNextIndex();
            return _hash.stateAt(_index);
        }

        /** @{inheritDoc} */
        public void remove() {
            if ( _expectedSize != _hash.size() ) {
                throw new ConcurrentModificationException();
            }

            // Disable auto compaction during the remove. This is a workaround for bug 1642768.
            try {
                _hash.tempDisableAutoCompaction();
                UnsafeTLongLongHashMap.this.removeAt( _index );
            }
            finally {
                _hash.reenableAutoCompaction( false );
            }

            _expectedSize--;
        }
    }


   
    class UnsafeTLongLongValueHashIterator extends UnsafeTHashPrimitiveIterator implements TLongIterator {

        /**
         * Creates an iterator over the specified map
         *
         * @param hash the <tt>TPrimitiveHash</tt> we will be iterating over.
         */
        UnsafeTLongLongValueHashIterator( UnsafeTPrimitiveHash hash ) {
            super( hash );
        }

        /** {@inheritDoc} */
        public long next() {
            moveToNextIndex();
            return _hash.stateAt(_index);
        }

        /** @{inheritDoc} */
        public void remove() {
            if ( _expectedSize != _hash.size() ) {
                throw new ConcurrentModificationException();
            }

            // Disable auto compaction during the remove. This is a workaround for bug 1642768.
            try {
                _hash.tempDisableAutoCompaction();
                UnsafeTLongLongHashMap.this.removeAt( _index );
            }
            finally {
                _hash.reenableAutoCompaction( false );
            }

            _expectedSize--;
        }
    }


    class UnsafeTLongLongHashIterator extends UnsafeTHashPrimitiveIterator implements TLongLongIterator {

        /**
         * Creates an iterator over the specified map
         *
         * @param map the <tt>UnsafeTLongLongHashMap</tt> we will be iterating over.
         */
    	UnsafeTLongLongHashIterator( UnsafeTLongLongHashMap map ) {
            super( map );
        }

        /** {@inheritDoc} */
        public void advance() {
            moveToNextIndex();
        }

        /** {@inheritDoc} */
        public long key() {        	
            return keyAt(_index);
        }

        /** {@inheritDoc} */
        public long value() {
            return valueAt(_index);
        }

        /** {@inheritDoc} */
        public long setValue( long val ) {
            long old = value();
            valueAt(_index, val);
            return old;
        }

        /** @{inheritDoc} */
        public void remove() {
            if ( _expectedSize != _hash.size() ) {
                throw new ConcurrentModificationException();
            }
            // Disable auto compaction during the remove. This is a workaround for bug 1642768.
            try {
                _hash.tempDisableAutoCompaction();
                UnsafeTLongLongHashMap.this.removeAt( _index );
            }
            finally {
                _hash.reenableAutoCompaction( false );
            }
            _expectedSize--;
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals( Object other ) {
        if ( ! ( other instanceof TLongLongMap ) ) {
            return false;
        }
        TLongLongMap that = ( TLongLongMap ) other;
        if ( that.size() != this.size() ) {
            return false;
        }
//        long[] values = _values;
//        byte[] states = _states;
        long this_no_entry_value = getNoEntryValue();
        long that_no_entry_value = that.getNoEntryValue();
        final int len = unsafe.getInt(addresses[0][0]);
        for ( int i = len; i-- > 0; ) {
            if ( stateAt(i) == FULL ) {
                long key = keyAt(i);
                long that_value = that.get( key );
                long this_value = valueAt(i);
                if ( ( this_value != that_value ) &&
                     ( this_value != this_no_entry_value ) &&
                     ( that_value != that_no_entry_value ) ) {
                    return false;
                }
            }
        }
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int hashcode = 0;
//        byte[] states = _states;
        final int len = unsafe.getInt(addresses[0][0]);
        for ( int i = len; i-- > 0; ) {
            if ( stateAt(i) == FULL ) {
                hashcode += HashFunctions.hash( keyAt(i) ) ^
                            HashFunctions.hash( valueAt(i) );
            }
        }
        return hashcode;
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder( "{" );
        forEachEntry( new TLongLongProcedure() {
            private boolean first = true;
            public boolean execute( long key, long value ) {
                if ( first ) first = false;
                else buf.append( ", " );

                buf.append(key);
                buf.append("=");
                buf.append(value);
                return true;
            }
        });
        buf.append( "}" );
        return buf.toString();
    }


    /** {@inheritDoc} */
    public void writeExternal(ObjectOutput out) throws IOException {
        // VERSION
    	out.writeByte( 0 );

        // SUPER
    	super.writeExternal( out );

    	// NUMBER OF ENTRIES
    	out.writeInt( _size );

    	// ENTRIES
    	final int len = unsafe.getInt(stateAddresses[0][0]);
    	for ( int i = len; i-- > 0; ) {
            if ( stateAt(i) == FULL ) {
                out.writeLong( keyAt(i) );
                out.writeLong( valueAt(i) );
            }
        }
    }


    /** {@inheritDoc} */
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // VERSION
    	in.readByte();

        // SUPER
    	super.readExternal( in );

    	// NUMBER OF ENTRIES
    	int size = in.readInt();
    	setUp( size );

    	// ENTRIES
        while (size-- > 0) {
            long key = in.readLong();
            long val = in.readLong();
            put(key, val);
        }
    }
} // UnsafeTLongLongHashMap
