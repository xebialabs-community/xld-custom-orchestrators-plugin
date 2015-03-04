/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.token

import com.xebialabs.deployit.engine.spi.orchestration.Orchestrator
import com.xebialabs.deployit.plugin.api.deployment.specification.Delta
import com.xebialabs.deployit.plugin.api.reflect.Type
import com.xebialabs.deployit.plugin.api.udm.Container

abstract class TokenOrchestratorBase extends Orchestrator {
  def addToken(c: Container, deltas: List[Delta], suffix: Option[String], maxParallel: Option[Int]): List[Delta] = {
    val c_id: String = c.getId.replace("/", "_")
    val deployable: TokenDeployable = Type.valueOf(classOf[TokenDeployable]).getDescriptor.newInstance(s"tokenDeployable-$c_id")
    val deployed: TokenDeployed = Type.valueOf(classOf[TokenDeployed]).getDescriptor.newInstance(s"tokenDeployed-$c_id")
    deployed.setDeployable(deployable)
    deployed.setContainer(c)
    deployed.tokenGeneratorIdSuffix = suffix
    deployed.tokenGeneratorMaxTokens = maxParallel
    new TokenDelta(deployed) :: deltas
  }

}
