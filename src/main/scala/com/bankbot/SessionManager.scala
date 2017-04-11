package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.event.LoggingAdapter
import com.bankbot.UserSession.Reply
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
  case class PossibleReply(chatId: Int, messageId: Int, text: String)
  case class WaitForReply(chatId: Int, messageId: Int)
}

class SessionManager(telegramApi: TelegramApi,
                     tinkoffApi: TinkoffApi) extends Actor with ActorLogging {
  import com.bankbot.SessionManager._
  import UserSession.SessionCommand
  implicit val logger: LoggingAdapter = log
  type UserId = Int
  type ChatId = Int
  type MessageId = Int

  private var contacts: Map[UserId, Contact] = Map()
  private var awatingReply: Set[(ChatId, MessageId)] = Set()

  override def receive: Receive = {

    case command: SessionCommand => {
      if (!contacts.contains(command.from.id)) {
        contactRequest(command.chatId)
      } else {
        val name = command.chatId.toString
        def create() = {
          val userSession = createUserSession(name, command)
          userSession forward command
        }
        context.child(name). fold(create)(_ forward command)
      }
    }

    case PossibleReply(chatId, messageId, text) => {
      if (awatingReply((chatId, messageId))) {
        context.child(chatId.toString) foreach { _ ! Reply(messageId, text) }
        awatingReply -= ((chatId, messageId))
      }
    }

    case WaitForReply(chatId, messageId) => {
      awatingReply += ((chatId, messageId))
    }

    case PossibleContact(chatId: Int, contact: Contact) => {
      contacts += (contact.user_id -> contact)
      val send = Map("chat_id" -> chatId.toString,
        "text" -> prettyThx4Contact, "parse_mode" -> "HTML")
      telegramApi.sendMessage(send)
    }

  }

  def createUserSession(name: String, command: SessionCommand): ActorRef = {
    context.actorOf(
      UserSession.props(
        command.chatId, contacts(command.from.id), command, telegramApi, tinkoffApi, context.system.scheduler
      ),
      name
    )
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
