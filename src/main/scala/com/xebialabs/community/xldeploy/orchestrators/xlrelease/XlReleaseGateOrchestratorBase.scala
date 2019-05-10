/**
 * Copyright 2019 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.xebialabs.community.xldeploy.orchestrators.xlrelease

import java.util
import java.util.Collections

import com.xebialabs.community.xldeploy.orchestrators.Descriptions._
import com.xebialabs.community.xldeploy.orchestrators.Orchestrators._
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._
import com.xebialabs.deployit.engine.spi.orchestration.Orchestrator.Metadata
import com.xebialabs.deployit.engine.spi.orchestration.{Orchestration, Orchestrator}
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, DeltaSpecification, Operation}
import com.xebialabs.deployit.plugin.api.reflect.{Descriptor, DescriptorRegistry, Type}
import com.xebialabs.deployit.plugin.api.udm._
import grizzled.slf4j.Logging

import scala.collection.convert.wrapAll._

object XlReleaseOrchestrators {
  val XlReleaseTaskProperty = "xlrTaskId"

  import com.xebialabs.community.xldeploy.orchestrators.RichConfigurationItem._

  lazy val xlrInstance: Type = Type.valueOf("xlrelease.Instance")

  def isXldXlReleasePluginPresent = DescriptorRegistry.exists(xlrInstance)

  def findXlrInstance(deployedApplication: DeployedApplication) =
    deployedApplication.getEnvironment.getMembers.find(_.getType.instanceOf(xlrInstance))

  def isXldXlReleasePluginEnabled(specification: DeltaSpecification) =
    isXldXlReleasePluginPresent &&
      findXlrInstance(specification.getDeployedApplication).isDefined

  def deployedApplicationHasXlrTaskProperty(application: DeployedApplication): Boolean =
    application.getPropertyIfExists(XlReleaseTaskProperty).isDefined

}

abstract class XlReleaseOrchestratorBase extends Orchestrator {

  import com.xebialabs.community.xldeploy.orchestrators.xlrelease.XlReleaseOrchestrators._

  def orchestrateXlRelease(specification: DeltaSpecification): Orchestration

  override final def orchestrate(specification: DeltaSpecification): Orchestration = {
    defaultOrchestrationUnless(specification)(isXldXlReleasePluginEnabled(specification)) {
      orchestrateXlRelease(specification)
    }
  }
}

@Metadata(name = "xl-release", description = "Adds an XLRelease Gate Trigger deployed if it does not exist and XLR integration is enabled")
class XLReleaseOrchestrator extends XlReleaseOrchestratorBase with Logging {

  import com.xebialabs.community.xldeploy.orchestrators.RichDelta._
  import com.xebialabs.community.xldeploy.orchestrators.xlrelease.XlReleaseOrchestrators._

  import scala.collection.convert.wrapAll._

  def addXlrDeployed(xlrInstance: Container, specification: DeltaSpecification): Orchestration = {
    val descriptor: Descriptor = Type.valueOf("xlrelease.CompletedTask").getDescriptor
    val deployableDescriptor: Descriptor = Type.valueOf("xlrelease.CompleteTask").getDescriptor
    val xlrTaskProp: String = specification.getDeployedApplication.getProperty(XlReleaseTaskProperty)
    val deployed: Deployed[ Deployable, Container] = descriptor.newInstance(xlrInstance.getId + "/completedTask")
    val deployable: Deployable = deployableDescriptor.newInstance("completeTask")
    descriptor.getPropertyDescriptor("taskId").set(deployed, xlrTaskProp)
//    deployed.setDeployable(deployable)
    deployed.setContainer(xlrInstance)
    interleaved(getDescriptionForSpec(specification), XlrDelta(deployed) :: specification.getDeltas.toList)
  }


  override def orchestrateXlRelease(specification: DeltaSpecification): Orchestration = {
    val application: DeployedApplication = specification.getDeployedApplication
    val xlrInstance: Container = findXlrInstance(application).get
    specification.getDeltas.find(_.container == xlrInstance) match {
      case Some(d) => defaultOrchestration(specification)
      case None if !deployedApplicationHasXlrTaskProperty(application) => defaultOrchestration(specification)
      case None => addXlrDeployed(xlrInstance, specification)
    }
  }
}

abstract class XlReleaseGateOrchestratorBase extends XlReleaseOrchestratorBase {

  import com.xebialabs.community.xldeploy.orchestrators.Descriptions._
  import com.xebialabs.community.xldeploy.orchestrators.RichDelta._
  import com.xebialabs.community.xldeploy.orchestrators.xlrelease.XlReleaseOrchestrators._

  override def orchestrateXlRelease(specification: DeltaSpecification): Orchestration = {
    val deployedApplication: DeployedApplication = specification.getDeployedApplication
    val instance: Container = findXlrInstance(deployedApplication).get
    byContainer(specification).get(instance) match {
      case None => defaultOrchestration(specification)
      case Some(d :: Nil) =>
        orchestrateXlReleaseGate(getDescriptionForSpec(specification),
          d, specification.getDeltas.toList.filterNot(_.container.getType.instanceOf(xlrInstance)))
      case Some(ds) => throw new IllegalStateException(s"Did not expect more than 1 deployed for XLR gate completion")
    }

  }

  def orchestrateXlReleaseGate(description: String, xlrDelta: Delta, otherDeltas: List[Delta]): Orchestration
}

@Metadata(name = "xl-release-gate-first", description = "Trigger an XL Release gate first in the (sub-)orchestration")
class XlReleaseGateOrchestratorFirst extends XlReleaseGateOrchestratorBase {

  import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._

  import scala.collection.convert.wrapAll._

  override def orchestrateXlReleaseGate(description: String, xlrDelta: Delta, otherDeltas: List[Delta]): Orchestration = {
    serial(description,
      interleaved("Complete task on XL Release", xlrDelta),
      interleaved(description, otherDeltas)
    )
  }
}

@Metadata(name = "xl-release-gate-last", description = "Trigger an XL Release gate lastly in the (sub-)orchestration")
class XlReleaseGateOrchestratorLast extends XlReleaseGateOrchestratorBase {

  import com.xebialabs.deployit.engine.spi.orchestration.Orchestrations._

  import scala.collection.convert.wrapAll._

  override def orchestrateXlReleaseGate(description: String, xlrDelta: Delta, otherDeltas: List[Delta]): Orchestration = {
    serial(description,
      interleaved(description, otherDeltas),
      interleaved("Complete task on XL Release", xlrDelta)
    )
  }
}

case class XlrDelta(d: Deployed[_ <: Deployable, _ <: Container]) extends Delta {
  override def getDeployed: Deployed[_ <: Deployable, _ <: Container] = d

  override def getOperation: Operation = Operation.CREATE

  override def getPrevious: Deployed[_ <: Deployable, _ <: Container] = d

  override def getIntermediateCheckpoints: util.List[String] = Collections.emptyList()
}
