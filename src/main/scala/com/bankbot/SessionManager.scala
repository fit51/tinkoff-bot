package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.event.LoggingAdapter
import akka.routing.ConsistentHashingRouter.ConsistentHashable
import com.bankbot.UserSession.Reply
import com.bankbot.tinkoff.{TinkoffApi, TinkoffApiImpl}
import telegram.TelegramTypes._
import telegram.PrettyMessage.prettyThx4Contact
import telegram.{TelegramApi, TelegramApiImpl}

/**
  * Master actor
  * Creates and manages UserSessions
  */

object SessionManager {
  def props() =
    Props(classOf[SessionManager], None, None)

  trait WithChatSession extends ConsistentHashable {
    val chatId: Int
    override def consistentHashKey: Any = chatId
  }

  case class PossibleContact(override val chatId: Int, contact: Contact) extends WithChatSession
  case class PossibleReply( override val chatId: Int, messageId: Int, text: String) extends WithChatSession
  case class WaitForReply(chatId: Int, messageId: Int)
}

class SessionManager(mayBeTelegramApi: Option[TelegramApi],
                     mayBeTinkoffApi: Option[TinkoffApi]) extends Actor with ActorLogging {
  import com.bankbot.SessionManager._
  import UserSession.SessionCommand
  implicit val logger: LoggingAdapter = log
  implicit val system = context.system
  val telegramApi = mayBeTelegramApi.getOrElse(new TelegramApiImpl)
  val tinkoffApi = mayBeTinkoffApi.getOrElse(new TinkoffApiImpl)

  type UserId = Int
  type ChatId = Int
  type MessageId = Int

  log.info("SessionManager" +
    " actions created! " + context.self.path.toString)

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
      val message = TelegramMessage(chatId, prettyThx4Contact)
      telegramApi.sendMessage(message)
    }

    case Terminated(actor)=> {
      val remove = awatingReply.filter(pair => pair._1 == actor.path.name)
      awatingReply = awatingReply -- remove
    }

  }

  def createUserSession(name: String, command: SessionCommand): ActorRef = {
    context.actorOf(
      UserSession.props(
        command.chatId, contacts(command.from.id), command
      ),
      name
    )
  }

  def contactRequest(chatId: Int) = {
    val keyboardButton = KeyboardButton("Send My Phone Number", Some(true))
    val replyKeyboardMarkup = ReplyKeyboardMarkup(Vector(Vector(keyboardButton)), Some(true), Some(true))
    val message = TelegramMessage(chatId, "This operation requires your phone number.",
      reply_markup = Some(Right(replyKeyboardMarkup)))
    telegramApi.sendMessage(message)
  }
}
