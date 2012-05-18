package net.vvakame.memvache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.MemcacheServicePb;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.DatastorePb;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Path;
import com.google.storage.onestore.v3.OnestoreEntity.Path.Element;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;

public class MemvacheDelegate implements ApiProxy.Delegate<Environment> {

	static final Logger logger = Logger.getLogger(MemvacheDelegate.class
			.getName());

	final ApiProxy.Delegate<Environment> parent;

	/**
	 * {@link MemvacheDelegate}を{@link ApiProxy}に設定する。
	 * <p>
	 * 現在{@link ApiProxy}に設定されている
	 * {@link com.google.apphosting.api.ApiProxy.Delegate}が
	 * {@link MemvacheDelegate}だった場合は何もしない。
	 * </p>
	 * 
	 * @return 新たに作成した{@link MemvacheDelegate}か、 既に適用済みだった場合は元々設定されていた
	 *         {@link MemvacheDelegate}
	 */
	public static MemvacheDelegate install() {
		@SuppressWarnings("unchecked")
		Delegate<Environment> originalDelegate = ApiProxy.getDelegate();
		if (originalDelegate instanceof MemvacheDelegate == false) {
			MemvacheDelegate newDelegate = new MemvacheDelegate(
					originalDelegate);
			ApiProxy.setDelegate(newDelegate);
			return newDelegate;
		} else {
			return (MemvacheDelegate) originalDelegate;
		}
	}

	/**
	 * {@link MemvacheDelegate}を{@link ApiProxy}からはずす。
	 * 
	 * @param originalDelegate
	 *            元々設定されていた{@link com.google.apphosting.api.ApiProxy.Delegate}.
	 *            {@link MemvacheDelegate#getParent()}を使用すると良い。
	 */
	public static void uninstall(Delegate<Environment> originalDelegate) {
		ApiProxy.setDelegate(originalDelegate);
	}

	/**
	 * {@link MemvacheDelegate}を{@link ApiProxy}からはずす。
	 */
	public void uninstall() {
		ApiProxy.setDelegate(parent);
	}

	@Override
	public Future<byte[]> makeAsyncCall(Environment env, String service,
			String method, byte[] requestBytes, ApiConfig config) {

		analysis(service, method, requestBytes);

		return getParent().makeAsyncCall(env, service, method, requestBytes,
				config);
	}

	@Override
	public byte[] makeSyncCall(Environment env, String service, String method,
			byte[] requestBytes) throws ApiProxyException {
		return getParent().makeSyncCall(env, service, method, requestBytes);
	}

	void analysis(String service, String method, byte[] requestBytes) {
		logger.log(Level.INFO, "service=" + service + ", method=" + method);

		if ("datastore_v3".equals(service) && "Put".equals(method)) {

			DatastorePb.PutRequest requestPb = new DatastorePb.PutRequest();
			requestPb.mergeFrom(requestBytes);

			final MemcacheService memcache = getMemcache();
			final Set<String> memcacheKeys = new HashSet<String>();

			for (EntityProto entity : requestPb.entitys()) {
				final Reference key = entity.getMutableKey();
				final String namespace = key.getNameSpace();
				final Path path = key.getPath();
				for (Element element : path.mutableElements()) {

					StringBuilder builder = new StringBuilder();
					builder.append(namespace).append("@");
					builder.append(element.getType());

					memcacheKeys.add(builder.toString());
				}
			}
			if (memcacheKeys.size() == 1) {
				memcache.increment(memcacheKeys.iterator().next(), 1, 0L);
			} else if (memcacheKeys.size() != 0) {
				// memcache.incrementAll(memcacheKeys, 1, 0L);
				// TODO is this broken method? ↑
				for (String key : memcacheKeys) {
					memcache.increment(key, 1, 0L);
				}
			}
		}
	}

	MemcacheService getMemcache() {
		return MemcacheServiceFactory.getMemcacheService("memvache");
	}

	/**
	 * the constructor.
	 * 
	 * @param delegate
	 * @category constructor
	 */
	MemvacheDelegate(Delegate<Environment> delegate) {
		this.parent = delegate;
	}

	@Override
	public void log(Environment env, LogRecord logRecord) {
		getParent().log(env, logRecord);
	}

	@Override
	public void flushLogs(Environment env) {
		getParent().flushLogs(env);
	}

	@Override
	public List<Thread> getRequestThreads(Environment env) {
		return getParent().getRequestThreads(env);
	}

	/**
	 * @return the parent
	 * @category accessor
	 */
	public ApiProxy.Delegate<Environment> getParent() {
		return parent;
	}
}