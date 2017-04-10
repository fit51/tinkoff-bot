package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import com.bankbot.SessionManager.PossibleReply
import com.bankbot.telegram.TelegramApi
import com.bankbot.telegram.PrettyMessage.{prettyHelp, prettyNonPrivate}
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
        if (update.message.chat.c_type == "private") {
          update.message match {
            case Message(_, Some(user), chat, _, None, Some(text), None) => {
              text match {
                // - Если кто-то пытается пользоваться без авторизации,
                // - то ему придёт кнопка отправки контакта.
                case s if s == "/rates" || s == "/r" => {
                  // – для получения курсов
                  noSessionActions ! NoSessionActions.SendRates(chat.id)
                }
                case s if s == "/balance" || s == "/b" => {
                  // – команды, которые требую аутентификации
                  sessionManager ! UserSession.BalanceCommand(user, chat.id)
                }
                case s if s == "/history" || s == "/hi" => {
                  // – команды, которые требую аутентификации
                  sessionManager ! UserSession.HistoryCommand(user, chat.id)
                }
                case s if s == "/help" || s == "/h" => {
                  // -  справочник доступных функций
                  noSessionActions ! NoSessionActions.SendMessage(chat.id, prettyHelp)
                }
                case s if s == "/start" || s == "/s" => {
                  // -  сообщение при старте
                  noSessionActions ! NoSessionActions.SendMessage(chat.id, prettyHelp)
                }
                case s => {
                  //            log.info("Got undefined command: " + s)
                  noSessionActions ! NoSessionActions.SendMessage(chat.id, "No Such Command\nSee /help")
                }
              }
            }
            case Message(_, _, _, _, _, _, Some(contact)) => {
              sessionManager ! SessionManager.PossibleContact(update.message.chat.id, contact)
            }
            case Message(message_id, Some(user), chat, _, Some(reply), Some(text), None) => {
              sessionManager ! PossibleReply(chat.id, reply.message_id, text)
            }
          }
        } else {
          noSessionActions ! NoSessionActions.SendMessage(update.message.chat.id, prettyNonPrivate)
        }
        offset = update.update_id + 1
      }
      telegramApi.getUpdates(offset)
    }
    case ServerAnswer(false, _) => telegramApi.getUpdates(offset)
    case getOffset => sender ! Offset(offset)
  }

}
