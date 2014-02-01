package testng.com.ingby.socbox.bischeck.service;

import java.util.Properties;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ingby.socbox.bischeck.configuration.ConfigurationManager;
import com.ingby.socbox.bischeck.configuration.ValidateConfiguration;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.service.ServiceFactory;
import com.ingby.socbox.bischeck.service.ServiceFactoryException;

public class ServiceFactoryTest {

	ConfigurationManager confMgmr;
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
	}
	
	
	@Test (groups = { "ServiceFactory" })
	public void createServiceByFactory() {
		Service service = null;
		try {
			service = ServiceFactory.createService("myJDBC","jdbc:derby:memory:myDB;create=true",
					url2service, new Properties());
		} catch (ServiceFactoryException e) {
			
		}
		Assert.assertEquals(service.getClass().getName(), "com.ingby.socbox.bischeck.service.JDBCService");
	
		try {
			service = ServiceFactory.createService("myLastCache","bischeck://localhost",
					url2service, new Properties());
		} catch (ServiceFactoryException e) {
			
		}
		Assert.assertEquals(service.getClass().getName(), "com.ingby.socbox.bischeck.service.LastCacheService");
	
		try {
			service = ServiceFactory.createService("myLivestatus","livestatus://somehost:6373",
					url2service, new Properties());
		} catch (ServiceFactoryException e) {
			
		}
		Assert.assertEquals(service.getClass().getName(), "com.ingby.socbox.bischeck.service.LivestatusService");
	
		try {
			service = ServiceFactory.createService("myShell","shell://localhost",
					url2service, new Properties());
		} catch (ServiceFactoryException e) {
			
		}
		Assert.assertEquals(service.getClass().getName(), "com.ingby.socbox.bischeck.service.ShellService");
	
	}

}
