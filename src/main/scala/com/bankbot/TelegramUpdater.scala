package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.bankbot.telegram.TelegramApi

import scala.util.{Failure, Success}
import telegram.TelegramTypes.ServerAnswer

/**
  * Actor that gets Telegram Updates
  */

object TelegramUpdater {
  def props(sessionManager: ActorRef, noSessionActions: ActorRef, telegramApi: TelegramApi) =
    Props(classOf[TelegramUpdater], sessionManager, noSessionActions, telegramApi)

}

class TelegramUpdater(sessionManager: ActorRef, noSessionActions: ActorRef, telegramApi: TelegramApi) extends Actor
  with ActorLogging with telegram.MessageMarshallingTelegram {

  import context.dispatcher
  import akka.pattern.pipe

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  var offset = 0

  override def preStart(): Unit = telegramApi.getUpdates(offset).pipeTo(self)

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      Unmarshal(entity).to[ServerAnswer].onComplete {
        case Success(answer) => {
          for (update <- answer.result) {
            log.info("Got update, body: " + update)
            update.message.text match {
              case Some(s) if s == "/rates" || s == "/r" => {
                // – для получения курсов
                noSessionActions ! NoSessionActions.SendRates(update.message)
              }
              case Some(s) if s == "/balance" || s == "/b" => {
                // – для получения текущих балансов
                noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
              }
              case Some(s) if s == "/history" || s == "/hi" => {
                // – для истории операций
                noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
              }
              case Some(s) if s == "/help" || s == "/h" => {
                // -  справочник доступных функций
                noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
              }
              case Some(s) => {
                log.info("Got undefined command: " + s)
                noSessionActions ! NoSessionActions.Reply(update.message, "Not Such Command\nSee /help")
              }
            }
            offset = update.update_id + 1
          }
          telegramApi.getUpdates(offset).pipeTo(self)
        }
        case Failure(t) => {
          log.info("An error has occured: " + t.getMessage)
          telegramApi.getUpdates(offset).pipeTo(self)
        }
      }
    case HttpResponse(code, _, _, _) => {
      log.info("Request failed, response code: " + code)
      telegramApi.getUpdates(offset).pipeTo(self)
    }
  }
}
