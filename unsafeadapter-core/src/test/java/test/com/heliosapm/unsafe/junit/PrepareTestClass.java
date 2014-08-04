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
package test.com.heliosapm.unsafe.junit;

import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import test.com.heliosapm.unsafe.UnsafeAdapterConfigurator;

/**
 * <p>Title: PrepareTestClass</p>
 * <p>Description: Test rule to prepare the UnsafeAdapter for unsafe config annotated test classes </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.PrepareTestClass</code></p>
 */

public class PrepareTestClass extends TestWatcher {
	/** The class name being tested */
	private String className;
	/** The class being tested */
	private Class<?> testClass;
	/** The method name being tested */
	private String methodName;
	/** The display name of the test */
	private String displayName;
	
	/** A set of already prepared classes */
	private static final Set<Class<?>> preparedClasses = new HashSet<Class<?>>();
	
	/**
	 * {@inheritDoc}
	 * @see org.junit.rules.TestWatcher#starting(org.junit.runner.Description)
	 */
	@Override
	protected void starting(Description d) {
		testClass = d.getTestClass();
		className = testClass.getName();
		methodName = d.getMethodName();
		displayName = d.getDisplayName();
		log("\n\t>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n\tRunning Test [%s.%s]\n\t>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n", testClass.getSimpleName(),  methodName);		
		if(!preparedClasses.contains(testClass)) {
			log("Preparing Test Class [%s]", className);
			preparedClasses.add(testClass);
			UnsafeAdapterConfigurator.setConfiguration(testClass);		
			Assert.assertFalse("UnsafeAdapter is not in expected state", UnsafeAdapterConfigurator.requiresReset(testClass));
			log("Prepared Test Class [%s], Unsafe Adapter Config: %s", className, UnsafeAdapterConfigurator.printActualConfiguration());
		}	
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.junit.rules.TestWatcher#failed(java.lang.Throwable, org.junit.runner.Description)
	 */
	@Override
	protected void failed(Throwable e, Description d) {
		System.err.println("\n\t[" + testClass.getSimpleName() + "." + d.getMethodName() + "]  FAILED:   [" + e + "]");
		super.failed(e, d);
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.junit.rules.TestWatcher#finished(org.junit.runner.Description)
	 */
	@Override
	protected void finished(Description description) {
		log("\n\t<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n\tFinished Test [%s.%s]\n\t<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n", testClass.getSimpleName(),  methodName);
		super.finished(description);
	}

	/**
	 * Returns the name of the currently-running test class
	 * @return the name of the currently-running test class
	 */
	public String getClassName() {
		return className;
	}
	
	/**
	 * Returns the name of the currently-running test class
	 * @return the name of the currently-running test class
	 */
	public Class<?> getTestClass() {
		return testClass;
	}
	
    /**
     * @return the name of the currently-running test method
     */
    public String getMethodName() {
        return methodName;
    }	
    
    /**
     * Returns the test display name
     * @return the test display name
     */
    public String getDisplayName() {
    	return displayName;
    }
	
	/**
	 * Out printer
	 * @param fmt the message format
	 * @param args the message values
	 */
	public static void log(String fmt, Object...args) {
		System.out.println(String.format(fmt, args));
	}
	
	
}
