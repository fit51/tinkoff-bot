package com.bankbot

import java.time.{Instant, ZoneId}

import akka.actor.{Actor, ActorLogging, Props, Scheduler}
import akka.event.LoggingAdapter
import com.bankbot.tinkoff.TinkoffApi

import scala.concurrent.duration._
import telegram.{PrettyMessage, TelegramApi}
import tinkoff.TinkoffTypes.{Rate, ServerAnswer}

/**
  * Actor used for actions that do not require authentication
  * Ex. get Exchange Rates
  */

object NoSessionActions {
  def props(scheduler: Scheduler, updateRatesInterval: Int, telegramApi: TelegramApi, tinkoffApi: TinkoffApi) =  Props(
    classOf[NoSessionActions], scheduler, updateRatesInterval, telegramApi, tinkoffApi
  )

  case object GetRates
  case class SendRates(chatId: Int)
  case object UpdateRates
  case class SendMessage(chatId: Int, text: String)
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

    case SendRates(chatId: Int) => {
          val send = Map("chat_id" -> chatId.toString,
            "text" -> PrettyMessage.prettyRates(
              last_update, rates, ZoneId.of("Europe/Moscow")
            ), "parse_mode" -> "HTML")
          telegramApi.sendMessage(send)
    }

    case SendMessage(chatId, text) => {
      val send = Map("chat_id" -> chatId.toString,
        "text" -> text, "parse_mode" -> "HTML")
      telegramApi.sendMessage(send)
    }
  }

}
