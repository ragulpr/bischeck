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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingby.socbox.bischeck.ClassCache;
import com.ingby.socbox.bischeck.Util;
import com.ingby.socbox.bischeck.configuration.ConfigurationManager;

/**
 * Service factory to instantiate {@link Service} objects 
 *
 */
public class ServiceFactory {

    private final static Logger LOGGER = LoggerFactory.getLogger(ServiceFactory.class);
    
    /**
     * Create the service based on the uri
     * @param serviceName the name of the service
     * @param url the service url
     * @return
     * @throws Exception if the service url is not available, the class do 
     * not exists, a constructor is not existing or if a constructor that 
     * take a the service name as a parameter do not exists
     */
    @SuppressWarnings("unchecked")
    public static Service createService(String serviceName, String url) throws Exception 
    {
        
        URI uri = null;
        try {
            uri= new URI(url);
            LOGGER.debug("uri - {}", uri.toString());
        } catch (URISyntaxException e) {
            LOGGER.warn("Malformed uri - {}", url, e.getMessage());
            throw new Exception(e.getMessage());
        }
        
        String clazzname = ConfigurationManager.getInstance().getURL2Service().getProperty(uri.getScheme());
        
        if (clazzname == null) {
            LOGGER.error("Service uri {} is not matched in the urlservice.xml configuration file.", Util.obfuscatePassword(uri.toString()));
            throw new Exception("Service uri " + Util.obfuscatePassword(uri.toString()) + " is not matched in the urlservice.xml configuration file.");
        }
        
        Class<Service> clazz = null;
        
        try {
            clazz = (Class<Service>) ClassCache.getClassByName("com.ingby.socbox.bischeck.service."+clazzname);
        } catch (ClassNotFoundException e) {
            try { 
                clazz = (Class<Service>) ClassCache.getClassByName(clazzname);
            }catch (ClassNotFoundException ee) {
                LOGGER.error("Service class {} not found.", clazzname);
                throw new Exception(e.getMessage());
            }
        }
         
        Class param[] = (Class[]) Array.newInstance(Class.class, 1);
        param[0] = String.class;
        
        
        Constructor cons = null;
        try {
            cons = clazz.getConstructor(param);
        } catch (Exception e) {
            LOGGER.error("Error getting class constructor for {}", clazz.getName());
            throw new Exception(e.getMessage());
        }
        
        Service service = null;
        try {
            service = (Service) cons.newInstance(serviceName);
        } catch (Exception e) {
            LOGGER.error("Error creating an instance of {} with class {}", serviceName, clazz.getName());
            throw new Exception(e.getMessage());
        }
        return service;
    }
}
