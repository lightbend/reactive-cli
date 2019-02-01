/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package foo

import akka.actor.Actor
import akka.actor.ActorLogging

class NoisySingleton extends Actor with ActorLogging {

  override def preStart(): Unit =
    log.info("Noisy singleton started")

  override def postStop(): Unit =
    log.info("Noisy singleton stopped")

  override def receive: Receive = {
    case msg => log.info("Msg: {}", msg)
  }
}
