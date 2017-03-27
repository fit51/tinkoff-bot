package com.bankbot.telegram

import java.sql.Timestamp
import com.bankbot.tinkoff.TinkoffTypes.Rate

/**
  * Object provides methods to Create Strings for Telegram Messages with HTML parse_mode
  */

object PrettyMessage {

  def prettyRates(last_update: Timestamp, rates: Vector[Rate]) = {
    if(rates.isEmpty)
      "Rates are not updated yet"
    else
    s"LastUpdate:\t${String.format("%1$TD %1$TT", last_update)}\n" +
    "<b>From</b>\t<b>To</b>\t<b>Sell</b>\t<b>Buy</b>\n" +
      (rates.map { rate =>
      rate.fromCurrency.name + "\t"+ rate.toCurrency.name + "\t" +rate.sell + "\t" + rate.buy + "\n"
      }).mkString("")
  }

}
