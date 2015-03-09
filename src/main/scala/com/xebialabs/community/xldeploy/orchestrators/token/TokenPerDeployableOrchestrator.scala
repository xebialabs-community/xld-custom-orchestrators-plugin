/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.token

import com.xebialabs.community.xldeploy.orchestrators.Descriptions._
import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._
import com.xebialabs.community.xldeploy.orchestrators.RichConfigurationItem._
import com.xebialabs.community.xldeploy.orchestrators.{RichConfigurationItem, RichDelta}
import com.xebialabs.community.xldeploy.orchestrators.RichDelta._
import com.xebialabs.community.xldeploy.orchestrators.token.Tokens.MaxContainersParallel
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification}
import com.xebialabs.deployit.plugin.api.reflect.Type
import com.xebialabs.deployit.plugin.api.udm.{Container, Deployable, Deployed}
import grizzled.slf4j.Logging

import scala.collection.convert.wrapAll._

@Orchestrator.Metadata(name = "parallel-by-container-token-per-deployable", description = "Ensures that token taking/releasing steps are generated for each container a specific deployable is deployed to.")
class TokenPerDeployableOrchestrator extends TokenOrchestratorBase with Logging {

  override def orchestrate(specification: DeltaSpecification): Orchestration = {
    def orchestrateDeltas(deployable: Deployable, deltas: List[Delta]) = {
      // Get a deployed that originated from this deployable, and get its 'maxContainersInParallel' property.
      val head = RichDelta.lift(deltas.head).deployed
      val prop: Option[Int] = RichConfigurationItem.lift(head).getPropertyIfExists(MaxContainersParallel)

      val deltasByContainer = byContainer(deltas)

      val descriptionForDeployable: String = getDescriptionForDeployable(specification.getOperation, deployable)
      logger.info(s"Property value $prop of $head, size of deltasByContainer: ${deltasByContainer.size}")
      prop match {
        case None =>
          parallel(descriptionForDeployable,
          deltasByContainer.map { case (c, ds) => interleaved(getDescriptionForContainer(specification.getOperation, c), ds)}.toList)
        case Some(v) if v >= deltasByContainer.size =>
          parallel(descriptionForDeployable,
          deltasByContainer.map { case (c, ds) => interleaved(getDescriptionForContainer(specification.getOperation, c), ds)}.toList)
        case Some(v) =>
          logger.info(s"Adding token for $head and $deltasByContainer")
          parallel(descriptionForDeployable,
            deltasByContainer.map { case (c, ds) => interleaved(getDescriptionForContainer(specification.getOperation, c),
              addToken(c, ds, Option(deployable.getId), Some(v))) }.toList)
      }
    }


    // Assumption is that every deployed in the grouped deltas contains the same 'maxContainersInParallel' value,
    // as it originated from the deployable.
    val deltasByDeployable: Map[Deployable, List[Delta]] = byDeployable(specification)

    defaultOrchestrationUnless(specification)(deltasByDeployable.nonEmpty) { deltasByDeployable.toList match {
        case (d, ds) :: Nil =>
          val desc = getDescriptionForSpec(specification)
          orchestrateDeltas(d, ds)
        case deltas =>
          val desc = getDescriptionForDeployables(specification.getOperation, deltasByDeployable.keys.toSeq)
          parallel(desc, deltas.map { case (d, ds) => orchestrateDeltas(d, ds)})
      }
    }
  }
}
