# SplitApp — Usability Test Cases

## 1. SMS Detection & Notifications

### TC-1.1: HSBC debit card SMS triggers notification
**Precondition**: SMS monitoring enabled, notification permission granted
**Steps**:
1. Receive SMS from HSBC sender: `"Your a/c XXX1234 is debited for INR 500.00 on 28/03/26 at AMAZON"`
2. Observe notification tray
**Expected**: Notification appears — "₹500.00 debited — Split this?" with description "AMAZON"

### TC-1.2: HSBC credit card "used at" SMS triggers notification
**Precondition**: SMS monitoring enabled
**Steps**:
1. Receive SMS: `"HSBC creditcard xxxxx6023 used at cas*swiggy for INR 1605.00 on 28/03/26.Limit Rs 89368.51 Due Rs 10631.49."`
**Expected**: Notification appears — "₹1605.00 debited — Split this?" with description "cas*swiggy"

### TC-1.3: ICICI Bank card SMS triggers notification
**Precondition**: SMS monitoring enabled
**Steps**:
1. Receive SMS from ICICI sender: `"INR 855.00 spent using ICICI Bank Card XX7025 on 28-Mar-26 on DREAMPLUG TECHN. Avl Limit: INR 3,50,311.15."`
**Expected**: Notification with amount ₹855.00 and description "DREAMPLUG TECHN"

### TC-1.4: BOB card with foreign currency (SGD)
**Precondition**: SMS monitoring enabled
**Steps**:
1. Receive SMS: `"Paid sgd 9.60 with your BOBCARD Uni Credit Card 9365 to comfort/citycab taxi at 05:10:38 pm on 18-03-2026."`
**Expected**: Notification with amount ₹9.60 and description "comfort/citycab taxi"

### TC-1.5: BOB card domestic SMS
**Precondition**: SMS monitoring enabled
**Steps**:
1. Receive SMS: `"ALERT: INR 197.00 is spent on your BOBCARD ending 4249 at Upi-m S Millennial Foo on 27-03-2026. Available credit limit is Rs 117,137.14"`
**Expected**: Notification with amount ₹197.00 and description "Upi-m S Millennial Foo"

### TC-1.6: SBI UPI debit SMS
**Precondition**: SMS monitoring enabled
**Steps**:
1. Receive SMS from SBI sender with UPI debit containing VPA
**Expected**: Notification with correct amount and UPI ID as description

### TC-1.7: Credit SMS does NOT trigger notification
**Steps**:
1. Receive SMS: `"Your HSBC account is credited for INR 10.00 on 28/03/26"`
**Expected**: No notification. No transaction saved. Credit keyword not in debit pattern.

### TC-1.8: Non-bank SMS does NOT trigger notification
**Steps**:
1. Receive SMS from unknown sender (e.g., promotional SMS with "Rs 500")
**Expected**: No notification. Sender pre-filter rejects it.

### TC-1.9: SMS monitoring disabled
**Precondition**: SMS monitoring toggled OFF in Settings
**Steps**:
1. Receive a valid HSBC debit SMS
**Expected**: No notification. SMS is ignored.

### TC-1.10: Notification tap opens SplitActivity
**Steps**:
1. Tap on a transaction notification
**Expected**: SplitActivity opens with correct transaction amount, description, and timestamp pre-filled

---

## 2. Transaction History

### TC-2.1: Empty state
**Precondition**: No transactions in database
**Steps**:
1. Open app → Transactions tab
**Expected**: Message "No transactions yet. SMS alerts will appear here."

### TC-2.2: Transaction list displays correctly
**Precondition**: Multiple transactions exist (PENDING, DONE, SKIPPED)
**Steps**:
1. Open Transactions tab
**Expected**: All transactions listed as cards with description, amount (₹X.XX), timestamp, and status badge. Status colors: PENDING = primary, DONE = tertiary, SKIPPED = grey.

### TC-2.3: Only PENDING transactions are clickable
**Steps**:
1. Tap a PENDING transaction → should navigate to split screen
2. Tap a DONE transaction → should be disabled (no navigation)
3. Tap a SKIPPED transaction → should be disabled

---

## 3. Settings

