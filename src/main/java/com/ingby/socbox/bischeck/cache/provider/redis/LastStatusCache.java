/*
#
# Copyright (C) 2010-2011 Anders Håål, Ingenjorsbyn AB
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

package com.ingby.socbox.bischeck.cache.provider.redis;



import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import com.ingby.socbox.bischeck.ServiceDef;
import com.ingby.socbox.bischeck.Util;
import com.ingby.socbox.bischeck.cache.CacheException;
import com.ingby.socbox.bischeck.cache.CacheFactory;
import com.ingby.socbox.bischeck.cache.CacheInf;
import com.ingby.socbox.bischeck.cache.CachePurgeInf;
import com.ingby.socbox.bischeck.cache.CacheQueue;
import com.ingby.socbox.bischeck.cache.CacheUtil;
import com.ingby.socbox.bischeck.cache.LastStatus;
import com.ingby.socbox.bischeck.cache.provider.redis.Lookup;
import com.ingby.socbox.bischeck.configuration.ConfigurationManager;
import com.ingby.socbox.bischeck.host.Host;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.serviceitem.ServiceItem;


/**
 * This is the Bischeck based redis cache class. The cache implements a two level 
 * cache - fast cache and redis cache. The fast cache is by default 100 slot fifo 
 * cache implemented on the heap. On write data is stored both in the fast heap 
 * cache and in the redis cache. On query the fast cache is first evaluated and
 * then the redis cache is queried.<p>
 * The cache has low memory footprint compare with the old all in heap cache 
 * since redis store data so effective.<p>
 * The class is controlled by X properties:<br>
 * cache.provider.redis.server - the ip or name of where the redis service reside, default is 
 * localhost.<br>
 * cache.provider.redis.port - the socket port where the redis server listen, default is 6379.<br>
 * cache.provider.redis.fastCacheSize - the size of the fast fifo cache, default is 0 and means disabled.<br>
 * cache.provider.redis.db - default is 0.<br>   
 * cache.provider.redis.auth - the password to the redis database, default is null.<br>
 * cache.provider.redis.timeout - the timeout in milliseconds, default is 2000. <br>
 */

public final class LastStatusCache implements CacheInf, CachePurgeInf, LastStatusCacheMBean {

	private final static Logger LOGGER = LoggerFactory.getLogger(LastStatusCache.class);

	private ConcurrentHashMap<String,CacheQueue<LastStatus>> fastCache = null;

	
	private static int fastCacheSize = 0;
	
	//private static boolean notFullListParse = false;
	private static LastStatusCache lsc; // = new LastStatusCache();
	private static MBeanServer mbs = null;
	private final static String BEANNAME = "com.ingby.socbox.bischeck:name=Cache";

	//private static final String JEPLISTSEP = ",";
	
	private static ObjectName   mbeanname = null;

	
	private static String redisserver;
	private static int redisport;
	private static String redisauth = null;
	private static int redisdb;
	private static int redistimeout;

	private JedisPoolWrapper jedispool = null;
	
	private Lookup lu = null;

	private long fastcachehitcount = 0L;
	private long rediscachehitcount = 0L;

	private boolean fastCacheEnable = true;
	
	private LastStatusCache() {
		fastCache = new ConcurrentHashMap<String,CacheQueue<LastStatus>>();
		
		jedispool = new JedisPoolWrapper(redisserver,redisport,redistimeout,redisauth,redisdb);	
		
		lu  = Lookup.init(jedispool);
	}

	/**
	 * Return the cache reference
	 * @return
	 */
	public static LastStatusCache getInstance() {
		if (lsc == null)
			LOGGER.error("Cache impl has not been initilized");
		return lsc;
	}
	
