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

import com.heliosapm.unsafe.ReflectionHelper;
import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: UnsafeAdapterConfigurator</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.UnsafeAdapterConfigurator</code></p>
 */

public class UnsafeAdapterConfigurator {

	public static void setConfiguration(Class<?> clazz) {
		if(clazz==null) return;
		UnsafeAdapterConfiguration uac = clazz.getAnnotation(UnsafeAdapterConfiguration.class);
		if(uac==null) return;
		if(requiresReset(uac)) {
			doReset(uac);
		}
	}
	
	public static void setConfiguration(Object testObject) {
		if(testObject==null) return;
		setConfiguration(testObject.getClass());
	}
	
	public static void doReset(UnsafeAdapterConfiguration uac) {
		if(uac.unsafe()) {
			System.clearProperty(UnsafeAdapter.SAFE_MANAGER_PROP);			
		} else {
			System.setProperty(UnsafeAdapter.SAFE_MANAGER_PROP, "true");
		}
		if(uac.memTracking()) {
			System.clearProperty(UnsafeAdapter.TRACK_ALLOCS_PROP);			
		} else {
			System.setProperty(UnsafeAdapter.TRACK_ALLOCS_PROP, "true");
		}
		if(uac.memAlignment()) {
			System.clearProperty(UnsafeAdapter.ALIGN_ALLOCS_PROP);			
		} else {
			System.setProperty(UnsafeAdapter.ALIGN_ALLOCS_PROP, "true");
		}
		if(uac.offHeap()) {
			System.clearProperty(UnsafeAdapter.SAFE_ALLOCS_ONHEAP_PROP);			
		} else {
			System.setProperty(UnsafeAdapter.SAFE_ALLOCS_ONHEAP_PROP, "true");
		}
		ReflectionHelper.invoke(UnsafeAdapter.class, "reset");
	}
	
	//System.clearProperty(UnsafeAdapter.TRACK_ALLOCS_PROP);
	
	public static boolean requiresReset(UnsafeAdapterConfiguration uac) {
		final boolean[] current = getCurrentConfiguration();
		final boolean[] requested = getRequestedConfiguration(uac);
		int indexCnt = requested[0] ? 3 : 4;
		for(int i = 0; i < indexCnt; i++ ) {
			if(current[i] != requested[i]) return true;
		}
		return false;
	}
	
	public static boolean[] getCurrentConfiguration() {
		final boolean[] current = new boolean[4];
		current[0] = !UnsafeAdapter.isSafeAdapter();
		current[1] = UnsafeAdapter.getMemoryMBean().isTrackingEnabled();
		current[2] = UnsafeAdapter.getMemoryMBean().isAlignmentEnabled();
		current[3] = UnsafeAdapter.getMemoryMBean().isSafeMemoryOffHeap();
		return current;
	}
	
	public static boolean[] getRequestedConfiguration(UnsafeAdapterConfiguration config) {
		final boolean[] current = new boolean[4];
		current[0] = config.unsafe();
		current[1] = config.memTracking();
		current[2] = config.memAlignment();
		current[3] = config.offHeap();
		return current;
	}
	
	
	private UnsafeAdapterConfigurator() {}

}
