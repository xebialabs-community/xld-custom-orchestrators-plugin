/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.byhost;

import com.xebialabs.deployit.engine.spi.orchestration.Orchestration;
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations;
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrator;
import com.xebialabs.deployit.plugin.api.deployment.specification.DeltaSpecification;

@Orchestrator.Metadata(name = "sequential-by-host-deployment-group", description = "Enforce a parallel deployment order based on the deployment group property set on the host")
public class SequentialByHostDeploymentGroupOrchestrator extends ByHostDeploymentGroupOrchestrator implements Orchestrator {

    @Override
    public Orchestration orchestrate(final DeltaSpecification deltaSpecification) {
        return Orchestrations.serial(descriptionForSpec(deltaSpecification), getOrchestrations(deltaSpecification, "deploymentGroup"));
    }
}

