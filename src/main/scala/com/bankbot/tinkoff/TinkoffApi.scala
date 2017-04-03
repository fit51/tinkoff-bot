package com.bankbot.tinkoff

import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer

import TinkoffTypes._
import akka.actor.{ActorContext, ActorRef, ActorSystem}
import com.bankbot.CommonTypes._

/**
  * Class that handles httpRequests to Tinkoff Api
  * @constructor creates a new Api class for Actor
  */


trait TinkoffApi {
  def getRates()(implicit materializer: ActorMaterializer, context: ActorContext, logger: LoggingAdapter)
}

class TinkoffApiImpl(processingActor: => ActorRef)(implicit system: ActorSystem)
  extends TinkoffApi with MessageMarshallingTinkoff {

  final val url = "https://www.tinkoff.ru/api/v1/"
  lazy val http = Http(system)

  def getRates()(implicit materializer: ActorMaterializer, context: ActorContext, logger: LoggingAdapter): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val uri = Uri(url + "/currency_rates")
    val response = http.singleRequest(HttpRequest(uri = uri))(materializer)
    (response flatMap {
      case HttpResponse(StatusCodes.OK, headers, entity, _) => {
        logger.info("Tinkoff getRates Request Success")
        Unmarshal(entity).to[ServerAnswer] map { a =>
          ServerAnswer(a.last_update, a.rates.filter(_.category equalsIgnoreCase ("PrepaidCardsOperations")))
        }
      }
      case HttpResponse(code, _, entity, _) => {
        logger.info("Tinkoff getRates Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(processingActor)
  }


}
