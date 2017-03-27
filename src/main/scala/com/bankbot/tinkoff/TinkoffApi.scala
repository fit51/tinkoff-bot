package com.bankbot.tinkoff

import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}
import TinkoffTypes._
import akka.actor.ActorRef
import com.bankbot.CommonTypes._

/**
  * Class that handles httpRequests to Tinkoff Api
  * @constructor creates a new Api class for Actor
  */


class TinkoffApi(http: HttpExt, materializer: ActorMaterializer, log: LoggingAdapter,
                 dispatcher: ExecutionContextExecutor, self: ActorRef) extends MessageMarshallingTinkoff {
  implicit val ec = dispatcher
  final implicit val mat = materializer
  import akka.pattern.pipe

  val url = "https://www.tinkoff.ru/api/v1/"

  def getRates(): Unit = {
    val uri = Uri(url + "/currency_rates")
    val response = http.singleRequest(HttpRequest(uri = uri))(materializer)
    response flatMap {
      case HttpResponse(StatusCodes.OK, headers, entity, _) => {
        log.info("Tinkoff getRates Request Success")
        Unmarshal(entity).to[ServerAnswer] map { a =>
          ServerAnswer(a.last_update, a.rates.filter(_.category equalsIgnoreCase ("PrepaidCardsOperations")))
        }
      }
      case HttpResponse(code, _, entity, _) => {
        log.info("Tinkoff getRates Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    } pipeTo(self)
  }


}
