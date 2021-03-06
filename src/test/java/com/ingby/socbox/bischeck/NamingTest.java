/*
#
# Copyright (C) 2010-2012 Anders Håål, Ingenjorsbyn AB
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

package com.ingby.socbox.bischeck;

import org.testng.Assert;

import org.testng.annotations.Test;

import com.ingby.socbox.bischeck.ObjectDefinitions;
import com.ingby.socbox.bischeck.Util;

public class NamingTest {

    @Test (groups = { "Naming" })
    public void verifyHostName() {
        Assert.assertEquals(
            ObjectDefinitions.verifyHostName("bischeck.ingby.com"),
            "bischeck.ingby.com");
        Assert.assertEquals(
                ObjectDefinitions.verifyHostName("bi_scheck.in\\\\-gby.com"),
                "bi_scheck.in\\\\-gby.com");
    }
    
    
    @Test (groups = { "Naming" })
    public void verifyHostServiceServiceItemName() {
        Assert.assertEquals(
            ObjectDefinitions.verifyHostServiceServiceItem("bischeck.ingby.com-@service0-_service1.2.3_99item[89]"),
            "bischeck.ingby.com-@service0-_service1.2.3_99item[89]");
    }
    
    
    @Test( groups = { "Naming" }, expectedExceptions = java.lang.IllegalStateException.class)  
    public void verifyNamingException() {
        ObjectDefinitions.verifyHostName("_bischeck.ingby.com");
    }
    

    @Test (groups = { "Naming" })
    public void verifyQoutedname() {
        Assert.assertEquals(Util.fullQoutedName("local-host", "service-1","serviceitem-1"),"local\\-host-service\\-1-serviceitem\\-1");
    }
    
    @Test (groups = { "Naming" })
    public void verifyAll() {
        Assert.assertEquals(Util.fullQoutedName("local-host", "serv/ic/e-1","service/it/em-1"),"local\\-host-serv/ic/e\\-1-service/it/em\\-1");
        Assert.assertEquals(Util.fullQoutedName("local-host", "serv/i c/e-1","serv ice/it/em-1"),"local\\-host-serv/i c/e\\-1-serv ice/it/em\\-1");
        Assert.assertEquals(Util.fullQoutedName("local-host", "serv/i  c/e-1","serv  ice/it/em-1"),"local\\-host-serv/i  c/e\\-1-serv  ice/it/em\\-1");
    }
}



