package my.pack.algo;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 
 * @author VLD It's test class for experiments.
 */
public class TestClass {

	public static void main(String[] args) {
		
		Calendar cal = Calendar.getInstance();
		cal.setTimeZone(TimeZone.getTimeZone("GMT"));
		cal.set(Calendar.HOUR_OF_DAY, 4);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		System.out.println(cal.getTimeInMillis());
	}

}
