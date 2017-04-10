package com.bankbot

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.event.LoggingAdapter
import com.bankbot.UserSession.SessionCommand
import com.bankbot.telegram.TelegramApi
import com.bankbot.tinkoff.TinkoffApi
import com.bankbot.telegram.TelegramTypes._
import com.bankbot.tinkoff.TinkoffTypes._
import com.bankbot.SessionManager.WaitForReply

/**
  * Actor that authenticates and handles commands for single user
  * Ex. /balance, /history
  */

object UserSession {
  def props(chatId: Int, contact: Contact, initialCommand: SessionCommand, telegramApi: TelegramApi,
            tinkoffApi: TinkoffApi) = Props(
    classOf[UserSession], chatId, contact, initialCommand, telegramApi, tinkoffApi
  )
  abstract class SessionCommand {
    val from: User
    val chatId: Int
  }
  case class BalanceCommand(override val from: User, override val chatId: Int) extends SessionCommand
  case class HistoryCommand(override val from: User, override val chatId: Int) extends SessionCommand
  case class Reply(messageId: Int, text: String)
}

class UserSession(chatId: Int, contact: Contact, var initialCommand: SessionCommand,
                  telegramApi: TelegramApi, tinkoffApi: TinkoffApi) extends Actor with ActorLogging {
  import UserSession._
  import context.dispatcher
  implicit val logger: LoggingAdapter = log

  var session: String = ""
  var operationTicket = ""

  override def preStart() = {
    tinkoffApi.getSession()
  }

  override def receive: Receive = registering

  def registering: Receive = {
    case command: SessionCommand => {
      initialCommand = command
    }
    case Session(_, payload) => {
      session = payload
      tinkoffApi.signUp(session, "+" + contact.phone_number)
    }
    case SignUp(_, opTicket, _) => {
      operationTicket = opTicket
      requestSMSId("Enter SMS Confirmation Code")
    }
    case Reply(messageId, smsId) => {
      if(smsId forall(_.isDigit))
        tinkoffApi.confirm(session, operationTicket, smsId)
      else
        requestSMSId("SMS Code must contain only Digits. Try again:")
    }
    case Confirm("OK", Some(userId), Some(payload)) => {
      session = payload.sessionid
      tinkoffApi.levelUp(session)
    }
    case Confirm(resultCode, _, _) => resultCode match {
      case "CONFIRMATION_FAILED" => requestSMSId("SMS confirmation code is wrong. Try again:")
      case _ => internalError("Internal Error.\n Try again Later")
    }

    case AccessLevel(level) => level match {
      case "REGISTERED" => {
        context.become(registered)
        self ! initialCommand
      }
      case _ => internalError("Internal Error.\n Try again Later")
    }
  }

  def registered: Receive = {
    case BalanceCommand => {
      val send = Map("chat_id" -> chatId.toString,
        "text" -> "Your balance is: 0", "parse_mode" -> "HTML")
      telegramApi.sendMessage(send)
    }
    case HistoryCommand => {
      val send = Map("chat_id" -> chatId.toString,
        "text" -> "Your have no history", "parse_mode" -> "HTML")
      telegramApi.sendMessage(send)
    }
  }

  def internalError(text: String) = {
    val send = Map("chat_id" -> chatId.toString,
      "text" -> text, "parse_mode" -> "HTML")
    telegramApi.sendMessage(send)
    self ! PoisonPill
  }

  def requestSMSId(text: String) = {
    val messageText = text
    val force_reply = """{"force_reply": true}"""
    val send = Map("chat_id" -> chatId.toString, "text" -> messageText,
      "reply_markup" -> force_reply)
    telegramApi.sendReplyMessage(send).onSuccess {
      case m => context.parent ! WaitForReply(m.chat.id, m.message_id)
    }
  }

}
