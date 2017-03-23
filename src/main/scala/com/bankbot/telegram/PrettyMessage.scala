package com.bankbot.telegram

import com.bankbot.tinkoff.TinkoffTypes.Rate

/**
  * Object provides methods to Create Strings for Telegram Messages with HTML parse_mode
  */

object PrettyMessage {

  def prettyRates(rates: Vector[Rate]) = {
    "<b>From</b>\t<b>To</b>\t<b>Sell</b>\t<b>Buy</b>\n" +
      (rates.map { rate =>
      rate.fromCurrency.name + "\t"+ rate.toCurrency.name + "\t" +rate.sell + "\t" + rate.buy + "\n"
      }).mkString("")
  }

}
