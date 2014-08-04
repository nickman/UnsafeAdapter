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
package com.heliosapm.unsafe;

import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * <p>Title: ReflectionHelper</p>
 * <p>Description: Reflection utility methods</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.unsafe.ReflectionHelper</code></p>
 */

public class ReflectionHelper {
	
	/** A no param class signature const */
	public static final Class<?>[] NO_ARG_SIG = {};
	/** A no arg object invocation const */
	public static final Object[] NO_ARG_PARAM = {};
	/** The field for accessing the pending queue length */
	private static final Field queueLengthField;
	/** The field for accessing the pending queue lock */
	private static final Field queueLockField;

	static {
		try {
			queueLengthField = ReferenceQueue.class.getDeclaredField("queueLength");
			queueLengthField.setAccessible(true);
			queueLockField = ReferenceQueue.class.getDeclaredField("lock");
			queueLockField.setAccessible(true); 
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}				
	}
	

	/**
	 * Sets a field in a class to be modifiable regardless of finality or privacy
	 * @param clazz The class to modify
	 * @param fieldName The name of the field to modify
	 * @return the modified field
	 */
	public static Field setFieldEditable(Class<?> clazz, String fieldName) {
		try {
			Field targetField = clazz.getDeclaredField(fieldName);
			Field modifierField = Field.class.getDeclaredField("modifiers");
			targetField.setAccessible(true);
			modifierField.setAccessible(true);
			modifierField.setInt(targetField, targetField.getModifiers() & ~Modifier.FINAL);
			return targetField;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Reverses the changes made in {@link #setFieldEditable(Class, String)}
	 * @param clazz The class to modify
	 * @param fieldName The name of the field to modify
	 */
	public static void setFieldReadOnly(Class<?> clazz, String fieldName) {
		try {
			Field targetField = clazz.getDeclaredField(fieldName);
			Field modifierField = Field.class.getDeclaredField("modifiers");
			targetField.setAccessible(true);
			modifierField.setAccessible(true);
			modifierField.setInt(targetField, targetField.getModifiers() | Modifier.FINAL);
			modifierField.setAccessible(false);
			targetField.setAccessible(false);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	
	/**
	 * Reflectively invokes a method 
	 * @param clazz The class defining the method to invoke
	 * @param methodName The method to invoke
	 * @param instance The object instance to invoke on
	 * @param signature The method signature
	 * @param args The method invocation arguments
	 * @return the return value of the invocation
	 */
	public static Object invoke(Class<?> clazz, String methodName, Object instance, Class<?>[] signature, Object...args) {
		boolean mod = false;
		Method m = null;
		try {
			try {
				m = clazz.getDeclaredMethod(methodName, signature==null ? NO_ARG_SIG : signature);
			} catch (NoSuchMethodException e) {
				m = clazz.getMethod(methodName, signature==null ? NO_ARG_SIG : signature);
			}
			if(!m.isAccessible()) {
				m.setAccessible(true);
				mod = true;
			}
			return m.invoke(instance, args);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		} finally {
			if(mod && m!=null) {
				m.setAccessible(false);
			}
		}
	}
	
	/**
	 * Reflectively invokes a static method 
	 * @param clazz The class defining the method to invoke
	 * @param methodName The method to invoke
	 * @param signature The method signature
	 * @param args The method invocation arguments
	 * @return the return value of the invocation
	 */
	public static Object invoke(Class<?> clazz, String methodName, Class<?>[] signature, Object...args) {
		return invoke(clazz, methodName, signature, args);
	}
	
	/**
	 * Reflectively invokes a static and parameterless method 
	 * @param clazz The class defining the method to invoke
	 * @param methodName The method to invoke
	 * @return the return value of the invocation
	 */
	public static Object invoke(Class<?> clazz, String methodName) {
		return invoke(clazz, methodName, null, null, NO_ARG_PARAM);
	}
	
	/**
	 * Reflectively invokes a parameterless method 
	 * @param methodName The method to invoke
	 * @param instance The object instance to invoke on
	 * @return the return value of the invocation
	 */
	public static Object invoke(Object instance, String methodName) {
		return invoke(instance.getClass(), methodName, instance, null, NO_ARG_PARAM);
	}
	
	/**
	 * Creates a new {@link ReferenceQueueLengthReader} for the passed {@link ReferenceQueue}.
	 * @param refQueue The reference queue to create the reader for
	 * @return the new ReferenceQueueLengthReader
	 */
	public static <T> ReferenceQueueLengthReader<T> newReferenceQueueLengthReader(ReferenceQueue<T> refQueue) {
		if(refQueue==null) throw new IllegalArgumentException("The passed reference queue was null");
		return new ReferenceQueueLengthReader<T>(refQueue);
	}
	
	
	/**
	 * <p>Title: ReferenceQueueLengthReader</p>
	 * <p>Description: A reflective reader of the queue length for {@link ReferenceQueue}'s</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.unsafe.ReflectionHelper.ReferenceQueueLengthReader</code></p>
	 * @param <T> The type of objects expected in the reference queue
	 */
	public static class ReferenceQueueLengthReader<T> {
		/** The reference queue to read the lenght for */
		private final ReferenceQueue<T> refQueue;
		/** The reference queue's lock field */
		private final Object queueLengthFieldLock;
		
		/**
		 * Creates a new ReferenceQueueLengthReader
		 * @param refQueue The reference queue to read the lenght for
		 */
		private ReferenceQueueLengthReader(ReferenceQueue<T> refQueue) {
			this.refQueue = refQueue;
			synchronized(refQueue) {
				try {
					queueLengthFieldLock = queueLockField.get(refQueue);
				} catch (Exception x) {
					throw new RuntimeException("Failed to get lock for ReferenceQueue", x);						
				}									
			}
		}
		
		/**
		 * Returns the length of the reference queue
		 * @return the length of the reference queue
		 */
		public long getQueueLength() {
			try {
				synchronized(queueLengthFieldLock) {
					return queueLengthField.getLong(refQueue);
				}				
			} catch (Exception x) {
				return -1L;
			}											
		}		
	}

	private ReflectionHelper() {}

}
