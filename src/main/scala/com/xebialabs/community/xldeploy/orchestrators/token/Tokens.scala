/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.token

import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock

import com.xebialabs.community.xldeploy.orchestrators.token.Tokens.MaxContainersParallel
import com.xebialabs.deployit.plugin.api.deployment.planning._
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, Operation}
import com.xebialabs.deployit.plugin.api.flow.{ITask, ExecutionContext, Step, StepExitCode}
import com.xebialabs.deployit.plugin.api.udm.base.{BaseDeployable, BaseDeployed}
import com.xebialabs.deployit.plugin.api.udm.{Container, DeployedApplication}

import scala.collection.mutable.{Map => MMap}

object Tokens {
  val MaxContainersParallel = "maxContainersInParallel"
  
  def constructTokenGeneratorId(task: ITask, suffix: Option[String]): String = suffix match {
    case Some(x) => s"${task.getId}-$x"
    case None => task.getId
  }
}

class TokenDelta(d: TokenDeployed) extends Delta {
  override def getOperation: Operation = Operation.NOOP

  override def getDeployed: TokenDeployed = d

  override def getPrevious: TokenDeployed = d
}

object TokenGenerator {
  import com.xebialabs.community.xldeploy.orchestrators.Locks._

  val s: ReentrantLock = new ReentrantLock()
  val cache: MMap[String, TokenGenerator] = MMap()
  def apply(tokenGeneratorIdentifier: String, maxNrTokens: Option[Int]): TokenGenerator = {
    using(s) {
      cache.getOrElseUpdate(tokenGeneratorIdentifier, {
        new TokenGenerator(tokenGeneratorIdentifier, maxNrTokens)
      })
    }
  }

  def remove(tokenGeneratorIdentifier: String): Unit = {
    using(s) {
      cache.remove(tokenGeneratorIdentifier)
    }
  }
}

class TokenGenerator(tokenGeneratorIdentifier: String, maxNrTokens: Option[Int]) extends Serializable {
  val semaphore: Option[Semaphore] = maxNrTokens.map(new Semaphore(_))

  def take(): Boolean = semaphore.map(_.tryAcquire()).getOrElse(true)

  def release(): Unit = {
    semaphore.foreach(_.release())
    if (semaphore.map {
      case s if !s.hasQueuedThreads => s.availablePermits()
      case _ => 0
    }.getOrElse(0) == maxNrTokens.getOrElse(0)) {
      TokenGenerator.remove(tokenGeneratorIdentifier)
    }
  }
}

class TokenDeployable extends BaseDeployable {
}

class TokenDeployed extends BaseDeployed[TokenDeployable, Container] {
  import com.xebialabs.community.xldeploy.orchestrators.RichConfigurationItem._

  var tokenGeneratorIdSuffix: Option[String] = None
  var tokenGeneratorMaxTokens: Option[Int] = None

  @Noop
  def tokens(ctx: DeploymentPlanningContext, delta: Delta): Unit = {
    if (tokenGeneratorMaxTokens.isDefined) {
      ctx.addStep(new TokenTakingStep(this))
      ctx.addStep(new TokenReturningStep(this))
    }
  }

}

class TokenTakingStep(deployed: TokenDeployed) extends Step {
  override def getOrder: Int = Int.MinValue

  override def execute(ctx: ExecutionContext): StepExitCode = {
    val id: String = Tokens.constructTokenGeneratorId(ctx.getTask, deployed.tokenGeneratorIdSuffix)
    TokenGenerator(id, deployed.tokenGeneratorMaxTokens).take() match {
      case true =>
        ctx.logOutput(s"Successfully acquired token for ${deployed.getContainer.getId}")
        StepExitCode.SUCCESS
      case false =>
        ctx.logOutput(s"No token available for ${deployed.getContainer.getId}")
        StepExitCode.RETRY
    }
  }

  override def getDescription: String = s"Take a token from the generator for deploying to ${deployed.getContainer.getId}"
}

class TokenReturningStep(deployed: TokenDeployed) extends Step {
  override def getOrder: Int = Int.MaxValue

  override def execute(ctx: ExecutionContext): StepExitCode = {
    val id: String = Tokens.constructTokenGeneratorId(ctx.getTask, deployed.tokenGeneratorIdSuffix)
    TokenGenerator(id, deployed.tokenGeneratorMaxTokens).release()
    ctx.logOutput(s"Successfully returned token for ${deployed.getContainer.getId}")
    StepExitCode.SUCCESS
  }

  override def getDescription: String = s"Return the token to the generator from ${deployed.getContainer.getId}"
}
