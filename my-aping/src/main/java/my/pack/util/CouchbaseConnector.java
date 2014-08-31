package my.pack.util;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import net.spy.memcached.FailureMode;

import com.couchbase.client.CouchbaseClient;
import com.couchbase.client.CouchbaseConnectionFactory;
import com.couchbase.client.CouchbaseConnectionFactoryBuilder;

public class CouchbaseConnector {

	public static HashMap<String, CouchbaseClient> clients = new HashMap<String, CouchbaseClient>();
//	private static final Logger log = LoggerFactory
//			.getLogger(CouchbaseConnector.class);

	public static CouchbaseClient getClient(String bucketName) {
		if (clients.get(bucketName) == null) {
			try {
				List<URI> uris = new LinkedList<URI>();
				for (String uri : CbConstants.COUCHBASE_URLS) {
					uris.add(URI.create(uri));
				}

				CouchbaseConnectionFactoryBuilder cfb = new CouchbaseConnectionFactoryBuilder();
				cfb.setOpTimeout(10000l);
				cfb.setFailureMode(FailureMode.Retry);
				CouchbaseConnectionFactory cf = cfb.buildCouchbaseConnection(
						uris, bucketName, "");

				clients.put(bucketName, new CouchbaseClient(cf));
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		return clients.get(bucketName);
	}

	public static void initClient(String bucketName) {
		getClient(bucketName);
	}

	public static void shutdownClient(String bucketName) {
		getClient(bucketName).shutdown();
		clients.put(bucketName, null);
	}

	public static void shutdownAllClients() {
		for (Entry<String, CouchbaseClient> client : clients.entrySet()) {
			shutdownClient(client.getKey());
		}
	}
}
