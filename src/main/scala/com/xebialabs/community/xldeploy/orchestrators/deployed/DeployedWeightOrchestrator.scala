/**
 * Copyright 2018 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.xebialabs.community.xldeploy.orchestrators.deployed

import com.xebialabs.community.xldeploy.orchestrators.Descriptions._
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification, Operation}
import com.xebialabs.deployit.plugin.api.udm.{Deployable, Deployed}

import scala.collection.convert.wrapAll._

trait DeployedOrchestratorBase extends Orchestrator {
  import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._

  def getOrchestrations(spec: DeltaSpecification): List[Orchestration] = {
    List(interleaved("Sorted by deployed type, weight and name", spec.getDeltas.sortBy(order)))
  }

  def order(delta: Delta):(String, Int, String) = {
    val deployed: Deployed[_,_] = if (delta.getOperation == Operation.DESTROY) delta.getPrevious else delta.getDeployed
    val weight: Int = if (deployed.hasProperty("deployedWeight")) deployed.getProperty("deployedWeight") else 50
    (deployed.getType.toString, weight, deployed.getName)
  }
}

@Orchestrator.Metadata(name = "sort-by-deployed-weight", description = "Sorts deployed by type, weight and name, before planning. Results in serial orchestrator")
class SerialByDeployed extends DeployedOrchestratorBase {
  override def orchestrate(specification: DeltaSpecification): Orchestration = serial(getDescriptionForSpec(specification), getOrchestrations(specification))
}
