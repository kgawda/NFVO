/*
 * Copyright (c) 2015 Fraunhofer FOKUS
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.openbaton.tosca.parser;

import org.openbaton.catalogue.mano.common.LifecycleEvent;
import org.openbaton.catalogue.mano.common.VNFDeploymentFlavour;
import org.openbaton.catalogue.mano.descriptor.*;
import org.openbaton.catalogue.nfvo.Configuration;
import org.openbaton.catalogue.nfvo.ConfigurationParameter;
import org.openbaton.tosca.catalogue.*;
import org.openbaton.tosca.catalogue.exceptions.NotSupportedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created by dbo on 26/11/15.
 */
@Service
public class ParserTosca implements org.openbaton.tosca.parser.interfaces.ParserTosca {

    public Definitions definitions;
    public NetworkServiceDescriptor networkServiceDescriptor;
    public Set<VirtualNetworkFunctionDescriptor> virtualNetworkFunctionDescriptors;
    public Set<VirtualDeploymentUnit> virtualDeploymentUnits;

    private Logger log = LoggerFactory.getLogger(this.getClass());
//    public ParserTosca(Definitions definitions) {
//        this.definitions = definitions;
//    }

    public Definitions getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Definitions definitions) {
        this.definitions = definitions;
    }

    public Set<VirtualNetworkFunctionDescriptor> getVirtualNetworkFunctionDescriptors() {
        return virtualNetworkFunctionDescriptors;
    }

    public void setVirtualNetworkFunctionDescriptors(Set<VirtualNetworkFunctionDescriptor> virtualNetworkFunctionDescriptors) {
        this.virtualNetworkFunctionDescriptors = virtualNetworkFunctionDescriptors;
    }

    public Set<VirtualDeploymentUnit> getVirtualDeploymentUnits() {
        return virtualDeploymentUnits;
    }

    public void setVirtualDeploymentUnits(Set<VirtualDeploymentUnit> virtualDeploymentUnits) {
        this.virtualDeploymentUnits = virtualDeploymentUnits;
    }

    public NetworkServiceDescriptor getNetworkServiceDescriptor(Definitions definitions) throws NotSupportedType {
        this.definitions = definitions;
        this.networkServiceDescriptor = new NetworkServiceDescriptor();
        networkServiceDescriptor.setName(definitions.getTosca_definitions_version());
        networkServiceDescriptor.setVendor(definitions.getMetadata().getVendor());
        networkServiceDescriptor.setVersion(definitions.getMetadata().getVersion());
        networkServiceDescriptor.setVld(new HashSet<VirtualLinkDescriptor>());
        NodeTemplates nodeTemplates = definitions.getTopology_template();
        for (String nodeName : nodeTemplates.getNode_templates().keySet()) {
            String type = nodeTemplates.getNode_templates().get(nodeName).getType();
            NodeTemplate nodeTemplate = nodeTemplates.getNode_templates().get(nodeName);

            if (type.startsWith("openbaton.type.VNF.")) {
                VirtualNetworkFunctionDescriptor vnfd = new VirtualNetworkFunctionDescriptor();
                Set<InternalVirtualLink> internaVLs = new HashSet<InternalVirtualLink>();
                Set<VirtualDeploymentUnit> virtualDeploymentUnits = new HashSet<VirtualDeploymentUnit>();
                vnfd.setVdu(virtualDeploymentUnits);
                vnfd.setVirtual_link(internaVLs);
                vnfd.setName(nodeName.toString());
                String endPoint = type.substring(type.lastIndexOf(".") + 1);
                vnfd.setType(endPoint.toLowerCase());
                vnfd.setEndpoint(endPoint.toLowerCase());
                nodeTemplate.setNSDandSourceName(this.networkServiceDescriptor, nodeName);
                vnfd.setLifecycle_event((Set<LifecycleEvent>) nodeTemplate.getInterfaces());
                PropertiesVnf properties = (PropertiesVnf) nodeTemplate.getProperties();
                vnfd.setVersion(properties.getVersion());
                vnfd.setVendor(properties.getVendor());
                vnfd.setId(properties.getId());
                vnfd.setVnfPackageLocation(properties.getVnfPackageLocation());

                Configuration configuration = new Configuration();
                configuration.setName(properties.getConfigurations().getName());
                Set<ConfigurationParameter> configurationParameters = new HashSet<>();
                for (HashMap<String, String> config : properties.getConfigurations().getConfigurationParameters()) {
                    for (String key : config.keySet()) {
                        ConfigurationParameter cfgP = new ConfigurationParameter();
                        cfgP.setConfKey(key);
                        cfgP.setValue(String.valueOf(config.get(key)));
                        configurationParameters.add(cfgP);
                    }

                }
                configuration.setConfigurationParameters(configurationParameters);
                List<Map<String, Object>> requirements = (List<Map<String, Object>>) nodeTemplate.getRequirements();

                for (Map<String, Object> req : requirements) {
                    for (String key : req.keySet()) {
                        if (key.equals("host")) {
                            String nodeRequest = (String) ((Map<String, Object>) req.get(key)).get("node");
                            vnfd.getVdu().add((VirtualDeploymentUnit) getObject(nodeRequest));
                        }
                        if (key.equals("virtualLink")) {
                            InternalVirtualLink ivl = new InternalVirtualLink();
                            String linkName = (String) req.get(key);
                            ivl.setName(linkName);
                            vnfd.getVirtual_link().add(ivl);
                        }
                    }
                }
                Set<VNFDeploymentFlavour> deploymentFlavours = new HashSet<VNFDeploymentFlavour>();
                for (HashMap<String, String> deploy_flavour : properties.getDeployment_flavour()) {
                    for (String key : deploy_flavour.keySet()) {
                        VNFDeploymentFlavour dp = new VNFDeploymentFlavour();
                        dp.setFlavour_key(deploy_flavour.get(key));
                        deploymentFlavours.add(dp);
                    }
                }

                vnfd.setDeployment_flavour(deploymentFlavours);
                networkServiceDescriptor.getVnfd().add(vnfd);
                networkServiceDescriptor.setVnf_dependency(getVNFDependencies());

            }

            if (type.equals("tosca.nodes.nfv.VL")) {
                VirtualLinkDescriptor vld = new VirtualLinkDescriptor();
                vld.setName(nodeName);
                networkServiceDescriptor.getVld().add(vld);

            }
        }

//        }
        return this.networkServiceDescriptor;
    }

    public void setNetworkServiceDescriptor(NetworkServiceDescriptor networkServiceDescriptor) {
        this.networkServiceDescriptor = networkServiceDescriptor;
    }

    public VirtualNetworkFunctionDescriptor findVNFD(String vndName) {
        for (VirtualNetworkFunctionDescriptor vnfd : networkServiceDescriptor.getVnfd()) {
            if (vnfd.getName().equals(vndName))
                return vnfd;
        }
        return null;
    }

    public Set<VNFDependency> getVNFDependencies() {
        Set<VNFDependency> vnfDependencies = new HashSet<>();

        for (String relationshipNode : definitions.getRelationships_templete().keySet()) {
            if (definitions.getRelationships_templete().get(relationshipNode) != null) {
                VNFDependency vnfDependency = new VNFDependency();
                Relationships relationships = definitions.getRelationships_templete().get(relationshipNode);
                VirtualNetworkFunctionDescriptor tempVnfdSource = new VirtualNetworkFunctionDescriptor();
                VirtualNetworkFunctionDescriptor tempVnfdTarget = new VirtualNetworkFunctionDescriptor();
                tempVnfdSource.setName(relationships.getSource());
                vnfDependency.setSource(tempVnfdSource);
                tempVnfdTarget.setName(relationships.getTarget());
                vnfDependency.setTarget(tempVnfdTarget);
                vnfDependency.setParameters(relationships.getParameters());
                vnfDependencies.add(vnfDependency);

            }
        }
        return vnfDependencies;
    }

    public Object getObject(String name) throws NotSupportedType {
        VirtualDeploymentUnit vdu = new VirtualDeploymentUnit();
        NodeTemplates nodeTemplates = definitions.getTopology_template();
        for (String key : nodeTemplates.getNode_templates().keySet()) {
            String type = nodeTemplates.getNode_templates().get(name).getType();
            NodeTemplate nodeTemplate = nodeTemplates.getNode_templates().get(name);
            if (name.equals(key)) {
                switch (type) {
                    case "VDU":
                    case "tosca:VDU":
                    case "openbaton.type.VDU":

                        PropertiesVdu propertiesV = (PropertiesVdu) nodeTemplate.getProperties();
                        nodeTemplate.getCapabilities();
                        vdu.setVm_image(new HashSet<String>(propertiesV.getVm_image()));
                        vdu.setScale_in_out(propertiesV.getScale_in_out());
                        vdu.setVimInstanceName(propertiesV.getVimInstance());
                        List<Map<String, Object>> requirements = (List<Map<String, Object>>) nodeTemplate.getRequirements();
                        for (Map<String, Object> req : requirements) {
                            for (String keyReq : req.keySet()) {
                                if (keyReq.equals("virtual_link")) {
                                    Set<VNFComponent> vnfComponents = new HashSet<>();
                                    List<String> virtual_links = (ArrayList<String>) req.get(keyReq);
                                    VNFComponent vnfc = new VNFComponent();
                                    Set<VNFDConnectionPoint> cps = new HashSet<>();
                                    vnfc.setConnection_point(cps);
                                    for (String vl : virtual_links) {
                                        cps.add(((VNFDConnectionPoint) getObject(vl)));
                                    }
                                    vnfc.setConnection_point(cps);
                                    vnfComponents.add(vnfc);
                                    vdu.setVnfc(vnfComponents);
                                }
                            }
                        }
                        return vdu;

                    case "tosca.nodes.nfv.CP":
                        VNFDConnectionPoint cp = new VNFDConnectionPoint();
                        cp.setVirtual_link_reference(nodeTemplate.getVirtualLink());
                        if (nodeTemplate.getProperties() != null) {
                            PropertiesCP prop = (PropertiesCP) nodeTemplate.getProperties();
                            cp.setFloatingIp(prop.getFloatingIp());
                        }
                        return cp;

                    default:
                        throw new NotSupportedType("The type : " +  type + " is not supported");

                }

            }
        }
        return null;
    }
}
