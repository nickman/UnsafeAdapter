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
package test.com.heliosapm.unsafe.jmh;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.internal.runners.InitializationError;
import org.junit.internal.runners.MethodValidator;
import org.junit.internal.runners.TestClass;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;

/**
 * <p>Title: JMHRunWith</p>
 * <p>Description: Integrates the junit {@link Runner} and the JMH {@link org.openjdk.jmh.runner.Runner} to run benchmarks in junit</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.jmh.JMHRunWith</code></p>
 */

public class JMHRunWith extends Runner {

    private final List<Method> fTestMethods;
    private TestClass fTestClass;

    public JMHRunWith(Class<?> klass) throws InitializationError {
        fTestClass = new TestClass(klass);
        fTestMethods = getTestMethods();
        validate();
    }
    
    protected List<Method> getTestMethods() {
        return fTestClass.getTestMethods();
    }

    protected void validate() throws InitializationError {
        MethodValidator methodValidator = new MethodValidator(fTestClass);
        methodValidator.validateMethodsForDefaultRunner();
        methodValidator.assertValid();
    }

    protected Annotation[] classAnnotations() {
        return fTestClass.getJavaClass().getAnnotations();
    }

    protected String getName() {
        return null;//getTestClass().getName();
    }

    protected Object createTest() throws Exception {
        return null; //getTestClass().getConstructor().newInstance();
    }
    


	/**
	 * {@inheritDoc}
	 * @see org.junit.runner.Runner#getDescription()
	 */
	@Override
	public Description getDescription() {
        Description spec = Description.createSuiteDescription(getName(), classAnnotations());
        List<Method> testMethods = fTestMethods;
        for (Method method : testMethods) {
            //spec.addChild(methodDescription(method));
        }
        return spec;
		
	}

	/**
	 * {@inheritDoc}
	 * @see org.junit.runner.Runner#run(org.junit.runner.notification.RunNotifier)
	 */
	@Override
	public void run(RunNotifier notifier) {
		// TODO Auto-generated method stub

	}

}
