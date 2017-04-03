package com.bankbot.telegram

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import com.bankbot.tinkoff.TinkoffTypes.Rate

/**
  * Object provides methods to Create Strings for Telegram Messages with HTML parse_mode
  */

object PrettyMessage {

  val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  def prettyRates(last_update: Instant, rates: Vector[Rate], zoneId: ZoneId) = {
    if(rates.isEmpty)
      "Rates are not updated yet"
    else
    s"LastUpdate:\t${last_update.atZone(zoneId).format(format)}\n" +
    "<b>From</b>\t<b>To</b>\t<b>Sell</b>\t<b>Buy</b>\n" +
      (rates.map { rate =>
      rate.fromCurrency.name + "\t"+ rate.toCurrency.name + "\t" +rate.sell + "\t" + rate.buy + "\n"
      }).mkString("")
  }

}
