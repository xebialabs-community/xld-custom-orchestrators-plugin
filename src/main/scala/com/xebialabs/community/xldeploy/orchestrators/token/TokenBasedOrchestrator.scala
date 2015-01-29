/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.token

import com.xebialabs.community.xldeploy.orchestrators.Descriptions
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{InterleavedOrchestration, Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification}
import com.xebialabs.deployit.plugin.api.reflect.Type
import com.xebialabs.deployit.plugin.api.udm.Container

import scala.collection.convert.Wrappers.{JCollectionWrapper, JIterableWrapper}

/**
 * Orchestrator that injects delta's in the deltaspecification for token taking/releasing steps
 */
@Orchestrator.Metadata(name = "token-inserter", description = "Ensures that token taking/releasing steps are generated for each container.")
class TokenBasedOrchestrator extends Orchestrator {
  import collection.convert.wrapAll._
  import com.xebialabs.community.xldeploy.orchestrators.RichDelta._

  def addToken(c: Container, deltas: List[Delta]): List[Delta] = {
    val c_id: String = c.getId.replace("/", "_")
    val deployable: TokenDeployable = Type.valueOf(classOf[TokenDeployable]).getDescriptor.newInstance(s"tokenDeployable-$c_id")
    val deployed: TokenDeployed = Type.valueOf(classOf[TokenDeployed]).getDescriptor.newInstance(s"tokenDeployed-$c_id")
    deployed.setDeployable(deployable)
    deployed.setContainer(c)
    new TokenDelta(deployed) :: deltas
  }


  override def orchestrate(specification: DeltaSpecification): Orchestration = {
    val deltasByContainer: Map[Container, List[Delta]] = specification.getDeltas.toList.groupBy(_.container)

    def orchestrateContainer(c: Container): Orchestration = {
      val d = Descriptions.getDescriptionForContainer(specification.getOperation, c)
      interleaved(d, addToken(c, deltasByContainer(c)))
    }

    deltasByContainer.keys.toList match {
      case Nil =>
        val d = Descriptions.getDescriptionForSpec(specification)
        interleaved(d, specification.getDeltas)
      case c :: Nil =>
        orchestrateContainer(c)
      case cs =>
        val d: String = Descriptions.getDescriptionForContainers(specification.getOperation, deltasByContainer.keys.toSeq)
        parallel(d, cs.map(orchestrateContainer))
    }
  }
}
