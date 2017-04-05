package com.bankbot

import java.time.{Instant, ZoneId}

import akka.actor.{Actor, ActorLogging, Props, Scheduler}
import akka.event.LoggingAdapter
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.bankbot.tinkoff.TinkoffApi

import scala.concurrent.duration._
import telegram.TelegramTypes.Message
import telegram.{PrettyMessage, TelegramApi}
import tinkoff.TinkoffTypes.{Rate, ServerAnswer}

/**
  * Actor used for actions that do not require authentication
  * Ex. get Exchange Rates
  */

object NoSessionActions {
  def props(scheduler: Scheduler, updateRatesInterval: Int, telegramApi: TelegramApi, tinkoffApi: TinkoffApi) =  Props(
    classOf[NoSessionActions], scheduler: Scheduler, updateRatesInterval, telegramApi, tinkoffApi
  )

  case object GetRates
  case class SendRates(m: Message)
  case object UpdateRates
  case class Reply(m: Message, text: String)
}

class NoSessionActions(scheduler: Scheduler, updateRatesInterval: Int, telegramApi: TelegramApi,
                       tinkoffApi: TinkoffApi) extends Actor with ActorLogging {
  import NoSessionActions._
  import context.dispatcher
  implicit val logger: LoggingAdapter = log

  val updateCancellable = scheduler.schedule(1 second, updateRatesInterval millis, self, UpdateRates)
  var rates: Vector[Rate] = Vector()
  var last_update = Instant.ofEpochMilli(0L)

  override def postStop() = {
    updateCancellable.cancel
  }

  override def receive: Receive = {

    case UpdateRates => tinkoffApi.getRates()

    case GetRates => sender ! rates

    case ServerAnswer(newLastUpdate, newRates) => {
      val date = Instant.ofEpochMilli(newLastUpdate)
      if(date.isAfter(last_update)) {
        rates = newRates
        last_update = date
      }
    }

    case SendRates(message: Message) => {
          val send = Map("chat_id" -> message.chat.id.toString,
            "text" -> PrettyMessage.prettyRates(
              last_update, rates, ZoneId.of("Europe/Moscow")
            ), "parse_mode" -> "HTML")
          telegramApi.sendMessage(send)
    }

    case Reply(message: Message, text) => {
      val send = Map("chat_id" -> message.chat.id.toString,
        "text" -> text, "parse_mode" -> "HTML")
      telegramApi.sendMessage(send)
    }
  }

}
