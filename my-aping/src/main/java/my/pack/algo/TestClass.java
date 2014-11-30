package my.pack.algo;

import org.apache.commons.collections4.queue.CircularFifoQueue;

/**
 * 
 * @author VLD It's test class for experiments.
 */
public class TestClass {

	public static void main(String[] args) {
		CircularFifoQueue<Double> dtv = new CircularFifoQueue<Double>(5);
		for (int i = 0; i < 7; i++) {
			dtv.add(Double.valueOf(i));
			System.out.println(dtv.size());
		}
	}

}
