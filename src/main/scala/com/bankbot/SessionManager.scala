package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import com.bankbot.tinkoff.TinkoffApi
import telegram.TelegramTypes._
import telegram.PrettyMessage.prettyThx4Contact
import telegram.TelegramApi

/**
  * Master actor
  * Creates and manages UserSessions
  */

object SessionManager {
  def props(telegramApi: TelegramApi, tinkoffApi: TinkoffApi) =
    Props(classOf[SessionManager], telegramApi, tinkoffApi)

  case class PossibleContact(chatId: Int, contact: Contact)
  abstract class SessionCommand {
    val from: User
    val chatId: Int
  }
  case class BalanceCommand(override val from: User, override val chatId: Int) extends SessionCommand
  case class HistoryCommand(override val from: User, override val chatId: Int) extends SessionCommand
}

class SessionManager(telegramApi: TelegramApi,
                     tinkoffApi: TinkoffApi) extends Actor with ActorLogging {
  import com.bankbot.SessionManager._
  implicit val logger: LoggingAdapter = log
  type UserId = Int
  type ChatId = Int

  var contacts: Map[UserId, Contact] = Map()
  var sessions: Map[ChatId, ActorRef] = Map()

  def createUserSession(chat: Chat) =
    context.actorOf(UserSession.props(chat), chat.id.toString)

  override def receive: Receive = {

    case command: SessionCommand => {
      if (!contacts.contains(command.from.id)) {
        contactRequest(command.chatId)
      } else {

        val send = Map("chat_id" -> command.chatId.toString,
          "text" -> "Your balance is: 0", "parse_mode" -> "HTML")
        telegramApi.sendMessage(send)
      }
    }

    case PossibleContact(chatId: Int, contact: Contact) => {
      contacts += (contact.user_id -> contact)
      val send = Map("chat_id" -> chatId.toString,
        "text" -> prettyThx4Contact, "parse_mode" -> "HTML")
      telegramApi.sendMessage(send)
    }
  }

  def contactRequest(chatId: Int) = {
    val messageText = "This operation requires your phone number."
    val reply_markup = "{\"keyboard\":[[{\"text\":\"Send My Phone Number\", \"request_contact\": true}]], " +
      "\"resize_keyboard\": true, \"one_time_keyboard\": true}"
    val send = Map("chat_id" -> chatId.toString, "text" -> messageText,
      "reply_markup" -> reply_markup)
    telegramApi.sendMessage(send)
  }
}
