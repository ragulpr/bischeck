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

package testng.com.ingby.socbox.bischeck.serviceitem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;


import com.ingby.socbox.bischeck.ConfigurationManager;
import com.ingby.socbox.bischeck.Util;
import com.ingby.socbox.bischeck.cache.CacheFactory;
import com.ingby.socbox.bischeck.cache.CacheInf;
import com.ingby.socbox.bischeck.cache.LastStatus;
import com.ingby.socbox.bischeck.cache.provider.laststatuscache.LastStatusCache;

import com.ingby.socbox.bischeck.service.JDBCService;
import com.ingby.socbox.bischeck.service.LastCacheService;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.service.ServiceException;
import com.ingby.socbox.bischeck.serviceitem.CalculateOnCache;
import com.ingby.socbox.bischeck.serviceitem.SQLServiceItem;
import com.ingby.socbox.bischeck.serviceitem.ServiceItem;

public class SQLServiceitemTest {



	private ConfigurationManager confMgmr;
	private CacheInf cache;
	private JDBCService jdbc;
	private SQLServiceItem sql;

	@BeforeTest
	public void beforeTest() throws Exception {


		confMgmr = ConfigurationManager.getInstance();

		if (confMgmr == null) {
			System.setProperty("bishome", ".");
			ConfigurationManager.init();
			confMgmr = ConfigurationManager.getInstance();
		}


		jdbc = new JDBCService("test");
		jdbc.setConnectionUrl("jdbc:derby:memory:myDB;create=true");
		jdbc.setDriverClassName("org.apache.derby.jdbc.EmbeddedDriver");
		sql = new SQLServiceItem("serviceItemName");
		sql.setService(jdbc);

		// Create table
		//Creating a database table
		Connection con = DriverManager.getConnection("jdbc:derby:memory:myDB;create=true");
		Statement stat = con.createStatement();

		stat.execute("create table test (id INT, value INT, createdate date)");
		stat.execute("insert into test (id, value, createdate) values (1,1000,CURRENT_DATE)");
		stat.execute("insert into test (id, value, createdate) values (2,1000,CURRENT_DATE)");
		stat.execute("insert into test (id, value, createdate) values (3,2000,CURRENT_DATE)");
		stat.execute("insert into test (id, value, createdate) values (4,3000,'2010-12-31')");
		con.commit();

		cache = CacheFactory.getInstance();		

		cache.clear();
	}

	@AfterTest
	public void afterTest() {
		cache.close();
	}

	@Test (groups = { "ServiceItem" })
	public void verifyService() throws Exception {

			LastStatus ls = new LastStatus("1", (float) 1.0);
			cache.add(ls, Util.fullName("host1", "web", "state"));
			ls = new LastStatus("2", (float) 1.0);
			cache.add(ls, Util.fullName("host2", "web", "state"));
			ls = new LastStatus("3", (float) 1.0);
			cache.add(ls, Util.fullName("host3", "web", "state"));


			jdbc.openConnection();

			sql.setExecution("select sum(value) from test");
			sql.execute();
			Assert.assertEquals(sql.getLatestExecuted(),"7000");

			sql.setExecution("select sum(value) from test where createdate = '%%yyyy-MM-dd%%'");
			sql.execute();
			Assert.assertEquals(sql.getLatestExecuted(),"4000");

			sql.setExecution("select sum(value) from test where (id = host1-web-state[0] or id = host2-web-state[0]) and createdate = '%%yyyy-MM-dd%%'");
			sql.execute();
			Assert.assertEquals(sql.getLatestExecuted(),"2000");

			jdbc.closeConnection();

	}

}