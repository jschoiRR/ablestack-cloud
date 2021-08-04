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

package com.cloud.desktop.cluster.actionworkers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cloudstack.api.BaseCmd;
import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Level;

import com.cloud.dc.DataCenter;
import com.cloud.dc.Vlan;
import com.cloud.dc.VlanVO;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ManagementServerException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.cloud.network.IpAddressManager;
import com.cloud.network.Network;
import com.cloud.network.addr.PublicIp;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.offering.ServiceOffering;
import com.cloud.user.Account;
import com.cloud.uservm.UserVm;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.Ip;
import com.cloud.utils.net.NetUtils;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.desktop.cluster.DesktopCluster;
import com.cloud.desktop.cluster.DesktopClusterVmMapVO;
import com.cloud.desktop.cluster.DesktopClusterManagerImpl;
import com.cloud.desktop.version.DesktopTemplateMapVO;
import com.cloud.desktop.version.dao.DesktopTemplateMapDao;
import com.cloud.desktop.version.DesktopControllerVersion;
import com.cloud.vm.Nic;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.ReservationContextImpl;
import com.cloud.vm.VirtualMachine;
import com.google.common.base.Strings;

public class DesktopClusterStartWorker extends DesktopClusterResourceModifierActionWorker {

    private DesktopControllerVersion desktopClusterVersion;
    private IpAddressManager ipAddressManager;
    private DesktopTemplateMapDao desktopTemplateMapDao;

    public DesktopClusterStartWorker(final DesktopCluster desktopCluster, final DesktopClusterManagerImpl clusterManager) {
        super(desktopCluster, clusterManager);
    }

    public DesktopControllerVersion getDesktopClusterVersion() {
        if (desktopClusterVersion == null) {
            desktopClusterVersion = desktopControllerVersionDao.findById(desktopCluster.getDesktopVersionId());
        }
        return desktopClusterVersion;
    }

    private Pair<String, Map<Long, Network.IpAddresses>> getDesktopControlNodeIpAddresses(final DataCenter zone, final Network network, final Account account) throws InsufficientAddressCapacityException {
        String controlNodeIp = null;
        Map<Long, Network.IpAddresses> requestedIps = null;
        if (Network.GuestType.Shared.equals(network.getGuestType())) {
            List<Long> vlanIds = new ArrayList<>();
            List<VlanVO> vlans = vlanDao.listVlansByNetworkId(network.getId());
            for (VlanVO vlan : vlans) {
                vlanIds.add(vlan.getId());
            }
            PublicIp ip = ipAddressManager.getAvailablePublicIpAddressFromVlans(zone.getId(), null, account, Vlan.VlanType.DirectAttached, vlanIds,network.getId(), null, false);
            if (ip != null) {
                controlNodeIp = ip.getAddress().toString();
            }
            requestedIps = new HashMap<>();
            Ip ipAddress = ip.getAddress();
            boolean isIp6 = ipAddress.isIp6();
            requestedIps.put(network.getId(), new Network.IpAddresses(ipAddress.isIp4() ? ip.getAddress().addr() : null, null));
        } else {
            controlNodeIp = ipAddressManager.acquireGuestIpAddress(networkDao.findById(desktopCluster.getNetworkId()), null);
        }
        return new Pair<>(controlNodeIp, requestedIps);
    }

    private UserVm provisionDesktopClusterDcControlVm(final Network network, String publicIpAddress) throws ManagementServerException,
            ResourceUnavailableException, InsufficientCapacityException {
        UserVm dcControlVm = null;
        dcControlVm = createDesktopClusterDcControlVm(network, publicIpAddress);
        addDesktopClusterVm(desktopCluster.getId(), dcControlVm.getId());
        startDesktopVM(dcControlVm);
        dcControlVm = userVmDao.findById(dcControlVm.getId());
        if (dcControlVm == null) {
            throw new ManagementServerException(String.format("Failed to provision dc control VM for Desktop cluster : %s" , desktopCluster.getName()));
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Provisioned dc control VM : %s in to the Desktop cluster : %s", dcControlVm.getDisplayName(), desktopCluster.getName()));
        }
        return dcControlVm;
    }

