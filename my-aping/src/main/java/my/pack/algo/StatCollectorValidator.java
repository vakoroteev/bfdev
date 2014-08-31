package my.pack.algo;

import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

import my.pack.model.HorseStatBean;
import my.pack.model.MarketBean;
import my.pack.util.CouchbaseHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.client.protocol.views.Paginator;
import com.couchbase.client.protocol.views.ViewResponse;
import com.couchbase.client.protocol.views.ViewRow;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StatCollectorValidator {

	private static final CouchbaseHandler cbClient = new CouchbaseHandler(
			"horses");

	private static final Logger log = LoggerFactory
			.getLogger(StatCollectorValidator.class);

	private static final ObjectMapper om = new ObjectMapper();

	private static final String DES_DOC = "des2";
	private static final String VIEW_NAME = "marketsByStartDate";

	private static final long DELTA = 10000L;

	public static void main(String[] args) {
		Object[] startKey = { getFirstMarketStartTime() };
		Calendar cal = Calendar.getInstance();
		cal.setTimeZone(TimeZone.getTimeZone("GMT"));
		cal.set(Calendar.HOUR_OF_DAY, 23);
		cal.set(Calendar.MINUTE, 59);
		Object[] endKey = {cal.getTimeInMillis()};
		Paginator scroll = cbClient.executeView(false, DES_DOC, VIEW_NAME, startKey, endKey);
		try {
			while (scroll.hasNext()) {
				ViewResponse resp = scroll.next();
				for (ViewRow viewRow : resp) {
					log.info("Validate market: {}", viewRow.getId()
							.substring(2));
					validateMarket(viewRow.getId());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			cbClient.shutdown();
		}
	}

	// WARNING: check only 1st horse
	private static void validateMarket(String marketDocId) {
		String marketDoc = cbClient.get(marketDocId);
		MarketBean market = null;
		try {
			market = om.readValue(marketDoc, MarketBean.class);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (market.getMarketStartTime() == null
				|| market.getEndMonitoringTime() == null
				|| market.getStartMonitoringTime() == null) {
			market.setValidated("FAIL");
			try {
				cbClient.set(marketDocId, om.writeValueAsString(market));
				log.error("Market {} is failed", marketDocId);
				return;
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
		if (market.getMarketStartTime().longValue() < market
				.getEndMonitoringTime().longValue()) {
			market.setValidated("OK");
		} else {
			market.setValidated("WARN");
		}
		Long horseId = market.getHorsesId().get(0);
		int cntOfProbes = market.getCntOfProbes();
		String monitoredDocId = marketDocId.substring(2) + "_" + horseId + "_";
		Long timestampPrev = null;
		Long timestampNext = null;
		String horseDoc = cbClient.get(monitoredDocId + "0");
		if (horseDoc != null) {
			try {
				HorseStatBean horse = om.readValue(horseDoc,
						HorseStatBean.class);
				timestampNext = timestampPrev = horse.getTimestamp();
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		long delta = 0;
		for (int i = 1; i < cntOfProbes; i++) {
			try {
				horseDoc = cbClient.get(monitoredDocId + i);
				if (horseDoc != null) {
					HorseStatBean horse = om.readValue(horseDoc,
							HorseStatBean.class);
					timestampNext = horse.getTimestamp();
					if (timestampNext - timestampPrev > DELTA) {
						delta += (timestampNext - timestampPrev);
						// log.info("{}: {} - {}", i, timestampNext,
						// timestampPrev);
					}
					timestampPrev = timestampNext;
				}
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				log.error("Excetpiont: {}", e);
			}
		}
		market.setMissedTime(delta);
		try {
			cbClient.set(marketDocId, om.writeValueAsString(market));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	}

	private static long getFirstMarketStartTime() {
		Calendar cal = Calendar.getInstance();
		cal.setTimeZone(TimeZone.getTimeZone("GMT"));
		cal.set(Calendar.HOUR_OF_DAY, 4);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
	}
}
