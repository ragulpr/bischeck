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

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.quartz.CronExpression;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import ch.qos.logback.classic.Level;

import com.ingby.socbox.bischeck.Util;
import com.ingby.socbox.bischeck.host.Host;
import com.ingby.socbox.bischeck.servers.Server;
import com.ingby.socbox.bischeck.service.RunAfter;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.service.ServiceFactory;
import com.ingby.socbox.bischeck.service.ServiceFactoryException;
import com.ingby.socbox.bischeck.service.ServiceJobConfig;
import com.ingby.socbox.bischeck.serviceitem.ServiceItem;
import com.ingby.socbox.bischeck.serviceitem.ServiceItemFactory;
import com.ingby.socbox.bischeck.serviceitem.ServiceItemFactoryException;
import com.ingby.socbox.bischeck.threshold.ThresholdFactory;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLBischeck;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLCache;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLHost;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLService;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLServiceitem;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLServiceitemtemplate;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLServicetemplate;
import com.ingby.socbox.bischeck.xsd.properties.XMLProperties;
import com.ingby.socbox.bischeck.xsd.properties.XMLProperty;
import com.ingby.socbox.bischeck.xsd.servers.XMLServer;
import com.ingby.socbox.bischeck.xsd.servers.XMLServers;
import com.ingby.socbox.bischeck.xsd.urlservices.XMLUrlproperty;
import com.ingby.socbox.bischeck.xsd.urlservices.XMLUrlservices;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;

/**
 * The ConfigurationManager class is responsible for all core configuration of 
 * bischeck.
 * The ConfigurationManager is shared and only instantiated once through the 
 * class factory at startup. 
 * At a reload the ConfigurationManager is recreated and all configuration is 
 * reread to enable update without a complete process restart.
 *
 */

public final class ConfigurationManager  {
    
    private static final String DEFAULT_TRESHOLD = "DummyThreshold";

	public static final String INTERVALSCHEDULEPATTERN = "^[0-9]+ *[HMS]{1} *$";

    private final static Logger LOGGER = LoggerFactory.getLogger(ConfigurationManager.class);

    /*
     * The ConfigurationManager 
     */
    private static ConfigurationManager configMgr = null;
    
    private Properties prop = null;    
    private Properties url2service = null;
    private Map<String,Host> hostsmap = null;
    private List<ServiceJobConfig> schedulejobs = null;
    private Map<String,Properties> servermap = null;
    private Map<String,Class<?>> serversclass = null;
    private ConfigFileManager xmlfilemgr = null;
    
    private Map<RunAfter,List<Service>> runafter = null;
	
    private Map<String,XMLServicetemplate> serviceTemplateMap = null;
    private Map<String,XMLServiceitemtemplate> serviceItemTemplateMap = null;
    
    private Map<String,String> purgeMap = null;
    
    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new GnuParser();
        CommandLine line = null;
        // create the Options
        Options options = new Options();
        options.addOption( "u", "usage", false, "show usage." );
        options.addOption( "v", "verify", false, "verify all xml configuration with their xsd" );
        options.addOption( "p", "pidfile", false, "Show bischeck pid file path" );
        
        try {
            // parse the command line arguments
            line = parser.parse( options, args );

        } catch (org.apache.commons.cli.ParseException e) {
            System.out.println( "Command parse error:" + e.getMessage() );
            System.exit(1);
        }

