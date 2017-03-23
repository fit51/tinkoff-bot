package com.bankbot

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

/**
  * Main class
  */
object Main extends App {
  lazy val token = ConfigFactory.load("telegram").getString("telegram.key")
  implicit val system = ActorSystem()

  val noSessionActions = system.actorOf(NoSessionActions.props)
  val sessionManager = system.actorOf(SessionManager.props)
  val getUpdates = system.actorOf(TelegramUpdater.props(sessionManager, noSessionActions))
}
