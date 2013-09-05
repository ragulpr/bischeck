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
 * the purge job should be run. The default is every hour, "0 0 0/1 * * ? *" 
 */
public class CachePurgeJob implements Job {

    private final static Logger  LOGGER = LoggerFactory.getLogger(CachePurgeJob.class);

    private static Scheduler sched;

    public static void init(ConfigurationManager configMgr) throws SchedulerException, ParseException {
    	
    	
    	sched = StdSchedulerFactory.getDefaultScheduler();
        if (!sched.isStarted())
        	sched.start();
        
        
        JobDetail job = newJob(CachePurgeJob.class).
            withIdentity("CachePurge", "DailyMaintenance").
            withDescription("CachePurge").    
            build();
                
        
        // Every minute TODO MAKE A PROPERTY
        CronTrigger trigger = newTrigger()
        .withIdentity("CachePurgeTrigger", "DailyMaintenance")
        .withSchedule(cronSchedule(configMgr.getProperties().getProperty("cachePurgeJobCron","0 0/1 0/1 * * ? *")))
        .forJob("CachePurge", "DailyMaintenance")
        .build();
        
        // If job exists delete and add
        if (sched.getJobDetail(job.getKey()) != null)
        		sched.deleteJob(job.getKey());
        Date ft = sched.scheduleJob(job, trigger);
        
        sched.addJob(job, true);
        
        LOGGER.info(job.getDescription() + " has been scheduled to run at: " + ft
                + " and repeat based on expression: "
                + trigger.getCronExpression());
    }
    
    
    @Override
    public void execute(JobExecutionContext arg0) throws JobExecutionException {
        final Timer timer = Metrics.newTimer(CachePurgeJob.class, 
				"purge" , TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
		final TimerContext context = timer.time();
		
		try {
			Map<String, String> purgeMap = ConfigurationManager.getInstance().getPurgeMap();
			for (String key : purgeMap.keySet()) {
				CacheInf cache = CacheFactory.getInstance();
				if (cache instanceof CachePurgeInf) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug(key + ":" + purgeMap.get(key));
					}
					
					if (CacheUtil.isByTime(purgeMap.get(key))) {
						// find the index of the time
						ServiceDef servicedef = new ServiceDef(key);
						Long index = cache.getIndexByTime( 
								servicedef.getHostName(),
								servicedef.getServiceName(), 
								servicedef.getServiceItemName(),
								System.currentTimeMillis() + ((long) CacheUtil.calculateByTime(purgeMap.get(key)))*1000);
						((CachePurgeInf) cache).trim(key, index);
					} else {
						((CachePurgeInf) cache).trim(key, Long.valueOf(purgeMap.get(key)));
					}
				}
			}
		} finally {
			long duration = context.stop()/1000000;
			LOGGER.info("CachePurge executed in " + duration + " ms");
		}
    }

}