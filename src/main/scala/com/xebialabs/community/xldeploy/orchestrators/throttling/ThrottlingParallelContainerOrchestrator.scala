/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.throttling

import com.xebialabs.community.xldeploy.orchestrators.Descriptions._
import com.xebialabs.community.xldeploy.orchestrators.token.Tokens
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{InterleavedOrchestration, Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification, Operation}
import com.xebialabs.deployit.plugin.api.udm.Container
import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._

import scala.collection.convert.wrapAll._

@Orchestrator.Metadata(name = "parallel-by-container-throttled", description = "The throttled parallel by container orchestrator")
class ThrottlingParallelContainerOrchestrator extends Orchestrator {
  import com.xebialabs.community.xldeploy.orchestrators.RichConfigurationItem._

  type DeltasForContainer = (Container, List[Delta])

  def orchestrate(spec: DeltaSpecification): Orchestration = getOrchestrations(spec)

  def getOrchestrations(spec: DeltaSpecification): Orchestration = {

    def toInterleaved(list: List[DeltasForContainer]): List[InterleavedOrchestration] = {
      list.map { case (c, ds) => interleaved(getDescriptionForContainer(spec.getOperation, c), ds)}
    }

    def stringOrderForOperation = getStringOrdering(spec.getOperation)

    val desc: String = getDescriptionForSpec(spec)
    val deltasByContainer: Map[Container, List[Delta]] = byContainer(spec)
    val sorted: List[DeltasForContainer] = deltasByContainer.toList.sortBy(_._1.getName)(stringOrderForOperation)

    val throttleProp: Option[Int] = spec.getDeployedApplication.getPropertyIfExists(Tokens.MaxContainersParallel)
    throttleProp match {
      case Some(mcip) if mcip >= 1 && sorted.size > mcip =>
        val chunked: Iterator[List[DeltasForContainer]] = sorted.grouped(mcip)
        val pars = chunked.map({ l => parallel(getDescriptionForContainers(spec.getOperation, l.map(_._1)), toInterleaved(l))}).toList
        serial(desc, pars)
      case _ =>
        parallel(desc, toInterleaved(sorted))
    }
  }

}
