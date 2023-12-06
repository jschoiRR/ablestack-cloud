// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// Automatically generated by addcopyright.py at 01/29/2013
package org.apache.cloudstack.api;

import javax.inject.Inject;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.cloudstack.api.response.PhysicalNetworkResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.baremetal.database.BaremetalDhcpVO;
import com.cloud.baremetal.networkservice.BaremetalDhcpManager;
import com.cloud.baremetal.networkservice.BaremetalDhcpResponse;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;

@APICommand(name = "addBaremetalDhcp", description = "adds a baremetal dhcp server", responseObject = BaremetalDhcpResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class AddBaremetalDhcpCmd extends BaseAsyncCmd {
    protected static Logger logger = LogManager.getLogger(AddBaremetalDhcpCmd.class);

    @Inject
    BaremetalDhcpManager mgr;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID,
               type = CommandType.UUID,
               entityType = PhysicalNetworkResponse.class,
               required = true,
               description = "the Physical Network ID")
    private Long physicalNetworkId;

    @Parameter(name = ApiConstants.DHCP_SERVER_TYPE, type = CommandType.STRING, required = true, description = "Type of dhcp device")
    private String dhcpType;

    @Parameter(name = ApiConstants.URL, type = CommandType.STRING, required = true, description = "URL of the external dhcp appliance.")
    private String url;

    @Parameter(name = ApiConstants.USERNAME, type = CommandType.STRING, required = true, description = "Credentials to reach external dhcp device")
    private String username;

    @Parameter(name = ApiConstants.PASSWORD, type = CommandType.STRING, required = true, description = "Credentials to reach external dhcp device")
    private String password;

    @Override
    public String getEventType() {
        return EventTypes.EVENT_BAREMETAL_DHCP_SERVER_ADD;
    }

    @Override
    public String getEventDescription() {
        return "Adding an external DHCP server";
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
        ResourceAllocationException, NetworkRuleConflictException {
        try {
            BaremetalDhcpVO vo = mgr.addDchpServer(this);
            BaremetalDhcpResponse response = mgr.generateApiResponse(vo);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } catch (Exception e) {
            logger.warn("Unable to add external dhcp server with url: " + getUrl(), e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }

    public String getDhcpType() {
        return dhcpType;
    }

    public void setDhcpType(String dhcpType) {
        this.dhcpType = dhcpType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    public void setPhysicalNetworkId(Long physicalNetworkId) {
        this.physicalNetworkId = physicalNetworkId;
    }
}
