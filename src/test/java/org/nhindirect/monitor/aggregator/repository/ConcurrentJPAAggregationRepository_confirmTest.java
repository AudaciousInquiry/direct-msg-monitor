package org.nhindirect.monitor.aggregator.repository;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nhindirect.common.tx.model.Tx;
import org.nhindirect.common.tx.model.TxMessageType;
import org.nhindirect.monitor.JPATestConfiguration;
import org.nhindirect.monitor.aggregator.repository.ConcurrentJPAAggregationRepository;
import org.nhindirect.monitor.dao.AggregationDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import org.nhindirect.monitor.util.TestUtils;


@RunWith(CamelSpringBootRunner.class)
@DataJpaTest
@Transactional
@ContextConfiguration(classes=JPATestConfiguration.class)
@DirtiesContext
public class ConcurrentJPAAggregationRepository_confirmTest extends CamelSpringTestSupport 
{
	@Autowired
	private AggregationDAO notifDao;
	
	@Before
	public void setUp() throws Exception
	{
		super.setUp();
		
		notifDao.purgeAll();
		
		List<String> keys = notifDao.getAggregationKeys();
		assertEquals(0, keys.size());
		
		keys = notifDao.getAggregationCompletedKeys();
		assertEquals(0, keys.size());
	}
	
	@Test
	public void testConfirm_exchangeNotInRepository_assertNoException()
	{
		final ConcurrentJPAAggregationRepository repo = new ConcurrentJPAAggregationRepository(notifDao);
		
		repo.confirm(context, "12345");
	}
	
	@Test
	public void testConfirm_completedExchangeInRepository_assertExchangeRemoved()
	{
		final Tx tx = TestUtils.makeMessage(TxMessageType.IMF, "12345", "", "me@test.com", "you@test.com", "", "", "");
		final Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(tx);
		
		final ConcurrentJPAAggregationRepository repo = new ConcurrentJPAAggregationRepository(notifDao);
		
		repo.add(context, "12345", exchange);
		
		repo.remove(context, "12345", exchange);
		
		assertNull(repo.get(context, "12345"));
		
		final Exchange completedExchange = repo.recover(context, exchange.getExchangeId());
		assertNotNull(completedExchange);

		repo.confirm(context, exchange.getExchangeId());
		
		assertNull(repo.recover(context, exchange.getExchangeId()));
	}
	
	@Test
	public void testConfirm_daoException_assertNoAggregation() throws Exception
	{
		AggregationDAO dao = mock(AggregationDAO.class);
		doThrow(new RuntimeException()).when(dao).confirmAggregation((String)any());
		
		final ConcurrentJPAAggregationRepository repo = new ConcurrentJPAAggregationRepository(dao);
		
		boolean exceptionOccured = false;
		try
		{
			repo.confirm(context, "12345");
		}
		catch(RuntimeException e)
		{
			exceptionOccured = true;
		}
		
		assertTrue(exceptionOccured);
	}	
	
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() 
    {
    	return new ClassPathXmlApplicationContext("distributedAggregatorRoutes/mock-route.xml");
    }
}
