package com.bankbot.telegram

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.bankbot.CommonTypes._
import com.bankbot.telegram.TelegramTypes.{ServerAnswer, ServerAnswerReply, TelegramMessage}
import spray.json._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Class that handles httpRequests to Telegram Api
  * @constructor creates a new Api class for Actor
  */
trait TelegramApi extends TelegramKey {
  def getUpdates(offset: Int)(implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef)
  def sendMessage(message: TelegramMessage)(implicit context: ActorContext, logger: LoggingAdapter)
  def sendReplyMessage(message: TelegramMessage)
                      (
                        implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef
                      ): Future[ServerAnswerReply]
}

class TelegramApiImpl(implicit system: ActorSystem) extends TelegramApi with MessageMarshallingTelegram {

  final val url = "https://api.telegram.org/bot" + token
  lazy val http = Http(system)
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system))

  def getUpdates(offset: Int)(implicit context: ActorContext, logger: LoggingAdapter, self: ActorRef): Unit = {
    import akka.pattern.pipe
    import context.dispatcher

    val params = Map("offset" -> offset.toString)
    val uri = Uri(url + "/getUpdates").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(uri = uri))
    (response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.debug("Telegram getUpdates Request Success")
        Unmarshal(entity).to[ServerAnswer]
      }
      case HttpResponse(code, _, entity, _) => {
        logger.warning("Telegram getUpdates Request failed, response code: " + code)
        throw ResponceCodeException("Telegram getUpdates Responce code:", entity)
      }
    } recover {
      case _ => ServerAnswer(false, Array())
    }).pipeTo(self)
  }

  def sendMessage(message: TelegramMessage)(implicit context: ActorContext, logger: LoggingAdapter): Unit = {
    import context.dispatcher

    val jsonEntity = HttpEntity(ContentType(MediaTypes.`application/json`), message.toJson.compactPrint)
    val uri = Uri(url + "/sendMessage")
    val response = http.singleRequest(HttpRequest(HttpMethods.POST, uri, entity = jsonEntity))
    response map {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.info("Telegram sendMessage Request Success")
        entity
      }
      case HttpResponse(code, _, entity, _) => {
        logger.info("Telegram sendMessage Request failed, response code: " + code)
        throw ResponceCodeException("Telegram sendMessage Responce code:", entity)
      }
    } onComplete {
      case Success(_) => logger.info("Message successfully send to " + message.chat_id)
      case Failure(t) => logger.info("Message send failed: " + t.getMessage + " to " + message.chat_id)
    }
  }

  def sendReplyMessage(message: TelegramMessage)
                       (implicit context: ActorContext,
                        logger: LoggingAdapter, self: ActorRef): Future[ServerAnswerReply] = {
    import context.dispatcher
    val jsonEntity = HttpEntity(ContentType(MediaTypes.`application/json`), message.toJson.compactPrint)
    val uri = Uri(url + "/sendMessage")
    val response = http.singleRequest(HttpRequest(HttpMethods.POST, uri, entity = jsonEntity))
    response flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.info("Telegram sendMessage Request Success")
        Unmarshal(entity).to[ServerAnswerReply]
      }
      case HttpResponse(code, _, entity, _) => {
        logger.info("Telegram sendMessage Request failed, response code: " + code)
        throw ResponceCodeException("Telegram sendMessage Responce code:", entity)
      }
    }
  }

}
