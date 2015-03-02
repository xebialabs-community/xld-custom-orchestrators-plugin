/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators

import com.xebialabs.deployit.plugin.api.deployment.specification.{Operation, Delta}
import com.xebialabs.deployit.plugin.api.udm.{Deployable, Deployed, Container}

object RichDelta {
  implicit def lift(delta: Delta): RichDelta = new RichDelta(delta)
}

class RichDelta(val delta: Delta) extends AnyVal {
  def deployed = if (delta.getDeployed != null) delta.getDeployed else delta.getPrevious

  def container = deployed.getContainer.asInstanceOf[Container]
  def deployable = deployed.getDeployable.asInstanceOf[Deployable]

  def deploymentGroup: Option[Int] = container.hasProperty("deploymentGroup") match {
    case true => Option(container.getProperty("deploymentGroup"))
    case false => None
  }
}

object ð {
  def unapply(delta: Delta): Option[(Operation, Option[Deployed[_, _]], Option[Deployed[_, _]])] = delta.getOperation match {
    case Operation.CREATE => Some((delta.getOperation, None, Some(delta.getDeployed)))
    case Operation.DESTROY => Some((delta.getOperation, Some(delta.getPrevious), None))
    case Operation.MODIFY => Some((delta.getOperation, Some(delta.getPrevious), Some(delta.getDeployed)))
    case Operation.NOOP => Some((delta.getOperation, Some(delta.getDeployed), Some(delta.getDeployed)))
  }
}