/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.community.xldeploy.orchestrators.token

import java.util
import java.util.Collections
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock

import com.xebialabs.deployit.plugin.api.deployment.planning._
import com.xebialabs.deployit.plugin.api.deployment.specification.{Delta, Operation}
import com.xebialabs.deployit.plugin.api.flow.{ExecutionContext, ITask, Step, StepExitCode}
import com.xebialabs.deployit.plugin.api.udm.Container
import com.xebialabs.deployit.plugin.api.udm.base.{BaseDeployable, BaseDeployed}
import grizzled.slf4j.Logging

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

  override def getIntermediateCheckpoints: util.List[String] = Collections.emptyList()
}

class TokenGeneratorHolder extends Serializable with Logging {
  val s: ReentrantLock = new ReentrantLock()
  val cache: MMap[String, TokenGenerator] = MMap()
  import com.xebialabs.community.xldeploy.orchestrators.Locks._

  def getOrCreate(tokenGeneratorIdentifier: String, maxNrTokens: Option[Int]): TokenGenerator = {
    using(s) {
      cache.getOrElseUpdate(tokenGeneratorIdentifier, {
        logger.info(s"Creating new TokenGenerator for $tokenGeneratorIdentifier with $maxNrTokens")
        new TokenGenerator(tokenGeneratorIdentifier, maxNrTokens)
      })
    }
  }

  def releaseToken(tokenGeneratorIdentifier: String): Unit = {
    using(s) {
      cache.get(tokenGeneratorIdentifier) match {
        case Some(x) =>
          val done = x.release()
          if (done) {
            remove(tokenGeneratorIdentifier)
          }
        case None => logger.warn(s"No TokenGenerator found for $tokenGeneratorIdentifier!")
      }
    }
  }

  def remove(tokenGeneratorIdentifier: String): Unit = {
    using(s) {
      logger.info(s"Done with TokenGeneator $tokenGeneratorIdentifier, removing from cache")
      cache.remove(tokenGeneratorIdentifier)
    }
  }
}

class TokenGenerator(tokenGeneratorIdentifier: String, maxNrTokens: Option[Int]) extends Serializable {
  val semaphore: Option[Semaphore] = maxNrTokens.map(new Semaphore(_))

  def take(): Boolean = semaphore.map(_.tryAcquire()).getOrElse(true)

  /**
    * Release a Token.
    * @return true iff all tokens have been returned to the TokenGenerator, false otherwise
    */
  def release(): Boolean = {
    semaphore.foreach(_.release())
    semaphore.map {
      case s if !s.hasQueuedThreads => s.availablePermits()
      case _ => 0
    }.getOrElse(0) == maxNrTokens.getOrElse(0)
  }
}

class TokenDeployable extends BaseDeployable {
}

class TokenDeployed extends BaseDeployed[TokenDeployable, Container] {

  var tokenGeneratorIdSuffix: Option[String] = None
  var tokenGeneratorMaxTokens: Option[Int] = None
  var tokenGeneratorHolder: TokenGeneratorHolder = _

  @Noop
  def tokens(ctx: DeploymentPlanningContext, delta: Delta): Unit = {
    if (tokenGeneratorMaxTokens.isDefined) {
      ctx.addStep(new TokenTakingStep(this, tokenGeneratorHolder))
      ctx.addStep(new TokenReturningStep(this, tokenGeneratorHolder))
    }
  }

}

class TokenTakingStep(deployed: TokenDeployed, tokenGeneratorHolder: TokenGeneratorHolder) extends Step {
  override def getOrder: Int = Int.MinValue

  override def execute(ctx: ExecutionContext): StepExitCode = {
    val id: String = Tokens.constructTokenGeneratorId(ctx.getTask, deployed.tokenGeneratorIdSuffix)
    tokenGeneratorHolder.getOrCreate(id, deployed.tokenGeneratorMaxTokens).take() match {
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

class TokenReturningStep(deployed: TokenDeployed, tokenGeneratorHolder: TokenGeneratorHolder) extends Step {
  override def getOrder: Int = Int.MaxValue

  override def execute(ctx: ExecutionContext): StepExitCode = {
    val id: String = Tokens.constructTokenGeneratorId(ctx.getTask, deployed.tokenGeneratorIdSuffix)
    tokenGeneratorHolder.getOrCreate(id, deployed.tokenGeneratorMaxTokens).release()
    ctx.logOutput(s"Successfully returned token for ${deployed.getContainer.getId}")
    StepExitCode.SUCCESS
  }

  override def getDescription: String = s"Return the token to the generator from ${deployed.getContainer.getId}"
}
