/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.token

import com.xebialabs.community.xldeploy.orchestrators.Descriptions.{getDescriptionForContainer, getDescriptionForContainers, getDescriptionForSpec}
import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification}
import com.xebialabs.deployit.plugin.api.reflect.Type
import com.xebialabs.deployit.plugin.api.udm.Container

/**
 * Orchestrator that injects delta's in the deltaspecification for token taking/releasing steps
 */
@Orchestrator.Metadata(name = "token-inserter", description = "Ensures that token taking/releasing steps are generated for each container.")
class TokenBasedOrchestrator extends Orchestrator {
  import scala.collection.convert.wrapAll._

  def addToken(c: Container, deltas: List[Delta]): List[Delta] = {
    val c_id: String = c.getId.replace("/", "_")
    val deployable: TokenDeployable = Type.valueOf(classOf[TokenDeployable]).getDescriptor.newInstance(s"tokenDeployable-$c_id")
    val deployed: TokenDeployed = Type.valueOf(classOf[TokenDeployed]).getDescriptor.newInstance(s"tokenDeployed-$c_id")
    deployed.setDeployable(deployable)
    deployed.setContainer(c)
    new TokenDelta(deployed) :: deltas
  }


  override def orchestrate(specification: DeltaSpecification): Orchestration = {
    val deltasByContainer: Map[Container, List[Delta]] = byContainer(specification)

    def orchestrateContainer(c: Container): Orchestration = {
      val d = getDescriptionForContainer(specification.getOperation, c)
      interleaved(d, addToken(c, deltasByContainer(c)))
    }

    deltasByContainer.keys.toList match {
      case Nil =>
        val d = getDescriptionForSpec(specification)
        interleaved(d, specification.getDeltas)
      case c :: Nil =>
        orchestrateContainer(c)
      case cs =>
        val d: String = getDescriptionForContainers(specification.getOperation, deltasByContainer.keys.toSeq)
        parallel(d, cs.map(orchestrateContainer))
    }
  }
}