	public static synchronized void init() throws CacheException {
		if (lsc == null) {
			
			redisserver = ConfigurationManager.getInstance().getProperties().
					getProperty("cache.provider.redis.server","localhost");

			try {
				redisport = Integer.parseInt(
						ConfigurationManager.getInstance().getProperties().
						getProperty("cache.provider.redis.port","6379"));
			} catch (NumberFormatException ne) {
				redisport = 6379;
			}

			redisauth = ConfigurationManager.getInstance().getProperties().
					getProperty("cache.provider.redis.auth",null);
			if (redisauth != null) {
				if (redisauth.length() == 0){
					redisauth = null;
				}
			}
			
			try {
				redisdb = Integer.parseInt(
						ConfigurationManager.getInstance().getProperties().
						getProperty("cache.provider.redis.db","0"));
			} catch (NumberFormatException ne) {
				redisdb=0;
			}
			
			try {
				redistimeout = Integer.parseInt(
						ConfigurationManager.getInstance().getProperties().
						getProperty("cache.provider.redis.timeout","2000"));
			} catch (NumberFormatException ne) {
				redistimeout=2000;
			}
			
			lsc = new LastStatusCache();
			lsc.testConnection();
			
			mbs = ManagementFactory.getPlatformMBeanServer();

			try {
				mbeanname = new ObjectName(BEANNAME);
			} catch (MalformedObjectNameException e) {
				LOGGER.error("MBean object name failed, " + e.getMessage(),e);
			} catch (NullPointerException e) {
				LOGGER.error("MBean object name failed, " + e.getMessage(),e);
			}


			try {
				mbs.registerMBean(lsc, mbeanname);
			} catch (InstanceAlreadyExistsException e) {
				LOGGER.error("Mbean exception - " + e.getMessage(),e);
			} catch (MBeanRegistrationException e) {
				LOGGER.error("Mbean exception - " + e.getMessage(),e);
			} catch (NotCompliantMBeanException e) {
				LOGGER.error("Mbean exception - " + e.getMessage(),e);
			}
			
			try {
				fastCacheSize = Integer.parseInt(
						ConfigurationManager.getInstance().getProperties().
						getProperty("cache.provider.redis.fastCacheSize","0"));
				if (fastCacheSize == 0) {
					lsc.disableFastCache();
				}
			} catch (NumberFormatException ne) {
				fastCacheSize = 0;
				lsc.disableFastCache();
			}
			
			lsc.updateRuntimeMetaData();
		}
		
	}

	public static synchronized void destroy() {
		lsc.jedispool.destroy();

		try {
			mbs.unregisterMBean(mbeanname);
		} catch (MBeanRegistrationException e) {
			LOGGER.warn("Mbean " + mbeanname +" could not be unregistered",e);
		} catch (InstanceNotFoundException e) {
			LOGGER.warn("Mbean " + mbeanname +" instance could not be found",e);
		}
		lsc = null;
		
	}
	
	/*
	 ***********************************************
	 ***********************************************
	 * Public methods
	 ***********************************************
	 ***********************************************
	 */
	
	public void disableFastCache() {
		LOGGER.info("Fast cache disabled");
		fastCacheEnable  = false;
	}
	
