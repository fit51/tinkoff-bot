package com.bankbot

import java.sql.Timestamp

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

import scala.util.{Failure, Success}
import scala.concurrent.duration._
import telegram.TelegramTypes.Message
import telegram.PrettyMessage
import tinkoff.TinkoffTypes.{ Rate, ServerAnswer }

/**
  * Actor used for actions that do not require authentication
  * Ex. get Exchange Rates
  */

object NoSessionActions {
  def props = Props(new NoSessionActions())

  case class GetRates(m: Message)
  case object UpdateRates
  case class Reply(m: Message, text: String)
}

class NoSessionActions extends Actor with ActorLogging with tinkoff.MessageMarshallingTinkoff {

  import NoSessionActions._
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  val telegramApi = new telegram.TelegramApi(Http(context.system), materializer, log, context.dispatcher)
  val tinkoffApi = new tinkoff.TinkoffApi(Http(context.system), materializer, log, context.dispatcher, self)
  val update_tick = context.system.scheduler.schedule(1 second, 2 second, self, UpdateRates)
  var rates: Vector[Rate] = Vector()
  var last_update = new Timestamp(0L)

  override def postStop() = {
    update_tick.cancel
  }

  override def receive: Receive = {

    case UpdateRates => tinkoffApi.getRates()

    case ServerAnswer(newLastUpdate, newRates) => {
      val date = new Timestamp(newLastUpdate)
      if(date.after(last_update)) {
        rates = newRates
        last_update = date
      }
    }

    case GetRates(message: Message) => {
          val send = Map("chat_id" -> message.chat.id.toString,
            "text" -> PrettyMessage.prettyRates(last_update, rates), "parse_mode" -> "HTML")
          telegramApi.sendMessage(send)
    }

    case Reply(message: Message, text) => {
      val send = Map("chat_id" -> message.chat.id.toString,
        "text" -> text, "parse_mode" -> "HTML")
      telegramApi.sendMessage(send)
    }
  }

}
