package testng.com.ingby.socbox.bischeck.servers;

import java.util.Properties;

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ingby.socbox.bischeck.configuration.ConfigurationManager;
import com.ingby.socbox.bischeck.host.Host;
import com.ingby.socbox.bischeck.servers.ServerMessageExecutor;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.service.ServiceException;
import com.ingby.socbox.bischeck.service.ServiceFactory;
import com.ingby.socbox.bischeck.service.ServiceFactoryException;
import com.ingby.socbox.bischeck.serviceitem.ServiceItem;
import com.ingby.socbox.bischeck.serviceitem.ServiceItemException;
import com.ingby.socbox.bischeck.serviceitem.ServiceItemFactory;
import com.ingby.socbox.bischeck.serviceitem.ServiceItemFactoryException;
import com.ingby.socbox.bischeck.threshold.Threshold;
import com.ingby.socbox.bischeck.threshold.ThresholdException;
import com.ingby.socbox.bischeck.threshold.ThresholdFactory;

public class ServerTest {

	private ConfigurationManager confMgmr;
	private ServerMessageExecutor serverexecutor;
	private Properties url2service;
	
	@BeforeTest
	public void beforeTest() throws Exception {

		try {
			confMgmr = ConfigurationManager.getInstance();
		} catch (java.lang.IllegalStateException e) {
			System.setProperty("bishome", ".");
			System.setProperty("xmlconfigdir","testetc");

			ConfigurationManager.init();
			confMgmr = ConfigurationManager.getInstance();	
		}
		url2service = confMgmr.getURL2Service();
		serverexecutor = ServerMessageExecutor.getInstance();
		
	}

	@Test (groups = { "Server" } )
	public void initServers()  {

		Assert.assertNotEquals(serverexecutor, null);
	}
	
	@Test (groups = { "Server" } )
	public void execute() throws ServiceFactoryException, ServiceItemFactoryException, ThresholdException, ServiceException, ServiceItemException {
		Service service = null;
		
		Assert.assertNotEquals(serverexecutor, null);
		
		
		service = ServiceFactory.createService("myShell","shell://localhost",
				url2service, new Properties());
		
		service.setConnectionUrl("shell://localhost");
		Assert.assertEquals(service.getClass().getName(), "com.ingby.socbox.bischeck.service.ShellService");
		
		Assert.assertEquals(service.getServiceName(), "myShell");
		
		ServiceItem serviceItem = null; 
		serviceItem = ServiceItemFactory.createServiceItem("myShellItem", "CheckCommandServiceItem");

		serviceItem.setService(service);
		serviceItem.setExecution("{\"check\":\"/bin/echo Ok\\|time=10;;;;\",\"label\":\"time\"}");
		
		
		Assert.assertEquals(service.getConnectionUrl(), "shell://localhost");
		service.addServiceItem(serviceItem);
		
		Host host = new Host("myhost");
		
		host.addService(service);
		service.setHost(host);


		serviceItem.setThreshold(ThresholdFactory.getCurrent(service, serviceItem));


		service.openConnection();
		serviceItem.execute();
		serviceItem.setExecutionTime(100L);
		service.closeConnection();

		serverexecutor.execute(service);

		
	}
	
	@AfterTest 
	public void unregisterServers()  {

		serverexecutor.unregisterAll();
	}
	
}
