package my.pack.algo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import my.pack.model.HorseStatBean;
import my.pack.model.MarketBean;
import my.pack.model.StartPrice;
import my.pack.util.AccountConstants;
import my.pack.util.ApplicationConstants;
import my.pack.util.CouchbaseConnector;
import my.pack.util.HttpClientSSO;
import net.spy.memcached.PersistTo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.betfair.aping.api.ApiNgJsonRpcOperations;
import com.betfair.aping.entities.ExchangePrices;
import com.betfair.aping.entities.MarketBook;
import com.betfair.aping.entities.MarketCatalogue;
import com.betfair.aping.entities.MarketFilter;
import com.betfair.aping.entities.PriceProjection;
import com.betfair.aping.entities.Runner;
import com.betfair.aping.entities.StartingPrices;
import com.betfair.aping.entities.TimeRange;
import com.betfair.aping.enums.MarketProjection;
import com.betfair.aping.enums.MarketSort;
import com.betfair.aping.enums.MatchProjection;
import com.betfair.aping.enums.OrderProjection;
import com.betfair.aping.enums.PriceData;
import com.betfair.aping.exceptions.APINGException;
import com.couchbase.client.CouchbaseClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StatCollector {

	private static final String MARKET_COUNTRY = "GB";

	private static final String SESSION_TOKEN = "sessionToken";

	private static final Logger log = LoggerFactory
			.getLogger(StatCollector.class);
	private static final ObjectMapper om = new ObjectMapper();
	private static final CouchbaseClient cbClient = CouchbaseConnector
			.getClient("horses");
	private static final ApiNgJsonRpcOperations rpcOperator = ApiNgJsonRpcOperations
			.getInstance();
	final static String ssoId = getSessionToken();

	public static void main(String[] args) {
		try {
			final int RUNNERS_CNT = 3;
			final String MARKET_CNT = "20";
			final int MARKET_CNT_FOR_MARKET_BOOK = 5;

			HashMap<String, Integer> marketCounters = new HashMap<String, Integer>();
			List<MarketCatalogue> marketCatalogues = listMarketCatalogue(
					rpcOperator, MARKET_CNT, ssoId);
			createMarketsDescriptions(marketCatalogues);
			long startTime = marketCatalogues.get(0).getDescription()
					.getMarketTime().getTime();
			waitFirstMarket(startTime);
			Queue<String> allMarketIds = new LinkedList<String>();
			for (MarketCatalogue marketCatalogue : marketCatalogues) {
				String marketId = marketCatalogue.getMarketId();
				allMarketIds.add(marketId);
				if (marketCounters.get(marketId) == null) {
					marketCounters.put(marketId, 0);
				}
			}
			// Prepare params for listMarketBook
			List<String> marketIds = new ArrayList<String>();
			for (int k = 0; k < MARKET_CNT_FOR_MARKET_BOOK; k++) {
				// TODO: if poll return null???
				marketIds.add(allMarketIds.poll());
			}

			for (String marketId : marketIds) {
				setStartMonitoringTime(marketId);
			}

			boolean observ = true;
			HashMap<String, Integer> mapToRemove = new HashMap<String, Integer>();
			while (observ) {
				try {
					PriceProjection priceProjection = new PriceProjection();
					Set<PriceData> priceData = new HashSet<PriceData>();
					priceData.add(PriceData.EX_ALL_OFFERS);
					priceData.add(PriceData.EX_TRADED);
					priceData.add(PriceData.SP_AVAILABLE);
					priceData.add(PriceData.SP_TRADED);
					priceProjection.setPriceData(priceData);

					final String appKey = AccountConstants.APP_KEY;
					List<MarketBook> listMarketBook = null;
					try {
						listMarketBook = rpcOperator.listMarketBook(marketIds,
								priceProjection, OrderProjection.ALL,
								MatchProjection.NO_ROLLUP, MARKET_COUNTRY,
								appKey, ssoId);
						for (MarketBook marketBook : listMarketBook) {
							// TODO: add counter on status. Stop after 5
							// suspended or closed
							if (marketBook.getStatus().equalsIgnoreCase(
									"suspended")
									|| marketBook.getStatus().equalsIgnoreCase(
											"closed")) {
								// to avoid fluctuations
								Integer cntOfNonActive = mapToRemove
										.get(marketBook.getMarketId());
								if (cntOfNonActive != null) {
									mapToRemove.put(marketBook.getMarketId(),
											++cntOfNonActive);
								} else {
									mapToRemove
											.put(marketBook.getMarketId(), 1);
								}
								log.info("Market {} was {}",
										marketBook.getMarketId(),
										marketBook.getStatus());
							}
						}

						for (Entry<String, Integer> en : mapToRemove.entrySet()) {
							if (en.getValue() > 20) {
								for (int i = 0; i < marketIds.size(); i++) {
									if (marketIds.get(0).equals(en.getKey())) {
										String marketId = marketIds.remove(i);
										mapToRemove.remove(en.getKey());
										setEndMonitoringTimeAndCntOfProbes(
												marketId,
												marketCounters.get(marketId));
										if (allMarketIds.peek() != null) {
											String poll = allMarketIds.poll();
											log.info(
													"New makret is monitoring: {}",
													poll);
											marketIds.add(poll);
										} else if (marketIds.size() == 0) {
											// stop observer or maybe something
											// else
											observ = false;
										}
									}
								}
							}
						}
					} catch (APINGException e) {
						e.printStackTrace();
					}
					for (MarketBook marketBook : listMarketBook) {
						String marketId = marketBook.getMarketId();
						Integer cnt = marketCounters.get(marketId);
						if (cnt == 0) {
							setHorsesAndStartMonitoringTime(marketId,
									marketBook.getRunners(), RUNNERS_CNT);
						}
						String num = String.valueOf(cnt);
						marketCounters.put(marketId, ++cnt);
						List<Runner> runners = marketBook.getRunners();
						int realCntOfRunners = 0;
						if (runners.size() < RUNNERS_CNT) {
							realCntOfRunners = runners.size();
						} else {
							realCntOfRunners = RUNNERS_CNT;
						}
						for (int i = 0; i < realCntOfRunners; i++) {
							// TODO: if runner not actual?
							Runner runner = runners.get(i);
							Long selectionId = runner.getSelectionId();
							Double totalMatched = runner.getTotalMatched();
							ExchangePrices ex = runner.getEx();
							StartingPrices sp = runner.getSp();
							StartPrice startPrice = null;
							if (sp != null) {
								startPrice = new StartPrice(sp.getActualSP(),
										sp.getFarPrice(), sp.getNearPrice());
							}
							// TODO: use Calendar!!!
							long timestamp = System.currentTimeMillis();
							HorseStatBean horse = new HorseStatBean(
									totalMatched, ex, startPrice, timestamp);
							try {
								String doc = om.writeValueAsString(horse);
								// TODO: think about async
								cbSet(marketId + "_" + selectionId + "_" + num,
										doc);
							} catch (JsonProcessingException e) {
								e.printStackTrace();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				} catch (Exception e) {
					log.error("Exception while observ: ", e);
				}
			}
		} finally {
			cbClient.shutdown();
		}
	}

	private static void setHorsesAndStartMonitoringTime(String marketId,
			List<Runner> runners, int runnersCnt) {
		String cbMarketId = "m_" + marketId;
		String doc = (String) cbClient.get(cbMarketId);
		MarketBean market;
		try {
			market = om.readValue(doc, MarketBean.class);
			market.setStartMonitoringTime(System.currentTimeMillis());
			int realCntOfRunners = 0;
			if (runners.size() < runnersCnt) {
				realCntOfRunners = runners.size();
			} else {
				realCntOfRunners = runnersCnt;
			}
			ArrayList<Long> horsesId = new ArrayList<Long>();
			for (int i = 0; i < realCntOfRunners; i++) {
				horsesId.add(runners.get(i).getSelectionId());
			}
			market.setHorsesId(horsesId);
			doc = om.writeValueAsString(market);
		} catch (IOException e) {
			e.printStackTrace();
		}
		cbSet(cbMarketId, doc);
	}

	private static void setEndMonitoringTimeAndCntOfProbes(String marketId,
			Integer cntOfProbes) {
		String id = "m_" + marketId;
		String doc = (String) cbClient.get(id);
		if (doc != null) {
			try {
				MarketBean market = om.readValue(doc, MarketBean.class);
				market.setEndMonitoringTime(System.currentTimeMillis());
				market.setCntOfProbes(cntOfProbes);
				doc = om.writeValueAsString(market);
				cbSet(id, doc);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void setStartMonitoringTime(String marketId) {
		String docId = "m_" + marketId;
		String doc = (String) cbClient.get(docId);
		if (doc != null) {
			try {
				MarketBean market = om.readValue(doc, MarketBean.class);
				market.setStartMonitoringTime(System.currentTimeMillis());
				doc = om.writeValueAsString(market);
				cbSet(docId, doc);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void createMarketsDescriptions(
			List<MarketCatalogue> marketCatalogues) {
		String docId = null;
		Long marketStartTime;
		String docBody = null;
		MarketBean market = new MarketBean();
		for (MarketCatalogue marketCatalogue : marketCatalogues) {
			docId = "m_" + marketCatalogue.getMarketId();
			marketStartTime = marketCatalogue.getDescription().getMarketTime()
					.getTime();
			market.setMarketStartTime(marketStartTime);
			try {
				docBody = om.writeValueAsString(market);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
			cbSet(docId, docBody);
		}
	}

	private static void waitFirstMarket(long startTime) {
		long HOUR_IN_MILLIS = 60L * 60L * 1000L;
		long timeToStart = startTime - System.currentTimeMillis();
		if (timeToStart > HOUR_IN_MILLIS) {
			long sleepTime = timeToStart - HOUR_IN_MILLIS;
			log.info("Sleeping time - {}", sleepTime);
			// keep alive every 10 seconds
			long period = 10000L;
			int cntOfKeepAlivedRequests = (int) (sleepTime / 10000L);
			while (cntOfKeepAlivedRequests > 0) {
				try {
					Thread.sleep(period);
					MarketFilter filter = new MarketFilter();
					filter.setInPlayOnly(true);
					rpcOperator.listEventTypes(filter,
							AccountConstants.APP_KEY, ssoId);
					log.info("Remain {} periods", cntOfKeepAlivedRequests);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				} catch (APINGException e) {
					log.error("Exception while keepAlived: {}", e);
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
				cntOfKeepAlivedRequests--;
			}
		}
	}

	public static List<MarketCatalogue> listMarketCatalogue(
			ApiNgJsonRpcOperations rpcOperator, String maxResult,
			String sessionToken) {
		final String ssoId = sessionToken;
		final String appKey = AccountConstants.APP_KEY;

		MarketFilter filter = new MarketFilter();
		Set<String> eventTypeIds = new HashSet<String>();
		eventTypeIds.add(ApplicationConstants.HORSE_EVENT_TYPE);
		filter.setEventTypeIds(eventTypeIds);
		Set<String> marketCountries = new HashSet<String>();
		marketCountries.add(MARKET_COUNTRY);
		filter.setMarketCountries(marketCountries);
		TimeRange time = new TimeRange();
		time.setFrom(new Date());
		filter.setMarketStartTime(time);
		Set<String> marketTypeCodes = new HashSet<String>();
		marketTypeCodes.add("WIN");
		// marketTypeCodes.add("PLACE");
		filter.setMarketTypeCodes(marketTypeCodes);
		Set<MarketProjection> marketProjection = new HashSet<MarketProjection>();
		// marketProjection.add(MarketProjection.RUNNER_DESCRIPTION);
		marketProjection.add(MarketProjection.MARKET_DESCRIPTION);
		marketProjection.add(MarketProjection.MARKET_START_TIME);
		final String MAX_RESULT = maxResult;
		List<MarketCatalogue> listMarketCatalogue = null;
		try {
			listMarketCatalogue = rpcOperator.listMarketCatalogue(filter,
					marketProjection, MarketSort.FIRST_TO_START, MAX_RESULT,
					appKey, ssoId);
		} catch (APINGException e) {
			e.printStackTrace();
		}
		return listMarketCatalogue;
	}

	// TODO: how to process wrong session token - exception or errorCode
	private static String getSessionToken() {
		JsonNode jsonNode = null;
		String sessionToken = null;
		try {
			String sessionResponse = null;
			if ((sessionResponse = HttpClientSSO.getSessionTokenResponse()) != null) {
				jsonNode = om.readTree(sessionResponse);
				sessionToken = jsonNode.get(SESSION_TOKEN).toString();
				sessionToken = sessionToken.substring(1,
						sessionToken.length() - 1);
				log.info("Session token: {}", sessionToken);
			} else {
				log.error("Getting null session token from BetFair");
			}
		} catch (IOException e) {
			log.error("Exception while processing session token: {}", e);
		}
		return sessionToken;
	}

	/**
	 * PersistTo equals to PersistTo.ZERO
	 * 
	 * @param docId
	 * @param docBody
	 */
	private static void cbSet(String docId, String docBody) {
		try {
			cbClient.set(docId, docBody, PersistTo.ZERO).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unused")
	private static void cbSet(String docId, String docBody, PersistTo persist) {
		try {
			cbClient.set(docId, docBody, persist).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}
}
