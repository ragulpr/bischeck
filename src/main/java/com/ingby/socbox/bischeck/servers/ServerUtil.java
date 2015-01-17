/*
#
# Copyright (C) 2010-2013 Anders Håål, Ingenjorsbyn AB
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
package com.ingby.socbox.bischeck.servers;

import org.slf4j.Logger;

import com.ingby.socbox.bischeck.Util;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.service.ServiceTO;


/**
 * Utilities used by the {@link Server} implementations.<br>
 * The utilities supported are: <br>
 * <ul>
 * <li>Different log formats for sent data to the server(s)</li>
 * </ul>
 *
 */
public class ServerUtil {

    /**
     * One line grep friendly log message 
     * @param instanceName name of the server instance
     * @param service the service object collected
     * @param message the formatted message
     * @return the string to log
     */
    public static String logFormat(String instanceName, Service service, String message) {

        return logFormat(instanceName, service.getHost().getHostname(),service.getServiceName(), message);
    }

    public static String logFormat(String instanceName, ServiceTO serviceTo, String message) {

        return logFormat(instanceName, serviceTo.getHostName(),serviceTo.getServiceName(), message);
    }

    public static String log(String instanceName, ServiceTO serviceTo, String message, Long sendTime) {

        StringBuilder strbuf = new StringBuilder();
        strbuf
        .append("{\"instanceName\":\"")
        .append(instanceName)
        .append("\",\"key\":")
        .append(Util.fullQouteHostServiceName(serviceTo.getHostName(),serviceTo.getServiceName()))
        .append("\",\"time_ms\":")
        .append(sendTime)
        .append(",\"message\":\"")
        .append(message)
        .append("\"}");

        return strbuf.toString();
    }

    public static String logError(String instanceName, ServiceTO serviceTo, Throwable exception, Long count) {

        StringBuilder strbuf = new StringBuilder();
        strbuf
        .append("{\"instanceName\":\"")
        .append(instanceName)
        .append("\",\"key\":")
        .append(Util.fullQouteHostServiceName(serviceTo.getHostName(),serviceTo.getServiceName()))
        .append("\",\"count\":")
        .append(count)
        .append(",\"exceptionClass\":\"")
        .append(exception.getClass().getName())
        .append("\",\"exceptionMessage\":\"")
        .append(exception.getMessage())
        .append("\"}");

        return strbuf.toString();
    }

    /**
     * One line grep friendly log message 
     * @param instanceName name of the server instance
     * @param hostName name of the host data is collected from
     * @param serviceName name of the service that was collected
     * @param message the formatted message
     * @return the string to log
     */
    public static String logFormat(String instanceName, String hostName, String serviceName, String message) {
        StringBuilder strbuf = new StringBuilder();
        strbuf.append(instanceName).
        append(":").
        append(hostName).
        append(":").
        append(serviceName).
        append(":").
        append(message);

        return strbuf.toString();
    }

    /**
     * The none grep friendly multi-line log message
     * @param instanceName name of the server instance
     * @param service the service object collected
     * @param message the formatted message
     * @param logger the logged object
     */
    public static void logFormat(String instanceName, Service service, String message,Logger logger) {
        logger.info("******************** "+ instanceName +" *******************");
        logger.info("*");
        logger.info("*    Host: " + service.getHost().getHostname());
        logger.info("* Service: " + service.getServiceName());
        logger.info("* Message: " + message);
        logger.info("*");
        logger.info("*********************************************");
    }

}
