package test.com.heliosapm.unsafe;
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


import java.io.File;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.heliosapm.unsafe.DefaultUnsafeAdapterImpl;
import com.heliosapm.unsafe.SafeMemoryAllocator;
import com.heliosapm.unsafe.UnsafeAdapter;

import sun.misc.Unsafe;
import test.com.heliosapm.unsafe.junit.PrepareLifecycle;
import test.com.heliosapm.unsafe.junit.PrepareTestClass;


/**
 * <p>Title: BaseTest</p>
 * <p>Description: Base unit test class</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.BaseTest</code></p>
 */
@Ignore
public class BaseTest implements PrepareLifecycle {
	/** The currently executing class name */
	@Rule public final PrepareTestClass cname = new PrepareTestClass();
	/** A random value generator */
	protected static final Random RANDOM = new Random(System.currentTimeMillis());
	
	/** The platform MBeanServer */
	protected static final MBeanServer PLATFORM_AGENT = ManagementFactory.getPlatformMBeanServer();
	
	/** The debug agent library signature */
	public static final String AGENT_LIB = "-agentlib:";	
	/** The legacy debug agent library signature */
	public static final String LEGACY_AGENT_LIB = "-Xrunjdwp:";
	
	public static final boolean DEBUG_AGENT_LOADED;
	
	
	
	
	/** Inited system out ref */
	protected static final PrintStream OUT = System.out;
	/** Inited system out ref */
	protected static final PrintStream ERR = System.err;
	
	/** A direct reference to the unsafe class instance */
	protected static final Unsafe testUnsafe;
	
