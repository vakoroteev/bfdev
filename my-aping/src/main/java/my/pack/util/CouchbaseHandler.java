package my.pack.util;

import java.util.concurrent.ExecutionException;

import net.spy.memcached.PersistTo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.client.CouchbaseClient;
import com.couchbase.client.protocol.views.ComplexKey;
import com.couchbase.client.protocol.views.Paginator;
import com.couchbase.client.protocol.views.Query;
import com.couchbase.client.protocol.views.Stale;
import com.couchbase.client.protocol.views.View;

public class CouchbaseHandler {

	private CouchbaseClient client;

	private static final Logger log = LoggerFactory
			.getLogger(CouchbaseHandler.class);

	public CouchbaseHandler(String bucket) {
		client = CouchbaseConnector.getClient(bucket);
		if (client == null) {
			log.error("There isn't client for bucket {}", bucket);
		}
	}

	public boolean set(String docId, String docBody) {
		boolean result = false;
		try {
			result = client.set(docId, docBody, PersistTo.ZERO).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return result;
	}

	public boolean set(String docId, String docBody, PersistTo persist) {

		boolean result = false;
		try {
			result = client.set(docId, docBody, persist).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return result;
	}

	public String get(String docId) {

		String result = null;
		if (client != null) {
			result = (String) client.get(docId);
		}
		return result;
	}

	public Paginator executeView(boolean useReduce, String desDoc,
			String viewName, Object[] keys) {

		View view = client.getView(desDoc, viewName);
		Query query = new Query();
		if (keys != null) {
			ComplexKey key = ComplexKey.of(keys);
			query.setKey(key);
		}
		query.setStale(Stale.FALSE);
		query.setReduce(useReduce);
		return client.paginatedQuery(view, query, 1000);
	}

	public Paginator executeView(boolean useReduce, String desDoc,
			String viewName) {

		View view = client.getView(desDoc, viewName);
		Query query = new Query();
		query.setStale(Stale.FALSE);
		query.setReduce(useReduce);
		return client.paginatedQuery(view, query, 1000);
	}

	public Paginator executeView(boolean useReduce, String desDoc,
			String viewName, Object[] startKeys, Object[] endKeys) {

		View view = client.getView(desDoc, viewName);
		Query query = new Query();
		if (startKeys != null && endKeys != null) {
			ComplexKey startKey = ComplexKey.of(startKeys);
			ComplexKey endKey = ComplexKey.of(endKeys);
			query.setRange(startKey, endKey);
		}
		query.setStale(Stale.FALSE);
		query.setReduce(useReduce);
		return client.paginatedQuery(view, query, 1000);
	}

	public void shutdown() {
		client.shutdown();
	}

}
