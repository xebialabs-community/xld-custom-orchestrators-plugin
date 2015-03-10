/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators

import com.xebialabs.deployit.plugin.api.deployment.specification.{DeltaSpecification, Operation}
import com.xebialabs.deployit.plugin.api.udm.{ConfigurationItem, Deployable, DeployedApplication, Container}

object Descriptions {
  private val verbs = Map(Operation.CREATE -> "Deploying", Operation.DESTROY -> "Undeploying", Operation.MODIFY -> "Updating", Operation.NOOP -> "Not updating")

  def nameOrNull(ci: ConfigurationItem): String = Option(ci).map(_.getName).orNull

  def getDescriptionForContainer(op: Operation, con: Container): String = List(verbs(op), "on container", con.getName).mkString(" ")
  def getDescriptionForContainers(op: Operation, con: Iterable[Container]): String = List(verbs(op), "on containers", con.map(_.getName).mkString(", ")).mkString(" ")

  def getDescriptionForDeployable(op: Operation, d: Deployable): String = List(verbs(op), "of deployable", nameOrNull(d)).mkString(" ")
  def getDescriptionForDeployables(op: Operation, ds: Iterable[Deployable]): String = List(verbs(op), "of deployables", ds.map(nameOrNull).mkString(", ")).mkString(" ")

  def getDescriptionForSpec(specification: DeltaSpecification): String = {
    val deployedApplication: DeployedApplication = specification.getDeployedApplication
    List(verbs(specification.getOperation), deployedApplication.getName, deployedApplication.getVersion.getName, "on environment", deployedApplication.getEnvironment.getName).mkString(" ")
  }
}