	static {
		DEBUG_AGENT_LOADED = isDebugAgentLoaded();
        try {        	
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            testUnsafe = (Unsafe) theUnsafe.get(null);
        } catch (Throwable t) {
        	throw new RuntimeException(t);
        }
	}
	
	
	/** A shared testing scheduler */
	protected static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new ThreadFactory(){
		final AtomicInteger serial = new AtomicInteger();
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "BaseTestScheduler#" + serial.incrementAndGet());
			t.setDaemon(true);
			return t;
		}
	});
	
	
	/**
	 * Returns a random positive long
	 * @return a random positive long
	 */
	protected static long nextPosLong() {
		return Math.abs(RANDOM.nextLong());
	}
	
	/**
	 * Returns a random positive int
	 * @return a random positive int
	 */
	protected static int nextPosInteger() {
		return Math.abs(RANDOM.nextInt());
	}
	
	/**
	 * Returns a random positive float
	 * @return a random positive float
	 */
	protected static float nextPosFloat() {
		return Math.abs(RANDOM.nextFloat());
	}
	
	/**
	 * Returns a random positive double
	 * @return a random positive double
	 */
	protected static double nextPosDouble() {
		return Math.abs(RANDOM.nextDouble());
	}
	
	
	/**
	 * Returns a random positive int
	 * @return a random positive int
	 */
	protected static int nextPosInt() {
		return Math.abs(RANDOM.nextInt());
	}
	
	/**
	 * Returns a random positive short
	 * @return a random positive short
	 */
	protected static short nextPosShort() {
		return (short) Math.abs(ByteBuffer.allocate(4).putInt(RANDOM.nextInt()).getShort(0));
	}
	
	/**
	 * Returns a random positive byte
	 * @return a random positive byte
	 */
	protected static byte nextPosByte() {
		final byte[] b = new byte[1];
		RANDOM.nextBytes(b);
		return (byte)Math.abs(b[0]);
	}
	
	/**
	 * Returns a random boolean
	 * @return a random boolean
	 */
	protected static boolean nextBoolean() {
		return RANDOM.nextBoolean();		
	}
	
	/**
	 * Returns a random char
	 * @return a random char
	 */
	protected static char nextCharacter() {
		return (char)nextPosShort();		
	}
	
	/**
	 * Causes the calling thread to pause for the designated time in ms.
	 * @param ms the pause time in ms.
	 */
	protected static void sleep(long ms) {
		try {
			Thread.currentThread().join(ms);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Causes the calling thread to pause for the designated time in ms according
	 * to whether or not the Debug Agent is loaded
	 * @param runMs the pause time in ms if the debug agent is NOT loaded
	 * @param debugMs the pause time in ms if the debug agent is loaded
	 */
	protected static void sleep(long runMs, long debugMs) {
		if(DEBUG_AGENT_LOADED) sleep(debugMs);
		else sleep(runMs);
	}

	/**
	 * Runs {@link Thread#yield()} in a loop
	 * @param loops the number of loops
	 */
	protected static void yield(final int loops) {
		for(int i = 0; i < loops; i++) {
			Thread.yield();
		}
	}
	
	
	/**
	 * Returns a random positive int within the bound
	 * @param bound the bound on the random number to be returned. Must be positive. 
	 * @return a random positive int
	 */
	protected static int nextPosInt(int bound) {
		return Math.abs(RANDOM.nextInt(bound));
	}
	
	
	public static final JMXServiceURL jmxUrl(String format, Object...args) {
		String _url = String.format(format, args);
		try {
			return new JMXServiceURL(_url);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to create JMXServiceURL for [" + _url + "]", ex);
		}
		
	}
	
	/** The test server */
	protected static JMXConnectorServer JMX_CONNECTOR_SERVER = null;
	
	/**
	 * Starts a JMXMP JMX connector server
	 * @param bind The bind address
	 * @param port The listening port 
	 * @return the started server
	 */
	protected static JMXConnectorServer startJmxMpServer(String bind, int port) {
		try {
			JMXServiceURL serviceURL = new JMXServiceURL(String.format("service:jmx:jmxmp://%s:%s", bind, port));
			JMX_CONNECTOR_SERVER  = JMXConnectorServerFactory.newJMXConnectorServer(serviceURL, null, ManagementFactory.getPlatformMBeanServer());
			JMX_CONNECTOR_SERVER.start();
			return JMX_CONNECTOR_SERVER;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to start JMXConnectorServer on [" + bind + ":" + port + "]", ex);
		}
	}
	
	/**
	 * Returns a connector to the test server
	 * @return a connector to the test server
	 */
	protected static JMXConnector getConnector() {
		try {
			JMXConnector conn = JMX_CONNECTOR_SERVER.toJMXConnector(null);
			conn.connect();
			return conn;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	
	
	/**
	 * Prints the test name about to be executed
	 */
	@Before
	public void printTestName() {
//		log("\n\t==================================\n\tRunning Test [%s.%s]\n\t==================================\n", cname.getClassName(),  cname.getMethodName());
	}
	
	/**
	 * Prints the test name that just completed
	 */
	@After
	public void printTestedName() {
//		log("\n\t==================================\n\tCompleted Test [%s.%s]\n\t==================================\n", cname.getClassName(),  cname.getMethodName());
	}
	
	/**
	 * Nothing yet
	 * @throws java.lang.Exception thrown on any error
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * Deletes the temp plugin directory
	 * @throws java.lang.Exception thrown on any error
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if(JMX_CONNECTOR_SERVER!=null) {
			try {
				JMX_CONNECTOR_SERVER.stop();
			} catch (Exception x) {/* No Op */}
			JMX_CONNECTOR_SERVER = null;
		}
	}

	/**
	 * Nothing yet...
	 * @throws java.lang.Exception thrown on any error
	 */
	@Before
	public void setUp() throws Exception {
	}
	

	/**
	 * Cleans up various artifacts after each test
	 * @throws java.lang.Exception thrown on any error
	 */
	@After
	public void tearDown() throws Exception {
	}
	

	/**
	 * Out printer
	 * @param fmt the message format
	 * @param args the message values
	 */
	public static void log(String fmt, Object...args) {
		OUT.println(String.format(fmt, args));
	}
	
	/**
	 * Err printer
	 * @param fmt the message format
	 * @param args the message values
	 */
	public static void loge(String fmt, Object...args) {
		ERR.print(String.format(fmt, args));
		if(args!=null && args.length>0 && args[0] instanceof Throwable) {
			ERR.println("  Stack trace follows:");
			((Throwable)args[0]).printStackTrace(ERR);
		} else {
			ERR.println("");
		}
	}
	
	/** A set of files to be deleted after each test */
	protected static final Set<File> TO_BE_DELETED = new CopyOnWriteArraySet<File>();
	
	
	/**
	 * Generates an array of random strings created from splitting a randomly generated UUID.
	 * @return an array of random strings
	 */
	public static String[] getRandomFragments() {
		return UUID.randomUUID().toString().split("-");
	}
	
	/**
	 * Generates a random string made up from a UUID.
	 * @return a random string
	 */
	public static String getRandomFragment() {
		return UUID.randomUUID().toString();
	}
	
	
	/** A serial number factory for stream threads */
	public static final AtomicLong streamThreadSerial = new AtomicLong();

	
	/**
	 * Validates that mem tracking reports correct values after memory is allocated  when tracking is turned on, 
	 * or that disabled mem tracking is reporting the disabled values. Assumes one allocation
	 * @param trackedValue The expected value when mem tracking is enabled
	 * @param untrackedValue The expected value when mem tracking is disabled
	 * 
	 */
	public void validateAllocated(long trackedValue, long untrackedValue) {
		validateAllocated(trackedValue, untrackedValue, 1);
	}

	
	/**
	 * Validates that mem tracking reports correct values after memory is allocated  when tracking is turned on, 
	 * or that disabled mem tracking is reporting the disabled values.
	 * @param trackedValue The expected value when mem tracking is enabled
	 * @param untrackedValue The expected value when mem tracking is disabled
	 * @param allocationCount The number of expected allocations
	 * 
	 */
	public void validateAllocated(long trackedValue, long untrackedValue, long allocationCount) {
		if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
			Assert.assertEquals("Mem Total Alloc was unexpected. --> ", trackedValue, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
			Assert.assertEquals("Mem Total Allocation Count was unexpected. --> ", allocationCount, UnsafeAdapter.getMemoryMBean().getTotalAllocationCount());
		} else {
			Assert.assertEquals("Mem Total Alloc was unexpected. --> ", untrackedValue, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
			Assert.assertEquals("Mem Total Allocation Count was unexpected. --> ", -1, UnsafeAdapter.getMemoryMBean().getTotalAllocationCount());
		}
	}
	
	/**
	 * Validates that mem tracking reports correct values after memory is deallocated  when tracking is turned on, 
	 * or that disabled mem tracking is reporting the disabled values.
	 * @param trackedValue The expected value when mem tracking is enabled
	 * @param untrackedValue The expected value when mem tracking is disabled
	 * 
	 */
	public void validateDeallocated(long trackedValue, long untrackedValue) {
		if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
			Assert.assertEquals("Mem Total Post DeAlloc was unexpected. --> ", trackedValue, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
		} else {
			Assert.assertEquals("Mem Total Post DeAlloc was unexpected. --> ", untrackedValue, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
		}
	}

	/**
	 * {@inheritDoc}
	 * @see test.com.heliosapm.unsafe.junit.PrepareLifecycle#beforeTestClass()
	 */
	@Override
	public void beforeTestClass() {
		/* No Op */
		
	}
	
	
	
	/**
	 * Determines if this JVM is running with the debug agent enabled
	 * @return true if this JVM is running with the debug agent enabled, false otherwise
	 */
	public static boolean isDebugAgentLoaded() {
		List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
		for(String s: inputArguments) {
			if(s.trim().startsWith(AGENT_LIB) || s.trim().startsWith(LEGACY_AGENT_LIB)) return true;
		}
		return false;
	}


	
}
