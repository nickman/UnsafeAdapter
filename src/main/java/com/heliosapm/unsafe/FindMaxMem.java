package com.heliosapm.unsafe;

public class FindMaxMem {

	/**
     * Finds the next <b><code>power of 2</code></b> higher or equal to than the passed value.
     * @param value The initial value
     * @return the pow2
     */
    public static int findNextPositivePowerOfTwo(final int value) {
    	return  1 << (32 - Integer.numberOfLeadingZeros(value - 1));
	}    
    
    /**
     * Finds the next <b><code>power of 2</code></b> higher or equal to than the passed value.
     * @param value The initial value
     * @return the pow2
     */
    public static long findNextPositivePowerOfTwo(final long value) {
    	return  1L << (64L - Long.numberOfLeadingZeros(value - 1L));
	}   
    
	public static void main(String[] args) {
		log("FindMaxAlignedMem");
		long x = intMax();
		long y = longMax();
		
		log("Long Sample:" + findNextPositivePowerOfTwo(y));
		log("Long Overrun Sample:" + findNextPositivePowerOfTwo(y+1));
	}
	
	public static int intMax() {
		int start = 1;
		int t = 0;

		while(true) {
		    if(start==Integer.MAX_VALUE) break;
		    t = findNextPositivePowerOfTwo(start);
		    if(t<0) {
		        log("INT POW2: %s: %s", start, t);
		        break;
		    }
		    start++;
		}
		log("MAX INT: %s: %s", start-1, findNextPositivePowerOfTwo(start-1));
		return start-1;
	}
	
	public static long longMax() {
		long start = Long.MAX_VALUE/2;
		long t = 0;

		while(true) {
		    if(start==Long.MAX_VALUE) break;
		    t = findNextPositivePowerOfTwo(start);
		    if(t<0) {
		        log("LONG POW2: %s: %s", start, t);
		        break;
		    }
		    start++;
		}
		log("MAX LONG: %s: %s", start-1, findNextPositivePowerOfTwo(start-1));
		return start-1;
	}
	
	
	/**
	 * Low maintenance out logger
	 * @param fmt The format of the message
	 * @param args The message token values
	 */
	public static void log(Object fmt, Object...args) {
		System.out.println(String.format("[FindMaxMem]" + fmt.toString(), args));
	}

}
