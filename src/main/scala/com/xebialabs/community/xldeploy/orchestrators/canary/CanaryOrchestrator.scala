/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.canary

import com.xebialabs.community.xldeploy.orchestrators.canary.CanaryOrchestrator.CanaryTag
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification}
import com.xebialabs.deployit.plugin.api.udm.Deployable

import scala.collection.mutable

object CanaryOrchestrator {
  val CanaryTag = "canary"
}

@Orchestrator.Metadata(name = "canary", description = "The Canary Deployment Orchestrator.")
class CanaryOrchestrator extends Orchestrator {
  import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._
  import com.xebialabs.community.xldeploy.orchestrators.RichDelta._
  import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
  import com.xebialabs.community.xldeploy.orchestrators.Descriptions._

  import scala.collection.convert.wrapAll._

  override def orchestrate(specification: DeltaSpecification): Orchestration = {
    val deltaByDeployable: Map[Deployable, List[Delta]] = byDeployable(specification)

    val canaries: mutable.Buffer[Delta] = mutable.ArrayBuffer()
    val allOthers: mutable.Buffer[Delta] = mutable.ArrayBuffer()

    deltaByDeployable.foreach {
      case (deployable, deltas) if deployable != null && deployable.getTags.contains(CanaryTag) =>
        canaries += deltas.head
        allOthers ++= deltas.tail
      case (deployable, deltas) =>
        allOthers ++= deltas
    }

    defaultOrchestrationUnless(specification)(canaries.nonEmpty) {
      serial("Canary-style deployment",
        interleaved(s"Canary deployment for ${canaries.map(c => nameOrNull(c.deployable)).mkString(", ")}", canaries),
        interleaved(s"", allOthers)
      )
    }
  }
}
