package my.pack.algo;

import java.util.Calendar;
import java.util.TimeZone;

import my.pack.util.CommonUtils;

/**
 * 
 * @author VLD It's test class for experiments.
 */
public class TestClass {

	public static void main(String[] args) {
		long firstMarketStartTime = CommonUtils.getFirstMarketStartTime(2014, 9, 7, 4, 0, 0);
		System.out.println(firstMarketStartTime);
	}

}
