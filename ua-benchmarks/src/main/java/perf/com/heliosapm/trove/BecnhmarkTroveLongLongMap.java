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
package perf.com.heliosapm.trove;

import java.util.Arrays;
import java.util.Collection;

import gnu.trove.map.hash.TLongLongHashMap;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.generators.core.ClassInfo;
import org.openjdk.jmh.generators.core.GeneratorSource;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * <p>Title: BecnhmarkTroveLongLongMap</p>
 * <p>Description: JMH Benchmark for the trove {@link TLongLongHashMap}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>perf.com.heliosapm.trove.BecnhmarkTroveLongLongMap</code></p>
 */
@State(Scope.Thread)
public class BecnhmarkTroveLongLongMap {
	protected int initialSize = 1024;
	protected int maxSize = 4214397;
	protected float loadFactor = 0.75f;
	protected long NULL = -1L;
	
	

	@Benchmark	
	public void testMap() {
		final TLongLongHashMap map = new TLongLongHashMap(initialSize, loadFactor, NULL, NULL);   
		int errors = 0;
    	for(int i = 0; i < maxSize; i++) {
    		map.put(i, i);
    	}
    	for(int i = 0; i < maxSize; i++) {
    		long x = map.get(i);
    		if(x!=i) {
    			errors++;
    		}
    	}
    	
    	log("Done: %s, errors: %s", map.size(), errors);
		
	}
	
	
	public static void main(String[] args) throws RunnerException {
		log("Starting Benchmarks on BecnhmarkTroveLongLongMap");
        Options opt = new OptionsBuilder()
        
	        .include(".*")
	        .forks(1)
	        .build();
        log("Includes: [%s]", opt.getIncludes());
        new Runner(opt).run();
    }	
	
    public static void log(Object fmt, Object...args) {
    	System.out.println(String.format(fmt.toString(), args));
    }
	
}
