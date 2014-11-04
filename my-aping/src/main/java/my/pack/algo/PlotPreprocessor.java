package my.pack.algo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import my.pack.model.HorseStatBean;
import my.pack.model.MarketBean;
import my.pack.util.CouchbaseHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.betfair.aping.entities.PriceSize;
import com.couchbase.client.protocol.views.Paginator;
import com.couchbase.client.protocol.views.ViewResponse;
import com.couchbase.client.protocol.views.ViewRow;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PlotPreprocessor {

	private static final String MARKETS_CSV = "markets.csv";
	private static final CouchbaseHandler cbClient = new CouchbaseHandler(
			"horses");
	private static final Logger log = LoggerFactory
			.getLogger(PlotPreprocessor.class);
	private static final ObjectMapper om = new ObjectMapper();
	private static final String VIEW_NAME = "getAllMarkets";
	private static final String DES_DOC = "des1";

	public static void main(String[] args) {
		// getAtbToFirstHorse(3);
		createMarketCsv();
		cbClient.shutdown();
	}

	public static void createMarketCsv() {
		Paginator scroll = cbClient.executeView(false, DES_DOC, VIEW_NAME);
		// for each portion of scroll
		BufferedWriter bwm = null;
		try {
			bwm = new BufferedWriter(new FileWriter(new File(MARKETS_CSV)));
			while (scroll.hasNext()) {
				ViewResponse resp = scroll.next();
				// for each market
				for (ViewRow viewRow : resp) {
					log.info("Process market: {}", viewRow.getId().substring(2));
					String marketId = viewRow.getId();
					MarketBean market = getMarketBeanFromCb(marketId);
					createMarketDoc(bwm, market.getMarketStartTime(),
							market.getHorsesId(), marketId);
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			try {
				bwm.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void getAtbToFirstHorse(final int horseCnt) {
		Paginator scroll = cbClient.executeView(false, DES_DOC, VIEW_NAME);
		// for each portion of scroll
		BufferedWriter bwm = null;
		try {
			bwm = new BufferedWriter(new FileWriter(new File(MARKETS_CSV)));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		while (scroll.hasNext()) {
			ViewResponse resp = scroll.next();
			// for each market
			for (ViewRow viewRow : resp) {
				log.info("Process market: {}", viewRow.getId().substring(2));
				String marketId = viewRow.getId();
				MarketBean market = getMarketBeanFromCb(marketId);
				Long horseIds[] = new Long[horseCnt];
				for (int i = 0; i < horseCnt; i++) {
					horseIds[i] = market.getHorsesId().get(i);
				}
				createMarketDoc(bwm, market.getMarketStartTime(),
						market.getHorsesId(), marketId);
				for (int j = 0; j < horseCnt; j++) {
					Long horseId = market.getHorsesId().get(j);
					int cntOfProbes = market.getCntOfProbes();
					String horseDoc = null;
					String monitoredDocId = marketId.substring(2) + "_"
							+ horseId + "_";
					BufferedWriter bw = null;
					try {
						bw = new BufferedWriter(new FileWriter(new File(
								monitoredDocId + j)));
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					for (int i = 0; i < cntOfProbes; i++) {
						try {
							horseDoc = cbClient.get(monitoredDocId + i);
							if (horseDoc != null) {
								HorseStatBean horseBean = om.readValue(
										horseDoc, HorseStatBean.class);
								Long timestamp = horseBean.getTimestamp();
								Double price = null;
								if (horseBean.getEx().getAvailableToBack()
										.size() != 0) {
									price = horseBean.getEx()
											.getAvailableToBack().get(0)
											.getPrice();
								} else {
									price = 0D;
								}
								Double totalMatched = horseBean
										.getTotalMatched();
								Double farPrice = horseBean.getStartPrice()
										.getFarFpice();
								Double nearPrice = horseBean.getStartPrice()
										.getNearPrice();

								int depth = 3;
								int wom;
								if (horseBean.getEx().getAvailableToBack()
										.size() >= depth
										&& horseBean.getEx()
												.getAvailableToLay().size() >= depth) {
									wom = calculateWom(horseBean.getEx()
											.getAvailableToBack(), horseBean
											.getEx().getAvailableToLay(), depth);
								} else {
									wom = 0;
								}
								bw.write(timestamp + "," + price + ","
										+ totalMatched + "," + wom + ","
										+ farPrice + "," + nearPrice);
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
		try {
			bwm.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates csv-string with market information
	 * @param bwm
	 * @param marketStartTime
	 * @param horsesIds
	 * @param marketId
	 */
	private static void createMarketDoc(BufferedWriter bwm,
			Long marketStartTime, ArrayList<Long> horsesIds, String marketId) {
		String cutMarketId = marketId.substring(2, marketId.length());
		try {
			bwm.write(marketStartTime + ",");
			for (int i = 0; i < horsesIds.size(); i++) {
				if (i != horsesIds.size() - 1) {
					bwm.write(cutMarketId + "_" + horsesIds.get(i) + "_" + i
							+ ",");
				} else {
					bwm.write(cutMarketId + "_" + horsesIds.get(i) + "_" + i);
				}
			}
			bwm.newLine();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static int calculateWom(List<PriceSize> atb, List<PriceSize> atl,
			int depth) {
		int wom = 0;
		for (int i = 0; i < depth; i++) {
			wom = (int) (atb.get(i).getSize() - atl.get(i).getSize());
		}
		return wom;
	}

	private static MarketBean getMarketBeanFromCb(String marketId) {
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
		return market;
	}
}
