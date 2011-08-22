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

package com.ingby.socbox.bischeck.serviceitem;

import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.threshold.Threshold;

/**
 * The interface describe all methods need to create a ServiceItem compatible 
 * class that can be instantiated by ServiceItemFactory class. The implemented 
 * class must have a constructor with a parameter of String that is the service 
 * item name.
 * The service item name is the identification of the ServiceItem and must be 
 * unique in scope of the service its configured for. <br> 
 * The reason this is set in the constructor is that the name is never allowed 
 * to change in runtime. <br>
 * <code>
 * public myserviceitem(String serviceitemname) { <br>
 * &nbsp;&nbsp;this.serviceitemname=serviceitemname; <br>
 * } <br>
 * </code> 
 * To implement a custom Service its adviced to extend the abstracted class
 * ServiceItemAbstract class.
 *  
 *   
 * @author Anders Håål
 *
 */
public interface ServiceItem {
	
    /**
     * Get the service item name for the ServiceItem
     * @return service item name
     */
	public String getServiceItemName();

	
	/**
     * Get the description text of the ServiceItem
     * @return description test
     */
    public String getDecscription();
    
    
    /**
     * Set the description text of the ServiceItem.
     * @param decscription Description text
     */
    public void setDecscription(String decscription);
    
    
    /**
     * Get the execution statement string.
     * @return the execute statement string
     */
    public String getExecution();

    
    /**
     * Set the execution statement string.
     * @param execution The execution statement for the ServiceItem
     */
    public void setExecution(String execution);

    
    /**
     * Get the execution statement string without DateQuery parsing. This is
     * for configuration presentations
     * @return the execute statement string
     */
    public String getExecutionStat();

    
    /**
     * Get the class name for the threshold class used for the ServiceItem.
     * @return the threshold class name
     */
    public String getThresholdClassName();
	
    
    /**
     * Set the class name for the threshold class used for the ServiceItem. 
     * @param thresholdclassname Name of the Threshold class 
     */
    public void setThresholdClassName(String thresholdclassname);
	
    
	/**
	 * Set the time for executing the ServiceItem's execution statement. 
	 * @param exectime The time to execute the ServiceItem statement
	 */
	public void setExecutionTime(Long exectime);
	
	
	/**
	 * Return the time it took to execute the execution statement. 
	 * @return the execution time
	 */
	public Long getExecutionTime();
	
	
	/**
	 * Set a reference to the currently used threshold object.
	 * @param threshold Threshold object to use for threshold calculation
	 */
	public void setThreshold(Threshold threshold);
	
	
	/**
	 * Get the threshold object used for the ServiceItem object.
	 * @return Threshold for the ServiceItem
	 */
	public Threshold getThreshold();
	
	
	/**
	 * Set the execution statement for the ServiceItem.
	 * @throws Exception
	 */
	public void execute() throws Exception;
	
	
	/**
	 * Return the last return value from the execution.
	 * @return last execution value
	 */
	public String getLatestExecuted();
	
	
	/**
	 * Set the last executed value form the execution.
	 * @param value
	 */
	public void setLatestExecuted(String value);
	
	
	/**
	 * Set the Service object the the ServiceItem is configured for. 
	 * @param service Service object that the ServiceItem belongs to
	 */
	public void setService(Service service);
}