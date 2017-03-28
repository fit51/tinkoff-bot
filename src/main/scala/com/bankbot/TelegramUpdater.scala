package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import scala.util.{Failure, Success}
import telegram.TelegramTypes.{Message, ServerAnswer, Update}

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
    // зачем headers?
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      Unmarshal(entity).to[ServerAnswer[Update]].onComplete {
        case Success(answer) => {
          for (update <- answer.result) {
            log.info("Got update, body: " + update)
              /* проверяем, пришел ли контакт, если пришел - добавляем
                 будет вынесено в отдельное место потом
                */
              if (update.message.contact.nonEmpty){
                log.info(update.message.contact.toString)
                SessionManager.contacts = SessionManager.contacts ++ update.message.contact
                log.info(SessionManager.contacts.toString())
              }
              update.message.text match {
                case s: Option[String] if s.contains("/rates") || s.contains("/r") => {
                  // – для получения курсов
                  noSessionActions ! NoSessionActions.Rates(update.message)
                }
                case s: Option[String] if s.contains("/balance") || s.contains("/b") => {
                  // – для получения текущих балансов
                  // - сначала получаем контакт
                  noSessionActions ! NoSessionActions.Contact(update.message)

                }
                case s: Option[String] if s.contains("/history") || s.contains("/hi") => {
                  // – для истории операций
                  noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
                }
                case s: Option[String] if s.contains("/help") || s.contains("/h") => {
                  // -  справочник доступных функций
                  noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
                }
                case x: Option[String] => {
                  log.info("Got undefined command: " + x)
                  noSessionActions ! NoSessionActions.Reply(update.message, "Not Such Command\nSee /help")
                }
              }
              offset = update.update_id + 1
            }
            telegramApi.getUpdates(offset).pipeTo(self)
          }
          case Failure(t) => {
            log.info("An error has occurred: " + t.getMessage + " " + t.printStackTrace())
            telegramApi.getUpdates(offset).pipeTo(self)
          }
        }

    case HttpResponse(code, _, _, _) => {
      log.info("Request failed, response code: " + code)
      telegramApi.getUpdates(offset).pipeTo(self)
    }
  }
}
