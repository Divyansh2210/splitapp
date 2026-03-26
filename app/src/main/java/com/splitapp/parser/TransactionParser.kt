package com.splitapp.parser

import com.splitapp.model.Transaction

/**
 * Parses bank SMS messages from HSBC, SBI, BOB, and Federal Bank.
 * Returns a Transaction if the SMS is a debit transaction, null otherwise.
 */
object TransactionParser {

    // Sender IDs used as a fast pre-filter before running regex
    private val KNOWN_SENDER_PREFIXES = setOf(
        "HSBCIN", "HSBC",
        "SBIINB", "SBIPSG", "SBIUPI", "SBI",
        "BOBSMS", "BOBUPI", "BARODA", "BOB",
        "FEDBAK", "FEDBNK", "FEDERAL"
    )

    // Matches: Rs.500, Rs 500.00, Rs.1,500.00, INR 500.00, ₹500
    private val AMOUNT_PATTERN = Regex(
        """(?:Rs\.?\s*|INR\s*|₹\s*)([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Debit keywords
    private val DEBIT_PATTERN = Regex(
        """(?:debited|debit|spent|paid|payment|withdrawn|purchase)""",
        RegexOption.IGNORE_CASE
    )

    // UPI ID or merchant name after common markers
    private val UPI_ID_PATTERN = Regex(
        """(?:VPA|UPI[:\s]+|to\s+)([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val MERCHANT_PATTERN = Regex(
        """(?:at|to|for)\s+([A-Za-z0-9 &'.\-]{3,30})""",
        RegexOption.IGNORE_CASE
    )

    fun parse(sender: String, body: String): Transaction? {
        // Quick sender pre-filter — avoids running regex on every SMS
        val senderUpper = sender.uppercase()
        val isKnownSender = KNOWN_SENDER_PREFIXES.any { senderUpper.contains(it) }
        if (!isKnownSender) return null

        // Must be a debit
        if (!DEBIT_PATTERN.containsMatchIn(body)) return null

        // Extract amount
        val amountMatch = AMOUNT_PATTERN.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

        // Try to get UPI ID first, then merchant name, else use a generic description
        val description = UPI_ID_PATTERN.find(body)?.groupValues?.get(1)
            ?: MERCHANT_PATTERN.find(body)?.groupValues?.get(1)?.trim()
            ?: "Transaction"

        return Transaction(
            amount = amount,
            description = description,
            rawSms = body,
            timestamp = System.currentTimeMillis()
        )
    }
}
