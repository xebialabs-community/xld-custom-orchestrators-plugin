/**
 * Copyright 2017 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
//
//object ð {
//  def unapply(delta: Delta): Option[(Operation, Option[Deployed[_, _]], Option[Deployed[_, _]])] = delta.getOperation match {
//    case Operation.CREATE => Some((delta.getOperation, None, Some(delta.getDeployed)))
//    case Operation.DESTROY => Some((delta.getOperation, Some(delta.getPrevious), None))
//    case Operation.MODIFY => Some((delta.getOperation, Some(delta.getPrevious), Some(delta.getDeployed)))
//    case Operation.NOOP => Some((delta.getOperation, Some(delta.getDeployed), Some(delta.getDeployed)))
//  }
//}
