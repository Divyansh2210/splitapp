package com.splitapp.parser

import com.splitapp.model.Transaction
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SMS-based transaction detection and parsing.
 *
 * Covers:
 * - Sender pre-filtering (known banks only)
 * - Debit keyword detection
 * - Amount extraction across currency formats
 * - Merchant/description extraction hierarchy (UPI > "at/to/for" > "on" > fallback)
 * - Credit SMS rejection
 * - Non-bank SMS rejection
 */
class TransactionParserTest {

    // =========================================================================
    // 1. HSBC SMS patterns
    // =========================================================================

    @Test
    fun `HSBC debit card SMS - extracts amount and merchant`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "Your a/c XXX1234 is debited for INR 500.00 on 28/03/26 at AMAZON"
        )
        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.001)
        assertEquals("AMAZON", result.description)
    }

    @Test
    fun `HSBC credit card 'used at' SMS - extracts amount and merchant`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "HSBC creditcard xxxxx6023 used at cas*swiggy for INR 1605.00 on 28/03/26.Limit Rs 89368.51 Due Rs 10631.49."
        )
        assertNotNull(result)
        assertEquals(1605.0, result!!.amount, 0.001)
        assertEquals("cas*swiggy", result.description)
    }

    @Test
    fun `HSBC credit SMS is NOT detected - credit keyword without debit`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "Your HSBC account is credited for INR 10.00 on 28/03/26"
        )
        assertNull("Credit SMS should not generate a transaction", result)
    }

    // =========================================================================
    // 2. ICICI SMS patterns
    // =========================================================================

    @Test
    fun `ICICI Bank card SMS - 'spent' keyword and 'on MERCHANT' fallback`() {
        val result = TransactionParser.parse(
            "VM-ICICIB",
            "INR 855.00 spent using ICICI Bank Card XX7025 on 28-Mar-26 on DREAMPLUG TECHN. Avl Limit: INR 3,50,311.15."
        )
        assertNotNull(result)
        assertEquals(855.0, result!!.amount, 0.001)
        assertEquals("DREAMPLUG TECHN", result.description)
    }

    @Test
    fun `ICICI alternate sender ID accepted`() {
        val result = TransactionParser.parse(
            "VD-ICICIN",
            "INR 200.00 spent using ICICI Bank Card XX1234 on 01-Jan-26 on SOME SHOP. Avl Limit: INR 50,000.00."
        )
        assertNotNull(result)
        assertEquals(200.0, result!!.amount, 0.001)
    }

    // =========================================================================
    // 3. BOB (Bank of Baroda) SMS patterns
    // =========================================================================

    @Test
    fun `BOB card domestic SMS with 'spent' keyword`() {
        val result = TransactionParser.parse(
            "AD-BOBSMS",
            "ALERT: INR 197.00 is spent on your BOBCARD ending 4249 at Upi-m S Millennial Foo on 27-03-2026. Available credit limit is Rs 117,137.14"
        )
        assertNotNull(result)
        assertEquals(197.0, result!!.amount, 0.001)
        assertEquals("Upi-m S Millennial Foo", result.description)
    }

    @Test
    fun `BOB card SGD foreign currency SMS`() {
        val result = TransactionParser.parse(
            "AD-BOBSMS",
            "Paid sgd 9.60 with your BOBCARD Uni Credit Card 9365 to comfort/citycab taxi at 05:10:38 pm on 18-03-2026."
        )
        assertNotNull(result)
        assertEquals(9.60, result!!.amount, 0.001)
        assertEquals("comfort/citycab taxi", result.description)
    }

    @Test
    fun `BOB UPI sender ID accepted`() {
        val result = TransactionParser.parse(
            "BZ-BOBUPI",
            "INR 100.00 debited from your account to merchant@upi on 01-01-2026"
        )
        assertNotNull(result)
    }

    // =========================================================================
    // 4. SBI SMS patterns
    // =========================================================================

    @Test
    fun `SBI UPI debit SMS with VPA - extracts UPI ID as description`() {
        val result = TransactionParser.parse(
            "AD-SBIUPI",
            "Dear Customer, Rs.250.00 debited from your A/c XXXXXX1234 on 15-Mar-26 by VPA merchant@ybl. If not done by you, call 18001234."
        )
        assertNotNull(result)
        assertEquals(250.0, result!!.amount, 0.001)
        assertEquals("merchant@ybl", result.description)
    }

    @Test
    fun `SBI INB sender ID accepted`() {
        val result = TransactionParser.parse(
            "AD-SBIINB",
            "INR 500.00 debited from your account at SBI ATM on 01-01-2026"
        )
        assertNotNull(result)
    }

    @Test
    fun `SBI PSG sender ID accepted`() {
        val result = TransactionParser.parse(
            "VK-SBIPSG",
            "Rs.1000.00 has been debited from A/c XXX5678 for payment to electricity board"
        )
        assertNotNull(result)
    }

    // =========================================================================
    // 5. Federal Bank SMS patterns
    // =========================================================================

    @Test
    fun `Federal Bank debit SMS accepted`() {
        val result = TransactionParser.parse(
            "AD-FEDBNK",
            "Rs.350.00 has been debited from your Federal Bank A/c XXX4567 at GROCERY STORE on 15-Mar-26"
        )
        assertNotNull(result)
        assertEquals(350.0, result!!.amount, 0.001)
        assertEquals("GROCERY STORE", result.description)
    }

    @Test
    fun `Federal Bank alternate sender ID FEDBAK accepted`() {
        val result = TransactionParser.parse(
            "AD-FEDBAK",
            "INR 100.00 debited from account for payment at ZOMATO"
        )
        assertNotNull(result)
    }

    // =========================================================================
    // 6. Amount format variations
    // =========================================================================

    @Test
    fun `amount with comma separator`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "Your a/c debited for INR 1,500.00 at MERCHANT on 01/01/26"
        )
        assertNotNull(result)
        assertEquals(1500.0, result!!.amount, 0.001)
    }

    @Test
    fun `amount with Rs dot prefix`() {
        val result = TransactionParser.parse(
            "AD-SBIUPI",
            "Rs.250.50 debited from your account at SHOP"
        )
        assertNotNull(result)
        assertEquals(250.50, result!!.amount, 0.001)
    }

    @Test
    fun `amount with Rs space prefix`() {
        val result = TransactionParser.parse(
            "AD-SBIUPI",
            "Rs 750 debited from your account at SHOP"
        )
        assertNotNull(result)
        assertEquals(750.0, result!!.amount, 0.001)
    }

    @Test
    fun `amount with rupee symbol`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "₹999.99 debited from your card at FLIPKART"
        )
        assertNotNull(result)
        assertEquals(999.99, result!!.amount, 0.001)
    }

    @Test
    fun `USD foreign currency amount`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "USD 25.00 charged to your card at AMAZON US on 01/01/26"
        )
        assertNotNull(result)
        assertEquals(25.0, result!!.amount, 0.001)
    }

    @Test
    fun `EUR foreign currency amount`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "EUR 15.50 charged to your card at PARIS CAFE"
        )
        assertNotNull(result)
        assertEquals(15.50, result!!.amount, 0.001)
    }

    @Test
    fun `GBP foreign currency amount`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "GBP 30.00 charged to your card at LONDON STORE"
        )
        assertNotNull(result)
        assertEquals(30.0, result!!.amount, 0.001)
    }

    @Test
    fun `AED foreign currency amount`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "AED 50.00 charged to your card at DUBAI MALL"
        )
        assertNotNull(result)
        assertEquals(50.0, result!!.amount, 0.001)
    }

    @Test
    fun `amount with large comma-separated value`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "INR 3,50,311.15 debited from account at MERCHANT"
        )
        assertNotNull(result)
        assertEquals(350311.15, result!!.amount, 0.001)
    }

    // =========================================================================
    // 7. Debit keyword variations
    // =========================================================================

    @Test
    fun `keyword debited detected`() {
        assertNotNull(TransactionParser.parse("AD-HSBCIN", "INR 100.00 debited at SHOP"))
    }

    @Test
    fun `keyword spent detected`() {
        assertNotNull(TransactionParser.parse("AD-ICICIB", "INR 100.00 spent at SHOP"))
    }

    @Test
    fun `keyword paid detected`() {
        assertNotNull(TransactionParser.parse("AD-BOBSMS", "Paid INR 100.00 at SHOP"))
    }

    @Test
    fun `keyword payment detected`() {
        assertNotNull(TransactionParser.parse("AD-SBIUPI", "INR 100.00 payment at SHOP"))
    }

    @Test
    fun `keyword withdrawn detected`() {
        assertNotNull(TransactionParser.parse("AD-HSBCIN", "INR 100.00 withdrawn at ATM"))
    }

    @Test
    fun `keyword purchase detected`() {
        assertNotNull(TransactionParser.parse("AD-HSBCIN", "INR 100.00 purchase at STORE"))
    }

    @Test
    fun `keyword used detected`() {
        assertNotNull(TransactionParser.parse("AD-HSBCIN", "Card used for INR 100.00 at SHOP"))
    }

    @Test
    fun `keyword charged detected`() {
        assertNotNull(TransactionParser.parse("AD-HSBCIN", "INR 100.00 charged at SHOP"))
    }

    @Test
    fun `keyword case insensitive`() {
        assertNotNull(TransactionParser.parse("AD-HSBCIN", "INR 100.00 DEBITED at SHOP"))
        assertNotNull(TransactionParser.parse("AD-HSBCIN", "INR 100.00 Spent at SHOP"))
    }

    // =========================================================================
    // 8. Description extraction priority
    // =========================================================================

    @Test
    fun `UPI ID takes highest priority over merchant patterns`() {
        val result = TransactionParser.parse(
            "AD-SBIUPI",
            "Rs.100.00 debited for payment to merchant@paytm at PAYTM on 01/01/26"
        )
        assertNotNull(result)
        assertEquals("merchant@paytm", result!!.description)
    }

    @Test
    fun `'at MERCHANT' pattern works`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "INR 500.00 debited at AMAZON on 01/01/26"
        )
        assertNotNull(result)
        assertEquals("AMAZON", result!!.description)
    }

    @Test
    fun `'to MERCHANT' pattern works`() {
        val result = TransactionParser.parse(
            "AD-BOBSMS",
            "Paid INR 100.00 to comfort/citycab taxi at 05:10 pm"
        )
        assertNotNull(result)
        // Should match "to comfort/citycab taxi" via MERCHANT_PATTERN
        assertTrue(result!!.description.contains("comfort/citycab"))
    }

    @Test
    fun `'on MERCHANT' fallback requires uppercase start`() {
        val result = TransactionParser.parse(
            "AD-ICICIB",
            "INR 855.00 spent using card on 28-Mar-26 on DREAMPLUG TECHN. Avl Limit: INR 350000"
        )
        assertNotNull(result)
        // "on 28-Mar-26" should NOT match (lowercase date), "on DREAMPLUG TECHN" should match
        assertEquals("DREAMPLUG TECHN", result!!.description)
    }

    @Test
    fun `fallback to generic 'Transaction' when no description found`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "INR 100.00 debited from your account"
        )
        assertNotNull(result)
        assertEquals("Transaction", result!!.description)
    }

    @Test
    fun `merchant name with special characters`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "INR 1605.00 used at cas*swiggy for Rs 89368"
        )
        assertNotNull(result)
        assertEquals("cas*swiggy", result!!.description)
    }

    @Test
    fun `merchant name stops at boundary words`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "INR 500.00 debited at AMAZON on 28/03/26"
        )
        assertNotNull(result)
        // Should NOT include "on 28/03/26" in the description
        assertFalse(result!!.description.contains("28/03"))
    }

    // =========================================================================
    // 9. Sender pre-filter rejection
    // =========================================================================

    @Test
    fun `unknown sender is rejected`() {
        val result = TransactionParser.parse(
            "AD-PROMO",
            "Get Rs 500 off on your next purchase! Debited from excitement!"
        )
        assertNull("Non-bank sender should be rejected", result)
    }

    @Test
    fun `empty sender is rejected`() {
        val result = TransactionParser.parse(
            "",
            "INR 500.00 debited at SHOP"
        )
        assertNull(result)
    }

    @Test
    fun `promotional SMS with amount is rejected`() {
        val result = TransactionParser.parse(
            "DM-AMAZIN",
            "Your order of Rs 1500 has been placed! Payment debited successfully."
        )
        assertNull("Promotional SMS from non-bank sender should be rejected", result)
    }

    // =========================================================================
    // 10. No debit keyword rejection
    // =========================================================================

    @Test
    fun `SMS without debit keyword returns null even from known bank`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "Your HSBC account balance is INR 50000.00 as on 28/03/26"
        )
        assertNull("Balance inquiry should not trigger", result)
    }

    // =========================================================================
    // 11. OTP / security SMS rejection
    // =========================================================================

    @Test
    fun `OTP SMS with debit keyword is rejected`() {
        assertNull(TransactionParser.parse("AD-HSBCIN",
            "OTP for debit of INR 500.00 from your account is 483921. Do not share with anyone."))
    }

    @Test
    fun `one time password SMS is rejected`() {
        assertNull(TransactionParser.parse("AD-SBIINB",
            "Your One Time Password for Rs 1000 payment is 582910. Valid for 5 min."))
    }

    @Test
    fun `verification code SMS is rejected`() {
        assertNull(TransactionParser.parse("AD-ICICIB",
            "Verification code 839201 for INR 2500.00 debited to Amazon. Do not share."))
    }

    @Test
    fun `secure code SMS is rejected`() {
        assertNull(TransactionParser.parse("AD-HSBCIN",
            "Your secure code for Rs.750 payment at Flipkart is 192830."))
    }

    @Test
    fun `security code SMS is rejected`() {
        assertNull(TransactionParser.parse("AD-SBIINB",
            "Security code: 491023. INR 300.00 debited for online purchase."))
    }

    @Test
    fun `normal transaction without OTP keywords still parses`() {
        val result = TransactionParser.parse("AD-HSBCIN",
            "INR 500.00 debited from your account at AMAZON on 28-Mar-26")
        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.001)
    }

    // =========================================================================
    // 12. Multi-part SMS (body already concatenated by BroadcastReceiver)
    // =========================================================================

    @Test
    fun `long SMS body with amount and merchant at different positions`() {
        val longBody = "Dear Customer, this is to inform you that INR 2,500.00 has been debited from your " +
                "HSBC Savings Account XXXXXX1234 towards payment at FLIPKART INTERNET PVT LTD on 28-Mar-2026. " +
                "Your available balance is Rs 45,000.00. If this transaction was not done by you, please call 18001234."
        val result = TransactionParser.parse("AD-HSBCIN", longBody)
        assertNotNull(result)
        assertEquals(2500.0, result!!.amount, 0.001)
    }

    // =========================================================================
    // 12. Transaction object correctness
    // =========================================================================

    @Test
    fun `parsed transaction has non-zero timestamp`() {
        val result = TransactionParser.parse("AD-HSBCIN", "INR 100.00 debited at SHOP")
        assertNotNull(result)
        assertTrue(result!!.timestamp > 0)
    }

    // =========================================================================
    // 13. Sender ID partial match (contains check, not exact)
    // =========================================================================

    @Test
    fun `sender with prefix before bank ID is accepted`() {
        val result = TransactionParser.parse(
            "AD-HSBCIN",
            "INR 100.00 debited at SHOP"
        )
        assertNotNull(result)
    }

    @Test
    fun `sender is case insensitive`() {
        val result = TransactionParser.parse(
            "ad-hsbcin",
            "INR 100.00 debited at SHOP"
        )
        assertNotNull(result)
    }

    @Test
    fun `BARODA sender ID accepted`() {
        assertNotNull(TransactionParser.parse("BX-BARODA", "INR 100.00 debited at SHOP"))
    }

    @Test
    fun `FEDERAL sender ID accepted`() {
        assertNotNull(TransactionParser.parse("AD-FEDERAL", "INR 100.00 debited at SHOP"))
    }

}
