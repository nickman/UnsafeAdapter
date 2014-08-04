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

import gnu.trove.iterator.TPrimitiveIterator;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/**
 * <p>Title: UnsafeTHashPrimitiveIterator</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>gnu.trove.impl.hash.UnsafeTHashPrimitiveIterator</code></p>
 */

public abstract class UnsafeTHashPrimitiveIterator implements TPrimitiveIterator {

    /** the data structure this iterator traverses */
    protected final UnsafeTPrimitiveHash _hash;
    /**
     * the number of elements this iterator believes are in the
     * data structure it accesses.
     */
    protected int _expectedSize;
    /** the index used for iteration. */
    protected int _index;


    /**
     * Creates a <tt>TPrimitiveIterator</tt> for the specified collection.
     *
     * @param hash the <tt>TPrimitiveHash</tt> we want to iterate over.
     */
    public UnsafeTHashPrimitiveIterator( UnsafeTPrimitiveHash hash ) {
        _hash = hash;
        _expectedSize = _hash.size();
        _index = _hash.capacity();
    }


    /**
     * Returns the index of the next value in the data structure
     * or a negative value if the iterator is exhausted.
     *
     * @return an <code>int</code> value
     * @throws java.util.ConcurrentModificationException
     *          if the underlying collection's
     *          size has been modified since the iterator was created.
     */
    protected final int nextIndex() {
        if ( _expectedSize != _hash.size() ) {
            throw new ConcurrentModificationException();
        }

//        byte[] states = _hash._states;
        int i = _index;
        while ( i-- > 0 && ( _hash.stateAt(i) != TPrimitiveHash.FULL ) ) {
            ;
        }
        return i;
    }


    /**
     * Returns true if the iterator can be advanced past its current
     * location.
     *
     * @return a <code>boolean</code> value
     */
    public boolean hasNext() {
        return nextIndex() >= 0;
    }


    /**
     * Removes the last entry returned by the iterator.
     * Invoking this method more than once for a single entry
     * will leave the underlying data structure in a confused
     * state.
     */
    public void remove() {
        if (_expectedSize != _hash.size()) {
            throw new ConcurrentModificationException();
        }

        // Disable auto compaction during the remove. This is a workaround for bug 1642768.
        try {
            _hash.tempDisableAutoCompaction();
            _hash.removeAt(_index);
        }
        finally {
            _hash.reenableAutoCompaction( false );
        }

        _expectedSize--;
    }


    /**
     * Sets the internal <tt>index</tt> so that the `next' object
     * can be returned.
     */
    protected final void moveToNextIndex() {
        // doing the assignment && < 0 in one line shaves
        // 3 opcodes...
        if ( ( _index = nextIndex() ) < 0 ) {
            throw new NoSuchElementException();
        }
    }


} // TPrimitiveIterator