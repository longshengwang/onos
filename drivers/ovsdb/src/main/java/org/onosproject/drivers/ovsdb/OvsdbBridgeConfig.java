/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.drivers.ovsdb;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.onlab.packet.IpAddress;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.BridgeConfig;
import org.onosproject.net.behaviour.BridgeDescription;
import org.onosproject.net.behaviour.BridgeName;
import org.onosproject.net.behaviour.DefaultBridgeDescription;
import org.onosproject.net.device.DefaultPortDescription;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.ovsdb.controller.OvsdbBridge;
import org.onosproject.ovsdb.controller.OvsdbClientService;
import org.onosproject.ovsdb.controller.OvsdbController;
import org.onosproject.ovsdb.controller.OvsdbNodeId;
import org.onosproject.ovsdb.controller.OvsdbPort;


/**
 * The implementation of BridageConfig.
 */
public class OvsdbBridgeConfig extends AbstractHandlerBehaviour
        implements BridgeConfig {


    @Override
    public boolean addBridge(BridgeDescription bridgeDesc) {
        OvsdbClientService client = getOvsdbClientService(handler());
        if (client == null) {
            return false;
        }
        OvsdbBridge.Builder bridgeBuilder = OvsdbBridge.builder(bridgeDesc);
        if (bridgeDesc.enableLocalController()) {
            bridgeBuilder.controller(client.localController());
        }
        return client.createBridge(bridgeBuilder.build());
    }

    @Override
    public void deleteBridge(BridgeName bridgeName) {
        OvsdbClientService client = getOvsdbClientService(handler());
        if (client == null) {
            return;
        }
        client.dropBridge(bridgeName.name());
    }

    @Override
    public Collection<BridgeDescription> getBridges() {
        OvsdbClientService client = getOvsdbClientService(handler());
        if (client == null) {
            return Collections.emptyList();
        }

        Set<OvsdbBridge> bridges = client.getBridges();

        return bridges.stream()
                .map(bridge -> DefaultBridgeDescription.builder()
                        .name(bridge.name())
                        .datapathId(bridge.datapathId().get())
                        .build())
                .collect(Collectors.toSet());
    }

    @Override
    public void addPort(BridgeName bridgeName, String portName) {
        OvsdbClientService client = getOvsdbClientService(handler());
        if (client == null) {
            return;
        }
        client.createPort(bridgeName.name(), portName);
    }

    @Override
    public void deletePort(BridgeName bridgeName, String portName) {
        OvsdbClientService client = getOvsdbClientService(handler());
        if (client == null) {
            return;
        }
        client.dropPort(bridgeName.name(), portName);
    }

    @Override
    public Collection<PortDescription> getPorts() {
        OvsdbClientService client = getOvsdbClientService(handler());
        if (client == null) {
            return Collections.emptyList();
        }
        Set<OvsdbPort> ports = client.getPorts();

        return ports.stream()
                .map(x -> DefaultPortDescription.builder()
                        .withPortNumber(PortNumber.portNumber(x.portNumber().value()))
                        .isEnabled(true)
                        .annotations(DefaultAnnotations.builder()
                                .set("portName", x.portName().value())
                                .build()).build())
                .collect(Collectors.toSet());
    }

    // OvsdbNodeId(IP) is used in the adaptor while DeviceId(ovsdb:IP)
    // is used in the core. So DeviceId need be changed to OvsdbNodeId.
    private OvsdbNodeId changeDeviceIdToNodeId(DeviceId deviceId) {
        String[] splits = deviceId.toString().split(":");
        if (splits == null || splits.length < 1) {
            return null;
        }
        IpAddress ipAddress = IpAddress.valueOf(splits[1]);
        return new OvsdbNodeId(ipAddress, 0);
    }

    // Used for getting OvsdbClientService.
    private OvsdbClientService getOvsdbClientService(DriverHandler handler) {
        OvsdbController ovsController = handler.get(OvsdbController.class);
        DeviceId deviceId = handler.data().deviceId();
        OvsdbNodeId nodeId = changeDeviceIdToNodeId(deviceId);
        return ovsController.getOvsdbClient(nodeId);
    }

    @Override
    public Set<PortNumber> getPortNumbers() {
        DriverHandler handler = handler();
        OvsdbClientService client = getOvsdbClientService(handler);
        if (client == null) {
            return Collections.emptySet();
        }
        Set<OvsdbPort> ports = client.getPorts();

        return ports.stream()
                .map(x -> PortNumber.portNumber(
                                x.portNumber().value(),
                                x.portName().value()
                        )
                )
                .collect(Collectors.toSet());
    }

    @Override
    public List<PortNumber> getLocalPorts(Iterable<String> ifaceIds) {
        List<PortNumber> ports = new ArrayList<>();
        DriverHandler handler = handler();
        OvsdbClientService client = getOvsdbClientService(handler);
        if (client == null) {
            return Collections.emptyList();
        }
        Set<OvsdbPort> ovsdbSet = client.getLocalPorts(ifaceIds);
        ovsdbSet.forEach(o -> {
            PortNumber port = PortNumber.portNumber(o.portNumber().value(),
                                                    o.portName().value());
            ports.add(port);
        });
        return ports;
    }
}
