/*
#
# Copyright (C) 2010-2012 Anders Håål, Ingenjorsbyn AB
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
*/

package testng.com.ingby.socbox.bischeck.cache;



import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ingby.socbox.bischeck.ConfigurationManager;
import com.ingby.socbox.bischeck.Util;
import com.ingby.socbox.bischeck.cache.CacheFactory;
import com.ingby.socbox.bischeck.cache.CacheInf;
import com.ingby.socbox.bischeck.cache.CacheUtil;
import com.ingby.socbox.bischeck.cache.LastStatus;
import com.ingby.socbox.bischeck.cache.provider.laststatuscache.LastStatusCache;

public class CacheTest {

	CacheInf cache = null;
	ConfigurationManager confMgmr = null;
	
	String hostname = "test-server.ingby.com";
	String qhostname = "test\\-server.ingby.com";
	String servicename = "service@first";
	String serviceitemname = "_service.item_123";
	//String cachekey = Util.fullName(hostname, servicename, serviceitemname);
	String cachekey = Util.fullName(qhostname, servicename, serviceitemname);
	
	@BeforeTest
    public void beforeTest() throws Exception {
	

		confMgmr = ConfigurationManager.getInstance();
	
		if (confMgmr == null) {
			System.setProperty("bishome", ".");
			ConfigurationManager.init();
			confMgmr = ConfigurationManager.getInstance();
			
		}
		
		cache = CacheFactory.getInstance();
		
	}

	@AfterTest
	public void afterTest() {
		cache.close();
	}

	@Test (groups = { "Cache" })
	public void verifyCache() {
		cache.clear();
		if (cache instanceof LastStatusCache)
			((LastStatusCache) cache).setFullListDef(true);
			
		long current = System.currentTimeMillis() - 10*300*1000;
		
		for (int i = 1; i < 11; i++) {
			LastStatus ls = new LastStatus(""+i, (float) i,  current + i*300*1000);
			System.out.println(CacheTest.class.getName()+">> " + i+":"+ls.getValue() +">"+hostname+"-"+servicename+"-"+serviceitemname);
			cache.add(ls, hostname, servicename, serviceitemname);
		
		}
		
		if (cache instanceof LastStatusCache)
			((LastStatusCache) cache).setFullListDef(true);
		
		Assert.assertEquals(CacheUtil.parse(cachekey + "[0]"),"10");
		Assert.assertEquals(CacheUtil.parse(cachekey + "[9]"),"1");
		Assert.assertEquals(CacheUtil.parse(cachekey + "[3:6]"),"7,6,5,4");
		// Test that a limited list will be returned even with some index out of
		// bounds
		Assert.assertEquals(CacheUtil.parse(cachekey + "[6:12]"),"4,3,2,1");
		Assert.assertEquals(CacheUtil.parse(cachekey + "[-10M]"),"8");
		Assert.assertEquals(CacheUtil.parse(cachekey + "[-10M:-20M]"),"8,7,6");
		// Test all keys outside range return "null"
		Assert.assertNull(CacheUtil.parse(cachekey + "[12:20]"));
		// Test that a time range with no data in the cache returns "null"
		Assert.assertNull(CacheUtil.parse(cachekey + "[-100M:-120M]"));
		
	}
	
	@Test (groups = { "Cache" })
	public void verifyCacheNullValue() {
		cache.clear();
		
		long current = System.currentTimeMillis() - 20*300*1000;
		
		for (int i = 1; i < 11; i++) {
			LastStatus ls = new LastStatus(""+i, (float) i,  current + i*300*1000);
			System.out.println(CacheTest.class.getName()+">> " + i+":"+ls.getValue() +">"+hostname+"-"+servicename+"-"+serviceitemname);
			cache.add(ls, hostname, servicename, serviceitemname);
			ls = new LastStatus(null, (float) i,  current + i*300*1000);
			cache.add(ls, hostname, servicename, serviceitemname);
		
		}
		
		if (cache instanceof LastStatusCache)
			((LastStatusCache) cache).setFullListDef(false);
	
		
		Assert.assertNull(CacheUtil.parse(cachekey + "[2:6]"));
	
		if (cache instanceof LastStatusCache)
			((LastStatusCache) cache).setFullListDef(true);
		
		Assert.assertEquals(CacheUtil.parse(cachekey + "[2:6]"),"9,8");
		
		}
	
}
