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

package com.ingby.socbox.bischeck.host;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingby.socbox.bischeck.service.Service;

public class Host {

    private final static Logger  LOGGER = LoggerFactory.getLogger(Host.class);
    
    private String hostname;
    private HashMap<String,Service> services = new HashMap<String,Service>();
    private String description;
    private String alias;


	public Host (String hostname) {
        this.hostname = hostname;
    }

    
    public void addService(Service service) {
        services.put(service.getServiceName(), service);
    }

    
    public HashMap<String,Service> getServices() {
        return services;
    }

    
    public Service getServiceByName(String name) {
        for (Map.Entry<String, Service> serviceentry: services.entrySet()) {
            Service service = serviceentry.getValue();
            if (service.getServiceName().compareTo(name) == 0) {
                return service;
            }
        }
        return null;
    }

    
    public String getHostname() {
        return hostname;
    }

    
    public String getDecscription() {
        return description;
    }
    
    
    public void setDecscription(String decscription) {
        this.description = decscription;
    }
    
    public String getAlias() {
		return alias;
	}


	public void setAlias(String alias) {
		this.alias = alias;
	}
}