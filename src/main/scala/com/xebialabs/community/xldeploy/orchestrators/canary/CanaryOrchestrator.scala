/**
 * Copyright 2019 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.xebialabs.community.xldeploy.orchestrators.canary

import com.xebialabs.community.xldeploy.orchestrators.canary.CanaryOrchestrator.{BirdcageTag, CanaryTag}
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification}
import com.xebialabs.deployit.plugin.api.udm.{Container, Deployable}

import scala.collection.mutable

object CanaryOrchestrator {
  val CanaryTag = "canary"
  val BirdcageTag = "birdcage"
}

@Orchestrator.Metadata(name = "canary", description = "The Canary Deployment Orchestrator.")
class CanaryOrchestrator extends Orchestrator {

  import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._
  import com.xebialabs.community.xldeploy.orchestrators.RichDelta._
  import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
  import com.xebialabs.community.xldeploy.orchestrators.Descriptions._

  import scala.collection.convert.wrapAll._

  override def orchestrate(specification: DeltaSpecification): Orchestration = {
    val deltasPerContainer: Map[Container, List[Delta]] = byContainer(specification)

    val canaries: mutable.Buffer[Delta] = mutable.ArrayBuffer()
    val allOthers: mutable.Buffer[Delta] = mutable.ArrayBuffer()
    val canarified: mutable.Set[Deployable] = mutable.HashSet()

    def shouldBeCanarified(deployable: Deployable): Boolean =
      deployable != null && !canarified.contains(deployable) && deployable.getTags.contains(CanaryTag)

    def canarify(map: Map[Container, List[Delta]]): Unit = {
      map.foreach {
        case (c, ds) =>
          byDeployable(ds).foreach {
            case (deployable, deltas) if shouldBeCanarified(deployable) =>
              canaries += deltas.head
              allOthers ++= deltas.tail
              canarified.add(deployable)
            case (deployable, deltas) =>
              allOthers ++= deltas
          }
      }
    }

    val birdcagesVsRest: (Map[Container, List[Delta]], Map[Container, List[Delta]]) = deltasPerContainer.partition(_._1.getTags.contains(BirdcageTag))
    // First canarify any birdcages
    canarify(birdcagesVsRest._1)
    // Then canarify the rest
    canarify(birdcagesVsRest._2)

    defaultOrchestrationUnless(specification)(canaries.nonEmpty) {
      serial("Canary-style deployment",
        interleaved(s"Canary deployment for ${canaries.map(c => nameOrNull(c.deployable)).mkString(", ")}", canaries),
        interleaved(s"", allOthers)
      )
    }
  }
}
