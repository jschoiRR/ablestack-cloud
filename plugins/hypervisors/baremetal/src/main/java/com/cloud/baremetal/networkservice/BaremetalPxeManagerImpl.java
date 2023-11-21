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
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.baremetal.networkservice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.api.AddBaremetalKickStartPxeCmd;
import org.apache.cloudstack.api.AddBaremetalPxeCmd;
import org.apache.cloudstack.api.AddBaremetalPxePingServerCmd;
import org.apache.cloudstack.api.ListBaremetalPxeServersCmd;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupPxeServerCommand;
import com.cloud.agent.api.routing.VmDataCommand;
import com.cloud.baremetal.database.BaremetalPxeVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.NetworkModel;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceStateAdapter;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.uservm.UserVm;
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.UserVmDao;

public class BaremetalPxeManagerImpl extends ManagerBase implements BaremetalPxeManager, ResourceStateAdapter {
    @Inject
    DataCenterDao _dcDao;
    @Inject
    HostDao _hostDao;
    @Inject
    AgentManager _agentMgr;
    @Inject
    ResourceManager _resourceMgr;
    @Inject
    List<BaremetalPxeService> _services;
    @Inject
    UserVmDao _vmDao;
    @Inject
    ServiceOfferingDao _serviceOfferingDao;
    @Inject
    NicDao _nicDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    PhysicalNetworkDao _phynwDao;
    @Inject
    NetworkModel _ntwkModel;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        setName(name);
        _resourceMgr.registerResourceStateAdapter(this.getClass().getSimpleName(), this);
        return true;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        _resourceMgr.unregisterResourceStateAdapter(this.getClass().getSimpleName());
        return true;
    }

    protected BaremetalPxeService getServiceByType(String type) {
        for (BaremetalPxeService service : _services) {
            if (service.getPxeServiceType().equals(type)) {
                return service;
            }
        }

        throw new CloudRuntimeException("Cannot find PXE service for " + type);
    }

    @Override
    public boolean prepare(VirtualMachineProfile profile, NicProfile nic, Network network, DeployDestination dest, ReservationContext context) {
        //TODO: select type from template
        BaremetalPxeType type = BaremetalPxeType.KICK_START;
        return getServiceByType(type.toString()).prepare(profile, nic, network, dest, context);
    }

    @Override
    public boolean prepareCreateTemplate(Long pxeServerId, UserVm vm, String templateUrl) {
        //TODO: select type from template
        BaremetalPxeType type = BaremetalPxeType.PING;
        return getServiceByType(type.toString()).prepareCreateTemplate(pxeServerId, vm, templateUrl);
    }

    @Override
    public BaremetalPxeType getPxeServerType(HostVO host) {
        if (host.getResource().equalsIgnoreCase(BaremetalPingPxeResource.class.getName())) {
            return BaremetalPxeType.PING;
        } else {
            throw new CloudRuntimeException("Unkown PXE server resource " + host.getResource());
        }
    }

    @Override
    public HostVO createHostVOForConnectedAgent(HostVO host, StartupCommand[] cmd) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public HostVO createHostVOForDirectConnectAgent(HostVO host, StartupCommand[] startup, ServerResource resource, Map<String, String> details, List<String> hostTags) {
        if (!(startup[0] instanceof StartupPxeServerCommand)) {
            return null;
        }

        host.setType(Host.Type.BaremetalPxe);
        return host;
    }

    @Override
    public DeleteHostAnswer deleteHost(HostVO host, boolean isForced, boolean isForceDeleteStorage) throws UnableDeleteHostException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BaremetalPxeVO addPxeServer(AddBaremetalPxeCmd cmd) {
        return getServiceByType(cmd.getDeviceType()).addPxeServer(cmd);
    }

    @Override
    public BaremetalPxeResponse getApiResponse(BaremetalPxeVO vo) {
        return getServiceByType(vo.getDeviceType()).getApiResponse(vo);
    }

    @Override
    public List<BaremetalPxeResponse> listPxeServers(ListBaremetalPxeServersCmd cmd) {
        return getServiceByType(BaremetalPxeType.KICK_START.toString()).listPxeServers(cmd);
    }

    @Override
    public boolean addUserData(NicProfile nic, VirtualMachineProfile profile) {
        UserVmVO vm = _vmDao.findById(profile.getVirtualMachine().getId());
        _vmDao.loadDetails(vm);

        String serviceOffering = _serviceOfferingDao.findByIdIncludingRemoved(vm.getId(), vm.getServiceOfferingId()).getDisplayText();
        String zoneName = _dcDao.findById(vm.getDataCenterId()).getName();
        NicVO nvo = _nicDao.findById(nic.getId());
        VmDataCommand cmd = new VmDataCommand(nvo.getIPv4Address(), vm.getInstanceName(), _ntwkModel.getExecuteInSeqNtwkElmtCmd());
        // if you add new metadata files, also edit systemvm/patches/debian/config/var/www/html/latest/.htaccess
        cmd.addVmData("userdata", "user-data", vm.getUserData());
        cmd.addVmData("metadata", "service-offering", StringUtils.unicodeEscape(serviceOffering));
        cmd.addVmData("metadata", "availability-zone", StringUtils.unicodeEscape(zoneName));
        cmd.addVmData("metadata", "local-ipv4", nic.getIPv4Address());
        cmd.addVmData("metadata", "local-hostname", StringUtils.unicodeEscape(vm.getInstanceName()));
        cmd.addVmData("metadata", "public-ipv4", nic.getIPv4Address());
        cmd.addVmData("metadata", "public-hostname", StringUtils.unicodeEscape(vm.getInstanceName()));
        cmd.addVmData("metadata", "instance-id", String.valueOf(vm.getUuid()));
        cmd.addVmData("metadata", "vm-id", String.valueOf(vm.getInstanceName()));
        cmd.addVmData("metadata", "public-keys", null);
        String cloudIdentifier = _configDao.getValue("cloud.identifier");
        if (cloudIdentifier == null) {
            cloudIdentifier = "";
        } else {
            cloudIdentifier = "CloudStack-{" + cloudIdentifier + "}";
        }
        cmd.addVmData("metadata", "cloud-identifier", cloudIdentifier);

        List<PhysicalNetworkVO> phys = _phynwDao.listByZone(vm.getDataCenterId());
        if (phys.isEmpty()) {
            throw new CloudRuntimeException(String.format("Cannot find physical network in zone %s", vm.getDataCenterId()));
        }
        if (phys.size() > 1) {
            throw new CloudRuntimeException(String.format("Baremetal only supports one physical network in zone, but zone %s has %s physical networks",
                vm.getDataCenterId(), phys.size()));
        }
        PhysicalNetworkVO phy = phys.get(0);

        QueryBuilder<BaremetalPxeVO> sc = QueryBuilder.create(BaremetalPxeVO.class);
        //TODO: handle both kickstart and PING
        //sc.addAnd(sc.getEntity().getPodId(), Op.EQ, vm.getPodIdToDeployIn());
        sc.and(sc.entity().getPhysicalNetworkId(), Op.EQ, phy.getId());
        BaremetalPxeVO pxeVo = sc.find();
        if (pxeVo == null) {
            throw new CloudRuntimeException("No PXE server found in pod: " + vm.getPodIdToDeployIn() + ", you need to add it before starting VM");
        }

        try {
            Answer ans = _agentMgr.send(pxeVo.getHostId(), cmd);
            if (!ans.getResult()) {
                s_logger.debug(String.format("Add userdata to vm:%s failed because %s", vm.getInstanceName(), ans.getDetails()));
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            s_logger.debug(String.format("Add userdata to vm:%s failed", vm.getInstanceName()), e);
            return false;
        }
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmds = new ArrayList<Class<?>>();
        cmds.add(AddBaremetalKickStartPxeCmd.class);
        cmds.add(AddBaremetalPxePingServerCmd.class);
        cmds.add(ListBaremetalPxeServersCmd.class);
        return cmds;
    }
}
