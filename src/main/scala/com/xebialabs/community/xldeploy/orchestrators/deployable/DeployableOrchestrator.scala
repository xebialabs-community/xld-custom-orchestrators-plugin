/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.deployable

import com.xebialabs.community.xldeploy.orchestrators.Descriptions._
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification}
import com.xebialabs.deployit.plugin.api.udm.Deployable

import scala.collection.convert.wrapAll._

trait DeployableOrchestratorBase extends Orchestrator {
  import com.xebialabs.community.xldeploy.orchestrators.Orchestators._

  def getOrchestrations(spec: DeltaSpecification): List[Orchestration] = {
    val orderForOperation = getStringOrdering(spec.getOperation)
    val deltasByDeployable: Map[Deployable, List[Delta]] = byDeployable(spec)
    deltasByDeployable.toList.sortBy(_._1.getName)(orderForOperation).map { case (d, ds) => interleaved(getDescriptionForDeployable(spec.getOperation, d), ds) }
  }
}

@Orchestrator.Metadata(name = "parallel-by-deployable", description = "The parallel by deployable orchestrator.")
class ParallelByDeployable extends DeployableOrchestratorBase {
  override def orchestrate(specification: DeltaSpecification): Orchestration = parallel(getDescriptionForSpec(specification), getOrchestrations(specification))
}

@Orchestrator.Metadata(name = "serial-by-deployable", description = "The serial by deployable orchestrator.")
class SerialByDeployable extends DeployableOrchestratorBase {
  override def orchestrate(specification: DeltaSpecification): Orchestration = serial(getDescriptionForSpec(specification), getOrchestrations(specification))
}