package my.pack.algo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import my.pack.model.FirstPriceValueChanged;
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

	private static final String NA_N = "0,";
	private static final String MARKET_LIST_TXT = "market_list.txt";
	private static final String PRICE_POWER_CSV = "price_power.csv";
	private static final String MARKETS_CSV = "markets.csv";
	private static final CouchbaseHandler cbClient = new CouchbaseHandler(
			"fast_horses");
	private static final Logger log = LoggerFactory
			.getLogger(PlotPreprocessor.class);
	private static final ObjectMapper om = new ObjectMapper();
	private static final String VIEW_NAME = "getAllMarkets";
	private static final String DES_DOC = "des1";

	public static void main(String[] args) {
		// getAtbToFirstHorse(3);
		// createMarketCsv();
		// createMarketList();
		// createMarketRegExp(0, 60);
		createPricePowerCsv(10);
		cbClient.shutdown();
	}

	public static void createMarketRegExp(int startMarketInd, int endMarketInd) {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(new File(MARKET_LIST_TXT)));
			String str = null;
			StringBuffer regExpString = new StringBuffer();
			int i = 0;
			while ((i < endMarketInd) && (str = br.readLine()) != null) {
				if (i >= startMarketInd) {
					regExpString.append(str + ".*|");
				}
				i++;
			}
			System.out.println(regExpString.substring(0,
					regExpString.length() - 1));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void createMarketList() {
		Paginator scroll = cbClient.executeView(false, DES_DOC, VIEW_NAME);
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(new File(MARKET_LIST_TXT)));
			while (scroll.hasNext()) {
				ViewResponse resp = scroll.next();
				for (ViewRow viewRow : resp) {
					String id = viewRow.getId().substring(2);
					bw.write(id);
					bw.newLine();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				bw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 
	 * @param marketDepth
	 *            - depth in back and lay
	 */
	public static void createPricePowerCsv(int marketDepth) {
		Paginator scroll = cbClient.executeView(false, DES_DOC, VIEW_NAME);
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(new File(PRICE_POWER_CSV)));
			while (scroll.hasNext()) {
				ViewResponse resp = scroll.next();
				for (ViewRow viewRow : resp) {
					String marketId = viewRow.getId();
					log.info("Process market: {}", marketId);
					MarketBean market = getMarketBeanFromCb(marketId);
					// get only first horse for process
					Long horseId = market.getHorsesId().get(0);
					createPricePowerDoc(bw, marketId, horseId,
							market.getCntOfProbes(), marketDepth);
					bw.write(marketId.substring(2) + ","
							+ market.getMarketStartTime() + "," + horseId);
					break;
				}
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				bw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private static void createPricePowerDoc(BufferedWriter bw, String marketId,
			Long horseId, Integer cntOfProbes, int marketDepth) {
		List<PriceSize> subListBack = new ArrayList<PriceSize>(marketDepth);
		List<PriceSize> subListLay = new ArrayList<PriceSize>(marketDepth);
		int differentProbesCnt = 0;
		for (int i = 0; i < cntOfProbes; i++) {
			String docId = marketId.substring(2) + "_" + horseId + "_" + i;
			String jsonHorse = cbClient.get(docId);
			try {
				HorseStatBean horse = om.readValue(jsonHorse,
						HorseStatBean.class);
				List<PriceSize> backPrices = horse.getEx().getAvailableToBack();
				List<PriceSize> layPrices = horse.getEx().getAvailableToLay();
				List<PriceSize> tmpBackSublist;
				List<PriceSize> tmpLaySublist;
				if (backPrices.size() < marketDepth) {
					tmpBackSublist = backPrices.subList(0, backPrices.size());
				} else {
					tmpBackSublist = backPrices.subList(0, marketDepth);
				}
				if (layPrices.size() < marketDepth) {
					tmpLaySublist = layPrices.subList(0, layPrices.size());
				} else {
					tmpLaySublist = layPrices.subList(0, marketDepth);
				}
				boolean isPriceChanged = comparePrices(subListBack, subListLay,
						tmpBackSublist, tmpLaySublist, marketDepth);
				if (isPriceChanged) {
					Long timestamp = horse.getTimestamp();
					bw.write(String.valueOf(timestamp) + ",");
					FirstPriceValueChanged[] changedPrice = getChangedPrice(
							subListBack, subListLay, tmpBackSublist,
							tmpLaySublist);
					bw.write(changedPrice[0] + "," + changedPrice[1] + ",");
					subListBack = tmpBackSublist;
					subListLay = tmpLaySublist;
					differentProbesCnt++;
					for (int j = marketDepth - 1; j >= 0; j--) {
						if (backPrices.size() > j) {
							bw.write(String.valueOf(backPrices.get(j)
									.getPrice()) + ",");
						} else {
							bw.write(NA_N);
						}
					}
					for (int j = 0; j < marketDepth; j++) {
						if (layPrices.size() > j) {
							bw.write(String
									.valueOf(layPrices.get(j).getPrice()) + ",");
						} else {
							bw.write(NA_N);
						}
					}
					for (int j = marketDepth - 1; j >= 0; j--) {
						if (backPrices.size() > j) {
							bw.write(String
									.valueOf(backPrices.get(j).getSize()) + ",");
						} else {
							bw.write(NA_N);
						}
					}
					for (int j = 0; j < marketDepth; j++) {
						if (layPrices.size() > j) {
							bw.write(String.valueOf(layPrices.get(j).getSize())
									+ ",");
						} else {
							bw.write(NA_N);
						}
					}

					Double lowPrice = null;
					if (backPrices.size() > marketDepth) {
						lowPrice = backPrices.get(marketDepth - 1).getPrice();
					} else if (backPrices.size() > 0) {
						lowPrice = backPrices.get(backPrices.size() - 1)
								.getPrice();
					}
					Double maxPrice = null;
					if (layPrices.size() > marketDepth) {
						maxPrice = layPrices.get(marketDepth - 1).getPrice();
						if (lowPrice == null) {
							lowPrice = layPrices.get(0).getPrice();
						}
					}
					final double LOW_PRICE = lowPrice;
					final double MAX_PRICE = maxPrice;
					List<PriceSize> tv = horse.getEx().getTradedVolume();
					List<PriceSize> filtredTv = tv
							.stream()
							.filter(ps -> ps.getPrice() >= LOW_PRICE
									&& ps.getPrice() <= MAX_PRICE)
							.collect(Collectors.toList());
					filtredTv.stream().forEachOrdered(ps -> {
						try {
							bw.write(String.valueOf(ps.getPrice()) + ",");
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
					filtredTv.stream().forEachOrdered(ps -> {
						try {
							bw.write(String.valueOf(ps.getSize()) + ",");
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
					bw.newLine();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			log.info("Different probes: {}", differentProbesCnt);
		}
	}

	/**
	 * Get changed value of first price
	 * 
	 * @param subListBack
	 * @param subListLay
	 * @param tmpBackSublist
	 * @param tmpLaySublist
	 */
	private static FirstPriceValueChanged[] getChangedPrice(
			List<PriceSize> subListBack, List<PriceSize> subListLay,
			List<PriceSize> tmpBackSublist, List<PriceSize> tmpLaySublist) {
		FirstPriceValueChanged f[] = new FirstPriceValueChanged[2];
		if (subListBack.size() == 0 || subListLay.size() == 0) {
			f[0] = FirstPriceValueChanged.EQ;
			f[1] = FirstPriceValueChanged.EQ;
			return f;
		}
		Double backPrice = subListBack.get(subListBack.size() - 1).getPrice();
		Double layPrice = subListLay.get(0).getPrice();
		if (tmpBackSublist.size() == 0) {
			f[0] = FirstPriceValueChanged.EQ;
		} else {
			int compareTo = backPrice.compareTo(tmpBackSublist.get(
					tmpBackSublist.size() - 1).getPrice());
			if (compareTo == 0) {
				f[0] = FirstPriceValueChanged.EQ;
			} else if (compareTo == 1) {
				f[0] = FirstPriceValueChanged.BD;
			} else {
				f[0] = FirstPriceValueChanged.BR;
			}
		}
		if (tmpLaySublist.size() == 0) {
			f[1] = FirstPriceValueChanged.EQ;
		} else {
			int compareTo = layPrice.compareTo(tmpLaySublist.get(0).getPrice());
			if (compareTo == 0) {
				f[1] = FirstPriceValueChanged.EQ;
			} else if (compareTo == 1) {
				f[1] = FirstPriceValueChanged.LD;
			} else {
				f[1] = FirstPriceValueChanged.LR;
			}
		}
		return f;
	}

	private static boolean comparePrices(List<PriceSize> saveBack,
			List<PriceSize> saveLay, List<PriceSize> back, List<PriceSize> lay,
			int marketDepth) {
		if (saveBack.size() != back.size() || saveLay.size() != lay.size()) {
			return true;
		}
		for (int i = 0; i < back.size(); i++) {
			if (!saveLay.get(i).getPrice().equals(lay.get(i).getPrice())
					|| !saveBack.get(i).getPrice()
							.equals(back.get(i).getPrice())) {
				return true;
			}
			if (!saveLay.get(i).getSize().equals(lay.get(i).getSize())
					|| !saveBack.get(i).getSize().equals(back.get(i).getSize())) {
				return true;
			}
		}
		return false;
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
		while (scroll.hasNext()) {
			ViewResponse resp = scroll.next();
			// for each market
			for (ViewRow viewRow : resp) {
				log.info("Process market: {}", viewRow.getId().substring(2));
				String marketId = viewRow.getId();
				MarketBean market = getMarketBeanFromCb(marketId);
				for (int j = 0; j < horseCnt; j++) {
					Long horseId = null;
					if (market.getHorsesId().size() < j) {
						horseId = market.getHorsesId().get(j);
					}
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
	}

	/**
	 * Creates csv-string with market information
	 * 
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
