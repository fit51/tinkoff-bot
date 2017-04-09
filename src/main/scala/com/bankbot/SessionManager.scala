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

  case class ContactRequest(m: Message)
  case class PossibleContact(chat_id: Int, contact: Contact)
  case class SessionCommand(m: Message)
}

class SessionManager(telegramApi: TelegramApi,
                     tinkoffApi: TinkoffApi) extends Actor with ActorLogging {
  import com.bankbot.SessionManager._
  implicit val logger: LoggingAdapter = log
  type UserId = Int

  var contacts: Map[UserId, Contact] = Map()
  var sessions: Map[UserId, ActorRef] = Map()

  def createUserSession(chat: Chat) =
    context.actorOf(UserSession.props(chat), chat.id.toString)

  override def receive: Receive = {

    case SessionCommand(message) => {
      if (!contacts.contains(message.from.get.id)) {
        contactRequest(message)
      } else {
        // - for now
        val send = Map("chat_id" -> message.chat.id.toString,
          "text" -> "Your balance is: 0", "parse_mode" -> "HTML")
        telegramApi.sendMessage(send)
      }
    }

    case PossibleContact(chat_id: Int, contact: Contact) => {
      contacts += (contact.user_id -> contact)
      val send = Map("chat_id" -> chat_id.toString,
        "text" -> prettyThx4Contact, "parse_mode" -> "HTML")
      telegramApi.sendMessage(send)
    }
  }

  def contactRequest(message: Message) = {
    val messageText = "This operation requires your phone number."
    val reply_markup = "{\"keyboard\":[[{\"text\":\"Send My Phone Number\", \"request_contact\": true}]], " +
      "\"resize_keyboard\": true, \"one_time_keyboard\": true}"
    val send = Map("chat_id" -> message.chat.id.toString, "text" -> messageText,
      "reply_markup" -> reply_markup)
    telegramApi.sendMessage(send)
  }
}
