package com.bankbot

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

import scala.util.{Failure, Success}
import telegram.TelegramTypes.{KeyboardButton, Message}
import telegram.PrettyMessage

/**
  * Actor used for actions that do not require authentication
  * Ex. get Exchange Rates
  */

object NoSessionActions {
  def props = Props(new NoSessionActions())

  case class Rates(m: Message)
  case class Contact(m: Message)
  case class Reply(m: Message, text: String)
}

class NoSessionActions extends Actor with ActorLogging with tinkoff.MessageMarshallingTinkoff {

  import NoSessionActions._
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  val telegramApi = new telegram.TelegramApi(Http(context.system), materializer, log, context.dispatcher)
  val tinkoffApi = new tinkoff.TinkoffApi(Http(context.system), materializer, log, context.dispatcher)

  override def receive: Receive = {
    case Rates(message: Message) => {
      tinkoffApi.getRates map { v =>
        PrettyMessage.prettyRates(v)
      } recover {
        case t: Exception => {
          log.info("Error getRates:" + t.getMessage)
          "Something went wrong"
        }
      } onSuccess {
        case s: String => {
          val send = Map("chat_id" -> message.chat.id.toString,
            "text" -> s, "parse_mode" -> "HTML")
          telegramApi.sendMessage(send) onComplete {
            case Success(_) => log.info("Rates successfully send to " + message.from)
            case Failure(t) => log.info("Rates send failed: " + t.getMessage + " to " + message.from)
          }
        }
      }
    }
    case Contact(message: Message) => {
      val messageText = "This operation requires your phone number."
      // отправляем юзеру маркап кнопки
      val send = Map("chat_id" -> message.chat.id.toString, "text" -> messageText,
        "reply_markup" -> "{\"keyboard\":[[{\"text\":\"Send number\", \"request_contact\": true}]]}")
      telegramApi.sendMessage(send) onComplete {
        case Success(_) => log.info("Contact button successfully send to " + message.from)
        case Failure(t) => log.info("Contact button send failed: " + t.getMessage + " to " + message.from)
      }
    }
    case Reply(message: Message, text) => {
      val send = Map("chat_id" -> message.chat.id.toString,
        "text" -> text, "parse_mode" -> "HTML")
      telegramApi.sendMessage(send) onComplete {
        case Success(_) => log.info("Reply successfully send to " + message.from)
        case Failure(t) => log.info("Reply send failed: " + t.getMessage + " to " + message.from)
      }
    }
  }
}
