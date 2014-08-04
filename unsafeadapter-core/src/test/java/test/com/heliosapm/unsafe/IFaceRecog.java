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

/**
 * <p>Title: IFaceRecog</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.unsafe.IFaceRecog</code></p>
 */

public class IFaceRecog {

	/**
	 * Creates a new IFaceRecog
	 */
	public IFaceRecog() {
		// TODO Auto-generated constructor stub
	}
	
	static final int A = 1;
	static final int B = A*2;
	static final int C = B*2;
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		log("IFaceRecog Test");
		log("Masks:  A:%s, B:%s, C:%s", A, B, C);
		int i = 0; print(i);
		i = i | A; print(i);
		i = 0;
		i = i | B; print(i);
		i = 0;
		i = i | C; print(i);
		i = 0;
		i = i | A; print(i);
		i = i | B; print(i);
		i = i | C; print(i);
		log("Dump Complete\n");
		i = 0;
		i = e(i, A); log("Enabled for A: %s", ie(i, A));
		i = e(i, B); log("Enabled for B: %s", ie(i, B));
		i = e(i, C); log("Enabled for C: %s", ie(i, C));
		
		i = d(i, C); log("Not Enabled for C: %s", !ie(i, C));
		i = d(i, B); log("Not Enabled for B: %s", !ie(i, B));
		i = d(i, A); log("Not Enabled for A: %s", !ie(i, A));
		
		
	}
	
	static boolean ie(int v, int mask) {
		return (v == (v | mask));
	}
	
	static int e(int v, int mask) {
		return v | mask;
	}
	
	static int d(int v, int mask) {
		return v & ~mask;
	}
	
	static void print(int x) {
		StringBuilder b = new StringBuilder("Printing Mask [").append(x).append("]");
		b.append("\n\tBinary String: ").append(Integer.toBinaryString(x));
		
		log(b);
	}
	
	public static void log(Object fmt, Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}

}
