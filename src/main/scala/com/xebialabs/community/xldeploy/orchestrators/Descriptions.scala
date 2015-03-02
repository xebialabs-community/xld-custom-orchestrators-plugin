/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators

import com.xebialabs.deployit.plugin.api.deployment.specification.{DeltaSpecification, Operation}
import com.xebialabs.deployit.plugin.api.udm.{Deployable, DeployedApplication, Container}

object Orchestators {
  import collection.convert.wrapAll._
  import RichDelta._

  def getStringOrdering(op:Operation) = op match {
    case Operation.DESTROY => Ordering.String.reverse
    case _                 => Ordering.String
  }

  def getIntOrdering(op:Operation) = op match {
    case Operation.DESTROY => Ordering.Int.reverse
    case _                 => Ordering.Int
  }

  def byContainer(spec: DeltaSpecification) = spec.getDeltas.toList.groupBy(_.container)
  def byDeployable(spec: DeltaSpecification) = spec.getDeltas.toList.groupBy(_.deployable)

}

object Descriptions {
  private val verbs = Map(Operation.CREATE -> "Deploying", Operation.DESTROY -> "Undeploying", Operation.MODIFY -> "Updating", Operation.NOOP -> "Not updating")

  def getDescriptionForContainer(op: Operation, con: Container): String = List(verbs.get(op), "on container", con.getName).mkString(" ")
  def getDescriptionForContainers(op: Operation, con: Seq[Container]): String = List(verbs.get(op), "on containers", con.map(_.getName).mkString(", ")).mkString(" ")
  def getDescriptionForDeployable(op: Operation, d: Deployable): String = List(verbs.get(op), "of deployable", d.getName).mkString(" ")
  def getDescriptionForDeployables(op: Operation, ds: Seq[Deployable]): String = List(verbs.get(op), "of deployables", ds.map(_.getName).mkString(", ")).mkString(" ")

  def getDescriptionForSpec(specification: DeltaSpecification): String = {
    val deployedApplication: DeployedApplication = specification.getDeployedApplication
    List(verbs.get(specification.getOperation), deployedApplication.getName, deployedApplication.getVersion.getName, "on environment", deployedApplication.getEnvironment.getName).mkString(" ")
  }
}
