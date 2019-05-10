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
