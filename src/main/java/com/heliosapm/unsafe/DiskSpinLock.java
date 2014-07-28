package com.heliosapm.unsafe;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
	 * <p>Title: MemSpinLock</p>
	 * <p>Description: Disk based spin lock that is sharable with other processes</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.UnsafeAdapterOld.DiskSpinLock</code></p>
	 * FIXME: 
	 */

	public class DiskSpinLock implements SpinLock, DeAllocateMe {
		/** The lock file name */
		protected final File diskFile;
		/** The mapped lock file address */
		protected final long address;
		/**
		 * Creates a new MemSpinLock
		 * @param address The address of the lock
		 */
		private DiskSpinLock() {
			try {
				diskFile = File.createTempFile("DiskSpinLock", ".spinlock");
				diskFile.deleteOnExit();
				FileChannel fc = new RandomAccessFile(diskFile, "rw").getChannel();
				MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_WRITE, 0, UnsafeAdapterOld.LONG_SIZE * 2);
				address =  ((sun.nio.ch.DirectBuffer) mbb).address();
			} catch (Exception ex) {
				throw new RuntimeException("Failed to allocate disk lock", ex);
			}
			
		}

		/**
		 * Returns the lock address
		 * @return the lock address
		 */
		public long address() {
			return address;
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
		public long[][] getAddresses() {
			return new long[][] {{address}};
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
			while(!UnsafeAdapterOld.compareAndSwapLong(null, address, UnsafeAdapterOld.NO_LOCK, UnsafeAdapterOld.JVM_PID)) { if(!barge) Thread.yield(); }
			while(!UnsafeAdapterOld.compareAndSwapLong(null, address + UnsafeAdapterOld.LONG_SIZE, UnsafeAdapterOld.NO_LOCK, tId)) { if(!barge) Thread.yield(); }
		}		
		
		/**
		 * Releases the lock if it is held by the calling thread
		 */
		@Override
		public void xunlock() {
			final long tId = Thread.currentThread().getId();
			if(UnsafeAdapterOld.getLong(address)==UnsafeAdapterOld.JVM_PID  &&  UnsafeAdapterOld.getLong(address + UnsafeAdapterOld.LONG_SIZE)==tId) {
				UnsafeAdapterOld.compareAndSwapLong(null, address + UnsafeAdapterOld.LONG_SIZE, tId, UnsafeAdapterOld.NO_LOCK);
				UnsafeAdapterOld.compareAndSwapLong(null, address, UnsafeAdapterOld.JVM_PID, UnsafeAdapterOld.NO_LOCK);
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