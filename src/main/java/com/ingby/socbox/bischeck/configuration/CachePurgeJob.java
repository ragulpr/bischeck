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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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

import com.codahale.metrics.Timer;
import com.ingby.socbox.bischeck.ServiceDef;
import com.ingby.socbox.bischeck.cache.CacheFactory;
import com.ingby.socbox.bischeck.cache.CacheInf;
import com.ingby.socbox.bischeck.cache.CachePurgeInf;
import com.ingby.socbox.bischeck.cache.CacheUtil;
import com.ingby.socbox.bischeck.monitoring.MetricsManager;
import com.ingby.socbox.bischeck.service.ServiceJob;

/**
 * This class is executed as a Quartz job to purge cache data according to:<br>
 * <ul>
 * <li>Max number of items in an LRU way - like 1000</li>
 * <li>Purge data older then a specific time from purging time - like 2 days</li>
 * </ul>
 * <br>
 * The purging strategy is based on what is specified in the &lt;cache&gt; tag
 * in the bischeck.xml file. The job is used both for &lt;purge&gt; and
 * &lt;retention&gt;. <br>
 * The class take one property cachePurgeJobCron that is a cron expression when
 * the purge job should be run. The default is every five minutes with start 2
 * minutes after the hour , "0 2/5 * * * ? *"
 */
public class CachePurgeJob implements Job {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(CachePurgeJob.class);

    private final static Logger LOGGER_TRANS = LoggerFactory
            .getLogger("transaction." + ServiceJob.class.getName());
    
    private static Scheduler sched;

    private static final String DAILY_MAINTENANCE = "DailyMaintenance";
    private static final String CACHE_PURGE = "CachePurge";

    /**
     * Initialize the purge job
     * 
     * @param prop
     *            the properties used for the purge job
     * @throws SchedulerException
     * @throws ParseException
     */
    public static void init(Properties prop) throws SchedulerException {

        sched = StdSchedulerFactory.getDefaultScheduler();
        if (!sched.isStarted()) {
            sched.start();
        }

        JobDetail job = newJob(CachePurgeJob.class)
                .withIdentity(CACHE_PURGE, DAILY_MAINTENANCE)
                .withDescription(CACHE_PURGE).build();

        CronTrigger trigger = newTrigger()
                .withIdentity(CACHE_PURGE + "Trigger", DAILY_MAINTENANCE)
                .withSchedule(
                        cronSchedule(prop.getProperty("cachePurgeJobCron",
                                "0 2/5 * * * ? *")))
                .forJob(CACHE_PURGE, DAILY_MAINTENANCE).build();

        // If job exists delete and add
        if (sched.getJobDetail(job.getKey()) != null) {
            sched.deleteJob(job.getKey());
        }

        Date ft = sched.scheduleJob(job, trigger);

        LOGGER.info(
                "{} has been scheduled to run at: {} and repeat based on expression: {}",
                job.getDescription(), ft, trigger.getCronExpression());
    }

    @Override
    public void execute(JobExecutionContext arg0) throws JobExecutionException {

        final Timer timer = MetricsManager.getTimer(CachePurgeJob.class,
                "purgeTimer");
        final Timer.Context context = timer.time();

        Map<String, PurgeDefinition> purgeMap = ConfigurationManager.getInstance()
                .getPurgeMap();
        try {
            LOGGER.debug("CachePurge purging {}", purgeMap.size());
            CacheInf cache = CacheFactory.getInstance();

            if (cache instanceof CachePurgeInf) {
                
                ((CachePurgeInf) cache).purge(purgeMap);
            }

        } finally {
            long duration = context.stop() / MetricsManager.TO_MILLI;
            LOGGER.debug("CachePurge executed in {} ms", duration);
            LOGGER_TRANS.info("{\"label\":\"cache-purge\",\"keys\":{}\",\"time_ms\":{}}",purgeMap.size(),duration);
        }
    }

}