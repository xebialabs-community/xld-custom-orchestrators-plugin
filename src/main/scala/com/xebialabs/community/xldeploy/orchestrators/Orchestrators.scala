/**
 * Copyright 2019 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.xebialabs.community.xldeploy.orchestrators

import com.xebialabs.community.xldeploy.orchestrators.Descriptions.getDescriptionForSpec
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations.interleaved
import com.xebialabs.deployit.engine.spi.orchestration.{InterleavedOrchestration, Orchestrations, Orchestration}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification, Operation}
import com.xebialabs.deployit.plugin.api.udm.{Deployable, Container}

object Orchestrators {

  import collection.convert.wrapAll._
  import RichDelta._

  def getStringOrdering(op: Operation): Ordering[String] = op match {
    case Operation.DESTROY => Ordering.String.reverse
    case _ => Ordering.String
  }

  def getIntOrdering(op: Operation): Ordering[Int] = op match {
    case Operation.DESTROY => Ordering.Int.reverse
    case _ => Ordering.Int
  }

  def byContainer(spec: DeltaSpecification): Map[Container, List[Delta]] = byContainer(spec.getDeltas.toList)

  def byContainer(deltas: List[Delta]): Map[Container, List[Delta]] = deltas.groupBy(_.container)

  def byDeployable(spec: DeltaSpecification): Map[Deployable, List[Delta]] = byDeployable(spec.getDeltas.toList)

  def byDeployable(deltas: List[Delta]): Map[Deployable, List[Delta]] = deltas.groupBy(_.deployable)

  def defaultOrchestrationUnless(deltaSpecification: DeltaSpecification)(condition: => Boolean)(orchestrate: => Orchestration): Orchestration = {
    if (condition) {
      orchestrate
    } else {
      defaultOrchestration(deltaSpecification)
    }
  }

  def defaultOrchestration(deltaSpecification: DeltaSpecification): Orchestration =
    interleaved(getDescriptionForSpec(deltaSpecification), deltaSpecification.getDeltas)


}
