/**
 * Copyright 2019 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

