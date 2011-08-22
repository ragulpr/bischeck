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

package com.ingby.socbox.bischeck.service;

import java.util.List;
import java.util.Map;

import com.ingby.socbox.bischeck.Host;
import com.ingby.socbox.bischeck.serviceitem.ServiceItem;

/**
 * The interface describe all methods need to create a Service compatible 
 * class that can be instantiated by ServiceFactory class. The implemented class
 * must have a constructor with a parameter of String that is the service name.
 * The service name is the identification of the Service and must be unique in 
 * scope of the host its configured for. <br> 
 * The reason this is set in the constructor is that the name is never allowed 
 * to change in runtime. <br>
 * <code>
 * public myservice(String servicename) { <br>
 * &nbsp;&nbsp;this.servicename=servicename; <br>
 * } <br>
 * </code> 
 * To implement a custom Service its adviced to extend the abstracted class
 * ServiceAbstract class.
 *  
 *   
 * @author Anders Håål
 *
 */
public interface Service {
    /**
     * Get the service name for the Service.
     * @return service name
     */
    public String getServiceName();
	
    
    /**
     * Get the connection url.
     * @return the connection url
     */
	public String getConnectionUrl();
	
	
	/**
	 * The url string must follow a the specification of a url. The 
	 * ServiceFactory class use the schema part of the url to understand what 
	 * Service class to instantiate. Currently the ServiceFactory must be hacked
	 * to enable the selection of custom Service classes. 
	 * This will change in the future by implementing a configuration based 
	 * definition of Service classes and corresponding url schema.
	 * @param connectionUrl e.g. jdbc://...
	 */
	public void setConnectionUrl(String connectionUrl);
	
	
	/**
	 * Get the description text of the Service.
	 * @return description text
	 */
	public String getDecscription();
	
	
	/**
	 * Set the List of execution schedules, according to quartz crontrigger format
	 * @param schedulelist 
	 */
	public void setSchedules(List<String> schedulelist);

	/**
	 * Get the list of execution schedules, 
	 * @return list of schedules
	 */
	public List<String> getSchedules();
	
	
	/**
	 * Set the description text of the Service.
	 * @param decscription Description text
	 */
	public void setDecscription(String decscription);

	
	/**
	 * Get the driver class. 
	 * @return the driver class name
	 */
	public String getDriverClassName();
	
	
	/**
	 * Set the driver class name. This is part of the service is often related 
	 * to the url description of the Service. This should be described as a full
	 * class name and the class or jar file should be located in the customlib
	 * directory of the bischeck installation directory.  
	 * @param driverClassName Class name for the driver class
	 */
	public void setDriverClassName(String driverClassName);
	
	
	/**
	 * The open method are responsible to create a connection based on the url
	 * provided. THe connection must remain open until the closeConnection 
	 * method is called.   
	 * @throws Exception
	 */
	public void openConnection() throws Exception;
	
	
	/**
	 * The method close the connection and any recourses related to the 
	 * connection when the method is called. 
	 * @throws Exception
	 */
	public void closeConnection() throws Exception ;

	
	/**
	 * The method execute the statement described in the exec parameter 
	 * according to the Service type. As an example is the Service is a 
	 * JDBCService the exec parameter is typical a select string. 
	 * @param exec The statement string to execute
	 * @return the result of the execution of the exec parameter. Only a single 
	 * value is allowed. 
	 * @throws Exception
	 */
	String executeStmt(String exec) throws Exception;
	
	
	/**
	 * The method is called by the framework to add all service items related
	 * to the specific instance of the Service. 
	 * @param serviceItem The ServiceItem object to add
	 */
	public void addServiceItem(ServiceItem serviceItem);
	
	
	/**
	 * Return all the service items instances configured for the Service. 
	 * @return a Map of service items with service item name as the key.
	 */
	public Map<String,ServiceItem> getServicesItems();
	
	
	/**
	 * Return a service item object based on the service item name.
	 * @param name Service item name
	 * @return Service item object for the named service item
	 */
	public ServiceItem getServiceItemByName(String name);
	
	
	/**
	 * Get the message the will be sent with NSCA to the Nagios server. For more 
	 * info about the message format please see the standard Nagios
	 * documentation.
	 * @return the NSCA message
	 */
	//public String getNSCAMessage();

	
	/**
	 * Called by the framework to map the Service to a specific Host object. 
	 * @param host Host object
	 */
	public void setHost(Host host);
	
	
	/**
	 * Get the Host object for the Service.
	 * @return Host that the Service is related to
	 */
	public Host getHost();
}