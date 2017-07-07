/**
 * Copyright 2017 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.xebialabs.community.xldeploy.orchestrators.token

import com.xebialabs.community.xldeploy.orchestrators.Descriptions._
import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._
import com.xebialabs.community.xldeploy.orchestrators.{RichConfigurationItem, RichDelta}
import RichDelta._
import RichConfigurationItem._
import com.xebialabs.community.xldeploy.orchestrators.token.TokenOrchestratorBase.DeltasForContainer
import com.xebialabs.community.xldeploy.orchestrators.token.Tokens._
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification}
import com.xebialabs.deployit.plugin.api.reflect.Type
import com.xebialabs.deployit.plugin.api.udm.{Container, Deployable, Deployed}
import grizzled.slf4j.Logging

import collection.convert.wrapAll._
import scala.collection.JavaConverters._

object TokenOrchestratorBase {
  type DeltasForContainer = (Container, List[Delta])
}

abstract class TokenOrchestratorBase extends Orchestrator {
  def token(c: DeltasForContainer, suffix: Option[String], maxParallel: Option[Int], tokenGeneratorHolder: TokenGeneratorHolder): Delta = {
    val c_id: String = c._1.getId.replace("/", "_")
    val deployable: TokenDeployable = Type.valueOf(classOf[TokenDeployable]).getDescriptor.newInstance(s"tokenDeployable-$c_id")
    val deployed: TokenDeployed = Type.valueOf(classOf[TokenDeployed]).getDescriptor.newInstance(s"tokenDeployed-$c_id")
    deployed.setDeployable(deployable)
    deployed.setContainer(c._1)
    deployed.tokenGeneratorIdSuffix = suffix
    deployed.tokenGeneratorMaxTokens = maxParallel
    deployed.tokenGeneratorHolder = tokenGeneratorHolder
    new TokenDelta(deployed)
  }

  def getDeltasForContainer(specification: DeltaSpecification): List[DeltasForContainer] = {
    val deltasByContainer: Map[Container, List[Delta]] = byContainer(specification)
    val orderForOperation = getStringOrdering(specification.getOperation)
    deltasByContainer.toList.sortBy(cd => nameOrNull(cd._1))(orderForOperation)
  }
}

@Orchestrator.Metadata(name = "parallel-by-container-global-token", description = "Ensures that token taking/releasing steps are generated for each container.")
class GlobalTokenOrchestrator extends TokenOrchestratorBase {
  import scala.collection.convert.wrapAll._
  import com.xebialabs.community.xldeploy.orchestrators.RichConfigurationItem._
  val tokenGeneratorHolder: TokenGeneratorHolder = new TokenGeneratorHolder

  def orchestrateContainer(dfc: DeltasForContainer, parallellism: Option[Int], specification: DeltaSpecification): Orchestration = {
    val desc = getDescriptionForContainer(specification.getOperation, dfc._1)
    parallellism match {
      case None => interleaved(desc, dfc._2)
      case Some(i) => interleaved(desc, token(dfc, None, parallellism, tokenGeneratorHolder) :: dfc._2)
    }
  }

  override def orchestrate(specification: DeltaSpecification): Orchestration = {
    val containersToDeploy = getDeltasForContainer(specification)
    val parallellism: Option[Int] = specification.getDeployedApplication.getPropertyIfExists(MaxContainersParallel)
    defaultOrchestrationUnless(specification)(containersToDeploy.nonEmpty) {
      containersToDeploy match {
        case c :: Nil =>
          orchestrateContainer(c, None, specification)
        case cs if parallellism.getOrElse(Int.MaxValue) < cs.size =>
          val d: String = getDescriptionForContainers(specification.getOperation, containersToDeploy.map(_._1))
          parallel(d, cs.map(c => orchestrateContainer(c, parallellism, specification)))
        case cs =>
          val d: String = getDescriptionForContainers(specification.getOperation, containersToDeploy.map(_._1))
          parallel(d, cs.map(c => orchestrateContainer(c, None, specification)))
      }
    }
  }
}

@Orchestrator.Metadata(name = "parallel-by-container-token-per-deployable", description = "Ensures that token taking/releasing steps are generated for each container a specific deployable is deployed to.")
class TokenPerDeployableOrchestrator extends TokenOrchestratorBase with Logging {

  val tokenGeneratorHolder = new TokenGeneratorHolder

  def orchestrateContainer(dfc: DeltasForContainer, specification: DeltaSpecification, deployableToAllDeltas: Map[Deployable, List[Delta]]): Orchestration = {
    // Assumption is that every deployed in the grouped deltas contains the same 'maxContainersInParallel' value,
    // as it originated from the deployable.
    val deltasByDeployable: Map[Deployable, List[Delta]] = byDeployable(dfc._2)
    logger.info(s"Deltas per Deployable $deltasByDeployable for container ${dfc._1}")
    val deployableToOption: Map[Deployable, Option[Int]] = deltasByDeployable.mapValues { ds =>
      val head: Delta = ds.head
      val deployed: Deployed[_, _] = RichDelta.lift(head).deployed
      RichConfigurationItem.lift(deployed).getPropertyIfExists(MaxContainersParallel)
    }
    logger.info(s"Max in parallel per deployable: $deployableToOption")

    val desc: String = getDescriptionForContainer(specification.getOperation, dfc._1)
    val tokenDeltas: List[Delta] = deployableToOption.toList.collect {
      case (d, Some(i)) if i < deployableToAllDeltas(d).size => token(dfc, Option(d.getId), Option(i), tokenGeneratorHolder)
    }
    logger.info(s"TokenDeltas")

    val ordering: Ordering[String] = getStringOrdering(specification.getOperation)
    interleaved(desc, (tokenDeltas ::: dfc._2).sortBy(delta => nameOrNull(RichDelta.lift(delta).deployable))(ordering))
  }

  override def orchestrate(specification: DeltaSpecification): Orchestration = {
    val containersToDeploy: List[(Container, List[Delta])] = getDeltasForContainer(specification)
    val deployableToAllDeltas: Map[Deployable, List[Delta]] = byDeployable(specification)
    defaultOrchestrationUnless(specification)(containersToDeploy.nonEmpty) {
      containersToDeploy match {
        case c :: Nil =>
          orchestrateContainer(c, specification, deployableToAllDeltas)
        case cs =>
          val d: String = getDescriptionForContainers(specification.getOperation, containersToDeploy.map(_._1))
          parallel(d, cs.map(c => orchestrateContainer(c, specification, deployableToAllDeltas)))
      }
    }
  }
}