### TC-3.1: Save Splitwise API key
**Steps**:
1. Go to Settings → enter Splitwise API key → tap "Save API Key"
2. Close and reopen app
**Expected**: Key is persisted. Field shows saved key (masked). Save button disabled when key matches saved value.

### TC-3.2: Save Gemini API key
**Steps**:
1. Go to Settings → enter Gemini API key → tap "Save Gemini Key"
**Expected**: Key is persisted. Subtitle "For receipt scanning (itemized split)" is visible.

### TC-3.3: Show/hide API key toggle
**Steps**:
1. Tap eye icon on either API key field
**Expected**: Toggles between masked (dots) and plain text display

### TC-3.4: Sync friends and groups
**Precondition**: Valid Splitwise API key saved
**Steps**:
1. Tap "Sync Friends & Groups from Splitwise"
**Expected**: Loading spinner shown during sync. On success: "Synced X friends and Y groups" message. On failure: error message displayed.

### TC-3.5: Sync button disabled without API key
**Steps**:
1. Clear Splitwise API key → observe Sync button
**Expected**: Button is disabled

### TC-3.6: Toggle SMS monitoring
**Steps**:
1. Toggle SMS monitoring switch OFF
2. Receive a bank SMS
**Expected**: No notification triggered. Toggle back ON → notifications resume.

---

## 4. Split Screen — Equal/Ratio Split

### TC-4.1: Transaction card shows editable title
**Steps**:
1. Open a PENDING transaction
**Expected**: Transaction card shows amount, editable title field (pre-filled with SMS description), and timestamp

### TC-4.2: Edit expense title
**Steps**:
1. Open a transaction → change title from "cas*swiggy" to "Swiggy dinner"
2. Submit the split
**Expected**: Expense appears on Splitwise with title "Swiggy dinner", not the original SMS description

### TC-4.3: Search friends
**Steps**:
1. Type "aman" in the friends search field
**Expected**: Only friends matching "aman" (by name or email) are shown. Selected friends remain visible regardless of search query.

### TC-4.4: Search groups
**Steps**:
1. Type "flat" in the groups search field
**Expected**: Only groups matching "flat" are shown as chips. Selected group always visible.

### TC-4.5: Select friends and apply equal split
**Steps**:
1. Select 2 friends via checkboxes → tap "Equal split"
**Expected**: Each friend gets 33.3% ratio (1/3 each including you). "Your share" updates to show ₹ amount.

### TC-4.6: Custom ratio split
**Steps**:
1. Select a friend → manually type "70" in their ratio field
**Expected**: Friend's share shows 70% of total. "Your share" shows remaining 30%. Submit button enabled.

### TC-4.7: Select group auto-populates members
**Steps**:
1. Tap a group chip (e.g., "Flatmates")
**Expected**: All group members appear as selected friends with equal split ratios applied automatically.

### TC-4.8: Deselect group keeps splits
**Steps**:
1. Select a group → modify some ratios → tap group chip again to deselect
**Expected**: Group is deselected but friend splits remain with their modified ratios.

### TC-4.9: Submit split to Splitwise
**Precondition**: API key valid, user ID cached, friends selected with valid ratios
**Steps**:
1. Select friends, set ratios, tap "Add to Splitwise"
**Expected**: Loading state shown → Snackbar "Added to Splitwise!" → Activity closes. Transaction status changes to DONE in history.

### TC-4.10: Skip transaction
**Steps**:
1. Open a transaction → tap "Skip"
**Expected**: Activity closes. Transaction status changes to SKIPPED in history.

### TC-4.11: Submit disabled with invalid ratios
**Steps**:
1. Select friends but total ratio exceeds 100%
**Expected**: "Add to Splitwise" button is disabled

---

## 5. Pin Feature

### TC-5.1: Pin a friend
**Steps**:
1. Open split screen → tap the pin icon (outlined pushpin) on a friend
**Expected**: Icon changes to filled pushpin (primary color). Friend moves to top of their section (selected or unselected).

### TC-5.2: Unpin a friend
**Steps**:
1. Tap filled pushpin on a pinned friend
**Expected**: Icon changes back to outlined. Friend returns to normal sort order.

### TC-5.3: Pin a group
**Steps**:
1. Tap the pin icon on a group chip
**Expected**: Group moves to the front of the chip row. Pin icon filled.