        if (line.hasOption("usage")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "ConfigurationManager", options );
            System.exit(0);
        }

        ConfigurationManager.initonce();
        ConfigurationManager confMgmr = ConfigurationManager.getInstance();
        
        ((ch.qos.logback.classic.Logger) LOGGER).setLevel(Level.WARN);
        
        if (line.hasOption("verify")) {
            System.exit(ValidateConfiguration.verify());
        }

        if (line.hasOption("pidfile")) {
            System.out.println("PidFile:"+confMgmr.getPidFile().getPath());    
        }
        
        
        /* Since this is running from command line stop all existing schedulers */
        StdSchedulerFactory.getDefaultScheduler().shutdown();
    }

    
    private ConfigurationManager() {}    
    
    
    private void allocateDataStructs() {
    	xmlfilemgr  = new ConfigFileManager();
    	prop = new Properties();    
    	url2service = new Properties();
    	hostsmap = new HashMap<String,Host>();
    	schedulejobs = new ArrayList<ServiceJobConfig>();
    	servermap = new HashMap<String,Properties>();
    	serversclass = new HashMap<String,Class<?>>();
    	runafter = new HashMap<RunAfter,List<Service>>();
    	serviceTemplateMap = new HashMap<String, XMLServicetemplate>();
    	serviceItemTemplateMap = new HashMap<String, XMLServiceitemtemplate>();
    	purgeMap = new HashMap<String, String>();
    }
    
    /**
     * The method is used to verify and init all data structures based on 
     * bischeck configuration files. The method will only schedule any services
     * just once. This can be used for testing.
     * This method should be called before any getInstance() calls are done. 
     * @throws ConfigurationException if the configuration is faulty
     */
    synchronized public static void initonce() throws  ConfigurationException {
    	initConfiguration(true);
    }
    
    
    /**
     * The method is used to verify and init all data structures based on 
     * bischeck configuration files. 
     * This method should be called before any getInstance() calls are done. 
     * @throws ConfigurationException if the configuration is faulty
     */
    synchronized public static void init() throws ConfigurationException {
    	initConfiguration(false);
    }

    
    private static void initConfiguration(boolean runOnce) throws ConfigurationException {
    	if (configMgr == null ) 
            configMgr = new ConfigurationManager();

    	final Timer timer = Metrics.newTimer(ConfigurationManager.class, 
				"ConfigurationInit" , TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
		final TimerContext context = timer.time();

        configMgr.allocateDataStructs();
        
        try {
        	// Init all data structures. 
        	configMgr.initProperties();
        	configMgr.initURL2Service();
        	configMgr.initServers();
        	configMgr.initBischeckServices(runOnce);
        	configMgr.initScheduler();
        	
        	ThresholdFactory.clearCache();
        	
        	// Verify if the pid file is writable
        	if (!configMgr.checkPidFile()) {
        		LOGGER.error("Can not write to pid file {}", configMgr.getPidFile());
        		throw new ConfigurationException("Can not write to pid file " + configMgr.getPidFile());
        	}
        	
        } catch (ConfigurationException e) {
        	LOGGER.error("Configuration Manager initzialization failed with {}", e.getMessage(),e);
        	throw e;
        }
        finally {
        	long duration = context.stop()/1000000;
			LOGGER.info("Configuration init time: {} ms", duration);
        }
    	
    }
    
    
    /**
     * Get the ConfigurationManager object that is shared among all. Make sure 
     * the init() method is called before any call to this metod. 
     * @return Configuration object if the init() method has been called. If not
     * null will be returned.
     */
    synchronized public static ConfigurationManager getInstance() {
        if (configMgr == null ) {
            return null;
        }
        return configMgr;
    }

    
    private void initProperties() throws ConfigurationException  {
        XMLProperties propertiesconfig = 
            (XMLProperties) xmlfilemgr.getXMLConfiguration(ConfigXMLInf.XMLCONFIG.PROPERTIES);

        Iterator<XMLProperty> iter = propertiesconfig.getProperty().iterator();

        while (iter.hasNext()) {
            XMLProperty propertyconfig = iter.next(); 
            prop.put(propertyconfig.getKey(),propertyconfig.getValue());      
        }
    }

    
    private void initURL2Service() throws ConfigurationException {     

        XMLUrlservices urlservicesconfig  = 
            (XMLUrlservices) xmlfilemgr.getXMLConfiguration(ConfigXMLInf.XMLCONFIG.URL2SERVICES);

        Iterator<XMLUrlproperty> iter = urlservicesconfig.getUrlproperty().iterator();
        while (iter.hasNext() ) {
            XMLUrlproperty urlpropertyconfig = iter.next(); 
            url2service.put(urlpropertyconfig.getKey(),urlpropertyconfig.getValue());
        }
    }

    
    private void initScheduler() throws ConfigurationException {
        try {
        	CachePurgeJob.init(this);
            ThresholdCacheClearJob.init(this);
        } catch (SchedulerException e) {
            LOGGER.error("Quartz scheduler failed with exception {}", e.getMessage(), e);
            throw new ConfigurationException(e);
        } catch (ParseException e) {
            LOGGER.error("Quartz scheduler failed with exception " + e.getMessage());
            throw new ConfigurationException(e);
        }
    }
    
    
    private void initBischeckServices(boolean once) 
    		throws ConfigurationException {
        XMLBischeck bischeckconfig  =
                (XMLBischeck) xmlfilemgr.getXMLConfiguration(ConfigXMLInf.XMLCONFIG.BISCHECK);

        // Init Service templates 
        for (XMLServicetemplate serviceTemplate: bischeckconfig.getServicetemplate()) {
        	serviceTemplateMap.put(serviceTemplate.getTemplatename(),serviceTemplate);
        }
        
        // Init Serviceitem templates
        for (XMLServiceitemtemplate serviceItemTemplate: bischeckconfig.getServiceitemtemplate()) {
        	serviceItemTemplateMap.put(serviceItemTemplate.getTemplatename(),serviceItemTemplate);
        }
        
        // Conduct the Host, Service and ServiceItem configuration 
        try {
			setupHost(bischeckconfig);
		} catch (ServiceFactoryException e) {
			throw new ConfigurationException(e);
		} catch (ServiceItemFactoryException e) {
			throw new ConfigurationException(e);
		}
        
        // Create the quartz schedule triggers and store in a List
        setServiceTriggers(once);
    }


    private void setServiceTriggers(boolean once) throws ConfigurationException {
        for (Map.Entry<String, Host> hostentry: hostsmap.entrySet()) {
            Host host = hostentry.getValue();
            for (Map.Entry<String, Service> serviceentry: host.getServices().entrySet()) {
                Service service = serviceentry.getValue();
                ServiceJobConfig servicejobconfig = new ServiceJobConfig(service);
                Iterator<String> schedulesIter = service.getSchedules().iterator();
                int triggerid = 0;
                
                // Just get the first and only one entry if once
                if (once) {
                    if (schedulesIter.hasNext()) {
                        String schedule = schedulesIter.next();
                        Trigger trigger = triggerFactoryOnce(schedule, service, triggerid++);
                        servicejobconfig.addSchedule(trigger);
                    }
                }else {
                    while (schedulesIter.hasNext()) {
                        String schedule = schedulesIter.next();
                        Trigger trigger = triggerFactory(schedule, service, triggerid++);
                        servicejobconfig.addSchedule(trigger);
                    }
                }
                schedulejobs.add(servicejobconfig);
            }    
        }
    }


    private void setupHost(XMLBischeck bischeckconfig) 
    		throws ServiceFactoryException, ConfigurationException, ServiceItemFactoryException {
        Iterator<XMLHost> iterhost = bischeckconfig.getHost().iterator();
        
        while (iterhost.hasNext() ) {
            XMLHost hostconfig = iterhost.next(); 

            Host host= null;
            if (hostsmap.containsKey(hostconfig.getName())) {
                host = hostsmap.get(hostconfig.getName());
            }
            else {
                host = new Host(hostconfig.getName());   
                hostsmap.put(hostconfig.getName(),host);
            }
            
            host.setAlias(hostconfig.getAlias());
            host.setDecscription(hostconfig.getDesc());

            setupService(hostconfig, host);

            // Set the macro values
            ConfigMacroUtil.replaceMacros(host);
            if (LOGGER.isDebugEnabled()) {
            	StringBuffer strbuf = ConfigMacroUtil.dump(host);
            	LOGGER.debug(strbuf.toString());
            }
        }
    }


    private void setupService(XMLHost hostconfig, Host host) 
    		throws ServiceFactoryException, ConfigurationException, ServiceItemFactoryException {
        Iterator<XMLService> iterservice = hostconfig.getService().iterator();
        
        while (iterservice.hasNext()) {
            XMLService serviceconfig = iterservice.next();
            
            Service service = null;
            
            // If a template is detected
            if (serviceTemplateMap.containsKey(serviceconfig.getTemplate())) { 
            	XMLServicetemplate template = serviceTemplateMap.get(serviceconfig.getTemplate());
            	LOGGER.debug("Found Service template {}", template.getTemplatename());
            	service = ServiceFactory.createService(
            			template.getName(),
            			template.getUrl().trim());

            	service.setHost(host);
            	service.setAlias(template.getAlias());
            	service.setDecscription(template.getDesc());
            	service.setSchedules(template.getSchedule());
            	service.setConnectionUrl(template.getUrl().trim());
            	service.setDriverClassName(template.getDriver());
            	if (template.isSendserver() != null) {
            		service.setSendServiceData(template.isSendserver());
            	} else {
            		service.setSendServiceData(true);
            	}

            }
            // If a normal service configuration is detected
            else {

            	service = ServiceFactory.createService(
            			serviceconfig.getName(),
            			serviceconfig.getUrl().trim());

            	//Check for null - not supported logger.error
            	service.setHost(host);
            	service.setAlias(serviceconfig.getAlias());
            	service.setDecscription(serviceconfig.getDesc());
            	service.setSchedules(serviceconfig.getSchedule());
            	service.setConnectionUrl(serviceconfig.getUrl().trim());
            	service.setDriverClassName(serviceconfig.getDriver());
            	if (serviceconfig.isSendserver() != null) {
            		service.setSendServiceData(serviceconfig.isSendserver());
            	} else {
            		service.setSendServiceData(true);
            	}

            }

            // Common actions
            if (service.getDriverClassName() != null) {
        		if (service.getDriverClassName().trim().length() != 0) {
        			LOGGER.debug("Driver name: {}", service.getDriverClassName().trim());
        			try {
        				Class.forName(service.getDriverClassName().trim()).newInstance();
						
        			} catch (ClassNotFoundException e) {
        				LOGGER.error("Could not find the driver class {} for service {} ", 
        						service.getServiceName(), service.getDriverClassName(), e);
        				throw new ConfigurationException(e);
        			} catch (InstantiationException e) {
        				LOGGER.error("Could not instantiate the driver class {} for service {}", 
        						service.getServiceName(), service.getDriverClassName(), e);
        				throw new ConfigurationException(e);
					} catch (IllegalAccessException e) {
						LOGGER.error("Could not acces the driver class {} for service {}", 
								service.getServiceName(), service.getDriverClassName(), e);
        				throw new ConfigurationException(e);
					}
        		}
        	}

            setupServiceItem(serviceconfig, service);
            
            host.addService(service);    
        }
    }


    private void setupServiceItem(XMLService serviceconfig, Service service)
            throws ServiceItemFactoryException, ServiceFactoryException {
        
    	Iterator<XMLServiceitem> iterserviceitem = null; 
    	
        // If the service was a template - search in the template
        if (serviceTemplateMap.containsKey(serviceconfig.getTemplate())) { 
        	XMLServicetemplate template = serviceTemplateMap.get(serviceconfig.getTemplate());
        	 iterserviceitem = template.getServiceitem().iterator();
        }
        else {
        	iterserviceitem = serviceconfig.getServiceitem().iterator();
        }
        
        while (iterserviceitem.hasNext()) {
        	ServiceItem serviceitem = null;
        	
        	// If a normal service configuration is detected
        	XMLServiceitem serviceitemconfig = iterserviceitem.next();

        	if (serviceItemTemplateMap.containsKey(serviceitemconfig.getTemplate())){
        		XMLServiceitemtemplate template = serviceItemTemplateMap.get(serviceitemconfig.getTemplate());
            	LOGGER.debug("Found ServiceItem template " + template.getTemplatename());
            	serviceitem = ServiceItemFactory.createServiceItem(
            			template.getName(),
            			template.getServiceitemclass().trim());
            	serviceitem.setService(service);
            	serviceitem.setClassName(template.getServiceitemclass().trim());
            	serviceitem.setAlias(template.getAlias());
            	serviceitem.setDecscription(template.getDesc());
            	serviceitem.setExecution(template.getExecstatement());

            	/*
            	 * Set default threshold class if not set in bischeck.xml
            	 */
            	if (template.getThresholdclass() == null || 
            			template.getThresholdclass().trim().length() == 0 ) {
            		serviceitem.setThresholdClassName(DEFAULT_TRESHOLD);
            	} else {
            		serviceitem.setThresholdClassName(template.getThresholdclass().trim());
            	}
            	
            	Aggregation aggregation = new Aggregation(template.getCache(),service,serviceitem);
            	aggregation.setAggregate();
            	//purgeMap.putAll(aggregation.getRetentionMap());
            	setPurge(aggregation.getRetentionMap());
            	setPurge(template.getCache(),service,serviceitem);
                
            } else {
            	serviceitem = ServiceItemFactory.createServiceItem(
            			serviceitemconfig.getName(),
            			serviceitemconfig.getServiceitemclass().trim());

            	serviceitem.setService(service);
            	serviceitem.setClassName(serviceitemconfig.getServiceitemclass().trim());
            	serviceitem.setAlias(serviceitemconfig.getAlias());
            	serviceitem.setDecscription(serviceitemconfig.getDesc());
            	serviceitem.setExecution(serviceitemconfig.getExecstatement());

            	/*
            	 * Set default threshold class if not set in bischeck.xml
            	 */
            	if (serviceitemconfig.getThresholdclass() == null || 
            			serviceitemconfig.getThresholdclass().trim().length() == 0 ) {
            		serviceitem.setThresholdClassName(DEFAULT_TRESHOLD);
            	} else {
            		serviceitem.setThresholdClassName(serviceitemconfig.getThresholdclass().trim());
            	}
            	
            	Aggregation aggregation = new Aggregation(serviceitemconfig.getCache(),service,serviceitem);
            	aggregation.setAggregate();
            	//purgeMap.putAll(aggregation.getRetentionMap());
            	setPurge(aggregation.getRetentionMap());
            	setPurge(serviceitemconfig.getCache(),service,serviceitem);
                
            }
        	
            
        	service.addServiceItem(serviceitem);

        }
    }
    
    

    /**
     * A Map with the key servicedefs and the value of the max number in the 
     * cache before purging. 
     * @param retentionMap
     */
    private void setPurge(Map<String, String> retentionMap) {
    	purgeMap.putAll(retentionMap);
	}


	/**
     * For serviceitem that has <purge> defined the purging will be set up.
     * Currently supporting only <maxcount>
     * @param xmlconfig
     * @param service
     * @param serviceitem
     */
	private void setPurge(XMLCache xmlconfig, Service service, ServiceItem serviceitem) {
		if (xmlconfig == null)
			return;
		
		if (xmlconfig.getPurge() != null) {
			String key = Util.fullName(service, serviceitem);
			if(xmlconfig.getPurge().getMaxcount() != null) {
				purgeMap.put(key, String.valueOf(xmlconfig.getPurge().getMaxcount()));
			} else if (xmlconfig.getPurge().getOffset() != null && xmlconfig.getPurge().getPeriod() != null) {
				purgeMap.put(key, "-" + xmlconfig.getPurge().getOffset() + xmlconfig.getPurge().getPeriod());
			}
		}
	}
    

	private void initServers() throws ConfigurationException {
        XMLServers serversconfig = (XMLServers) xmlfilemgr.getXMLConfiguration(ConfigXMLInf.XMLCONFIG.SERVERS);

        Iterator<XMLServer> iter = serversconfig.getServer().iterator();

        while (iter.hasNext()) {
            XMLServer serverconfig = iter.next(); 
            try {
				setServers(serverconfig);
			} catch (ClassNotFoundException e) {
				LOGGER.error("The class {} for server {} was not found", serverconfig.getClazz(), serverconfig.getName(), e);
				throw new ConfigurationException(e);
			}        
        }
    }


    private void setServers(XMLServer serverconfig)
            throws ClassNotFoundException {
        
        Iterator<com.ingby.socbox.bischeck.xsd.servers.XMLProperty> propIter = serverconfig.getProperty().iterator();
        
        Properties prop = setServerProperties(propIter);
        
        servermap.put(serverconfig.getName(), prop);            
        
        serversclass.put(serverconfig.getName(), getServerClass(serverconfig.getClazz().trim()));
    }

    @SuppressWarnings("unchecked")
    private Class<?> getServerClass(String clazzname) throws ClassNotFoundException { 

    	Class<Server> clazz = null;

    	try {
    		clazz=(Class<Server>) Class.forName("com.ingby.socbox.bischeck.servers." +clazzname);
    	} catch (ClassNotFoundException e) {
    		try {
    			clazz=(Class<Server>) Class.forName(clazzname);
    		}catch (ClassNotFoundException ee) {
    			LOGGER.error("Server class {} not found.",  clazzname);
    			throw ee;
    		}
    	}
    	return clazz;
    }


    private Properties setServerProperties(
            Iterator<com.ingby.socbox.bischeck.xsd.servers.XMLProperty> propIter) {
        Properties prop = new Properties();
        
        while (propIter.hasNext()) {
            com.ingby.socbox.bischeck.xsd.servers.XMLProperty propertyconfig = propIter.next();
            prop.put(propertyconfig.getKey(),propertyconfig.getValue());
        }
        return prop;
    }

    
    /**
     * Creates a simple or cron trigger based on format.
     * @param schedule
     * @param service
     * @param triggerid
     * @return 
     * @throws ConfigurationException 
     */
    private Trigger triggerFactory(String schedule, Service service, int triggerid) throws ConfigurationException {
        
        Trigger trigger = null;
        
        if (isCronTrigger(schedule)) {
            // Cron schedule    
                trigger = newTrigger()
                .withIdentity(service.getServiceName()+"Trigger-"+(triggerid), service.getHost().getHostname()+"TriggerGroup")
                .withSchedule(
                		cronSchedule(schedule).withMisfireHandlingInstructionDoNothing())
                .build();
            
            
        } else if (isIntervalTrigger(schedule)){
        	// Simple schedule
        	trigger = newTrigger()
        	.withIdentity(service.getServiceName()+"Trigger-"+(triggerid), service.getHost().getHostname()+"TriggerGroup")
        	.startAt(randomStartTime(calculateInterval(schedule)))
        	.withSchedule(
        			//simpleSchedule().
        			SimpleScheduleBuilder.
        			repeatSecondlyForever(calculateInterval(schedule)).
        			withMisfireHandlingInstructionNextWithRemainingCount())
        			.build();
        	
        } else if (isRunAfterTrigger(schedule)) {
        	int index = schedule.indexOf("-");
        	String hostname = schedule.substring(0, index);
        	String servicename = schedule.substring(index+1, schedule.length());
        	LOGGER.debug("Check for services that will run after host {} and service {}", hostname, servicename);
        	RunAfter runafterkey = new RunAfter(hostname, servicename);

        	if (!runafter.containsKey(runafterkey)) {
        		LOGGER.debug("Add service {}-{} to run after host {} and service {} ", 
        				service.getHost().getHostname(),service.getServiceName(), hostname, servicename);
        		runafter.put(runafterkey, new ArrayList<Service>());		
        	}
        	
        	runafter.get(runafterkey).add(service);
        }
        	
        return trigger;
    }

    
    /**
     * The method calculate Date based on the 
     * current-time + random(0,intervalinsec). This value is used to set the
     * Initial start time of an interval schedule. 
     * @param intervalinsec the interval to calculate the offset from
     * @return a Date that is current-time + current-time + random(0,intervalinsec) 
     */
    private Date randomStartTime(int intervalinsec) {
    	long randomininterval = ((long) (Math.random()*intervalinsec*1000));
    	long starttime = System.currentTimeMillis()+randomininterval;
    	return new Date(starttime);
    }
    
    
    /**
     * Creates a simple or cron trigger based on format.
     * @param schedule
     * @param service
     * @param triggerid
     * @return 
     */
    private Trigger triggerFactoryOnce(String schedule, Service service, int triggerid) {

    	Trigger trigger = null;

    	trigger = newTrigger()
    			.withIdentity(service.getServiceName()+"Trigger-"+(triggerid), service.getHost().getHostname()+"TriggerGroup")
    			.withSchedule(simpleSchedule().
    					withRepeatCount(0))
    					.startNow()
    					.build();
    	LOGGER.debug("Tigger for host {} and service {} for schedule {}", 
    			service.getHost().getHostname(), service.getServiceName(), schedule);

        return trigger;
    }

    /**
     * The method calculate the interval for continues scheduling if the format
     * is time interval and time unit, like "50 S" where the scheduling occur.
     * every 50 seconds.
     * @param schedule the scheduling string
     * @return the interval in seconds
     * @throws ConfigurationException if the formating of an interval is not 
     * correct
     */
    private int calculateInterval(String schedule) throws ConfigurationException {
        //"^[0-9]+ *[HMS]{1} *$" - check for a
        Pattern pattern = Pattern.compile(INTERVALSCHEDULEPATTERN);

        // Determine if there is an exact match
        Matcher matcher = pattern.matcher(schedule);
        if (matcher.matches()) {
            String withoutSpace=schedule.replaceAll(" ","");
            char time = withoutSpace.charAt(withoutSpace.length()-1);
            String value = withoutSpace.substring(0, withoutSpace.length()-1);
            LOGGER.debug("Time selected {}:{}", time, value);
            switch (time) {
            case 'S' : return (Integer.parseInt(value)); 
            case 'M' : return (Integer.parseInt(value)*60); 
            case 'H' : return (Integer.parseInt(value)*60*60);
            }
        }
        
        throw new ConfigurationException("String" + schedule + " is not according to regex " + INTERVALSCHEDULEPATTERN );
    }


    private static boolean isCronTrigger(String schedule) { 
        return CronExpression.isValidExpression(schedule);    
    }
    
    
    private static boolean isIntervalTrigger(String schedule) {
    	Pattern pattern = Pattern.compile(INTERVALSCHEDULEPATTERN);
        Matcher matcher = pattern.matcher(schedule);
        
        if (matcher.matches()) {
        	return true;
        }
    	
        return false;
    }
    
    
    private static boolean isRunAfterTrigger(String schedule) {
    	int index = schedule.indexOf("-");
    	if (index == -1) {
    		return false;
    	}
    	
    	String hostname = schedule.substring(0, index);
    	String servicename = schedule.substring(index+1, schedule.length());
    	
    	Host hostafter = ConfigurationManager.getInstance().hostsmap.get(hostname);
    	
    	if (hostafter != null) {
    		
    		Service serviceafter = hostafter.getServiceByName(servicename);
        	
    		if (serviceafter != null) {
    		
    			if (!(hostname.equals(hostafter.getHostname()) && 
    				servicename.equals(serviceafter.getServiceName()))) { 
    				LOGGER.warn("RunAfter host and/or service do not exists for host " + 
    						hostname + 
    						"-" +
    						servicename);
    				return false;
    			}
    		} else {
    			LOGGER.warn("RunAfter service do not exists for " + 
    					hostname + 
    					"-" +
    					servicename);
    			return false;
    		}
    			
    	} else {
    		LOGGER.warn("RunAfter host do not exists for " + 
					hostname + 
					"-" +
					servicename);
			return false;
    	}
    	
    	return true;
    }
    
        
    public Properties getURL2Service() {
        return url2service;
    }


    public Properties getProperties() {
        return prop;
    }
    
    public Map<String, Host> getHostConfig() {
        return hostsmap;
    }


    public List<ServiceJobConfig> getScheduleJobConfigs() {
        return schedulejobs;
    }
        
    public Map<String,String> getPurgeMap() {
    	return purgeMap;
    }
    
    public  File getPidFile() {
        return new File(prop.getProperty("pidfile","/var/tmp/bischeck.pid"));
    }

    public  boolean checkPidFile() {
        File pidfile = getPidFile();
        if (pidfile.exists()) {
        	if (pidfile.canWrite())
        		return true;
        	else
        		return false;
        }
        else {
        	if(new File(pidfile.getParent()).canWrite())
        		return true;
        	else
        		return false;
        }
    }
    
    public Properties getServerProperiesByName(String name) {
        return servermap.get(name);
    }

    public Map<String,Class<?>> getServerClassMap() {
        return serversclass;
    }
    
    public Map<RunAfter,List<Service>> getRunAfterMap() {
    	return runafter;
    }

}