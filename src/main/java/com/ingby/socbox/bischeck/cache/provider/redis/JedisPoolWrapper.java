package com.ingby.socbox.bischeck.cache.provider.redis;

import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Provide a wrapper of JedisPool to enable specific pool configuration.
 */
public class JedisPoolWrapper {

	private final static Logger LOGGER = LoggerFactory.getLogger(JedisPoolWrapper.class);

	private JedisPool jedispool;

	/**
	 * Create the jedis connection pool
	 * @param redisserver name of the server running the redis server- IP or FQDN
	 * @param redisport the server socket port the redis server is listening on
	 * @param redistimeout the connection timeout when connecting to redis server
	 * @param redisauth the authentication token used when connecting to redis server
	 * @param redisdb - the number of the redis database
	 */
	public JedisPoolWrapper(String redisserver, Integer redisport, Integer redistimeout, String redisauth, Integer redisdb, Integer maxPoolSize) {
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setWhenExhaustedAction(GenericObjectPool.WHEN_EXHAUSTED_BLOCK);
		poolConfig.setMaxActive(maxPoolSize);
		LOGGER.info("Max active: {} Max Idel: {} When exhusted: {}",poolConfig.getMaxActive(), poolConfig.getMaxIdle(), poolConfig.getWhenExhaustedAction());
		jedispool = new JedisPool(poolConfig,redisserver,redisport,redistimeout,redisauth,redisdb);	
	}

	/**
	 * Get a connection resources from the pool
	 * @return the connection 
	 */
	public Jedis getResource() {
		Jedis jedis = jedispool.getResource();
		if (jedis == null) 
			throw new JedisConnectionException("No pool resources available");
		return jedis;
	}
	
	/**
	 * Return the connection to the pool after usage
	 * @param jedis
	 */
	public void returnResource(Jedis jedis) {
		if (jedis != null) {
			jedispool.returnResource(jedis);
		} else {
			LOGGER.warn("Tried to return a null object to the redis pool");
		}
		
	}
	
	/**
	 * Destroy the pool
	 */
	public void destroy() {
		jedispool.destroy();
	}
}