	public void updateRuntimeMetaData() {
		Map<String, Host> hostsmap = ConfigurationManager.getInstance().getHostConfig();
		Jedis jedis = null;
		try {
			jedis = jedispool.getResource();
			
			deleteAllMetaData(jedis);
				
			for (Map.Entry<String, Host> hostentry : hostsmap.entrySet()) {
				Host host = hostentry.getValue();

				for (Map.Entry<String, Service> serviceentry : host.getServices().entrySet()) {
					Service service = serviceentry.getValue();

					for (Map.Entry<String, ServiceItem> serviceItemEntry : service.getServicesItems().entrySet()) {
						ServiceItem serviceitem = serviceItemEntry.getValue();					
						updateMetaData(jedis, host, service, serviceitem);
					}
				}
			}
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(), je);
		} finally {
			jedispool.returnResource(jedis);
		}

	}



	/*
	 ***********************************************
	 ***********************************************
	 * Implement CacheInf
	 ***********************************************
	 ***********************************************
	 */
	
	/*
	 ***********************************************
	 * Add methods
	 ***********************************************
	 */
	
	@Override
	public  void add(Service service, ServiceItem serviceitem) {

		String key = Util.fullName(service, serviceitem);
		add(new LastStatus(serviceitem), key);    
	}


	@Override
	public void add(LastStatus ls, 
			String hostName, 
			String serviceName,
			String serviceItemName) {
		String key = Util.fullName(hostName, serviceName, serviceItemName); 
		add(ls,key);
		
	}
	
	
	@Override
	public void add(LastStatus ls, String key) {
		CacheQueue<LastStatus> fifo;
		
		Jedis jedis = null;
		
		try {
			jedis = jedispool.getResource();
			
			if (fastCache.get(key) == null) {
				fifo = new CacheQueue<LastStatus>(fastCacheSize);
				fastCache.put(key, fifo);
			} else {
				fifo = fastCache.get(key);
			}

			// Add local cache
			fastCache.get(key).addFirst(ls);

			// Add redis
			jedis.lpush(key, ls.getJson());
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
	}

	
	
	/*
     ***********************************************
	 * Get data methods - LastStatus
	 ***********************************************
	 */

	@Override
	public LastStatus getLastStatusByTime(String host, String service,
			String serviceitem, long timestamp) {
		String key = Util.fullName( host, service, serviceitem);
		
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Find cache data for " + key + " at time " + new java.util.Date(timestamp));
		
		LastStatus ls = null;

		//String id = lu.getIdByName(key);
		Jedis jedis = null;;
		try {	
			jedis = jedispool.getResource();
			
			if (jedis.llen(key) == 0)
				return null;

			ls = nearest(timestamp, key);

		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
		
		if (ls == null) 
			return null;
		else
			return ls;    
	}

	@Override
	public LastStatus getLastStatusByIndex(String hostName, String serviceName,
			String serviceItemName, long index) {
		
		
		
		String key = Util.fullName( hostName, serviceName, serviceItemName);
		
		lu.setOptimizIndex(key, index);
		
		LastStatus ls = null;
		
		Jedis jedis = null;
		try {
			jedis = jedispool.getResource();
			
			if (fastCacheEnable && fastCache.get(key) != null && index < fastCache.get(key).size()-1) {
				if (LOGGER.isDebugEnabled() ) {
					LOGGER.debug("Fast cache used for key " + key +" index " + index);
				}
				incFastCacheCount();
				ls = fastCache.get(key).get((int)index).copy();
			}
		
			else {
				if (LOGGER.isDebugEnabled() ) {
					LOGGER.debug("Redis cache used for key " + key +" index " + index);
				}
				String redstr = jedis.lindex(key, index);

				if (redstr == null)
					return null;
				else {
					incRedisCacheCount();	
					ls = new LastStatus(redstr);
				}
			}
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
		
		return ls;
	}

	@Override
	public List<LastStatus> getLastStatusListByTime(String host, 
			String service, 
			String serviceitem, 
			long from, long to) {
		
		Long indfrom = this.getIndexByTime( 
				host,
				service, 
				serviceitem,from);
		
		if (indfrom == null) {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("No data for from timestamp "+ from);
			return null;
		}
		
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Index from " + indfrom);
		Long indto = this.getIndexByTime( 
				host,
				service, 
				serviceitem,to);
		if (indto == null) {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("No data for from timestamp "+ to);
			return null;
		}
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Index from " + indto);
		
		List<LastStatus> lslist = new ArrayList<LastStatus>();
		
		lslist = getLastStatusListByIndex(host, service, serviceitem, indfrom,indto);
		
		return lslist;
	}

	@Override
	public List<LastStatus> getLastStatusListByIndex(String hostName, String serviceName,
			String serviceItemName, long fromIndex, long toIndex) {
		
		long numberOfindex = toIndex-fromIndex;
		if (numberOfindex > Integer.MAX_VALUE){
			toIndex = Integer.MAX_VALUE;
		}

		
		
		String key = Util.fullName( hostName, serviceName, serviceItemName);

		lu.setOptimizIndex(key, toIndex);
		
		
		List<LastStatus> lslist = new  ArrayList<LastStatus>();
		List<String> lsstr = null;
		
	
		if (fastCacheEnable && fastCache.get(key) != null && toIndex < fastCache.get(key).size()-1) {
			if (LOGGER.isDebugEnabled() ) {
				LOGGER.debug("Fast cache used for key " + key +" index " + toIndex);
			}
			incFastCacheCount(toIndex-fromIndex+1);
			
			
			for (long index = fromIndex; index <= toIndex; index++) {
				LastStatus ls = getLastStatusByIndex(hostName, serviceName, serviceItemName, index);
				if (ls == null)
					break;
				lslist.add(ls.copy());
			}
			
			return lslist;

		}
	
		Jedis jedis = null;
		try {
			jedis = jedispool.getResource();
			
			lsstr = jedis.lrange(key, fromIndex, toIndex);
			
			if (lsstr != null) {
				incRedisCacheCount(toIndex-fromIndex+1);	

				for (String redstr: lsstr) {
					LastStatus ls = new LastStatus(redstr);
					lslist.add(ls);
				}
			}

		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}	

		return lslist;
	}


	@Override
	public List<LastStatus> getLastStatusListAll(String hostName,
			String serviceName, 
			String serviceItemName) {
		
		List<LastStatus> lslist = new ArrayList<LastStatus>();
		
		lslist = getLastStatusListByIndex(hostName, serviceName, serviceItemName, 0L, getLastIndex(hostName, serviceName, serviceItemName));
		
		return lslist;
	}
	
    /*
     ***********************************************
	 * Get data methods - String
	 ***********************************************
	 */

	@Override
	public String getByIndex(String hostName, 
			String serviceName,
			String serviceItemName, 
			long index) {
		
		LastStatus ls = getLastStatusByIndex(hostName, serviceName, serviceItemName, index);
		if (ls == null) 
			return null;
		else
			return ls.getValue();
		
	}


	@Override
	public String getByIndex(String hostName, 
			String serviceName,
			String serviceItemName, 
			long fromIndex, long toIndex,
			String separator) {
		List<LastStatus> lslist = getLastStatusListByIndex(hostName, serviceName, serviceItemName, fromIndex, toIndex);
		
		if (lslist == null)
			return null;
		
		if (lslist.isEmpty())
			return null;
		
		StringBuffer strbuf = new StringBuffer();
		for (LastStatus ls : lslist) {
			strbuf.append(ls.getValue()).append(separator);
		}
		String str = strbuf.toString();
		return str.substring(0, str.lastIndexOf(separator));
	}

	@Override
	public String getByTime(String hostName, 
			String serviceName,
			String serviceItemName, 
			long timestamp) {
		
		LastStatus ls = getLastStatusByTime(hostName, serviceName, serviceItemName, timestamp);
		if (ls == null) 
			return null;
		else
			return ls.getValue();
	}	

	@Override
	public String getByTime(String hostName, 
			String serviceName,
			String serviceItemName, 
			long from, long to, 
			String separator) {
		
		List<LastStatus> lslist = getLastStatusListByTime(hostName, serviceName, serviceItemName, from, to);
		
		if (lslist == null)
			return null;
		
		StringBuffer strbuf = new StringBuffer();
		for (LastStatus ls : lslist) {
			strbuf.append(ls.getValue()).append(separator);
		}
		String str = strbuf.toString();

		return str.substring(0, str.lastIndexOf(separator));
	}


	@Override
	public String getAll(String hostName, 
			String serviceName,
			String serviceItemName,
			String separator) {


		List<LastStatus> lslist = getLastStatusListAll(hostName, serviceName, serviceItemName);
		
		if (lslist.isEmpty())
			return null;
		
		StringBuffer strbuf = new StringBuffer();
		for (LastStatus ls : lslist) {
			strbuf.append(ls.getValue()).append(separator);
		}
		String str = strbuf.toString();

		return str.substring(0, str.lastIndexOf(separator));
	}

    /*
     ***********************************************
	 * Position and size methods
	 ***********************************************
	 */
	@Override
    public Long size(String hostName, String serviceName,
			String serviceItemName) {

    	String key = Util.fullName( hostName, serviceName, serviceItemName);
		
    	Long size = 0L;
		Jedis jedis = null;
		try {	
			jedis = jedispool.getResource();
			
			size = jedis.llen(key);
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
		return size;
	}


	@Override
	public Long getIndexByTime(String hostname, String serviceName,
			String serviceItemName, long stime) {
		
		String key = Util.fullName( hostname, serviceName, serviceItemName);
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Find cache index for " + key +" at time " + new java.util.Date(stime));
		
		//String id = lu.getIdByName(key);
		Long index = null;
		Jedis jedis = null;
		try {
			jedis = jedispool.getResource();
			
			if (jedis.llen(key) == 0)
				return null;
		
			index = nearestByIndex(stime, key);
			
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
		if (index == null) 
			return null;
		else
			return index;    
	}
	
	

	@Override
	public long getLastIndex(String hostName, String serviceName,
			String serviceItemName) {
		return size(hostName, serviceName, serviceItemName) - 1;
	}
	
	@Override
	public long getLastTime(String hostName, 
			String serviceName, 
			String serviceItemName) {
		long lastindex = getLastIndex(hostName, serviceName, serviceItemName);
		long lasttimestamp = getLastStatusByIndex(hostName, serviceName, serviceItemName, lastindex).getTimestamp();
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Last index is" + lastindex + " and have timestamp " + lasttimestamp);
		return lasttimestamp;
	}

	
	
	/*
     ***********************************************
	 * Clear methods
	 ***********************************************
	 */
	@Override
	public void clear() {
		clearFastCache();
		clearRedisCache();
		
		
	}

	@Override
	public void clear(String hostName, String serviceName,
			String serviceItemName) {
		
		String key = Util.fullName( hostName, serviceName, serviceItemName);
		
		// Clear fast cache data
		if (fastCache == null)
			fastCache.get(key).clear();
		
		// Clear redis cache data
		Jedis jedis = null;
		try {
			jedis = jedispool.getResource();
			jedis.del(key);
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}	
	}


    
	/*
	 ***********************************************
	 ***********************************************
	 * Implement LastStatusMBean
	 ***********************************************
	 ***********************************************
	 */

	/*
	 * (non-Javadoc)
	 * @see com.ingby.socbox.bischeck.LastStatusCacheMBean#getFastCacheCount()
	 */
	@Override
	public long getFastCacheCount() {
		return fastcachehitcount;
	}

	/*
	 * (non-Javadoc)
	 * @see com.ingby.socbox.bischeck.LastStatusCacheMBean#getRedisCacheCount()
	 */
	@Override
	public long getRedisCacheCount() {
		return rediscachehitcount;
	}

	/*
	 * (non-Javadoc)
	 * @see com.ingby.socbox.bischeck.LastStatusCacheMBean#getCacheRatio()
	 */
	@Override
	public int getCacheRatio() {
		if(rediscachehitcount == 0L)
			return 100;
		else
			return (int) (fastcachehitcount*100/(rediscachehitcount+fastcachehitcount));
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.ingby.socbox.bischeck.LastStatusCacheMBean#dump2file()
	 */	
	@Override
	public void dump2file() {
		//BackendStorage.dump2file(cache,lastStatusCacheDumpFile);
	}

	/*
	 * (non-Javadoc)
	 * @see com.ingby.socbox.bischeck.LastStatusCacheMBean#clearCache()
	 */
	@Override
	public void clearCache() {
		clear();
		
	}

	/*
	 * (non-Javadoc)
	 * @see com.ingby.socbox.bischeck.LastStatusCacheMBean#getCacheKeyCount()
	 */
	@Override
	public int getCacheKeyCount() {
		return fastCache.size();
	}


	/*
	 * (non-Javadoc)
	 * @see com.ingby.socbox.bischeck.LastStatusCacheMBean#getCacheKeys()
	 */
	@Override
	public String[] getCacheKeys() {
		String[] key = new String[fastCache.size()];

		Iterator<String> itr = fastCache.keySet().iterator();

		int ind = 0;
		while(itr.hasNext()){
			String entry=itr.next();
			int size = fastCache.get(entry).size();
			key[ind++]=entry+":"+size;
		}    
		return key; 
	}

	
	/*
	 ***********************************************
	 ***********************************************
	 * Private methods
	 ***********************************************
	 ***********************************************
	 */

	private void deleteAllMetaData(Jedis jedis) {
		Set<String> runtimeEntries = jedis.keys("config/*");
		for (String entry: runtimeEntries) {
			jedis.del(entry);
		}
	}


	
	private void updateMetaData(Jedis jedis, Host host, Service service,
			ServiceItem serviceItem) {
		
		String key = "config/"+Util.fullName(host.getHostname(),service.getServiceName(), serviceItem.getServiceItemName());
		jedis.hset(key,"hostDesc",checkNull(host.getDecscription()));
		jedis.hset(key,"serviceDesc",checkNull(service.getDecscription()));
		jedis.hset(key,"serviceConnectionUrl",service.getConnectionUrl());
		jedis.hset(key,"serviceDriverClass",checkNull(service.getDriverClassName()));
		int i = 0;
		for (String schedule:service.getSchedules()){
			jedis.hset(key,"serviceSchedule-"+i ,checkNull(schedule));
			i++;
		}
		jedis.hset(key,"serviceItemDesc",checkNull(serviceItem.getDecscription()));
		jedis.hset(key,"serviceItemExecuteStatement",checkNull(serviceItem.getExecutionStat()));
		jedis.hset(key,"serviceItemClassName",checkNull(serviceItem.getClassName()));
		jedis.hset(key,"serviceItemThresholdClass",checkNull(serviceItem.getThresholdClassName()));
	}

	private String checkNull(String str) {
		if (str == null)
			return "";
		return str;
	}

	private void testConnection() {
		jedispool.getResource();
	}
	
	


	/**
	 * Remove all data in the fast cache and the keys
	 */
	private void clearFastCache() {
		Iterator<String> iter = fastCache.keySet().iterator();
		while (iter.hasNext()) {
			String key = iter.next();
			fastCache.get(key).clear(); 
			iter.remove();
		}
	}

	
	/**
	 * Remove every key that don not begin with ^config/
	 */
	private void clearRedisCache() {
		
		Jedis jedis = null;
		
		try {
			jedis = jedispool.getResource();
			Iterator<String> iter = jedis.keys("*").iterator();
			while (iter.hasNext()) {
				// Clear redis cache data
				String key = iter.next();
				if (!key.matches("^config/.*")) {
					jedis.del(key);
				}

			}	
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
	}
	
	
	private LastStatus nearest(long time,  String id) {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Find value in cache at nearest " + new java.util.Date(time));
		
		
		LastStatus nearest = null;
		
		// Search the fast cache first. If a hit is in the fast cache return 
		nearest = nearestFast(time, id);
		
		if (nearest != null) {
			incFastCacheCount();
		} else {
			// Search the slow cache
			nearest = nearestSlow(time, id);
			incRedisCacheCount();
		}
		return nearest;

	}

	
	/**
	 * The method search for the LastStatus object stored in the cache that has 
	 * a timestamp closest to the time parameter.
	 * @param time 
	 * @param listtosearch
	 * @return the LastStatus object closes to the time
	 */
	private LastStatus nearestFast(long time, String key) {
		if (!fastCacheEnable)
			return null;
		
		LinkedList<LastStatus> listtosearch = fastCache.get(key);
		if (listtosearch == null)
			return null;
		
		if (time > listtosearch.getFirst().getTimestamp() || 
			time < listtosearch.getLast().getTimestamp() ) {
			return null;
		}

		LastStatus nearest = null;
		long bestDistanceFoundYet = Long.MAX_VALUE;
		
		for (int i = 0; i < listtosearch.size(); i++) {
			long d1 = Math.abs(time - listtosearch.get(i).getTimestamp());
			long d2;
			if (i+1 < listtosearch.size())
				d2 = Math.abs(time - listtosearch.get(i+1).getTimestamp());
			else 
				d2 = Long.MAX_VALUE;

			if ( d1 < bestDistanceFoundYet ) {

				// For the moment, this value is the nearest to the desired number...
				bestDistanceFoundYet = d1;
				nearest = listtosearch.get(i);
				if (d1 <= d2) { 
					if (LOGGER.isDebugEnabled())
						LOGGER.debug("Break at index " + i);
					break;
				}
			}
		}
		
		return nearest.copy();

	}



	private LastStatus nearestSlow(long time, String key) {
		// Search the redis cache
		LastStatus nearest = null;
		Jedis jedis = null;
		try {
			jedis = jedispool.getResource();
			if (time > new LastStatus(jedis.lindex(key, 0L)).getTimestamp() || 
					time < new LastStatus(jedis.lindex(key, jedis.llen(key)-1)).getTimestamp() ) {
				return null;
			}

			long bestDistanceFoundYet = Long.MAX_VALUE;
			long size = jedis.llen(key);

			for (int i = 0; i < size; i++) {
				long d1 = Math.abs(time - new LastStatus(jedis.lindex(key, i)).getTimestamp());
				long d2;
				if (i+1 < size)
					d2 = Math.abs(time - new LastStatus(jedis.lindex(key, i+1)).getTimestamp());
				else 
					d2 = Long.MAX_VALUE;

				if ( d1 < bestDistanceFoundYet ) {

					// For the moment, this value is the nearest to the desired number...
					bestDistanceFoundYet = d1;
					nearest = new LastStatus(jedis.lindex(key, i));
					if (d1 <= d2) { 
						if (LOGGER.isDebugEnabled())
							LOGGER.debug("Break at index " + i);
						break;
					}
				}
			}
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
		
		return nearest;
	}

	
	/**
	 * Return the cache index closes to the timestamp define in time
	 * @param time
	 * @param listtosearch
	 * @return cache index
	 */
	private Long nearestByIndexFast(long time, String key) {
		if (!fastCacheEnable)
			return null;
		
		LinkedList<LastStatus> listtosearch =  fastCache.get(key);
		if (listtosearch == null)
			return null;
		if (time > listtosearch.getFirst().getTimestamp() || 
			time < listtosearch.getLast().getTimestamp() ) {
			return null;
		}
		
		Long index = null;
		long bestDistanceFoundYet = Long.MAX_VALUE;
		
		for (long i = 0; i < listtosearch.size(); i++) {
			long d1 = Math.abs(time - listtosearch.get((int) i).getTimestamp());
			long d2;
			if (i+1 < listtosearch.size())
				d2 = Math.abs(time - listtosearch.get((int) i+1).getTimestamp());
			else 
				d2 = Long.MAX_VALUE;

			if ( d1 < bestDistanceFoundYet ) {

				// For the moment, this value is the nearest to the desired number...
				bestDistanceFoundYet = d1;
				index=i;
				if (d1 <= d2) {
					if (LOGGER.isDebugEnabled())
						LOGGER.debug("Break at index " + i);
					break;
				}
			}
		}
		
		return index;
	}

	private Long nearestByIndexSlow(long time, String key) {
		// Search the redis cache
		Long index = null;
		Jedis jedis = null;
		try {
			jedis = jedispool.getResource();
			if (time > new LastStatus(jedis.lindex(key, 0L)).getTimestamp() || 
					time < new LastStatus(jedis.lindex(key, jedis.llen(key)-1)).getTimestamp() ) {
				
				if (LOGGER.isDebugEnabled())
					LOGGER.debug("Out of bounds");
						
				return null;
			}

			long bestDistanceFoundYet = Long.MAX_VALUE;
			long size = jedis.llen(key);

			for (long i = 0; i < size; i++) {
				long d1 = Math.abs(time - new LastStatus(jedis.lindex(key, i)).getTimestamp());
				long d2;
				if (i+1 < size)
					d2 = Math.abs(time - new LastStatus(jedis.lindex(key, i+1)).getTimestamp());
				else 
					d2 = Long.MAX_VALUE;

				if ( d1 < bestDistanceFoundYet ) {

					// For the moment, this value is the nearest to the desired number...
					bestDistanceFoundYet = d1;
					index=i;
					if (d1 <= d2) {
						if (LOGGER.isDebugEnabled())
							LOGGER.debug("Break at index " + i);
						break;
					}
				}
			}
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
		return index;
	}

	
	private Long nearestByIndex(long time, String key) {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Find value in cache at index " + new java.util.Date(time));
		
		Long index = null;
		
		// Search the fast cache first. If a hit is in the fast cache return 
		index = nearestByIndexFast(time, key);
		
		if (index != null) {
			incFastCacheCount();
		}
		else {
			// Search slow cache if no hit
			index = nearestByIndexSlow(time, key);
			incRedisCacheCount();
		}
		
		return index;
	}

	private synchronized void incFastCacheCount(long inc){
		fastcachehitcount += inc;
		if (fastcachehitcount == Long.MAX_VALUE) {
			fastcachehitcount = 0L;
			rediscachehitcount = 0L;
		}
	}
	
	private void incFastCacheCount(){
		incFastCacheCount(1);
	}
	
	
	private synchronized void incRedisCacheCount(long inc){
		rediscachehitcount += inc;
		if (rediscachehitcount == Long.MAX_VALUE) {
			rediscachehitcount = 0L;
			fastcachehitcount = 0L;
		}
	}

	private  void incRedisCacheCount(){
		incRedisCacheCount(1);
	}

	@Override
	public void trim(String key, Long maxSize) {
		Jedis jedis = null;
		try {
			jedis = jedispool.getResource();
			jedis.ltrim(key, 0, maxSize-1);
		} catch (JedisConnectionException je) {
			LOGGER.error("Redis connection failed: " + je.getMessage(),je);
		} finally {
			jedispool.returnResource(jedis);
		}
	}

}
