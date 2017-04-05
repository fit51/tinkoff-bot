package com.bankbot

import scala.util.{Failure, Success}
import akka.actor.{Actor, ActorLogging, Props}
import com.bankbot.SessionManager.ContactRequest
import com.bankbot.telegram.TelegramApi
import com.bankbot.tinkoff.TinkoffApi
import telegram.TelegramTypes._
import telegram.TelegramApi

/**
  * Master actor
  * Creates and manages UserSessions
  */

object SessionManager {
  def props(telegramApi: TelegramApi, tinkoffApi: TinkoffApi) = Props(classOf[SessionManager], telegramApi, tinkoffApi)
  var contacts: Map[User, Contact] = Map()

  case class ContactRequest(m: Message)
}

class SessionManager(telegramApi: TelegramApi,
                     tinkoffApi: TinkoffApi) extends Actor with ActorLogging {

  def createUserSession(chat: Chat) =
    context.actorOf(UserSession.props(chat), chat.id.toString)

  override def receive: Receive = {
    case Message(message_id: Int, from: User, chat: Chat, date: Int, text: Option[String], contact: Option[Contact]) => {
      if (contact.nonEmpty) {
        // - User has already shared the contact
        if (SessionManager.contacts.contains(from)) {
          val send = Map("chat_id" -> chat.id.toString,
            "text" -> "Already in the contact list", "parse_mode" -> "HTML")
          telegramApi.sendMessage(send)
        } else {
        // - Otherwise
          SessionManager.contacts += (from -> contact)
          val send = Map("chat_id" -> chat.id.toString,
            "text" -> "Thanks for sharing your contact!", "parse_mode" -> "HTML")
          telegramApi.sendMessage(send)
        }
      }
    }

    case ContactRequest(message) => {
      val messageText = "This operation requires your phone number."
      val send = Map("chat_id" -> message.chat.id.toString, "text" -> messageText,
        "reply_markup" -> "{\"keyboard\":[[{\"text\":\"Send number\", \"request_contact\": true}]]}")
      telegramApi.sendMessage(send)
    }

    case Message(message_id: Int, from: User, chat: Chat, date: Int, text: Option[String], _) => {

    }
  }
}
