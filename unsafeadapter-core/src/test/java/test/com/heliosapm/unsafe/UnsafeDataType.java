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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Random;

import sun.misc.Unsafe;

import com.heliosapm.unsafe.ReflectionHelper;
import com.heliosapm.unsafe.UnsafeAdapter;

/**
 * <p>Title: UnsafeDataType</p>
 * <p>Description: Test supporting enumeration of data types manipulatable through the UnsafeAdapter</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.UnsafeDataType</code></p>
 */
@SuppressWarnings("restriction")
public enum UnsafeDataType implements UnsafeDataTypeInvoker<Object> {
	/**  */
	BOOLEAN(1, "getBoolean", "putBoolean", "randomBoolean", boolean.class, Boolean.class),
	/**  */
	BYTE(1, "getByte", "putByte", "randomByte", byte.class, Byte.class),
	/**  */
	CHAR(2, "getChar", "putChar", "randomChar", char.class, Character.class),
	/**  */
	SHORT(2, "getShort", "putShort", "randomShort", short.class, Short.class),
	/**  */
	INTEGER(4, "getInt", "putInt", "randomInt", int.class, Integer.class),
	/**  */
	FLOAT(4, "getFloat", "putFloat", "randomFloat", float.class, Float.class),
	/**  */
	LONG(8, "getLong", "putLong", "randomLong", long.class, Long.class), 
//	/**  */
//	ADDRESS(AllocationPointerOperations.ADDRESS_SIZE, "getLong", "putLong", "randomLong", long.class, Long.class),
	/**  */
	DOUBLE(8, "getDouble", "putDouble", "randomDouble", double.class, Double.class);
	
