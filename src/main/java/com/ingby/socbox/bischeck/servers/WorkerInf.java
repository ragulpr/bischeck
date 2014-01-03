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

import com.ingby.socbox.bischeck.service.Service;

public interface WorkerInf {
    
    final int MAX_RUNS_BEFORE_END = 100;
    
    /**
     * Send the Service information to the server. Implementation is responsible
     * to manage protocol and formatting of message data.
     * @param service
     * @throws ServerException if any communication exception occur. 
     * If {@ ServerCircuitBreak} is used it will be triggered by the exception.
     */
    public void send(Service service) throws ServerException;

}