    private UserVm createDesktopClusterDcControlVm(final Network network, String serverIp) throws ManagementServerException,
            ResourceUnavailableException, InsufficientCapacityException {
        UserVm dcControlVm = null;
        DataCenter zone = dataCenterDao.findById(desktopCluster.getZoneId());
        ServiceOffering serviceOffering = serviceOfferingDao.findById(desktopCluster.getServiceOfferingId());
        List<Long> networkIds = new ArrayList<Long>();
        networkIds.add(desktopCluster.getNetworkId());
        Pair<String, Map<Long, Network.IpAddresses>> ipAddresses = getDesktopControlNodeIpAddresses(zone, network, owner);
        String controlDcIp = ipAddresses.first();
        Map<Long, Network.IpAddresses> requestedIps = ipAddresses.second();
        if (Network.GuestType.Shared.equals(network.getGuestType()) && Strings.isNullOrEmpty(serverIp)) {
            serverIp = controlDcIp;
        }
        VirtualMachineTemplate dcTemplate = null;
        List<DesktopTemplateMapVO> templateList = desktopTemplateMapDao.listByVersionId(desktopCluster.getDesktopVersionId());
        for (DesktopTemplateMapVO templateMapVO : templateList) {
            if (templateMapVO.getVersionId() == desktopCluster.getDesktopVersionId()) {
                if (templateMapVO.getType() == "dc") {
                    dcTemplate = templateDao.findById(templateMapVO.getTemplateId());
                }
            }
        }
        Network.IpAddresses addrs = new Network.IpAddresses(controlDcIp, null);
        String hostName = desktopCluster.getName() + "-dc-control";
        dcControlVm = userVmService.createAdvancedVirtualMachine(zone, serviceOffering, dcTemplate, networkIds, owner,
                hostName, hostName, null, null, null,
                dcTemplate.getHypervisorType(), BaseCmd.HTTPMethod.POST, null, null,
                requestedIps, addrs, null, null, null, null, null, null, null, null, true);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Created control VM ID: %s, %s in the Desktop cluster : %s", dcControlVm.getUuid(), hostName, desktopCluster.getName()));
        }
        return dcControlVm;
    }

    private UserVm provisionDesktopClusterWorksControlVm(final Network network, String publicIpAddress) throws
            InsufficientCapacityException, ManagementServerException, ResourceUnavailableException {
        UserVm worksControlVm = null;
        worksControlVm = createDesktopClusterWorksControlVm(network, publicIpAddress);
        addDesktopClusterVm(desktopCluster.getId(), worksControlVm.getId());
        startDesktopVM(worksControlVm);
        worksControlVm = userVmDao.findById(worksControlVm.getId());
        if (worksControlVm == null) {
            throw new ManagementServerException(String.format("Failed to provision works control VM for Desktop cluster : %s" , desktopCluster.getName()));
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Provisioned works control VM : %s in to the Desktop cluster : %s", worksControlVm.getDisplayName(), desktopCluster.getName()));
        }
        return worksControlVm;
    }


    private UserVm createDesktopClusterWorksControlVm(final Network network, String serverIp) throws ManagementServerException,
            ResourceUnavailableException, InsufficientCapacityException {
        UserVm worksControlVm = null;
        DataCenter zone = dataCenterDao.findById(desktopCluster.getZoneId());
        ServiceOffering serviceOffering = serviceOfferingDao.findById(desktopCluster.getServiceOfferingId());
        List<Long> networkIds = new ArrayList<Long>();
        networkIds.add(desktopCluster.getNetworkId());
        VirtualMachineTemplate worksTemplate = null;
        List<DesktopTemplateMapVO> templateList = desktopTemplateMapDao.listByVersionId(desktopCluster.getDesktopVersionId());
        for (DesktopTemplateMapVO templateMapVO : templateList) {
            if (templateMapVO.getVersionId() == desktopCluster.getDesktopVersionId()) {
                if (templateMapVO.getType() == "works") {
                    worksTemplate = templateDao.findById(templateMapVO.getTemplateId());
                }
            }
        }
        Network.IpAddresses addrs = new Network.IpAddresses(null, null);
        String hostName = desktopCluster.getName() + "-works-control";
        worksControlVm = userVmService.createAdvancedVirtualMachine(zone, serviceOffering, worksTemplate, networkIds, owner,
                hostName, hostName, null, null, null,
                worksTemplate.getHypervisorType(), BaseCmd.HTTPMethod.POST, null, null,
                null, addrs, null, null, null, null, null, null, null, null, true);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Created control VM ID : %s, %s in the Desktop cluster : %s", worksControlVm.getUuid(), hostName, desktopCluster.getName()));
        }
        return worksControlVm;
    }

    private Network startDesktopClusterNetwork(final DeployDestination destination) throws ManagementServerException {
        final ReservationContext context = new ReservationContextImpl(null, null, null, owner);
        Network network = networkDao.findById(desktopCluster.getNetworkId());
        if (network == null) {
            String msg  = String.format("Network for desktop cluster : %s not found", desktopCluster.getName());
            LOGGER.warn(msg);
            stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.CreateFailed);
            throw new ManagementServerException(msg);
        }
        try {
            networkMgr.startNetwork(network.getId(), destination, context);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(String.format("Network : %s is started for the  desktop cluster : %s", network.getName(), desktopCluster.getName()));
            }
        } catch (ConcurrentOperationException | ResourceUnavailableException |InsufficientCapacityException e) {
            String msg = String.format("Failed to start desktop cluster : %s as unable to start associated network : %s" , desktopCluster.getName(), network.getName());
            LOGGER.error(msg, e);
            stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.CreateFailed);
            throw new ManagementServerException(msg, e);
        }
        return network;
    }

    private void provisionLoadBalancerRule(final IpAddress publicIp, final Network network,
                                           final Account account, final List<Long> clusterVMIds, final int port) throws NetworkRuleConflictException,
            InsufficientAddressCapacityException {
        LoadBalancer lb = lbService.createPublicLoadBalancerRule(null, "api-lb", "LB rule for API access",
                port, port, port, port,
                publicIp.getId(), NetUtils.TCP_PROTO, "roundrobin", network.getId(),
                account.getId(), false, NetUtils.TCP_PROTO, true);

        Map<Long, List<String>> vmIdIpMap = new HashMap<>();
        for (int i = 0; i < 3; ++i) {
            List<String> ips = new ArrayList<>();
            Nic controlVmNic = networkModel.getNicInNetwork(clusterVMIds.get(i), desktopCluster.getNetworkId());
            ips.add(controlVmNic.getIPv4Address());
            vmIdIpMap.put(clusterVMIds.get(i), ips);
        }
        lbService.assignToLoadBalancer(lb.getId(), null, vmIdIpMap);
    }

    private void startDesktopClusterVMs() {
        List <UserVm> clusterVms = getDesktopClusterVMs();
        for (final UserVm vm : clusterVms) {
            if (vm == null) {
                logTransitStateAndThrow(Level.ERROR, String.format("Failed to start all VMs in Desktop cluster : %s", desktopCluster.getName()), desktopCluster.getId(), DesktopCluster.Event.OperationFailed);
            }
            try {
                startDesktopVM(vm);
            } catch (ManagementServerException ex) {
                LOGGER.warn(String.format("Failed to start VM : %s in Desktop cluster : %s due to ", vm.getDisplayName(), desktopCluster.getName()) + ex);
                // dont bail out here. proceed further to stop the reset of the VM's
            }
        }
        for (final UserVm userVm : clusterVms) {
            UserVm vm = userVmDao.findById(userVm.getId());
            if (vm == null || !vm.getState().equals(VirtualMachine.State.Running)) {
                logTransitStateAndThrow(Level.ERROR, String.format("Failed to start all VMs in Desktop cluster : %s", desktopCluster.getName()), desktopCluster.getId(), DesktopCluster.Event.OperationFailed);
            }
        }
    }

    public boolean startDesktopClusterOnCreate() {
        init();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Starting Desktop cluster : %s", desktopCluster.getName()));
        }
        final long startTimeoutTime = System.currentTimeMillis() + 3600 * 1000;
        stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.StartRequested);
        DeployDestination dest = null;
        try {
            dest = plan();
        } catch (InsufficientCapacityException e) {
            logTransitStateAndThrow(Level.ERROR, String.format("Provisioning the cluster failed due to insufficient capacity in the Desktop cluster: %s", desktopCluster.getUuid()), desktopCluster.getId(), DesktopCluster.Event.CreateFailed, e);
        }
        Network network = null;
        try {
            network = startDesktopClusterNetwork(dest);
        } catch (ManagementServerException e) {
            logTransitStateAndThrow(Level.ERROR, String.format("Failed to start Desktop cluster : %s as its network cannot be started", desktopCluster.getName()), desktopCluster.getId(), DesktopCluster.Event.CreateFailed, e);
        }
        Pair<String, Integer> publicIpSshPort = getDesktopClusterServerIpSshPort(null);
        publicIpAddress = publicIpSshPort.first();
        if (Strings.isNullOrEmpty(publicIpAddress) && (Network.GuestType.Isolated.equals(network.getGuestType()))) { // Shared network, single-control node cluster won't have an IP yet
            logTransitStateAndThrow(Level.ERROR, String.format("Failed to start Desktop cluster : %s as no public IP found for the cluster" , desktopCluster.getName()), desktopCluster.getId(), DesktopCluster.Event.CreateFailed);
        }
        List<UserVm> clusterVMs = new ArrayList<>();
        UserVm dcVM = null;
        try {
            dcVM = provisionDesktopClusterDcControlVm(network, publicIpAddress);
        } catch (CloudRuntimeException | ManagementServerException | ResourceUnavailableException | InsufficientCapacityException e) {
            logTransitStateAndThrow(Level.ERROR, String.format("Provisioning the dc control VM failed in the Desktop cluster : %s", desktopCluster.getName()), desktopCluster.getId(), DesktopCluster.Event.CreateFailed, e);
        }
        clusterVMs.add(dcVM);
        UserVm worksVM = null;
        try {
            worksVM = provisionDesktopClusterWorksControlVm(network, publicIpAddress);
        }  catch (CloudRuntimeException | ManagementServerException | ResourceUnavailableException | InsufficientCapacityException e) {
            logTransitStateAndThrow(Level.ERROR, String.format("Provisioning the works control VM failed in the Desktop cluster : %s", desktopCluster.getName()), desktopCluster.getId(), DesktopCluster.Event.CreateFailed, e);
        }
        clusterVMs.add(worksVM);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Desktop cluster : %s VMs successfully provisioned", desktopCluster.getName()));
        }
        stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.OperationSucceeded);
        return true;
    }

    public boolean startStoppedDesktopCluster() throws CloudRuntimeException {
        init();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Starting Desktop cluster : %s", desktopCluster.getName()));
        }
        final long startTimeoutTime = System.currentTimeMillis() + 3600 * 1000;
        stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.StartRequested);
        startDesktopClusterVMs();
        stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.OperationSucceeded);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Desktop cluster : %s successfully started", desktopCluster.getName()));
        }
        return true;
    }

    public boolean reconcileAlertCluster() {
        init();
        final long startTimeoutTime = System.currentTimeMillis() + 3 * 60 * 1000;
        List<DesktopClusterVmMapVO> vmMapVOList = getDesktopClusterVMMaps();
        if (CollectionUtils.isEmpty(vmMapVOList)) {
            return false;
        }
        // mark the cluster to be running
        stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.RecoveryRequested);
        stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.OperationSucceeded);
        return true;
    }
}