	/** The op recorder for the current thread */
	private static final ThreadLocal<OpRecorder> recorder = new ThreadLocal<OpRecorder>(); 
	/** The native undafe */
	private static final Unsafe unsafe;
	static {
		try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to access sun.misc.Unsafe.theUnsafe", ex);
		}
	}

	private UnsafeDataType(int size, String getOp, String putOp, String random, Class<?> primitiveType, Class<?> type) {
		this.size = (byte)size;
		this.getOp = getOp;
		this.putOp = putOp;
		this.random = random;
		this.primitiveType = primitiveType;
		this.type = type;
		if(primitiveType==boolean.class) {
			unsafeGetOp = ReflectionHelper.getStaticMethod(Unsafe.class, getOp, Object.class, long.class);
			unsafePutOp = ReflectionHelper.getStaticMethod(Unsafe.class, putOp, Object.class, long.class, primitiveType);			
		} else {
			unsafeGetOp = ReflectionHelper.getStaticMethod(Unsafe.class, getOp, long.class);
			unsafePutOp = ReflectionHelper.getStaticMethod(Unsafe.class, putOp, long.class, primitiveType);						
		}
		unsafeVolatilePutOp  = ReflectionHelper.getStaticMethod(Unsafe.class, putOp + "Volatile", Object.class, long.class, primitiveType);
		adapterGetOp = ReflectionHelper.getStaticMethod(UnsafeAdapter.class, getOp, long.class);
		adapterPutOp = ReflectionHelper.getStaticMethod(UnsafeAdapter.class, putOp, long.class, primitiveType);
		adapterVolatilePutOp = ReflectionHelper.getStaticMethod(UnsafeAdapter.class, putOp + "Volatile", long.class, primitiveType);
	}
	
	private static String readFile(String fileName) {
		File f = new File(fileName);
		if(!f.canRead()) throw new RuntimeException("The file [" + fileName + "] cannot be read");
		if(f.isDirectory()) throw new RuntimeException("The file [" + fileName + "] is a directory");
		StringBuilder b = new StringBuilder((int)f.length());
		FileReader fr = null;
		BufferedReader br = null;
		try {
			fr = new FileReader(f);
			br = new BufferedReader(fr);
			String line = null;
			while((line = br.readLine())!=null) {
				b.append(line).append("\n");				
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try { br.close(); } catch (Exception x) {/* No Op */}
			try { fr.close(); } catch (Exception x) {/* No Op */}
			
		}
		return b.toString();
	}
	
	private static String doReplace(UnsafeDataType udt, String template) {
		return template
			.replace("##Type##", udt.type.getSimpleName())
			.replace("##type##", udt.primitiveType.getName())
			.replace("##udt##", "" + udt.name())
			.replace("##size##", "" + udt.size)
			.replace("##short##", udt.getOp.replace("get", ""));
	}
	
	
	public static void main(String[] args) {
		mainx("/tmp/template.txt");
	}
	
	public static void mainx(String...args) {
		if(args.length==0) {
			log("Provide code gen template");
			return;
		}
		String template = readFile(args[0]);
		for(UnsafeDataType udt: UnsafeDataType.values()) {
			log(doReplace(udt, template));
			log("\n");
		}
		
		
		
	}
	
	/**
	 * Out printer
	 * @param fmt the message format
	 * @param args the message values
	 */
	public static void log(String fmt, Object...args) {
		System.out.println(String.format(fmt, args));
	}
	
	
	/** The size of this data type */
	public final byte size;
	/** The name of the read op */
	public final String getOp;
	/** The name of the write op */
	public final String putOp;
	/** The name of the random data generator op */
	public final String random;
	/** The primitive type */
	public final Class<?> primitiveType;
	/** The non-primitive type */
	public final Class<?> type;

	/** The adapter read method */
	public final Method adapterGetOp;
	/** The unsafe read method */
	public final Method unsafeGetOp;
	/** The adapter write method */
	public final Method adapterPutOp;
	/** The unsafe write method */
	public final Method unsafePutOp;
	/** The adapter volatile write method */
	public final Method adapterVolatilePutOp;
	/** The unsafe volatile write method */
	public final Method unsafeVolatilePutOp;
	
	
	private Object invoke(Method m, Object...args) {
		if(m==null) throw new IllegalArgumentException("The passed method was null");
		try {
			if(Modifier.isNative(m.getModifiers())) {
				return m.invoke(unsafe, args);
			}
			return m.invoke(null, args);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to invoke [" + m.getName() + "]", ex);
		}
	}
	
	private void recordPut(UnsafeDataType type, Object value, boolean adapter, long address) {
		OpRecorder rec = recorder.get();
		if(rec!=null) {
			rec.recordPut(type, value, adapter, address) ;
		}
	}
	
	/**
	 * Starts op recording
	 */
	public static void startRecording() {
		recorder.set(new OpRecorder());
	}
	
	/**
	 * Returns the currently recording op recorder
	 * @return the currently recording op recorder
	 */
	public static OpRecorder getRecorder() {
		return recorder.get();
	}
	
	/**
	 * Stops recording and returns the op recorder
	 * @return the stopped op recorder
	 */
	public static OpRecorder stopRecording() {
		OpRecorder o = recorder.get();
		recorder.remove();
		return o;
	}
	
	/**
	 * Resets the recorder for the current thread
	 */
	public static void resetRecorder() {
		recorder.remove();
		recorder.set(new OpRecorder());
	}
	
	/**
	 * Indicates if there is an op recorder installed for the current thread
	 * @return true if there is an op recorder installed for the current thread, false otherwise
	 */
	public static boolean isRecording() {
		return recorder.get()!=null;
	}
	
	
	// ===================================================================================================================================
	//  Put Ops
	// ===================================================================================================================================
	
	/**
	 * {@inheritDoc}
	 * @see test.com.heliosapm.unsafe.UnsafeDataTypeInvoker#adapterPut(long, java.lang.Object)
	 */
	@Override
	public void adapterPut(long address, Object t) {
		invoke(adapterPutOp, address, t);
		recordPut(this, t, true, address);		
	}
	
	/**
	 * {@inheritDoc}
	 * @see test.com.heliosapm.unsafe.UnsafeDataTypeInvoker#unsafePut(long, java.lang.Object)
	 */
	@Override
	public void unsafePut(long address, Object t) {
		if(primitiveType==boolean.class) {
			invoke(unsafePutOp, null, address, t);
		} else {
			invoke(unsafePutOp, address, t);
		}		
		recordPut(this, t, false, address);
	}
	
	/**
	 * {@inheritDoc}
	 * @see test.com.heliosapm.unsafe.UnsafeDataTypeInvoker#unsafeVolatilePut(long, java.lang.Object)
	 */
	public void unsafeVolatilePut(long address, Object t) {
		invoke(unsafeVolatilePutOp, null, address, t);
		recordPut(this, t, false, address);
		
	}
	
	/**
	 * {@inheritDoc}
	 * @see test.com.heliosapm.unsafe.UnsafeDataTypeInvoker#adapterVolatilePut(long, java.lang.Object)
	 */
	public void adapterVolatilePut(long address, Object t) {
		invoke(adapterVolatilePutOp, address, t);
		recordPut(this, t, true, address);		
	}
	
	// ===================================================================================================================================
	//  Get Ops
	// ===================================================================================================================================
	

	
	/**
	 * {@inheritDoc}
	 * @see test.com.heliosapm.unsafe.UnsafeDataTypeInvoker#adapterGet(long)
	 */
	@Override
	public Object adapterGet(long address) {
		return invoke(adapterGetOp, address);
	}
	/**
	 * {@inheritDoc}
	 * @see test.com.heliosapm.unsafe.UnsafeDataTypeInvoker#unsafeGet(long)
	 */
	@Override
	public Object unsafeGet(long address) {
		if(primitiveType==boolean.class) {
			return invoke(unsafeGetOp, null, address);
		} 
		return invoke(unsafeGetOp, address);
	}
	/**
	 * {@inheritDoc}
	 * @see test.com.heliosapm.unsafe.UnsafeDataTypeInvoker#randomValue()
	 */
	@Override
	public Object randomValue() {
		final byte[] b = new byte[1];
		switch(this) {
		case BOOLEAN:
			return R.nextBoolean();			
		case BYTE:
			R.nextBytes(b);
			return b[0];
		case CHAR:
			char a = (char)ByteBuffer.allocate(4).putInt(R.nextInt()).getShort(0);
			return a;
		case DOUBLE:
			return R.nextDouble();
		case FLOAT:
			return R.nextFloat();
		case INTEGER:
			return R.nextInt();
		case LONG:
			return R.nextLong();
		case SHORT:
			return ByteBuffer.allocate(4).putInt(R.nextInt()).getShort(0);
		default:			
			break;		
		}
		return null;
	}

	private static final Random R = new Random(System.currentTimeMillis());
	
	//public void put(final long targetAddress)
	
}


