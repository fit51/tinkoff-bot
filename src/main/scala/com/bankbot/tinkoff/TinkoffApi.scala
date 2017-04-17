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
import java.time.Instant
import scala.concurrent.Future
import scala.util.{Failure, Success}

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

  def warmupCache(sessionid: String)(implicit context: ActorContext, logger: LoggingAdapter)

  def sessionStatus(sessionid: String)
                   (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef)

  def accountsFlat(sessionid: String)
                  (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Future[AccountsFlat]

  def operations(sessionid: String)
                (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Future[Operations]
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
      ("confirmationData", Confirmation(confirmationData).toJson.compactPrint)).toEntity(HttpCharsets.`UTF-8`)
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
        Unmarshal(entity).to[LevelUp]
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff levelUp Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(self)
  }

  def warmupCache(sessionid: String)(implicit context: ActorContext, logger: LoggingAdapter): Unit = {
    import context.dispatcher

    val params = Map("sessionid" -> sessionid, "origin" -> "web,ib5,platform")
    val uri = Uri(url + "/warmup_cache").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(HttpMethods.POST, uri))
    response map {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        Unmarshal(entity).to[WarmUp] map { w =>
          if(w.resultCode != "OK")
            throw ResponceCodeException("warmup_cache Result code: " + w.resultCode, entity)
        }
      }
      case HttpResponse(code, _, entity, _) => {
        throw ResponceCodeException("warmup_cache Responce code:", entity)
      }
    } onComplete {
      case Success(_) => logger.info("Tinkoff warmup_cache Request Success")
      case Failure(t) => logger.info("Tinkoff warmup_cache Request failed: " + t.getMessage)
    }
  }

  def sessionStatus(sessionid: String)
                  (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val params = Map("sessionid" -> sessionid, "origin" -> "web,ib5,platform")
    val uri = Uri(url + "/session_status").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(HttpMethods.POST, uri))
    (response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        Unmarshal(entity).to[SessionStatus]
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff session_status Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }).pipeTo(self)
  }

  def accountsFlat(sessionid: String)
                   (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Future[AccountsFlat] = {
    import context.dispatcher

    val params = Map("sessionid" -> sessionid)
    val uri = Uri(url + "/accounts_flat").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(uri = uri))
    response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        Unmarshal(entity).to[AccountsFlat]
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff session_status Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }
  }

  def operations(sessionid: String)
                (implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Future[Operations] = {
    import context.dispatcher

    val timestamp : Long = Instant.now.getEpochSecond
    val params = Map("sessionid" -> sessionid, "start" -> "0000000000000", "end" -> (timestamp.toString + "000"))
    val uri = Uri(url + "/operations").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(uri = uri))
    response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        Unmarshal(entity).to[Operations]
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Tinkoff session_status Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    }

  }

}
