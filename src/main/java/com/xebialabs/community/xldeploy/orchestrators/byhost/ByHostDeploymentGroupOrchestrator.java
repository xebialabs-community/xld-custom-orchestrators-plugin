/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */

package com.xebialabs.community.xldeploy.orchestrators.byhost;

import java.util.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;

import com.xebialabs.deployit.engine.spi.orchestration.InterleavedOrchestration;
import com.xebialabs.deployit.engine.spi.orchestration.Orchestration;
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations;
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrator;
import com.xebialabs.deployit.plugin.api.deployment.specification.Delta;
import com.xebialabs.deployit.plugin.api.deployment.specification.DeltaSpecification;
import com.xebialabs.deployit.plugin.api.deployment.specification.Operation;
import com.xebialabs.deployit.plugin.api.udm.*;
import com.xebialabs.deployit.plugin.overthere.Host;
import com.xebialabs.deployit.plugin.overthere.HostContainer;


public abstract class ByHostDeploymentGroupOrchestrator implements Orchestrator {

    protected List<Orchestration> getOrchestrations(final DeltaSpecification deltaSpecification, final String groupProperty) {
        final SortedSetMultimap<Host, Delta> deltasByHosts = TreeMultimap.create(new Comparator<Host>() {
            @Override
            public int compare(final Host o1, final Host o2) {
                int group1 = (int) getPropertyOrDefault(o1, groupProperty, 0);
                int group2 = (int) getPropertyOrDefault(o2, groupProperty, 0);
                return group1 - group2;
            }
        }, Ordering.usingToString());
        for (Delta delta : deltaSpecification.getDeltas()) {
            final Host host = getHost(delta);
            if (host == null)
                continue;
            deltasByHosts.put(host, delta);
        }

        List<Orchestration> items = Lists.newArrayList();
        for (Map.Entry<Host, Collection<Delta>> entry : deltasByHosts.asMap().entrySet()) {
            final Collection<Delta> value = entry.getValue();
            final ArrayList<Delta> deltas = Lists.newArrayList(value);
            final InterleavedOrchestration interleaved = Orchestrations.interleaved("Deploy on host " + entry.getKey().getName(), deltas);
            items.add(interleaved);
        }
        return items;
    }

    private Object getPropertyOrDefault(ConfigurationItem ci, String propertyName, Object defaultValue) {
        if (ci.hasProperty(propertyName)) {
            final Object value = ci.getProperty(propertyName);
            return (value == null ? defaultValue : value);
        } else {
            return defaultValue;
        }
    }


    String descriptionForSpec(DeltaSpecification specification) {
        final DeployedApplication app = specification.getDeployedApplication() == null ? specification.getPreviousDeployedApplication() : specification.getDeployedApplication();
        return String.format("%s %s %s on environment %s", performing(specification.getOperation()), app.getName(), app.getVersion().getVersion(), app.getEnvironment().getName());
    }

    private String performing(Operation operation) {
        switch (operation) {
            case CREATE:
                return "Deploy";
            case DESTROY:
                return "Undeploy";
            case MODIFY:
                return "Update";
            case NOOP:
                return "Not update";
        }
        return null;

    }

    private Host getHost(Delta delta) {
        final Deployed<? extends Deployable, ? extends Container> deployed = delta.getDeployed() == null ? delta.getPrevious() : delta.getDeployed();

        final Container container = deployed.getContainer();
        if (container instanceof HostContainer) {
            HostContainer hc = (HostContainer) container;
            return hc.getHost();
        }

        if (container.hasProperty("host")) {
            return container.getProperty("host");
        }

        return null;
    }
}
