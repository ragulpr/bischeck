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

import org.jetlang.core.Callback;

import com.ingby.socbox.bischeck.service.Service;

/**
 * The interface used by Server implementations to manage async message based 
 * communication with ServiceJobs.
 *  
 * @author andersh
 *
 */
public interface MessageServerInf extends Callback<Service>{
	
	public void onMessage(Service message);	
}