### TC-5.4: Pins persist across sessions
**Steps**:
1. Pin 2 friends and 1 group → close and reopen app → open any transaction
**Expected**: Previously pinned friends and groups still appear at the top with filled pin icons.

### TC-5.5: Pinned friends appear first in both selected and unselected sections
**Steps**:
1. Pin friend A (not selected) and friend B (selected)
2. Observe friend list order
**Expected**: In the selected section, B appears first. In the unselected section, A appears first. Non-pinned items follow.

### TC-5.6: Pins work with search
**Steps**:
1. Pin friend "Aman" → type "a" in search
**Expected**: Aman appears at top of results (pinned), other matches follow.

---

## 6. Itemized Split — Receipt Scanning

### TC-6.1: Itemized split button disabled without friends selected
**Steps**:
1. Open a transaction (no friends selected) → observe "Itemized split (scan receipt)" button
**Expected**: Button is disabled

### TC-6.2: Enter itemized split mode
**Steps**:
1. Select 2 friends → tap "Itemized split (scan receipt)"
**Expected**: Screen switches to itemized view with image capture card (Take Photo / Pick Image buttons), transaction summary shown.

### TC-6.3: Scan Blinkit receipt
**Precondition**: Gemini API key saved, friends selected
**Steps**:
1. Enter itemized mode → tap "Pick Image" → select Blinkit order screenshot
**Expected**: Loading spinner "Scanning receipt..." → Items extracted with correct discounted prices (not MRP). Example: Tender Coconut ₹71 (not ₹90). Handling charge marked as fee.

### TC-6.4: Scan Swiggy Instamart receipt
**Steps**:
1. Scan Instamart screenshot
**Expected**: Items like "Baby Banana (Aratipandu) ₹19.0" extracted correctly. "1 x" prefix stripped from names. Delivery Fee ₹30.00 and Handling Fee ₹10.62 marked as fees. FREE items (₹0) excluded. Totals/discounts excluded.

### TC-6.5: Scan Zepto receipt
**Steps**:
1. Scan Zepto screenshot
**Expected**: Items extracted correctly (Onion ₹10, Green Peas ₹14, Potato ₹26, etc.). UI noise ("Return", "Return policy ended") excluded. Bill summary lines excluded.

### TC-6.6: Scan Flipkart Minutes receipt
**Steps**:
1. Scan Flipkart Minutes screenshot
**Expected**: Items with correct discounted prices (Lady Finger ₹45, not MRP ₹78). FREE delivery/handling fees excluded. Bill summary excluded.

### TC-6.7: No Gemini API key shows clear error
**Precondition**: Gemini API key NOT saved
**Steps**:
1. Enter itemized mode → pick an image
**Expected**: Error message "Gemini API key not set. Add it in Settings." with "Add items manually" fallback.

### TC-6.8: Assign item to bucket
**Steps**:
1. After scanning, tap the "Split between" dropdown on an item
2. Change from "All" to "You + Aman"
**Expected**: Dropdown shows all participant subsets (All, You+A, You+B, A+B, Just You, Just A, Just B for 3 people). Per-person breakdown updates live.

### TC-6.9: Fee items locked to "All"
**Steps**:
1. Observe a fee item (e.g., "Handling charge")
**Expected**: Shows "Shared by all" text with lock icon. No bucket dropdown. Distinct background styling.

### TC-6.10: Manual add item
**Steps**:
1. Tap "+" icon → fill in name "Extra milk", price "45", uncheck fee → tap "Add"
**Expected**: Item appears in list with "All" bucket. Per-person totals update.

### TC-6.11: Edit item
**Steps**:
1. Tap edit icon on an item → change name and price → tap "Save"
**Expected**: Item updates in list. Per-person totals recalculate.

### TC-6.12: Delete item
**Steps**:
1. Tap delete icon on an item → confirm
**Expected**: Item removed from list. Per-person totals recalculate.

### TC-6.13: Mismatch warning
**Steps**:
1. Scan a receipt where extracted item total differs from SMS transaction amount by > ₹1
**Expected**: Yellow warning card: "Item total (₹X) differs from transaction (₹Y) by ₹Z". "Add adjustment item" button available.

### TC-6.14: Add adjustment item from mismatch warning
**Steps**:
1. Tap "Add adjustment item" on the mismatch warning
**Expected**: An "Adjustment" item is added with the difference amount. Mismatch warning disappears (or reduces to < ₹1).

