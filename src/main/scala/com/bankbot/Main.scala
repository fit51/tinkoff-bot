package com.bankbot

import akka.actor.{ActorRef, ActorSystem}
import com.bankbot.telegram.TelegramApiImpl
import com.bankbot.tinkoff.{TinkoffApi, TinkoffApiImpl}
import com.typesafe.config.ConfigFactory

/**
  * Main class
  */
object Main extends App {
  val conf = ConfigFactory.load()
  implicit val system = ActorSystem("BotSystem")

  val tinkoffApi = new TinkoffApiImpl
  val telegramApi = new TelegramApiImpl
  val noSessionActions: ActorRef = system.actorOf(
    NoSessionActions.props(system.scheduler, conf.getInt("app.updateRatesInterval"), telegramApi, tinkoffApi))
  val sessionManager = system.actorOf(SessionManager.props(telegramApi, tinkoffApi))
  val getUpdates = system.actorOf(TelegramUpdater.props(sessionManager, noSessionActions, telegramApi))
}
