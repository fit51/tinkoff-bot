package com.bankbot.tinkoff

import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import TinkoffTypes._
import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.http.scaladsl.model.Uri.Query
import com.bankbot.CommonTypes._
import spray.json._

/**
  * Class that handles httpRequests to Tinkoff Api
  * @constructor creates a new Api class for Actor
  */


trait TinkoffApi {
  def getRates()(implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef)

  def getSession()(implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef)

  def signUp(sessionid: String, phone: String)
            (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef)

  def confirm(sessionid: String, initialOperationTicket: String, confirmationData: String)
             (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef)

  def levelUp(sessionid: String)
             (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef)
}

class TinkoffApiImpl(implicit system: ActorSystem)
  extends TinkoffApi with MessageMarshallingTinkoff {

  final val url = "https://www.tinkoff.ru/api/v1/"
  lazy val http = Http(system)
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system))

  def getRates()(implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val uri = Uri(url + "/currency_rates")
    val response = http.singleRequest(HttpRequest(uri = uri))
    (response flatMap {
      case HttpResponse(StatusCodes.OK, headers, entity, _) => {
        logger.debug("Tinkoff getRates Request Success")
        Unmarshal(entity).to[ServerAnswer] map { a =>
          ServerAnswer(a.last_update, a.rates.filter(_.category equalsIgnoreCase ("PrepaidCardsOperations")))
        }
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff getRates Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(self)
  }

  def getSession()(implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val params = Map("origin" -> "web,ib5,platform")
    val uri = Uri(url + "/session").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(uri = uri))
    (response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.debug("Tinkoff getSession Request Success")
        Unmarshal(entity).to[Session] map { s =>
          if(s.resultCode != "OK")
            throw ResponceCodeException("get Session Result code: " + s.resultCode, entity)
          else
            s
        }
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff get Session Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(self)
  }

  def signUp(sessionid: String, phone: String)
            (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val params = Map("sessionid" -> sessionid, "origin" -> "web,ib5,platform")
    val uri = Uri(url + "/sign_up").withQuery(Query(params))
    val form = FormData(("phone", phone)).toEntity
    val response = http.singleRequest(HttpRequest(HttpMethods.POST, uri, entity = form))
    (response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.debug("Tinkoff signUp Request Success")
        Unmarshal(entity).to[SignUp] map { s =>
          if(s.resultCode != "WAITING_CONFIRMATION" || s.confirmations.head != "SMSBYID")
            throw ResponceCodeException("signUp Result code: " + s.resultCode, entity)
          else
            s
        }
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff signUp Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(self)
  }

  def confirm(sessionid: String, initialOperationTicket: String, confirmationData: String)
            (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val params = Map("sessionid" -> sessionid, "origin" -> "web,ib5,platform")
    val uri = Uri(url + "/confirm").withQuery(Query(params))
    val form = FormData(
      ("initialOperationTicket", initialOperationTicket),
      ("initialOperation", "sign_up"),
      ("confirmationData", Confirmation(confirmationData).toJson.toString())).toEntity(HttpCharsets.`UTF-8`)
    val response = http.singleRequest(HttpRequest(HttpMethods.POST, uri, entity = form))
    (response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.debug("Tinkoff confirm Request Success")
        Unmarshal(entity).to[Confirm]      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff confirm Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(self)
  }

  def levelUp(sessionid: String)
  (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val params = Map("sessionid" -> sessionid, "origin" -> "web,ib5,platform")
    val uri = Uri(url + "/level_up").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(HttpMethods.POST, uri))
    (response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.debug("Tinkoff levelUp Request Success")
        Unmarshal(entity).to[LevelUp] map { s =>
          if(s.resultCode != "OK")
            throw ResponceCodeException("levelUp Result code: " + s.resultCode, entity)
          else
            s.payload
        }
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff levelUp Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(self)
  }

  def warmup_cache(sessionid: String)
             (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val params = Map("warmup_cache" -> sessionid, "origin" -> "web,ib5,platform")
    val uri = Uri(url + "/level_up").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(HttpMethods.POST, uri))
    (response map {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.debug("Tinkoff warmup_cache Request Success")
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff warmup_cache Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(self)
  }

}
