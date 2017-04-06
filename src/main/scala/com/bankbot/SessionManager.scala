package com.bankbot

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingAdapter
import com.bankbot.SessionManager.{ContactRequest, PossibleContact, SendBalance}
import com.bankbot.tinkoff.TinkoffApi
import telegram.TelegramTypes._
import telegram.TelegramApi

/**
  * Master actor
  * Creates and manages UserSessions
  */

object SessionManager {
  def props(telegramApi: TelegramApi, tinkoffApi: TinkoffApi) = Props(classOf[SessionManager], telegramApi, tinkoffApi)

  case class ContactRequest(m: Message)
  case class PossibleContact(m: Message)
  case class SendBalance(m: Message)
}

class SessionManager(telegramApi: TelegramApi,
                     tinkoffApi: TinkoffApi) extends Actor with ActorLogging {
  implicit val logger: LoggingAdapter = log
  var contacts: Map[Int, Contact] = Map()

  def createUserSession(chat: Chat) =
    context.actorOf(UserSession.props(chat), chat.id.toString)

  def getContacts = contacts

  override def receive: Receive = {
    case Message(message_id: Int, from: Option[User], chat: Chat, date: Int, text: Option[String], contact: Option[Contact]) => {
      if (contact.nonEmpty) {
        // - User has already shared the contact
        if (contacts.contains(from.get.id)) {
          val send = Map("chat_id" -> chat.id.toString,
            "text" -> "Already in the contact list", "parse_mode" -> "HTML")
          telegramApi.sendMessage(send)
        } else {
        // - Otherwise
          contacts += (from.get.id -> contact.get)
          val send = Map("chat_id" -> chat.id.toString,
            "text" -> "Thanks for sharing your contact!", "parse_mode" -> "HTML")
          telegramApi.sendMessage(send)
        }
      }
    }

    case SendBalance(message) => {
      if (!contacts.contains(message.from.get.id)) {
        val send = Map("chat_id" -> message.chat.id.toString,
          "text" -> "You have to share your contact first. Then request balance again!", "parse_mode" -> "HTML")
        telegramApi.sendMessage(send)
        self ! ContactRequest(message)
      } else {
        // - for now
        val send = Map("chat_id" -> message.chat.id.toString,
          "text" -> "Your balance is: 0", "parse_mode" -> "HTML")
        telegramApi.sendMessage(send)
      }
    }

    case PossibleContact(message) => {
      if (contacts.contains(message.from.get.id)) {
        val send = Map("chat_id" -> message.chat.id.toString,
          "text" -> "Already got your contact!", "parse_mode" -> "HTML")
        telegramApi.sendMessage(send)
      } else {
        self ! message
      }
    }

    case ContactRequest(message) => {
      if (!contacts.contains(message.from.get.id)) {
        val messageText = "This operation requires your phone number."
        val send = Map("chat_id" -> message.chat.id.toString, "text" -> messageText,
          "reply_markup" -> "{\"keyboard\":[[{\"text\":\"Send number\", \"request_contact\": true}]]}")
        telegramApi.sendMessage(send)
      } else {

      }
    }

    case Message(message_id: Int, from: Option[User], chat: Chat, date: Int, text: Option[String], _) => {

    }
  }
}
