/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators

import java.util

import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.{InterleavedOrchestration, Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification, Operation}
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor
import com.xebialabs.deployit.plugin.api.udm.{Container, DeployedApplication}

import scala.collection.convert.wrapAll._

@Orchestrator.Metadata(name = "parallel-by-container-throttle", description = "The throttled parallel by container orchestrator")
class ThrottlingParallelContainerOrchestrator extends Orchestrator {
  def orchestrate(spec: DeltaSpecification): Orchestration = getOrchestrations(spec)

  def getOrchestrations(spec: DeltaSpecification): Orchestration = {
    def toInterleaved(list: List[(Container, List[Delta])]): List[InterleavedOrchestration] = {
      list.map { case (c, ds) => interleaved(getDescriptionForContainer(spec.getOperation, c), ds)}
    }

    def stringOrderForOperation = if (spec.getOperation == Operation.DESTROY) Ordering.String.reverse else Ordering.String

    val desc: String = getDescriptionForSpec(spec)
    val deltasByContainer: Map[Container, List[Delta]] = spec.getDeltas.toList.groupBy(_.container)
    val sorted: List[(Container, List[Delta])] = deltasByContainer.toList.sortBy(_._1.getName)(stringOrderForOperation)

    val throttleProperty: Option[PropertyDescriptor] = Option(spec.getDeployedApplication.getType.getDescriptor.getPropertyDescriptor("maxContainersInParallel"))
    throttleProperty.map(_.get(spec.getDeployedApplication).asInstanceOf[Int]) match {
      case Some(mcip) if mcip >= 1 =>
        val chunked: Iterator[List[(Container, List[Delta])]] = sorted.grouped(mcip)
        val pars = chunked.map({ l => parallel(desc, toInterleaved(l))}).toList
        serial(desc, pars)
      case _ =>
        parallel(desc, toInterleaved(sorted))
    }
  }

  private val verbs: Map[_, _] = Map(Operation.CREATE -> "Deploying", Operation.DESTROY -> "Undeploying", Operation.MODIFY -> "Updating", Operation.NOOP -> "Not updating")

  def getDescriptionForContainer(op: Operation, con: Container): String = List(verbs.get(op), "on container", con.getName).mkString(" ")

  def getDescriptionForSpec(specification: DeltaSpecification): String = {
    val deployedApplication: DeployedApplication = specification.getDeployedApplication
    List(verbs.get(specification.getOperation), deployedApplication.getName, deployedApplication.getVersion.getName, "on environment", deployedApplication.getEnvironment.getName).mkString(" ")
  }

  implicit class DeltaUtils(delta: Delta) {
    def deployed = if (delta.getDeployed != null) delta.getDeployed else delta.getPrevious

    def container = deployed.getContainer.asInstanceOf[Container]

    def deploymentGroup: Option[Int] = container.hasProperty("deploymentGroup") match {
      case true => Option(container.getProperty("deploymentGroup"))
      case false => None
    }
  }

}