### TC-6.15: Per-person breakdown card
**Steps**:
1. Scan receipt with 3 items, assign 1 to "All", 1 to "You + Aman", 1 to "Just you"
**Expected**: Breakdown shows each person's total. Shared items divided equally among bucket members. Total matches item total.

### TC-6.16: Re-scan receipt
**Steps**:
1. After scanning, tap "Re-scan" button
**Expected**: All items cleared. Returns to image capture card. Can pick a new image.

### TC-6.17: Back from itemized mode
**Steps**:
1. Tap "Back" from itemized screen
**Expected**: Returns to normal split screen. Itemized state cleared. Previously selected friends/group preserved.

### TC-6.18: Submit itemized split
**Steps**:
1. Scan receipt → assign buckets → tap "Submit"
**Expected**: Loading → Snackbar "Added to Splitwise!" → Activity closes. Expense on Splitwise shows correct per-person amounts (BigDecimal rounded to 2 decimal places). Transaction marked DONE.

### TC-6.19: Camera capture
**Steps**:
1. Tap "Take Photo" → grant camera permission → take photo of receipt
**Expected**: Photo captured via FileProvider → sent to Gemini for extraction → items displayed.

### TC-6.20: Camera permission denied
**Steps**:
1. Tap "Take Photo" → deny camera permission
**Expected**: No crash. User can still use "Pick Image" (gallery) as fallback.

### TC-6.21: Empty scan result
**Steps**:
1. Pick an image that is NOT a receipt (e.g., a selfie)
**Expected**: Error: "No items found. Try a clearer screenshot or add items manually." with manual add fallback.

### TC-6.22: Add items manually without scanning
**Steps**:
1. Enter itemized mode → tap "Add items manually" (without scanning)
2. Add 3 items manually
**Expected**: Items appear in list. Can assign buckets and submit normally.

---

## 7. Edge Cases & Error Handling

### TC-7.1: Network error during Splitwise submission
**Precondition**: No internet connectivity
**Steps**:
1. Try to submit a split
**Expected**: Error message displayed (not a crash). User can retry.

### TC-7.2: Network error during Gemini scan
**Precondition**: No internet connectivity
**Steps**:
1. Try to scan a receipt
**Expected**: Error: "Receipt scan failed: [network error]" with manual add fallback.

### TC-7.3: Invalid Splitwise API key
**Steps**:
1. Enter wrong API key → try to sync friends
**Expected**: Error message from Splitwise API displayed. No crash.

### TC-7.4: User ID not cached
**Steps**:
1. Clear app data → open a transaction → select friends → submit
**Expected**: If user ID fetch fails: "Could not load your Splitwise user: [error]". If succeeds: normal flow.

### TC-7.5: Multi-part SMS
**Steps**:
1. Receive a long bank SMS that spans multiple parts
**Expected**: SMS parts concatenated correctly before parsing.

### TC-7.6: Rounding accuracy in itemized split
**Steps**:
1. Scan 3 items totaling ₹100 → split among 3 people
2. Check per-person amounts
**Expected**: Each person's share rounded to 2 decimal places (BigDecimal HALF_UP). Residual absorbed by current user. Total sent to Splitwise matches item total exactly.

### TC-7.7: Friends changed after assigning buckets
**Steps**:
1. Enter itemized mode with friends A and B → scan → assign buckets
2. Go back → deselect friend B, add friend C → re-enter itemized mode
**Expected**: Buckets remapped — items assigned to B reset to "All". Items assigned to A remain. Friend C added to participant list.

---

## 8. Permissions

### TC-8.1: First launch permission requests
**Steps**:
1. Install and launch app for the first time
**Expected**: Permission dialogs for RECEIVE_SMS, READ_SMS, and POST_NOTIFICATIONS (Android 13+) appear.

### TC-8.2: SMS permission denied
**Steps**:
1. Deny SMS permissions
**Expected**: App functions normally but no SMS interception. User can still manually view transactions if any exist.

### TC-8.3: Notification permission denied (Android 13+)
**Steps**:
1. Deny notification permission
**Expected**: SMS still parsed and saved to database, but no notification shown. Transactions visible in history.
