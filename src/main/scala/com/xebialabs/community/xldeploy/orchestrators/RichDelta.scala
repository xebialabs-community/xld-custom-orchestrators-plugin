/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators

import com.xebialabs.deployit.plugin.api.deployment.specification.Delta
import com.xebialabs.deployit.plugin.api.udm.Container

object RichDelta {
  implicit def lift(delta: Delta): RichDelta = new RichDelta(delta)
}

class RichDelta(val delta: Delta) extends AnyVal {
  def deployed = if (delta.getDeployed != null) delta.getDeployed else delta.getPrevious

  def container = deployed.getContainer.asInstanceOf[Container]

  def deploymentGroup: Option[Int] = container.hasProperty("deploymentGroup") match {
    case true => Option(container.getProperty("deploymentGroup"))
    case false => None
  }
}
