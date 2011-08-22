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

import com.ingby.socbox.bischeck.QueryDate;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.threshold.Threshold;

/**
 * The ServiceItemAbstract class provide most of the methods needed by a 
 * ServiceItem implementation. The methods not implemented in the abstract 
 * class is: <b>
 * public void execute() throws Exception <b>
 *  
 * @author Anders Håål
 *
 */


public abstract class ServiceItemAbstract {

	protected String 	serviceItemName;
	protected String 	decscription;
	protected String 	execution;
	protected Service 	service;
	protected String 	thresholdclassname;
	protected String 	latestValue = null;
	protected Long 		exectime;
	protected Threshold threshold;

	
	public void setService(Service service) {
		this.service = service;
	}

	
	public String getServiceItemName() {
		return this.serviceItemName;
	}

	
	public String getDecscription() {
		return decscription;
	}

	
	public void setDecscription(String decscription) {
		this.decscription = decscription;
	}
	
	
	public String getExecution() {
		return QueryDate.parse(execution);	
	}

	
	public String getExecutionStat() {
		return execution;	
	}
	
	
	public void setExecution(String execution) {
		this.execution = execution;
	}

	
	public void execute() throws Exception {				
		latestValue = service.executeStmt(this.getExecution());	
	}

	
	public String getThresholdClassName() {
		return this.thresholdclassname;
		
	}

	
	public void setThresholdClassName(String thresholdclassname) {
		this.thresholdclassname = thresholdclassname;
	}

	
	public void setLatestExecuted(String value) {
		latestValue = value;
	}

	
	public String getLatestExecuted() {
		return latestValue;
	}

	
	public void setExecutionTime(Long exectime) {
		this.exectime = exectime;
	}

	
	public Long getExecutionTime() {
		return exectime;
	}

	
	public Threshold getThreshold() {
		return this.threshold;
	}

	
	public void setThreshold(Threshold threshold) {
		this.threshold = threshold;
	}
}