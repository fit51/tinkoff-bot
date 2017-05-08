package com.bankbot

import akka.actor.{ActorRef, ActorSystem}
import akka.routing.FromConfig
import com.bankbot.telegram.TelegramApiImpl
import com.bankbot.tinkoff.TinkoffApiImpl
import com.typesafe.config.ConfigFactory

/**
  * Main class
  */
object Main extends App {
  val conf = ConfigFactory.load()
  implicit val system = ActorSystem("BotSystem")

  val tinkoffApi = new TinkoffApiImpl
  val telegramApi = new TelegramApiImpl
//  val noSessionActions: ActorRef = system.actorOf(
//    NoSessionActions.props(system.scheduler, conf.getInt("app.updateRatesInterval"), telegramApi, tinkoffApi))
//  val sessionManager = system.actorOf(SessionManager.props(telegramApi, tinkoffApi))
  val noSessionRouter = system.actorOf(
    FromConfig.props(NoSessionActions.props(
      system.scheduler,
      conf.getInt("app.updateRatesInterval"),
      telegramApi,
      tinkoffApi)),
      "nosessionrouter"
  )
  val sessionRouter = system.actorOf(
    FromConfig.props(SessionManager.props(telegramApi, tinkoffApi)),
    "sessionrouter"
  )

  val getUpdates = system.actorOf(TelegramUpdater.props(sessionRouter, noSessionRouter, telegramApi))
}
