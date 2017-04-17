package com.bankbot

import akka.actor.{Actor, ActorLogging, PoisonPill, Props, Scheduler}
import akka.event.LoggingAdapter
import com.bankbot.UserSession.SessionCommand
import com.bankbot.telegram.TelegramApi
import com.bankbot.tinkoff.TinkoffApi
import com.bankbot.telegram.TelegramTypes._
import com.bankbot.tinkoff.TinkoffTypes._
import com.bankbot.SessionManager.WaitForReply
import telegram.PrettyMessage._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Actor that authenticates and handles commands for single user
  * Ex. /balance, /history
  */

object UserSession {
  def props(chatId: Int, contact: Contact, initialCommand: SessionCommand, telegramApi: TelegramApi,
            tinkoffApi: TinkoffApi, scheduler: Scheduler) = Props(
    classOf[UserSession], chatId, contact, initialCommand, telegramApi, tinkoffApi, scheduler
  )
  abstract class SessionCommand {
    val from: User
    val chatId: Int
  }
  case class BalanceCommand(override val from: User, override val chatId: Int) extends SessionCommand
  case class HistoryCommand(override val from: User, override val chatId: Int) extends SessionCommand
  case class Reply(messageId: Int, text: String)
  case object WarmUpSession
  case object GetAccessLevel
}

class UserSession(chatId: Int, contact: Contact, var initialCommand: SessionCommand, telegramApi: TelegramApi,
                  tinkoffApi: TinkoffApi, scheduler: Scheduler) extends Actor with ActorLogging {
  import UserSession._
  import context.dispatcher
  implicit val logger: LoggingAdapter = log

  var session: String = ""
  var operationTicket = ""
  var accessLevel = "REGISTERING"
  var poisonTick = scheduler.scheduleOnce(5 minutes, self, PoisonPill)

  override def preStart() = {
    tinkoffApi.getSession()
  }

  override def postStop() = {
    if(accessLevel == "REGISTERED") {
      log.info(s"User session $session is Over!")
      informUser("Your session is Over")
    }
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
      restartPoisonTick
      if(smsId forall(_.isDigit)) {
        tinkoffApi.confirm(session, operationTicket, smsId)
      }
      else
        requestSMSId("SMS Code must contain only Digits. Try again:")
    }
    case Confirm("OK", Some(payload)) => {
      session = payload.sessionid
      tinkoffApi.levelUp(session)
    }
    case Confirm(resultCode, _) => resultCode match {
      case "CONFIRMATION_FAILED" => requestSMSId("SMS confirmation code is wrong. Try again:")
      case _ => informUser("Internal Error.\n Try again Later")
    }

    case LevelUp(resultCode, AccessLevel(level)) => (resultCode, level) match {
      case ("OK", "REGISTERED") => {
        accessLevel = level
        log.info(s"User with session $session registered!")
        context.become(registered)
        self ! initialCommand
        self ! WarmUpSession
      }
      case _ => informUser("Internal Error.\n Try again Later")
    }

    case GetAccessLevel => sender ! "REGISTERING"
  }

  def registered: Receive = {
    case m: BalanceCommand => {
      restartPoisonTick
      val showBalance = (ac: AccountsFlat) => ac match {
        case AccountsFlat(_, Some(payload)) => {
          payload.map( ac => {
            prettyBalance(ac.name, ac.accountType, ac.moneyAmount)
          }).mkString("\n")
        }
        case _ => "Your have no balance"
      }
      processAccounts(showBalance)
    }
    case m: HistoryCommand => {
      restartPoisonTick
      val showHistory = (op: Operations) => op match {
        case Operations(_, Some(payload)) => {
          payload.take(10).map(op => {
            prettyOperation(op.description, op.debitingTime, op.amount, op.spendingCategory)
          }).mkString("\n")
        }
        case _ => {
          log.info(op.toString)
          "Your have no history"
        }
      }
      processOperations(showHistory)
    }
    case SessionStatus("OK", Some(payload), _) => {
      log.info(s"SessionStatus OK")
      val nextWarmUp = payload.millisLeft/2
      scheduler.scheduleOnce(nextWarmUp millis, self, WarmUpSession)
    }
    case SessionStatus(resultCode, _, Some(errorMessage)) => {
      log.info(s"$session : $errorMessage")
      self ! PoisonPill
    }
    case WarmUpSession => {
      log.info("WarmUp!")
      tinkoffApi.warmupCache(session)
      tinkoffApi.sessionStatus(session)
    }
    case GetAccessLevel => sender ! "REGISTERED"
  }

  def processAccounts(f: (AccountsFlat) => String) = {
    tinkoffApi.accountsFlat(session).onComplete {
      case Success(acFlat) => informUser(f(acFlat))
      case Failure(t) => {
        val send = Map("chat_id" -> chatId.toString,
          "text" -> "You have no Accounts", "parse_mode" -> "HTML")
        telegramApi.sendMessage(send)
        logger.info("Getting Accounts Flat failed!: " + t.getMessage)
      }
    }
  }

  def processOperations(g: (Operations) => String) = {
    tinkoffApi.operations(session).onComplete {
      case Success(opers) => informUser(g(opers))
      case Failure(t) => {
        val send = Map("chat_id" -> chatId.toString,
          "text" -> "You have no Operations in the last month!", "parse_mode" -> "HTML")
        telegramApi.sendMessage(send)
        logger.info("Getting Operations failed!: " + t.getMessage)
      }
    }
  }

  def restartPoisonTick() = {
    poisonTick.cancel()
    poisonTick = scheduler.scheduleOnce(5 minutes, self, PoisonPill)
  }

  def informUser(text: String) = {
    val send = Map("chat_id" -> chatId.toString,
      "text" -> text, "parse_mode" -> "HTML")
    telegramApi.sendMessage(send)
  }

  def requestSMSId(text: String) = {
    val messageText = text
    val force_reply = """{"force_reply": true}"""
    val send = Map("chat_id" -> chatId.toString, "text" -> messageText,
      "reply_markup" -> force_reply)
    telegramApi.sendReplyMessage(send) onSuccess {
      case ServerAnswerReply(_, m) => {
        context.parent ! WaitForReply(m.chat.id, m.message_id)
      }
    }
  }

}
