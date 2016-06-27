/**
 * 
 */
package org.microbench.esper.input;

import java.io.Serializable;

/**
 * @author miyuru
 *
 */
public class Tuple implements Serializable {
	public Tuple() {
	}

	public Tuple(int bigObjectSize){
		bigObject = new long[bigObjectSize];
	}
	
	public int increment=0;
	public long[] bigObject;
	//Java long is a 64-bit two's complement. That means One long varibale is 8 bytes.
	//1KB of memory is used for 128 long variables
	//1MB of memory is used for 131072 long variables
}
