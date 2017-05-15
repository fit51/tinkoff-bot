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

  case class Session(resultCode: String, payload: String)

  case class SignUp(resultCode: String, operationTicket: String, confirmations: Vector[String])

  case class AdditionalAuth(needLogin: Boolean, needPassword: Boolean, needRegister: Boolean)

  case class ConfirmPayLoad(accessLevel: String, sessionid: String, sessionTimeout: Int, additionalAuth: AdditionalAuth,
                            userId: String)

  case class Confirm(resultCode: String, payload: Either[ConfirmPayLoad, Confirmation])

  case class AccessLevel(accessLevel: String)

  case class LevelUp(resultCode: String, payload: AccessLevel)

  case class Confirmation(SMSBYID: String)

  case class WarmUp(resultCode: String)

  case class SessionStatusPayLoad(accessLevel: String, millisLeft: Int, userId: String, additionalAuth: AdditionalAuth)

  case class SessionStatus(resultCode: String, payload: Option[SessionStatusPayLoad], errorMessage: Option[String])

  case class Amount(currency: Currency, value: Int)

  case class Account(id: String, name: String, moneyAmount: Amount, accountType: String)

  case class AccountsFlat(resultCode: String, payload: Option[Vector[Account]])

  case class TimeStamp(milliseconds: Long)

  case class Payment(paymentId: String, paymentType: String, providerId: String, cardNumber: String)

  case class Operation(description: String, operationTime: TimeStamp, amount: Amount,
                       payment: Payment, account: String)

  case class Operations(resultCode: String, payload: Option[Vector[Operation]])


}

/**
  * Json Marshalling for case classes used in Tinkoff Api
  */

trait MessageMarshallingTinkoff extends DefaultJsonProtocol {

  import TinkoffTypes._

  implicit val currencyFormat = jsonFormat2(Currency)
  implicit val rateFormat = jsonFormat5(Rate)
  implicit val sessionFormat = jsonFormat2(Session)
  implicit val signUpFormat = jsonFormat3(SignUp)
  implicit val additionalAuthFormat = jsonFormat3(AdditionalAuth)
  implicit val confirmPayLoadFormat = jsonFormat5(ConfirmPayLoad)
  implicit val confirmationFormat = jsonFormat1(Confirmation)
  implicit val confirmFormat = jsonFormat2(Confirm)
  implicit val accessLevelFormat = jsonFormat1(AccessLevel)
  implicit val levelUpFormat = jsonFormat2(LevelUp)
  implicit val warmUpFormat = jsonFormat1(WarmUp)
  implicit val sessionStatusPayLoadFormat = jsonFormat4(SessionStatusPayLoad)
  implicit val sessionStatusFormat = jsonFormat3(SessionStatus)
  implicit val amountFormat = jsonFormat2(Amount)
  implicit val accountFormat = jsonFormat4(Account)
  implicit val accountsFlatFormat = jsonFormat2(AccountsFlat)
  implicit val paymentFormat = jsonFormat4(Payment)
  implicit val timeStampFormat = jsonFormat1(TimeStamp)
  implicit val operationFormat = jsonFormat5(Operation)
  implicit val operationsFormat = jsonFormat2(Operations)

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
