/**
 * Copyright 2019 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.xebialabs.community.xldeploy.orchestrators.deployable

import com.xebialabs.community.xldeploy.orchestrators.Descriptions._
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification}
import com.xebialabs.deployit.plugin.api.udm.Deployable

import scala.collection.convert.wrapAll._

trait DeployableOrchestratorBase extends Orchestrator {
  import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._

  def getOrchestrations(spec: DeltaSpecification): List[Orchestration] = {
    val orderForOperation = getStringOrdering(spec.getOperation)
    val deltasByDeployable: Map[Deployable, List[Delta]] = byDeployable(spec)
    deltasByDeployable.toList.sortBy(t => nameOrNull(t._1))(orderForOperation).map { case (d, ds) => interleaved(getDescriptionForDeployable(spec.getOperation, d), ds) }
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
