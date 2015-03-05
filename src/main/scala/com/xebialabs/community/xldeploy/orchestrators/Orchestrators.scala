/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators

import com.xebialabs.community.xldeploy.orchestrators.Descriptions.getDescriptionForSpec
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations.interleaved
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestrations, Orchestration}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification, Operation}
import com.xebialabs.deployit.plugin.api.udm.{Deployable, Container}

object Orchestrators {
  import collection.convert.wrapAll._
  import RichDelta._

  def getStringOrdering(op:Operation): Ordering[String] = op match {
    case Operation.DESTROY => Ordering.String.reverse
    case _                 => Ordering.String
  }

  def getIntOrdering(op:Operation): Ordering[Int] = op match {
    case Operation.DESTROY => Ordering.Int.reverse
    case _                 => Ordering.Int
  }

  def byContainer(spec: DeltaSpecification): Map[Container, List[Delta]] = byContainer(spec.getDeltas.toList)
  def byContainer(deltas: List[Delta]): Map[Container, List[Delta]] = deltas.groupBy(_.container)
  def byDeployable(spec: DeltaSpecification): Map[Deployable, List[Delta]] = byDeployable(spec.getDeltas.toList)
  def byDeployable(deltas: List[Delta]): Map[Deployable, List[Delta]] = deltas.groupBy(_.deployable)

  def defaultOrchestrationUnless(deltaSpecification: DeltaSpecification)(condition: => Boolean)(orchestrate: => Orchestration) = {
    if (condition) {
      orchestrate
    } else {
      interleaved(getDescriptionForSpec(deltaSpecification), deltaSpecification.getDeltas)
    }
  }

}
