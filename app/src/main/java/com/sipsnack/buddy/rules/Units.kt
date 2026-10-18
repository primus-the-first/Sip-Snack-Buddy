package com.sipsnack.buddy.rules

/**
 * Amounts are stored in cups (so logs from earlier versions stay valid) and shown in millilitres.
 * One cup is 250 ml, which makes the 8-cup goal a round 2,000 ml.
 */
object Units {
    const val ML_PER_CUP = 250

    fun cups(ml: Int): Double = ml.toDouble() / ML_PER_CUP

    fun mlValue(cups: Double): Int = Math.round(cups * ML_PER_CUP).toInt()

    fun ml(cups: Double): String = "%,d ml".format(java.util.Locale.US, mlValue(cups))
}
