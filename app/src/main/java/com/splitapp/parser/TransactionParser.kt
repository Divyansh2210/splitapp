package com.splitapp.parser

import com.splitapp.model.Transaction

/**
 * Parses bank SMS messages from HSBC, SBI, BOB, Federal Bank, and ICICI.
 * Returns a Transaction if the SMS is a debit transaction, null otherwise.
 */
object TransactionParser {

    // Sender IDs used as a fast pre-filter before running regex
    private val KNOWN_SENDER_PREFIXES = setOf(
        "HSBCIN", "HSBC",
        "SBIINB", "SBIPSG", "SBIUPI", "SBI",
        "BOBSMS", "BOBUPI", "BARODA", "BOB",
        "FEDBAK", "FEDBNK", "FEDERAL",
        "ICICIB", "ICICIN", "ICICI"
    )

    // Matches: Rs.500, Rs 500.00, Rs.1,500.00, INR 500.00, ₹500
    // Also handles foreign currencies: SGD, USD, EUR, GBP, AED
    private val AMOUNT_PATTERN = Regex(
        """(?:Rs\.?\s*|INR\s*|₹\s*|SGD\s*|USD\s*|EUR\s*|GBP\s*|AED\s*)([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Debit keywords
    private val DEBIT_PATTERN = Regex(
        """(?:debited|debit|spent|paid|payment|withdrawn|purchase|used|charged)""",
        RegexOption.IGNORE_CASE
    )

    // OTP / security message keywords — reject these even if they contain debit keywords
    private val OTP_PATTERN = Regex(
        """\b(?:OTP|O\.T\.P|one\s*time\s*password|verification\s*code|secure\s*code|security\s*code)\b""",
        RegexOption.IGNORE_CASE
    )

    // UPI ID or merchant name after common markers
    private val UPI_ID_PATTERN = Regex(
        """(?:VPA[:\s]*|UPI[:\s]+|to\s+)([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    // Primary merchant pattern: "at/to/for MERCHANT_NAME" — stops at boundary words
    private val MERCHANT_PATTERN = Regex(
        """(?:at|to|for)\s+(?!(?:Rs|INR|₹|SGD|USD|EUR|GBP|AED)\b)([A-Za-z0-9&'*/#][\w &'.\-*/# ]*?)(?=\s+(?:for|on|at|Rs|INR|₹|Avl|Limit|Due|If|Available|Current|Not)\b|\.\s|$)""",
        RegexOption.IGNORE_CASE
    )

    // Fallback: "on MERCHANT_NAME" — requires uppercase start to avoid matching dates like "on 28-Mar-26"
    private val MERCHANT_ON_PATTERN = Regex(
        """(?:\bon\s+)([A-Z][A-Za-z &'.\-*/#]{2,30}?)(?=\s*[.](?:\s|$)|\s+(?:Avl|Limit|Due|If|Available|Not)\b|$)""",
    )

    fun parse(sender: String, body: String): Transaction? {
        // Quick sender pre-filter — avoids running regex on every SMS
        val senderUpper = sender.uppercase()
        val isKnownSender = KNOWN_SENDER_PREFIXES.any { senderUpper.contains(it) }
        if (!isKnownSender) return null

        // Must be a debit
        if (!DEBIT_PATTERN.containsMatchIn(body)) return null

        // Reject OTP / security messages
        if (OTP_PATTERN.containsMatchIn(body)) return null

        // Extract amount
        val amountMatch = AMOUNT_PATTERN.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

        // Try to get UPI ID first, then merchant name via primary pattern,
        // then fallback "on MERCHANT" pattern, else use generic description
        val description = UPI_ID_PATTERN.find(body)?.groupValues?.get(1)
            ?: MERCHANT_PATTERN.find(body)?.groupValues?.get(1)?.trim()
            ?: MERCHANT_ON_PATTERN.find(body)?.groupValues?.get(1)?.trim()
            ?: "Transaction"

        return Transaction(
            amount = amount,
            description = description,
            timestamp = System.currentTimeMillis()
        )
    }
}
