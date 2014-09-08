package my.pack.util;

import java.util.Calendar;
import java.util.TimeZone;

public class CommonUtils {
	private static Calendar cal = Calendar.getInstance();

	public static long getTodayFirstMarketStartTime() {
		cal.setTimeZone(TimeZone.getTimeZone("GMT"));
		cal.set(Calendar.HOUR_OF_DAY, 4);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
	}

	/**
	 * 
	 * @param year
	 * @param month - real month!!! not 0, 1st - January
	 * @param date
	 * @param hourOfDay
	 * @param minute
	 * @param second
	 * @return
	 */
	public static long getFirstMarketStartTime(int year, int month, int date,
			int hourOfDay, int minute, int second) {
		cal.setTimeZone(TimeZone.getTimeZone("GMT"));
		cal.set(year, month-1, date, hourOfDay, minute, second);
		return cal.getTimeInMillis();
	}

}
