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

package com.ingby.socbox.bischeck.configuration;

import java.text.ParseException;


import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;
import static org.quartz.CronScheduleBuilder.*;

import com.ingby.socbox.bischeck.ServiceDef;
import com.ingby.socbox.bischeck.cache.CacheFactory;
import com.ingby.socbox.bischeck.cache.CacheInf;
import com.ingby.socbox.bischeck.cache.CachePurgeInf;
import com.ingby.socbox.bischeck.cache.CacheUtil;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;

/**
 * This class is executed as a Quartz job to purge cache data according to:<br>
 * <ul>
 * <li>Max number of items in an LRU way - like 1000</li>
 * <li>Purge data older then a specific time from purging time - like 2 days</li>
 * </ul>
 * <br>
 * The purging strategy is based on what is specified in the &lt;cache&gt; tag 
 * in the bischeck.xml file. The job is used both for &lt;purge&gt; and 
 * &lt;retention&gt;.
 * <br>
 * The class take one property cachePurgeJobCron that is a cron expression when
 * the purge job should be run. The default is every five minutes with start 
 * 2 minutes after the hour , "0 2/5 * * * ? *" 
 */
public class CachePurgeJob implements Job {

    private final static Logger  LOGGER = LoggerFactory.getLogger(CachePurgeJob.class);

    private static Scheduler sched;
    
    
    /**
     * Initialize the purge job
     * @param prop the properties used for the purge job
     * @throws SchedulerException
     * @throws ParseException
     */
    public static void init(Properties prop) throws SchedulerException {
    	
    	
    	sched = StdSchedulerFactory.getDefaultScheduler();
        if (!sched.isStarted()) {
        	sched.start();
        }
        
        
        JobDetail job = newJob(CachePurgeJob.class).
            withIdentity("CachePurge", "DailyMaintenance").
            withDescription("CachePurge").    
            build();
                
        
        CronTrigger trigger = newTrigger()
        .withIdentity("CachePurgeTrigger", "DailyMaintenance")
        .withSchedule(cronSchedule(prop.getProperty("cachePurgeJobCron","0 2/5 * * * ? *")))
        .forJob("CachePurge", "DailyMaintenance")
        .build();
        
        // If job exists delete and add
        if (sched.getJobDetail(job.getKey()) != null) {
        	sched.deleteJob(job.getKey());
        }
        
        Date ft = sched.scheduleJob(job, trigger);
        
        sched.addJob(job, true);
        
        LOGGER.info("{} has been scheduled to run at: {} and repeat based on expression: {}",
                job.getDescription(), ft, trigger.getCronExpression());
    }
    

    @Override
    public void execute(JobExecutionContext arg0) throws JobExecutionException {
        final Timer timer = Metrics.newTimer(CachePurgeJob.class, "purgeTimer",
                TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
        final TimerContext context = timer.time();

        try {
            Map<String, String> purgeMap = ConfigurationManager.getInstance()
                    .getPurgeMap();
            LOGGER.info("CachePurge purging {}", purgeMap.size());
            CacheInf cache = CacheFactory.getInstance();

            for (String key : purgeMap.keySet()) {
                if (cache instanceof CachePurgeInf) {
                    
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Purge key {} with rule {}", key,
                                purgeMap.get(key));
                        
                        ServiceDef servicedef = new ServiceDef(key);

                        LOGGER.debug("Key {} size {} before purge", key, cache.size(servicedef.getHostName(),
                                servicedef.getServiceName(),
                                servicedef.getServiceItemName()
                                ));
                        if (cache.size(servicedef.getHostName(),
                                servicedef.getServiceName(),
                                servicedef.getServiceItemName()
                                ) > 0) {
                            LOGGER.debug(
                                    "Key {} time at 0 <{}> and time at end <{}> before purge",
                                    key,
                                    new Date (cache.getLastStatusByIndex(
                                            servicedef.getHostName(),
                                            servicedef.getServiceName(),
                                            servicedef.getServiceItemName(), 0).getTimestamp()).toString(),
                                            new Date (cache.getLastStatusByIndex(
                                                    servicedef.getHostName(),
                                                    servicedef.getServiceName(),
                                                    servicedef.getServiceItemName(),
                                                    cache.size(servicedef.getHostName(),
                                                            servicedef.getServiceName(),
                                                            servicedef.getServiceItemName()) - 1).getTimestamp()).toString());
                        }

                    }

                    if (CacheUtil.isByTime(purgeMap.get(key))) {
                        // find the index of the time
                        LOGGER.debug("Purge key {} by time", key);
                        ServiceDef servicedef = new ServiceDef(key);
                        Long index = cache.getIndexByTime(
                                servicedef.getHostName(),
                                servicedef.getServiceName(),
                                servicedef.getServiceItemName(),
                                System.currentTimeMillis()
                                        + ((long) CacheUtil
                                                .calculateByTime(purgeMap
                                                        .get(key))) * 1000);
                        // if index is null there is no items in the cache older
                        // then the time offset
                        
                        LOGGER.debug("Purge key {} purge from index 0 to index {}", key, index);
                        if (index != null) {
                            ((CachePurgeInf) cache).trim(key, index);
                        }
                    } else {
                        LOGGER.debug("Purge key {} by index", key);
                        LOGGER.debug("Purge key {} purge from index 0 to index {}", key, Long.valueOf(purgeMap.get(key)));
                        ((CachePurgeInf) cache).trim(key,
                                Long.valueOf(purgeMap.get(key)));
                    }
                }
                
                if (LOGGER.isDebugEnabled()) {
                    
                    ServiceDef servicedef = new ServiceDef(key);

                    LOGGER.debug("Key {} size {} after purge", key, cache.size(servicedef.getHostName(),
                            servicedef.getServiceName(),
                            servicedef.getServiceItemName()
                            ));
                    if (cache.size(servicedef.getHostName(),
                            servicedef.getServiceName(),
                            servicedef.getServiceItemName()
                            ) > 0) {
                        LOGGER.debug(
                                "Key {} time at 0 <{}> and time at end <{}> after purge",
                                key,
                                new Date (cache.getLastStatusByIndex(
                                        servicedef.getHostName(),
                                        servicedef.getServiceName(),
                                        servicedef.getServiceItemName(), 0).getTimestamp()).toString(),
                                        new Date (cache.getLastStatusByIndex(
                                                servicedef.getHostName(),
                                                servicedef.getServiceName(),
                                                servicedef.getServiceItemName(),
                                                cache.size(servicedef.getHostName(),
                                                        servicedef.getServiceName(),
                                                        servicedef.getServiceItemName()) - 1).getTimestamp()).toString());
                    }
                }

            }
        } finally {
            long duration = context.stop() / 1000000;
            LOGGER.info("CachePurge executed in {} ms", duration);
        }
    }

}