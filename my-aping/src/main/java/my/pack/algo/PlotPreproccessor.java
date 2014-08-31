package my.pack.algo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import my.pack.model.HorseStatBean;
import my.pack.model.MarketBean;
import my.pack.util.CouchbaseHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.betfair.aping.entities.ExchangePrices;
import com.betfair.aping.entities.PriceSize;
import com.couchbase.client.protocol.views.Paginator;
import com.couchbase.client.protocol.views.ViewResponse;
import com.couchbase.client.protocol.views.ViewRow;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extract prices from horses and generate file for GNUPlot
 * 
 * @author VLD
 * 
 */
public class PlotPreproccessor {

	private static final String DES_DOC = "des1";
	private static final String VIEW_NAME = "getAllMarkets";
	private static final CouchbaseHandler cbClient = new CouchbaseHandler(
			"horses");
	private static final ObjectMapper om = new ObjectMapper();
	private static final Logger log = LoggerFactory
			.getLogger(PlotPreproccessor.class);

	public static void main(String[] args) {
		Paginator scroll = cbClient.executeView(false, DES_DOC, VIEW_NAME);
		while (scroll.hasNext()) {
			ViewResponse resp = scroll.next();
			for (ViewRow viewRow : resp) {
				String marketDocId = viewRow.getId();
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
				String validateStatus = null;
				if ((validateStatus = market.getValidated()) != null
						&& !validateStatus.equalsIgnoreCase("FAIL")
						&& market.getProcessed() == null) {
					processMarket(marketDocId);
				}
			}
		}
		cbClient.shutdown();
	}

	private static void processMarket(String marketDocId) {
		MarketBean market = null;
		HashMap<Double, BufferedWriter> backBw = null;
		HashMap<Double, BufferedWriter> layBw = null;
		String horseDocId = null;
		try {
			String marketDoc = cbClient.get(marketDocId);
			market = om.readValue(marketDoc, MarketBean.class);
			ArrayList<Long> horsesId = market.getHorsesId();
			for (Long horseId : horsesId) {
				Integer cntOfProbes = market.getCntOfProbes();
				horseDocId = marketDocId.substring(2) + "_" + horseId + "_";
				String filePathBack = "C:\\new-bf\\" + horseDocId + "\\back\\";
				String filePathLay = "C:\\new-bf\\" + horseDocId + "\\lay\\";
				backBw = createOutputFiles(horseDocId, cntOfProbes, true);
				layBw = createOutputFiles(horseDocId, cntOfProbes, false);
				for (int i = 0; i < cntOfProbes; i++) {
					String horseDoc = cbClient.get(horseDocId + i);
					if (horseDoc == null) {
						continue;
					}
					try {
						HorseStatBean horse = om.readValue(horseDoc,
								HorseStatBean.class);
						long timestamp = horse.getTimestamp();
						ExchangePrices ex = horse.getEx();
						/**
						 * Columns: 1st - timestamp, 2nd - avb, 3rd - avl, 4th -
						 * tot mat, 5th - startPrice
						 */
						String raw = String.valueOf(timestamp) + " ";
						List<PriceSize> availableToBack = ex
								.getAvailableToBack();
						for (PriceSize priceSize : availableToBack) {
							BufferedWriter bw = backBw
									.get(priceSize.getPrice());
							if (bw == null) {
								Double price = priceSize.getPrice();
								bw = new BufferedWriter(new FileWriter(
										new File(filePathBack + price)));
								backBw.put(price, bw);
							}
							bw.write(raw + priceSize.getSize());
							bw.newLine();
						}
						List<PriceSize> availableToLay = ex.getAvailableToLay();
						for (PriceSize priceSize : availableToLay) {
							BufferedWriter bw = layBw.get(priceSize.getPrice());
							if (bw == null) {
								Double price = priceSize.getPrice();
								bw = new BufferedWriter(new FileWriter(
										new File(filePathLay + price)));
								layBw.put(price, bw);
							}
							bw.write(raw + priceSize.getSize());
							bw.newLine();
						}
					} catch (JsonParseException e) {
						e.printStackTrace();
					} catch (JsonMappingException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			log.error("Exception while plotProcessing {}", e);
		} finally {
			for (Entry<Double, BufferedWriter> en : backBw.entrySet()) {
				try {
					en.getValue().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			for (Entry<Double, BufferedWriter> en : layBw.entrySet()) {
				try {
					en.getValue().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			market.setProcessed(true);
			try {
				cbClient.set(marketDocId, om.writeValueAsString(market));
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
			log.info("Horse {} is processed", horseDocId);
		}
	}

	/**
	 * 
	 * @param horseDocId
	 * @param cntOfProbes
	 * @param type
	 *            - back = true, lay = false;
	 * @return
	 */
	private static HashMap<Double, BufferedWriter> createOutputFiles(
			String horseDocId, Integer cntOfProbes, boolean type) {
		// create directory
		String filePath = "C:\\bf\\" + horseDocId + "\\";
		if (type) {
			filePath += "back\\";
		} else {
			filePath += "lay\\";
		}
		new File(filePath).mkdirs();
		String horseDoc = null;
		int i = 1;
		while (horseDoc == null) {
			horseDoc = cbClient.get(horseDocId + (cntOfProbes - i));
			i++;
			if (cntOfProbes - i < 0) {
				log.error("Fucking fuck {}", horseDocId);
			}
		}
		HorseStatBean horse = null;
		HashMap<Double, BufferedWriter> map = null;
		try {
			horse = om.readValue(horseDoc, HorseStatBean.class);
			ExchangePrices ex = horse.getEx();
			map = new HashMap<Double, BufferedWriter>();
			List<PriceSize> priceSizes = null;
			if (type) {
				priceSizes = ex.getAvailableToBack();
			} else {
				priceSizes = ex.getAvailableToLay();
			}
			for (PriceSize priceSize : priceSizes) {
				BufferedWriter bw = new BufferedWriter(new FileWriter(new File(
						filePath + priceSize.getPrice())));
				map.put(priceSize.getPrice(), bw);
			}
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return map;
	}

}
