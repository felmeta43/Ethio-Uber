package com.ethiouber.driver.data.model

object TransactionType {
    const val DELIVERY_PAYMENT = "DELIVERY_PAYMENT"
    const val WITHDRAWAL = "WITHDRAWAL"
    const val BONUS = "BONUS"
    const val PENALTY = "PENALTY"
    const val REFUND = "REFUND"

    fun getDisplayName(type: String): String {
        return when (type) {
            DELIVERY_PAYMENT -> "Delivery Payment"
            WITHDRAWAL -> "Withdrawal"
            BONUS -> "Bonus"
            PENALTY -> "Penalty"
            REFUND -> "Refund"
            else -> type
        }
    }

    fun isCredit(type: String): Boolean {
        return type in listOf(DELIVERY_PAYMENT, BONUS, REFUND)
    }
}

object BankName {
    const val CBE = "CBE"
    const val AWASH = "AWASH"
    const val ABYSSINIA = "ABYSSINIA"
    const val DASHEN = "DASHEN"
    const val BOA = "BOA"

    fun getAllBanks(): List<Pair<String, String>> = listOf(
        CBE to "Commercial Bank of Ethiopia (CBE)",
        AWASH to "Awash Bank",
        ABYSSINIA to "Bank of Abyssinia",
        DASHEN to "Dashen Bank",
        BOA to "Bank of Africa (BOA)"
    )
}
