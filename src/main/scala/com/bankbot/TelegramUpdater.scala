package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import com.bankbot.telegram.TelegramApi
import com.bankbot.telegram.PrettyMessage.prettyHelp
import telegram.TelegramTypes.{Message, ServerAnswer}

/**
  * Actor that gets Telegram Updates
  */

object TelegramUpdater {
  def props(sessionManager: ActorRef, noSessionActions: ActorRef, telegramApi: TelegramApi) =
    Props(classOf[TelegramUpdater], sessionManager, noSessionActions, telegramApi)
  case object getOffset
  case class Offset(offset: Int)
}

class TelegramUpdater(sessionManager: ActorRef, noSessionActions: ActorRef,
                      telegramApi: TelegramApi)
  extends Actor with ActorLogging {
  import TelegramUpdater._
  implicit val logger: LoggingAdapter = log

  private var offset = 0
  override def preStart(): Unit = telegramApi.getUpdates(offset)

  def receive = {
    case ServerAnswer(true, result) => {
      for (update <- result) {
        if(update.message.chat.c_type == "private") {
          update.message match {
            case Message(_, _, _, _, Some(text), None) => {
              text match {
                // - Если кто-то пытается пользоваться без авторизации,
                // - то ему придёт кнопка отправки контакта.
                case s if s == "/rates" || s == "/r" => {
                  // – для получения курсов
                  noSessionActions ! NoSessionActions.SendRates(update.message)
                }
                case s if isSessionCommand(s) => {
                  // – команды, которые требую аутентификации
                  sessionManager ! SessionManager.SessionCommand(update.message)
                }
                case s if s == "/help" || s == "/h" => {
                  // -  справочник доступных функций
                  noSessionActions ! NoSessionActions.Reply(update.message, prettyHelp)
                }
                case s if s == "/start" || s == "/s" => {
                  // -  сообщение при старте
                  noSessionActions ! NoSessionActions.Reply(update.message, prettyHelp)
                }
                case s => {
                  //            log.info("Got undefined command: " + s)
                  noSessionActions ! NoSessionActions.Reply(update.message, "No Such Command\nSee /help")
                }
              }
            }
            case Message(_, _, _, _, _, Some(contact)) => {
              sessionManager ! SessionManager.PossibleContact(update.message.chat.id, contact)
            }
          }
        } else {
          noSessionActions ! NoSessionActions.Reply(update.message, prettyHelp)
        }
        offset = update.update_id + 1
      }
      telegramApi.getUpdates(offset)
    }
    case ServerAnswer(false, _) => telegramApi.getUpdates(offset)
    case getOffset => sender ! Offset(offset)
  }

  def isSessionCommand(text: String) = {
    text == "/balance" || text == "/b" ||
      text == "/history" || text == "/hi"
  }
}
