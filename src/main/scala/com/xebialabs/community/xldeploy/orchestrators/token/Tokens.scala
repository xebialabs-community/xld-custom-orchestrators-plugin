/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.token

import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock

import com.xebialabs.deployit.plugin.api.deployment.planning._
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, Operation}
import com.xebialabs.deployit.plugin.api.flow.{ExecutionContext, Step, StepExitCode}
import com.xebialabs.deployit.plugin.api.udm.base.{BaseDeployable, BaseDeployed}
import com.xebialabs.deployit.plugin.api.udm.{Container, DeployedApplication}

import scala.collection.mutable.{Map => MMap}

object Tokens {
  val TokenGenerator = "tokenGenerator"
}

class TokenDelta(d: TokenDeployed) extends Delta {
  override def getOperation: Operation = Operation.NOOP

  override def getDeployed: TokenDeployed = d

  override def getPrevious: TokenDeployed = d
}

object TokenGenerator {
  import com.xebialabs.community.xldeploy.orchestrators.Locks._
  import com.xebialabs.community.xldeploy.orchestrators.RichConfigurationItem._

  val s: ReentrantLock = new ReentrantLock()
  val cache: MMap[String, TokenGenerator] = MMap()
  def apply(taskId: String, deployedApplication: DeployedApplication): TokenGenerator = {
    using(s) {
      cache.getOrElseUpdate(taskId, {
        new TokenGenerator(taskId, deployedApplication.getPropertyIfExists("maxContainersInParallel"))
      })
    }
  }

  def remove(taskId: String): Unit = {
    using(s) {
      cache.remove(taskId)
    }
  }
}

class TokenGenerator(taskId: String, maxTokens: Option[Int]) extends Serializable {
  val semaphore: Option[Semaphore] = maxTokens.map(new Semaphore(_))

  def take(): Boolean = semaphore.map(_.tryAcquire()).getOrElse(true)

  def release(): Unit = {
    semaphore.foreach(_.release())
    if (semaphore.map {
      case s if !s.hasQueuedThreads => s.availablePermits()
      case _ => 0
    }.getOrElse(0) == maxTokens.getOrElse(0)) {
      TokenGenerator.remove(taskId)
    }
  }
}

class TokenDeployable extends BaseDeployable {
}

class TokenDeployed extends BaseDeployed[TokenDeployable, Container] {
  import com.xebialabs.community.xldeploy.orchestrators.RichConfigurationItem._

  @Noop
  def tokens(ctx: DeploymentPlanningContext, delta: Delta): Unit = {
    if (ctx.getDeployedApplication.getPropertyIfExists("maxContainersInParallel").isDefined) {
      ctx.addStep(new TokenTakingStep(getContainer, ctx.getDeployedApplication))
      ctx.addStep(new TokenReturningStep(getContainer, ctx.getDeployedApplication))
    }
  }

}

class TokenTakingStep(container: Container, deployedApplication: DeployedApplication) extends Step {
  override def getOrder: Int = Int.MinValue

  override def execute(ctx: ExecutionContext): StepExitCode = {
    TokenGenerator(ctx.getTask.getId, deployedApplication).take() match {
      case true =>
        ctx.logOutput(s"Successfully acquired token for ${container.getId}")
        StepExitCode.SUCCESS
      case false =>
        ctx.logOutput(s"No token available for ${container.getId}")
        StepExitCode.RETRY
    }
  }

  override def getDescription: String = s"Take a token from the generator for deploying to ${container.getId}"
}

class TokenReturningStep(container: Container, deployedApplication: DeployedApplication) extends Step {
  override def getOrder: Int = Int.MaxValue

  override def execute(ctx: ExecutionContext): StepExitCode = {
    TokenGenerator(ctx.getTask.getId, deployedApplication).release()
    ctx.logOutput(s"Successfully returned token for ${container.getId}")
    StepExitCode.SUCCESS
  }

  override def getDescription: String = s"Return the token to the generator from ${container.getId}"
}
