package com.bankbot.telegram

import akka.actor.{ActorContext, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.bankbot.CommonTypes._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Class that handles httpRequests to Telegram Api
  * @constructor creates a new Api class for Actor
  */
trait TelegramApi extends TelegramKey {
  def getUpdates(offset: Int): Future[HttpResponse]
  def sendMessage(params: Map[String, String])(implicit context: ActorContext, logger: LoggingAdapter): Unit
}

class TelegramApiImpl(implicit system: ActorSystem)
  extends TelegramApi with MessageMarshallingTelegram {

  final val url = "https://api.telegram.org/bot" + token
  lazy val http = Http(system)
  implicit val materializer = ActorMaterializer()

  def getUpdates(offset: Int): Future[HttpResponse] = {
    val params = Map("offset" -> offset.toString)
    val uri = Uri(url + "/getUpdates").withQuery(Query(params))
    http.singleRequest(HttpRequest(uri = uri))(materializer)
  }

  @throws(classOf[IllegalArgumentException])
  def sendMessage(params: Map[String, String])(implicit context: ActorContext, logger: LoggingAdapter): Unit = {
    import context.dispatcher
    require(params.keySet("chat_id") && params.keySet("text"))

    val uri = Uri(url + "/sendMessage").withQuery(Query(params))
    val response = http.singleRequest(HttpRequest(uri = uri))(materializer)
    response map {
      case HttpResponse(StatusCodes.OK, _, entity, _) => {
        logger.info("Telegram sendMessage Request Success")
        entity
      }
      case HttpResponse(code, _, entity, _) => {
        logger.info("Telegram sendMessage Request failed, response code: " + code)
        throw ResponceCodeException("Tinkoff Responce code:", entity)
      }
    } onComplete {
      case Success(_) => logger.info("Reply successfully send to " + params("chat_id"))
      case Failure(t) => logger.info("Reply send failed: " + t.getMessage + " to " + params("chat_id"))
    }
  }

}
