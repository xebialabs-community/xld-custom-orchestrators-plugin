/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators

import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem

object RichConfigurationItem {
  implicit def lift(ci: ConfigurationItem): RichConfigurationItem = new RichConfigurationItem(ci)
}

class RichConfigurationItem(val ci: ConfigurationItem) extends AnyVal {
  def getPropertyIfExists[T](prop: String): Option[T] = ci.hasProperty(prop) match {
    case false => None
    case _ =>
      val property: T = ci.getProperty(prop)
      Option(property)
  }
}
