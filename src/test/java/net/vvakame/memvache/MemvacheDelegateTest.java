package net.vvakame.memvache;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import net.vvakame.memvache.ProofOfConceptTest.DebugDelegate;

import org.junit.Test;
import org.slim3.datastore.Datastore;
import org.slim3.memcache.Memcache;
import org.slim3.tester.ControllerTestCase;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.Entity;

public class MemvacheDelegateTest extends ControllerTestCase {

	DebugDelegate debugDelegate;
	MemvacheDelegate delegate;

	@Test
	public void put_singleEntity_noNameSpace() throws Exception {
		assertThat(Memcache.statistics().getItemCount(), is(0L));

		{
			Entity entity = new Entity("test");
			Datastore.put(entity);
		}

		assertThat(Memcache.statistics().getItemCount(), is(1L));

		NamespaceManager.set("memvache");
		long count = Memcache.get("@test");
		assertThat(count, is(1L));
	}

	@Test
	public void put_singleEntity_hasNameSpace() throws Exception {
		assertThat(Memcache.statistics().getItemCount(), is(0L));

		{
			NamespaceManager.set("hoge");
			Entity entity = new Entity("test");
			Datastore.put(entity);
		}

		assertThat(Memcache.statistics().getItemCount(), is(1L));

		NamespaceManager.set("memvache");
		long count = Memcache.get("hoge@test");
		assertThat(count, is(1L));
	}

	@Test
	public void put_multiEntity_sameKind() throws Exception {
		assertThat(Memcache.statistics().getItemCount(), is(0L));

		{
			Entity entityA = new Entity("test");
			Entity entityB = new Entity("test");
			Datastore.put(entityA, entityB);
		}

		assertThat(Memcache.statistics().getItemCount(), is(1L));

		NamespaceManager.set("memvache");
		long count = Memcache.get("@test");
		assertThat(count, is(1L));
	}

	@Test
	public void put_multiEntity_otherKind() throws Exception {
		assertThat(Memcache.statistics().getItemCount(), is(0L));

		{
			Entity entityA = new Entity("test1");
			Entity entityB = new Entity("test2");
			Datastore.put(entityA, entityB);
		}

		assertThat(Memcache.statistics().getItemCount(), is(2L));

		NamespaceManager.set("memvache");
		assertThat((Long) Memcache.get("@test1"), is(1L));
		assertThat((Long) Memcache.get("@test2"), is(1L));
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();

		debugDelegate = DebugDelegate.install();
		delegate = MemvacheDelegate.install();
	}

	@Override
	public void tearDown() throws Exception {
		delegate.uninstall();
		debugDelegate.uninstall();

		super.tearDown();
	}
}
