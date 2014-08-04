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
 * <p>Description: Testing only UnsafeAdapter configuration tester and reset.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.UnsafeAdapterConfigurator</code></p>
 */

public class UnsafeAdapterConfigurator {
	
	/** The default unsafe adapter configuration */
	public static final UnsafeAdapterConfiguration DEFAULT_CONFIG;
	
	static {
		DEFAULT_CONFIG = DefaultUnsafeAdapterConfiguration.class.getAnnotation(UnsafeAdapterConfiguration.class);
	}

	/**
	 * Sets the configuration of the UnsafeAdapter in accordance 
	 * with the @UnsafeAdapterConfiguration annotation on the passed class.
	 * @param clazz The class defining the UnsafeAdapter configuration to be tested
	 */
	public static void setConfiguration(Class<?> clazz) {
		if(clazz==null) return;
		UnsafeAdapterConfiguration uac = clazz.getAnnotation(UnsafeAdapterConfiguration.class);
		if(uac==null) uac = DEFAULT_CONFIG;
		if(requiresReset(uac)) {
			doReset(uac);
		}
	}
	
	/**
	 * Sets the configuration of the UnsafeAdapter in accordance 
	 * with the @UnsafeAdapterConfiguration annotation on the class of the passed object.
	 * @param testObject The test object  defining the UnsafeAdapter configuration to be tested
	 */
	public static void setConfiguration(Object testObject) {
		if(testObject==null) return;
		setConfiguration(testObject.getClass());
	}
	
	/**
	 * Executes an UnsafeAdapter reset in accordance with the passed @UnsafeAdapterConfiguration annotation instance
	 * @param uac the @UnsafeAdapterConfiguration annotation instance specifiying the UnsafeAdapter configuration to reset to
	 */
	public static void doReset(UnsafeAdapterConfiguration uac) {
		if(uac.unsafe()) {
			System.clearProperty(UnsafeAdapter.SAFE_MANAGER_PROP);			
		} else {
			System.setProperty(UnsafeAdapter.SAFE_MANAGER_PROP, "true");
		}
		if(uac.memTracking()) {			
			System.setProperty(UnsafeAdapter.TRACK_ALLOCS_PROP, "true");
		} else {
			System.clearProperty(UnsafeAdapter.TRACK_ALLOCS_PROP);
		}
		if(uac.memAlignment()) {
			System.setProperty(UnsafeAdapter.ALIGN_ALLOCS_PROP, "true");
		} else {
			System.clearProperty(UnsafeAdapter.ALIGN_ALLOCS_PROP);			
		}
		if(uac.offHeap()) {
			System.clearProperty(UnsafeAdapter.SAFE_ALLOCS_ONHEAP_PROP);			
		} else {
			System.setProperty(UnsafeAdapter.SAFE_ALLOCS_ONHEAP_PROP, "true");
		}
		ReflectionHelper.invoke(UnsafeAdapter.class, "reset");
	}
	

	
	/**
	 * Determines if the passed test class has an UnsafeAdapter configuration that will require a reset
	 * @param testClass The test class to test for a reset 
	 * @return true if a reset is required, false otherwise
	 */
	public static boolean requiresReset(Class<?> testClass) {
		if(testClass==null) return false;
		UnsafeAdapterConfiguration uac = testClass.getAnnotation(UnsafeAdapterConfiguration.class);
		if(uac==null) uac = DEFAULT_CONFIG;
		final boolean required = requiresReset(uac);
		if(required) {
			log("Configuration Mismatch.\n\tRequested: \n\t\t%s  \n\t   Actual: \n\t\t%s", printRequestedConfiguration(uac), printActualConfiguration());
		}
		return required;
		
	}
	
	/**
	 * Determines if the passed @UnsafeAdapterConfiguration instance has a configuration that will require an UnsafeAdapter reset
	 * @param uac The @UnsafeAdapterConfiguration instance to test for a reset 
	 * @return true if a reset is required, false otherwise
	 */
	public static boolean requiresReset(UnsafeAdapterConfiguration uac) {
		final boolean[] current = getCurrentConfiguration();
		final boolean[] requested = getRequestedConfiguration(uac);
		int indexCnt = requested[0] ? 3 : 4;
		for(int i = 0; i < indexCnt; i++ ) {
			if(current[i] != requested[i]) return true;
		}
		return false;
	}
	
	/**
	 * Returns the current UnsafeAdapter configuration
	 * @return an array of booleans indicating the current configuration
	 */
	public static boolean[] getCurrentConfiguration() {
		final boolean[] current = new boolean[4];
		current[0] = !UnsafeAdapter.getMemoryMBean().isSafeMemory();
		current[1] = UnsafeAdapter.getMemoryMBean().isTrackingEnabled();
		current[2] = UnsafeAdapter.getMemoryMBean().isAlignmentEnabled();
		current[3] = UnsafeAdapter.getMemoryMBean().isSafeMemoryOffHeap();
		return current;
	}
	
	/**
	 * Returns the UnsafeAdapter configuration as defined by the passed @UnsafeAdapterConfiguration instance
	 * @param config The @UnsafeAdapterConfiguration instance to test
	 * @return an array of booleans indicating the configuration of the passed @UnsafeAdapterConfiguration instance
	 */
	public static boolean[] getRequestedConfiguration(UnsafeAdapterConfiguration config) {
		final boolean[] current = new boolean[4];
		current[0] = config.unsafe();
		current[1] = config.memTracking();
		current[2] = config.memAlignment();
		current[3] = config.offHeap();
		return current;
	}
	
	/**
	 * Returns a string message containing an UnsafeAdapter configuration as defined by the passed @UnsafeAdapterConfiguration instance
	 * @param config The @UnsafeAdapterConfiguration instance to print
	 * @return a string message
	 */
	public static String printRequestedConfiguration(UnsafeAdapterConfiguration config) {
		StringBuilder b = new StringBuilder("Requested: [")
			.append("Unsafe:").append(config.unsafe()).append(", ")
			.append("Mem Tracking:").append(config.memTracking()).append(", ")
			.append("Mem Alignment:").append(config.memAlignment());
		if(!config.unsafe()) {
			b.append(", Safe OffHeap:").append(config.offHeap());
		}		
		return b.append("]").toString();
	}
	
	/**
	 * Prints the actual current configuration of the UnsafeAdapter
	 * @return a string message
	 */
	public static String printActualConfiguration() {
		StringBuilder b = new StringBuilder("Actual   : [")
			.append("Unsafe:").append(!UnsafeAdapter.getMemoryMBean().isSafeMemory()).append(", ")
			.append("Mem Tracking:").append(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()).append(", ")
			.append("Mem Alignment:").append(UnsafeAdapter.getMemoryMBean().isAlignmentEnabled());
		if(UnsafeAdapter.isSafeAdapter()) {
			b.append(", Safe OffHeap:").append(UnsafeAdapter.getMemoryMBean().isSafeMemoryOffHeap());
		}			
		return b.append("]").toString();
	}
	
	
	@UnsafeAdapterConfiguration
	private static class DefaultUnsafeAdapterConfiguration {
		/* No Op */
	}
	
	/**
	 * Err printer
	 * @param fmt the message format
	 * @param args the message values
	 */
	public static void log(String fmt, Object...args) {
		System.err.println(String.format(fmt, args));
	}
	
	
	private UnsafeAdapterConfigurator() {}

}
