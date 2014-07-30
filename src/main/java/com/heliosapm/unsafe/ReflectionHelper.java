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
	
	private static final Class<?>[] NO_ARG_SIG = {};
	private static final Object[] NO_ARG_PARAM = {};
	
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
	
	public static Object invoke(Class<?> clazz, String methodName, Class<?>[] signature, Object...args) {
		return invoke(clazz, methodName, signature, args);
	}
	
	public static Object invoke(Class<?> clazz, String methodName) {
		return invoke(clazz, methodName, null, null, NO_ARG_PARAM);
	}
	
	public static Object invoke(Object instance, String methodName) {
		return invoke(instance.getClass(), methodName, instance, null, NO_ARG_PARAM);
	}
	

	private ReflectionHelper() {}

}
