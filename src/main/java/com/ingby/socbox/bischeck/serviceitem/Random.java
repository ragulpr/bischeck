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

import com.ingby.socbox.bischeck.service.ServiceException;

/**
 * The class simply generate a random number between 0 and the number define in
 * the executestatement.
 */
public class Random extends ServiceItemAbstract implements ServiceItem {

    public Random(String name) {
        this.serviceItemName = name;
    }

    /**
     * The serviceitem
     */
    @Override
    public void execute() throws ServiceException, ServiceItemException {

        Float rand = (float) Math.random() * Float.parseFloat(getExecution());
        setLatestExecuted(Float.toString(rand));

    }
}
