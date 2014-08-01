package com.heliosapm.unsafe;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
	 * <p>Title: MemSpinLock</p>
	 * <p>Description: Disk based spin lock that is sharable with other processes</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.UnsafeAdapter.DiskSpinLock</code></p>
	 * FIXME: INCOMPLETE
	 */

	public class DiskSpinLock implements SpinLock, DeAllocateMe {
		/** The lock file name */
		protected final File diskFile;
		/** The mapped lock file address */
		protected final long[] address = new long[1];
		/**
		 * Creates a new MemSpinLock
		 * @param address The address of the lock
		 */
		private DiskSpinLock() {
			try {
				diskFile = File.createTempFile("DiskSpinLock", ".spinlock");
				diskFile.deleteOnExit();
				FileChannel fc = new RandomAccessFile(diskFile, "rw").getChannel();
				MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_WRITE, 0, UnsafeAdapter.LONG_SIZE * 2);
				address[0] =  ((sun.nio.ch.DirectBuffer) mbb).address();
			} catch (Exception ex) {
				throw new RuntimeException("Failed to allocate disk lock", ex);
			}
			
		}

		/**
		 * Returns the lock address
		 * @return the lock address
		 */
		public long address() {
			return address[0];
		}
		
		/**
		 * Returns the fully qualified file name of the disk lock
		 * @return the fully qualified file name of the disk lock
		 */
		public String getFileName() {
			return diskFile.getAbsolutePath();
		}
		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.unsafe.DeAllocateMe#getAddresses()
		 */
		@Override
		public long[] getAddresses() {
			return address;
		}

		/**
		 * Acquires the lock with the calling thread
		 */
		@Override
		public void xlock() {
			xlock(false);
		}
		
		
/**
		 * {@inheritDoc}
		 * @see com.heliosapm.unsafe.SpinLock#xlock(boolean)
		 */
		@Override
		public void xlock(boolean barge) {
			final long tId = Thread.currentThread().getId();
			while(!UnsafeAdapter.compareAndSwapLong(null, address[0], NO_LOCK, JVM_PID)) { if(!barge) Thread.yield(); }
			while(!UnsafeAdapter.compareAndSwapLong(null, address[0] + UnsafeAdapter.LONG_SIZE, NO_LOCK, tId)) { if(!barge) Thread.yield(); }
		}		
		
		/**
		 * Releases the lock if it is held by the calling thread
		 */
		@Override
		public void xunlock() {
			final long tId = Thread.currentThread().getId();
			if(UnsafeAdapter.getLong(address[0])==JVM_PID  &&  UnsafeAdapter.getLong(address[0] + UnsafeAdapter.LONG_SIZE)==tId) {
				UnsafeAdapter.compareAndSwapLong(null, address[0] + UnsafeAdapter.LONG_SIZE, tId, NO_LOCK);
				UnsafeAdapter.compareAndSwapLong(null, address[0], JVM_PID, NO_LOCK);
			}
		}

		@Override
		public boolean isLocked() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isLockedByMe() {
			// TODO Auto-generated method stub
			return false;
		}
	}