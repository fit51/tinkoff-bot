package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import scala.util.{Failure, Success}

import telegram.TelegramTypes.ServerAnswer

/**
  * Actor that gets Telegram Updates
  */

object TelegramUpdater {
  def props(sessionManager: ActorRef, noSessionActions: ActorRef) =
    Props(new TelegramUpdater(sessionManager: ActorRef, noSessionActions: ActorRef))

}

class TelegramUpdater(sessionManager: ActorRef, noSessionActions: ActorRef) extends Actor
  with ActorLogging with telegram.MessageMarshallingTelegram {

  import context.dispatcher
  import akka.pattern.pipe

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val telegramApi = new telegram.TelegramApi(Http(context.system), materializer, log, context.dispatcher)
  var offset = 0

  override def preStart(): Unit = telegramApi.getUpdates(offset).pipeTo(self)

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      Unmarshal(entity).to[ServerAnswer].onComplete {
        case Success(answer) => {
          for (update <- answer.result) {
            log.info("Got update, body: " + update)
            update.message.text match {
              case s: String if s == "/rates" || s == "/r" => {
                // – для получения курсов
                noSessionActions ! NoSessionActions.Rates(update.message)
              }
              case s: String if s == "/balance" || s == "/b" => {
                // – для получения текущих балансов
                noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
              }
              case s: String if s == "/history" || s == "/hi" => {
                // – для истории операций
                noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
              }
              case s: String if s == "/help" || s == "/h" => {
                // -  справочник доступных функций
                noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
              }
              case x: String => {
                log.info("Got undefined command: " + x)
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
