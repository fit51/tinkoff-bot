package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import com.bankbot.telegram.TelegramApi
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
        update.message match {
          case Message(_, _, _, _, _, Some(contact)) => {
            sessionManager ! SessionManager.PossibleContact(update.message)
          }
          case Message(_, _, _, _, Some(text), None) => {
            text match {
              // - Если кто-то пытается пользоваться без авторизации,
              // - то ему придёт кнопка отправки контакта.
              case s if s == "/rates" || s == "/r" => {
                // – для получения курсов
                noSessionActions ! NoSessionActions.SendRates(update.message)
              }
              case s if s == "/balance" || s == "/b"  => {
                // – для получения текущих балансов
                sessionManager ! SessionManager.SendBalance(update.message)
                //            noSessionActions ! NoSessionActions.Reply(update.message, "Your balance: -1")
              }
              case s if s == "/history" || s == "/hi" => {
                // – для истории операций
                noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
              }
              case s if s == "/help" || s == "/h" => {
                // -  справочник доступных функций
                noSessionActions ! NoSessionActions.Reply(update.message, "Not implemented yet")
              }
              case s => {
                //            log.info("Got undefined command: " + s)
                noSessionActions ! NoSessionActions.Reply(update.message, "No Such Command\nSee /help")
              }
            }
          }
        }
        offset = update.update_id + 1
      }
      telegramApi.getUpdates(offset)
    }
    case ServerAnswer(false, _) => telegramApi.getUpdates(offset)
  }
}
