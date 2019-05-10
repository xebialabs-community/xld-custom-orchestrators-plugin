/**
 * Copyright 2019 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

  def takeToken(task: ITask, deployed: TokenDeployed, ctx: ExecutionContext): Boolean = {
    val tokenGeneratorIdentifier = Tokens.constructTokenGeneratorId(task, deployed.tokenGeneratorIdSuffix)
    using(s) {
      val create: TokenGenerator = getOrCreate(tokenGeneratorIdentifier, deployed.tokenGeneratorMaxTokens, ctx)
      create.take(ctx)
    }
  }

  def releaseToken(task: ITask, suffix: Option[String], ctx: ExecutionContext): Unit = {
    val tokenGeneratorIdentifier = Tokens.constructTokenGeneratorId(task, suffix)
    using(s) {
      cache.get(tokenGeneratorIdentifier) match {
        case Some(x) =>
          if (x.release(ctx)) {
            remove(tokenGeneratorIdentifier, ctx)
          }
        case None =>
          ctx.logError(s"No TokenGenerator found for $tokenGeneratorIdentifier! Could not return the token.")
          ctx.logError(s"Cache contents = ${cache.filterKeys(_.startsWith(task.getId))}")
      }
    }
  }

  private[this] def getOrCreate(tokenGeneratorIdentifier: String, maxNrTokens: Option[Int], ctx: ExecutionContext): TokenGenerator = {
    cache.getOrElseUpdate(tokenGeneratorIdentifier, {
      ctx.logOutput(s"Creating new TokenGenerator for $tokenGeneratorIdentifier with $maxNrTokens")
      new TokenGenerator(tokenGeneratorIdentifier, maxNrTokens)
    })
  }

  private[this] def remove(tokenGeneratorIdentifier: String, ctx: ExecutionContext): Unit = {
    ctx.logOutput(s"Done with TokenGenerator $tokenGeneratorIdentifier, removing from cache")
    cache.remove(tokenGeneratorIdentifier)
  }
}

class TokenGenerator(tokenGeneratorIdentifier: String, maxNrTokens: Option[Int]) extends Serializable {
  val semaphore: Option[Semaphore] = maxNrTokens.map(new Semaphore(_))

  def take(ctx: ExecutionContext): Boolean = semaphore match {
    case Some(x) => x.tryAcquire() match {
      case true =>
        ctx.logOutput(s"Took a token from $tokenGeneratorIdentifier, now there are ${x.availablePermits()} tokens left")
        true
      case false =>
        ctx.logError(s"No tokens are left for $tokenGeneratorIdentifier")
        false
    }
    case None =>
      ctx.logOutput(s"No tokens are specified for $tokenGeneratorIdentifier, continuing execution...")
      true
  }

  /**
    * Release a Token.
    * @return true iff all tokens have been returned to the TokenGenerator, false otherwise
    */
  def release(ctx: ExecutionContext): Boolean = {
    semaphore match {
      case Some(x) =>
        x.release()
        ctx.logOutput(s"Token was returned for $tokenGeneratorIdentifier. We now have ${x.availablePermits()} of ${maxNrTokens.getOrElse(0)}")
        val permits = if (!x.hasQueuedThreads) x.availablePermits() else 0
        permits == maxNrTokens.getOrElse(0)
      case None =>
        ctx.logOutput(s"No tokens are specified for $tokenGeneratorIdentifier, continuing execution")
        true
    }
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
    tokenGeneratorHolder.takeToken(ctx.getTask, deployed, ctx) match {
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
    tokenGeneratorHolder.releaseToken(ctx.getTask, deployed.tokenGeneratorIdSuffix, ctx)
    ctx.logOutput(s"Successfully returned token for ${deployed.getContainer.getId}")
    StepExitCode.SUCCESS
  }

  override def getDescription: String = s"Return the token to the generator from ${deployed.getContainer.getId}"
}
