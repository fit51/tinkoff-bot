package com.bankbot

import java.time.{Instant, ZoneId}

import akka.actor.{Actor, ActorLogging, Props, Scheduler}
import akka.event.LoggingAdapter
import com.bankbot.telegram.TelegramTypes.TelegramMessage
import com.bankbot.tinkoff.{TinkoffApi, TinkoffApiImpl}

import scala.concurrent.duration._
import telegram.{PrettyMessage, TelegramApi, TelegramApiImpl}
import tinkoff.TinkoffTypes.{Rate, ServerAnswer}

/**
  * Actor used for actions that do not require authentication
  * Ex. get Exchange Rates
  */

object NoSessionActions {
  def props(updateRatesInterval: Int) =
    Props(
    classOf[NoSessionActions], None, updateRatesInterval, None, None
  )

  case object GetRates
  case class SendRates(chatId: Int)
  case object UpdateRates
  case class SendMessage(chatId: Int, text: String)
}

class NoSessionActions(mayBeScheduler: Option[Scheduler], updateRatesInterval: Int, mayBeTelegramApi: Option[TelegramApi],
                       mayBeTinkoffApi: Option[TinkoffApi]) extends Actor with ActorLogging {
  import NoSessionActions._
  import context.dispatcher
  implicit val logger: LoggingAdapter = log
  implicit val system = context.system
  val scheduler = mayBeScheduler.getOrElse(context.system.scheduler)
  val telegramApi = mayBeTelegramApi.getOrElse(new TelegramApiImpl)
  val tinkoffApi = mayBeTinkoffApi.getOrElse(new TinkoffApiImpl)

  log.info("NoSession actions created! " + context.self.path.toString)

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
      val message = TelegramMessage(chatId, PrettyMessage.prettyRates(
        last_update, rates, ZoneId.of("Europe/Moscow")
      ))
      telegramApi.sendMessage(message)
    }

    case SendMessage(chatId, text) => {
      val message = TelegramMessage(chatId, text)
      telegramApi.sendMessage(message)
    }
  }

}
