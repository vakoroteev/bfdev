package my.pack.algo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

import my.pack.model.HorseStatBean;
import my.pack.model.MarketBean;
import my.pack.util.CommonUtils;
import my.pack.util.CouchbaseHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.client.protocol.views.Paginator;
import com.couchbase.client.protocol.views.ViewResponse;
import com.couchbase.client.protocol.views.ViewRow;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PlotPreprocessor {

	private static final CouchbaseHandler cbClient = new CouchbaseHandler(
			"horses");
	private static final Logger log = LoggerFactory
			.getLogger(PlotPreprocessor.class);
	private static final ObjectMapper om = new ObjectMapper();
	private static final String VIEW_MARKETS_BY_START_DATE = "getAllMarkets";
	private static final String DES_DOC2 = "des1";

	public static void main(String[] args) {
		getAtbToFirstHorse();
	}

	public static void getAtbToFirstHorse() {
		long marketStartDate = CommonUtils.getFirstMarketStartTime(2014, 9, 8,
				4, 0, 0);
		Object[] startKey = { marketStartDate };
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(marketStartDate);
		cal.setTimeZone(TimeZone.getTimeZone("GMT"));
		cal.set(Calendar.HOUR_OF_DAY, 23);
		cal.set(Calendar.MINUTE, 59);
		Object[] endKey = { cal.getTimeInMillis() };
		Paginator scroll = cbClient.executeView(false, DES_DOC2,
				VIEW_MARKETS_BY_START_DATE, startKey, endKey);
		while (scroll.hasNext()) {
			ViewResponse resp = scroll.next();
			for (ViewRow viewRow : resp) {
				log.info("Process market: {}", viewRow.getId().substring(2));
				String marketId = viewRow.getId();
				String marketJson = cbClient.get(marketId);
				MarketBean market = null;
				try {
					market = om.readValue(marketJson, MarketBean.class);
				} catch (JsonParseException e) {
					e.printStackTrace();
				} catch (JsonMappingException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				Long horseId = market.getHorsesId().get(0);
				int cntOfProbes = market.getCntOfProbes();
				String horseDoc = null;
				String monitoredDocId = marketId.substring(2) + "_" + horseId
						+ "_";
				BufferedWriter bw = null;
				try {
					bw = new BufferedWriter(new FileWriter(new File(monitoredDocId)));
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				for (int i = 0; i < cntOfProbes; i++) {
					try {
						horseDoc = cbClient.get(monitoredDocId + i);
						if (horseDoc != null) {
							HorseStatBean horseBean = om.readValue(horseDoc,
									HorseStatBean.class);
							Long timestamp = horseBean.getTimestamp();
							Double price = null;
							if (horseBean.getEx().getAvailableToBack().size() != 0) {
								price = horseBean.getEx().getAvailableToBack()
										.get(0).getPrice();
							} else {
								price = 0D;
							}
							Double totalMatched = horseBean.getTotalMatched();
							bw.write(timestamp + "," + price + ","
									+ totalMatched);
							bw.newLine();
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
				try {
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
