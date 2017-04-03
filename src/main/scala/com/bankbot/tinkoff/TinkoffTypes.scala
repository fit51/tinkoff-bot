package com.bankbot.tinkoff

import spray.json._

import scala.util.{Success, Try}

/**
  * Case classes used in Tinkoff Api
  */

object TinkoffTypes {

  case class ServerAnswer(last_update: Long, rates: Vector[Rate])

  case class Currency(code: Int, name: String)

  case class Rate(category: String, fromCurrency: Currency, toCurrency: Currency, buy: Float, sell: Float)

}

/**
  * Json Marshalling for case classes used in Tinkoff Api
  */

trait MessageMarshallingTinkoff extends DefaultJsonProtocol {

  import TinkoffTypes._

  implicit val currencyFormat = jsonFormat2(Currency)
  implicit val rateFormat = jsonFormat5(Rate)

  implicit object ServerAnswerJsonFormat extends RootJsonFormat[ServerAnswer] {

    def write(answer: ServerAnswer) =
      JsArray(answer.rates.map(_.toJson))

    def read(value: JsValue) = {
      def getPrepaidCardsOperations(rate: JsValue): Try[Rate] = Try {
        rate.convertTo[Rate]
      }
      value.asJsObject.getFields("resultCode", "payload") match {
        case Seq(JsString(resultCode), JsObject(payload)) => {
          val last_update = payload("lastUpdate").asJsObject("Rates expected").getFields("milliseconds") match {
            case Seq(JsNumber(last_update)) => last_update
            case _ => throw new DeserializationException("Rates expected")
          }
          payload("rates") match {
            case JsArray(rates) => {
              val serializedRates = for (rate: JsValue <- rates) yield getPrepaidCardsOperations(rate)
              new ServerAnswer(last_update.toLong, serializedRates collect { case Success(r) => r })
            }
            case _ => throw new DeserializationException("Rates expected")
          }
        }
      }
    }
  }
}
