package com.bankbot

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

/**
  * Main class
  */
object Main extends App {
  val conf = ConfigFactory.load()
  implicit val system = ActorSystem("BotSystem")

  val noSessionActions = system.actorOf(
    NoSessionActions.props(conf.getInt("app.updateRatesInterval"))
  )
  val sessionManager = system.actorOf(SessionManager.props)
  val getUpdates = system.actorOf(TelegramUpdater.props(sessionManager, noSessionActions))
}
