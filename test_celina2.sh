#!/bin/bash
# ============================================================
# Celina 2 E2E API Test Suite
# Testira sve scenarije iz specifikacije
# ============================================================

BASE="http://localhost:8080"
PASS=0
FAIL=0
ERRORS=""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

assert_status() {
  local test_name="$1" expected="$2" actual="$3" body="$4"
  if [ "$expected" = "$actual" ]; then
    echo -e "  ${GREEN}PASS${NC} $test_name (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $test_name (expected $expected, got $actual)"
    echo -e "       Body: $(echo "$body" | head -c 200)"
    FAIL=$((FAIL + 1))
    ERRORS="$ERRORS\n  - $test_name"
  fi
}

login() {
  local email="$1" password="$2"
  curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" | sed 's/.*accessToken":"//' | sed 's/".*//'
}

api() {
  local method="$1" path="$2" token="$3" data="$4"
  if [ -n "$data" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" \
      -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d "$data")
  else
    RESPONSE=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token")
  fi
  BODY=$(echo "$RESPONSE" | sed '$d')
  STATUS=$(echo "$RESPONSE" | tail -1)
}

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 1: Autentifikacija i Zaposleni${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 1.1 Login ---"
ADMIN_TOKEN=$(login "marko.petrovic@banka.rs" "Admin12345")
[ -n "$ADMIN_TOKEN" ] && assert_status "Admin login" "200" "200" "" || assert_status "Admin login" "200" "401" "no token"

CLIENT_TOKEN=$(login "stefan.jovanovic@gmail.com" "Klijent12345")
[ -n "$CLIENT_TOKEN" ] && assert_status "Client login" "200" "200" "" || assert_status "Client login" "200" "401" "no token"

echo -e "\n--- 1.2 Pogresan login ---"
api POST "/auth/login" "" '{"email":"stefan.jovanovic@gmail.com","password":"pogresna"}'
assert_status "Wrong password returns error" "400" "$STATUS" "$BODY"

echo -e "\n--- 1.3 Employee CRUD ---"
api GET "/employees?page=0&limit=5" "$ADMIN_TOKEN"
assert_status "List employees" "200" "$STATUS" "$BODY"

api GET "/employees/1" "$ADMIN_TOKEN"
assert_status "Get employee by ID" "200" "$STATUS" "$BODY"

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Kreiranje racuna${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 2.1 Kreiranje tekuceg racuna (RSD) ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"CHECKING","accountSubtype":"STANDARD","currency":"RSD","initialDeposit":10000,"ownerEmail":"stefan.jovanovic@gmail.com","createCard":false}'
assert_status "Create checking account RSD" "201" "$STATUS" "$BODY"
NEW_ACC_1=$(echo "$BODY" | sed 's/.*"accountNumber":"//' | sed 's/".*//')
NEW_ACC_1_ID=$(echo "$BODY" | sed 's/.*"id"://' | sed 's/,.*//')

echo -e "\n--- 2.2 Kreiranje deviznog racuna (EUR) ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"FOREIGN","currency":"EUR","initialDeposit":500,"ownerEmail":"stefan.jovanovic@gmail.com","createCard":false}'
assert_status "Create foreign account EUR" "201" "$STATUS" "$BODY"
NEW_ACC_2=$(echo "$BODY" | sed 's/.*"accountNumber":"//' | sed 's/".*//')

echo -e "\n--- 2.3 Kreiranje sa automatskom karticom ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"CHECKING","accountSubtype":"STANDARD","currency":"RSD","initialDeposit":5000,"ownerEmail":"milica.nikolic@gmail.com","createCard":true}'
assert_status "Create account with auto-card" "201" "$STATUS" "$BODY"
CARD_ACC_ID=$(echo "$BODY" | sed 's/.*"id"://' | sed 's/,.*//')

echo -e "\n--- 2.4 Kreiranje poslovnog racuna ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"CHECKING","accountSubtype":"STANDARD","currency":"RSD","initialDeposit":100000,"ownerEmail":"lazar.ilic@yahoo.com","companyName":"Test DOO","registrationNumber":"11112222","taxId":"333344444","activityCode":"62.01","firmAddress":"Beograd"}'
assert_status "Create business account" "201" "$STATUS" "$BODY"

echo -e "\n--- 2.5 Kreiranje bez klijenta (treba da padne) ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"CHECKING","accountSubtype":"STANDARD","currency":"RSD","initialDeposit":1000}'
assert_status "Create without owner fails" "400" "$STATUS" "$BODY"

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Racuni - pregled${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 3.1 GET /accounts/my (klijent) ---"
api GET "/accounts/my" "$CLIENT_TOKEN"
assert_status "Get my accounts" "200" "$STATUS" "$BODY"
ACC_COUNT=$(echo "$BODY" | grep -o '"id":' | wc -l)
echo "       Accounts found: $ACC_COUNT"

echo -e "\n--- 3.2 GET /accounts/{id} ---"
api GET "/accounts/1" "$CLIENT_TOKEN"
assert_status "Get account by ID" "200" "$STATUS" "$BODY"

echo -e "\n--- 3.3 PATCH /accounts/{id}/name ---"
api PATCH "/accounts/1/name" "$CLIENT_TOKEN" '{"name":"Moj glavni racun"}'
assert_status "Update account name" "200" "$STATUS" "$BODY"

echo -e "\n--- 3.4 PATCH /accounts/{id}/limits ---"
api PATCH "/accounts/1/limits" "$CLIENT_TOKEN" '{"dailyLimit":300000,"monthlyLimit":1500000}'
assert_status "Update account limits" "200" "$STATUS" "$BODY"

echo -e "\n--- 3.5 GET /accounts/all (employee portal) ---"
api GET "/accounts/all?page=0&limit=10" "$ADMIN_TOKEN"
assert_status "Employee: list all accounts" "200" "$STATUS" "$BODY"

echo -e "\n--- 3.6 GET /accounts/client/{id} ---"
api GET "/accounts/client/1" "$ADMIN_TOKEN"
assert_status "Employee: accounts by client" "200" "$STATUS" "$BODY"

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Placanja${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 4.1 Uspesno placanje ---"
# Stefan placa Milici (razliciti klijenti, ista valuta RSD)
api POST "/payments" "$CLIENT_TOKEN" '{"fromAccount":"222000112345678911","toAccount":"222000112345678913","amount":5000,"paymentCode":"289","referenceNumber":"123456","description":"Test uplata"}'
assert_status "Create payment (same currency)" "201" "$STATUS" "$BODY"
PAYMENT_ID=$(echo "$BODY" | sed 's/.*"id"://' | sed 's/,.*//')

echo -e "\n--- 4.2 Placanje - nedovoljno sredstava ---"
api POST "/payments" "$CLIENT_TOKEN" '{"fromAccount":"222000112345678911","toAccount":"222000112345678913","amount":999999999,"paymentCode":"289","description":"Prevelik iznos"}'
assert_status "Payment insufficient funds" "400" "$STATUS" "$BODY"

echo -e "\n--- 4.3 GET /payments (lista) ---"
api GET "/payments?page=0&size=10" "$CLIENT_TOKEN"
assert_status "List payments" "200" "$STATUS" "$BODY"

echo -e "\n--- 4.4 GET /payments/{id} ---"
if [ -n "$PAYMENT_ID" ] && [ "$PAYMENT_ID" != "null" ]; then
  api GET "/payments/$PAYMENT_ID" "$CLIENT_TOKEN"
  assert_status "Get payment by ID" "200" "$STATUS" "$BODY"
else
  echo -e "  ${YELLOW}SKIP${NC} Get payment by ID (no payment created)"
fi

echo -e "\n--- 4.5 GET /payments/{id}/receipt (PDF) ---"
if [ -n "$PAYMENT_ID" ] && [ "$PAYMENT_ID" != "null" ]; then
  api GET "/payments/$PAYMENT_ID/receipt" "$CLIENT_TOKEN"
  assert_status "Download payment receipt PDF" "200" "$STATUS" "$BODY"
fi

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Payment Recipients${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 5.1 GET /payment-recipients ---"
api GET "/payment-recipients?page=0&limit=10" "$CLIENT_TOKEN"
assert_status "List payment recipients" "200" "$STATUS" "$BODY"

echo -e "\n--- 5.2 POST /payment-recipients ---"
api POST "/payment-recipients" "$CLIENT_TOKEN" '{"name":"Ana Stojanovic","accountNumber":"222000112345678917"}'
assert_status "Create payment recipient" "201" "$STATUS" "$BODY"
RECIP_ID=$(echo "$BODY" | sed 's/.*"id"://' | sed 's/,.*//')

echo -e "\n--- 5.3 PUT /payment-recipients/{id} ---"
if [ -n "$RECIP_ID" ] && [ "$RECIP_ID" != "null" ]; then
  api PUT "/payment-recipients/$RECIP_ID" "$CLIENT_TOKEN" '{"name":"Ana S.","accountNumber":"222000112345678917"}'
  assert_status "Update payment recipient" "200" "$STATUS" "$BODY"
fi

echo -e "\n--- 5.4 DELETE /payment-recipients/{id} ---"
if [ -n "$RECIP_ID" ] && [ "$RECIP_ID" != "null" ]; then
  api DELETE "/payment-recipients/$RECIP_ID" "$CLIENT_TOKEN"
  assert_status "Delete payment recipient" "204" "$STATUS" "$BODY"
fi

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Transferi${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 6.1 Internal transfer (ista valuta) ---"
api POST "/transfers/internal" "$CLIENT_TOKEN" '{"fromAccountNumber":"222000112345678911","toAccountNumber":"222000112345678912","amount":1000}'
assert_status "Internal transfer same currency" "200" "$STATUS" "$BODY"

echo -e "\n--- 6.2 FX transfer (razlicite valute) ---"
api POST "/transfers/fx" "$CLIENT_TOKEN" '{"fromAccountNumber":"222000112345678911","toAccountNumber":"222000121345678921","amount":1000}'
assert_status "FX transfer RSD->EUR" "200" "$STATUS" "$BODY"

echo -e "\n--- 6.3 Transfer - nedovoljno sredstava ---"
api POST "/transfers/internal" "$CLIENT_TOKEN" '{"fromAccountNumber":"222000112345678911","toAccountNumber":"222000112345678912","amount":999999999}'
assert_status "Transfer insufficient funds" "400" "$STATUS" "$BODY"

echo -e "\n--- 6.4 Transfer - isti racun ---"
api POST "/transfers/internal" "$CLIENT_TOKEN" '{"fromAccountNumber":"222000112345678911","toAccountNumber":"222000112345678911","amount":100}'
assert_status "Transfer same account fails" "400" "$STATUS" "$BODY"

echo -e "\n--- 6.5 GET /transfers ---"
api GET "/transfers" "$CLIENT_TOKEN"
assert_status "List transfers" "200" "$STATUS" "$BODY"

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Menjacnica${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 7.1 GET /exchange-rates ---"
api GET "/exchange-rates" ""
assert_status "Get exchange rates" "200" "$STATUS" "$BODY"

echo -e "\n--- 7.2 GET /exchange/calculate ---"
api GET "/exchange/calculate?amount=1000&fromCurrency=RSD&toCurrency=EUR" "$CLIENT_TOKEN"
assert_status "Calculate RSD->EUR" "200" "$STATUS" "$BODY"

echo -e "\n--- 7.3 Cross-rate calculate ---"
api GET "/exchange/calculate?amount=100&fromCurrency=EUR&toCurrency=USD" "$CLIENT_TOKEN"
assert_status "Calculate EUR->USD cross" "200" "$STATUS" "$BODY"

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Kartice${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 8.1 GET /cards (moje kartice) ---"
api GET "/cards" "$CLIENT_TOKEN"
assert_status "Get my cards" "200" "$STATUS" "$BODY"
CARD_COUNT=$(echo "$BODY" | grep -o '"id":' | wc -l)
echo "       Cards found: $CARD_COUNT"

echo -e "\n--- 8.2 POST /cards (kreiranje) ---"
api POST "/cards" "$CLIENT_TOKEN" '{"accountId":1}'
assert_status "Create card for account" "201" "$STATUS" "$BODY"
NEW_CARD_ID=$(echo "$BODY" | sed 's/.*"id"://' | sed 's/,.*//')

echo -e "\n--- 8.3 PATCH /cards/{id}/block (klijent blokira) ---"
if [ -n "$NEW_CARD_ID" ] && [ "$NEW_CARD_ID" != "null" ]; then
  api PATCH "/cards/$NEW_CARD_ID/block" "$CLIENT_TOKEN"
  assert_status "Client blocks card" "200" "$STATUS" "$BODY"
fi

echo -e "\n--- 8.4 PATCH /cards/{id}/unblock (zaposleni odblokira) ---"
if [ -n "$NEW_CARD_ID" ] && [ "$NEW_CARD_ID" != "null" ]; then
  api PATCH "/cards/$NEW_CARD_ID/unblock" "$ADMIN_TOKEN"
  assert_status "Employee unblocks card" "200" "$STATUS" "$BODY"
fi

echo -e "\n--- 8.5 PATCH /cards/{id}/limit ---"
if [ -n "$NEW_CARD_ID" ] && [ "$NEW_CARD_ID" != "null" ]; then
  api PATCH "/cards/$NEW_CARD_ID/limit" "$CLIENT_TOKEN" '{"cardLimit":500000}'
  assert_status "Change card limit" "200" "$STATUS" "$BODY"
fi

echo -e "\n--- 8.6 PATCH /cards/{id}/deactivate (zaposleni deaktivira) ---"
if [ -n "$NEW_CARD_ID" ] && [ "$NEW_CARD_ID" != "null" ]; then
  api PATCH "/cards/$NEW_CARD_ID/deactivate" "$ADMIN_TOKEN"
  assert_status "Employee deactivates card" "200" "$STATUS" "$BODY"
fi

echo -e "\n--- 8.7 Deaktivirana kartica ne moze da se blokira ---"
if [ -n "$NEW_CARD_ID" ] && [ "$NEW_CARD_ID" != "null" ]; then
  api PATCH "/cards/$NEW_CARD_ID/block" "$CLIENT_TOKEN"
  assert_status "Cannot block deactivated card" "400" "$STATUS" "$BODY"
fi

echo -e "\n--- 8.8 GET /cards/account/{id} (employee portal) ---"
api GET "/cards/account/1" "$ADMIN_TOKEN"
assert_status "Employee: cards by account" "200" "$STATUS" "$BODY"

echo -e "\n--- 8.9 Max kartice po racunu ---"
api POST "/cards" "$CLIENT_TOKEN" '{"accountId":1}'
assert_status "Create 2nd card for account" "201" "$STATUS" "$BODY"
api POST "/cards" "$CLIENT_TOKEN" '{"accountId":1}'
assert_status "3rd card fails (max 2)" "400" "$STATUS" "$BODY"

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Klijenti (Employee Portal)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 9.1 GET /clients ---"
api GET "/clients?page=0&limit=10" "$ADMIN_TOKEN"
assert_status "List clients" "200" "$STATUS" "$BODY"

echo -e "\n--- 9.2 GET /clients/{id} ---"
api GET "/clients/1" "$ADMIN_TOKEN"
assert_status "Get client by ID" "200" "$STATUS" "$BODY"

echo -e "\n--- 9.3 POST /clients ---"
api POST "/clients" "$ADMIN_TOKEN" '{"firstName":"Petar","lastName":"Petrovic","dateOfBirth":"1990-05-15","gender":"M","email":"petar.petrovic@test.com","phone":"+381601234567","address":"Beograd"}'
assert_status "Create client" "201" "$STATUS" "$BODY"

echo -e "\n--- 9.4 PUT /clients/{id} ---"
api PUT "/clients/1" "$ADMIN_TOKEN" '{"phone":"+381649999999","address":"Nova adresa 123"}'
assert_status "Update client" "200" "$STATUS" "$BODY"

echo -e "\n--- 9.5 Klijent ne moze da pristupi /clients ---"
api GET "/clients?page=0&limit=10" "$CLIENT_TOKEN"
assert_status "Client cannot access /clients" "403" "$STATUS" "$BODY"

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Transactions${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 10.1 GET /transactions ---"
api GET "/transactions?page=0&size=10" "$CLIENT_TOKEN"
assert_status "List transactions" "200" "$STATUS" "$BODY"

echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  CELINA 2: Krediti${NC}"
echo -e "${YELLOW}============================================${NC}"

echo -e "\n--- 11.1 POST /loans (klijent podnosi zahtev) ---"
api GET "/accounts/my" "$CLIENT_TOKEN"
FIRST_ACCOUNT=$(echo "$BODY" | grep -o '"accountNumber":"[^"]*"' | head -1 | sed 's/.*:"//' | sed 's/"//')
api POST "/loans" "$CLIENT_TOKEN" "{\"loanType\":\"CASH\",\"interestType\":\"FIXED\",\"amount\":100000,\"currency\":\"RSD\",\"loanPurpose\":\"Renoviranje\",\"repaymentPeriod\":24,\"accountNumber\":\"$FIRST_ACCOUNT\",\"phoneNumber\":\"+381601234567\",\"employmentStatus\":\"stalno\",\"monthlyIncome\":80000}"
assert_status "Client applies for loan" "201" "$STATUS" "$BODY"
LOAN_REQUEST_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')

echo -e "\n--- 11.2 GET /loans/requests (admin vidi zahteve) ---"
api GET "/loans/requests?status=PENDING" "$ADMIN_TOKEN"
assert_status "Admin lists loan requests" "200" "$STATUS" "$BODY"

echo -e "\n--- 11.3 PATCH /loans/requests/{id}/approve ---"
if [ -n "$LOAN_REQUEST_ID" ] && [ "$LOAN_REQUEST_ID" != "null" ]; then
  api PATCH "/loans/requests/$LOAN_REQUEST_ID/approve" "$ADMIN_TOKEN"
  assert_status "Admin approves loan" "200" "$STATUS" "$BODY"
  LOAN_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')
fi

echo -e "\n--- 11.4 GET /loans/my (klijent vidi svoje kredite) ---"
api GET "/loans/my" "$CLIENT_TOKEN"
assert_status "Client views own loans" "200" "$STATUS" "$BODY"

echo -e "\n--- 11.5 GET /loans/{id} (detalji kredita) ---"
if [ -n "$LOAN_ID" ] && [ "$LOAN_ID" != "null" ]; then
  api GET "/loans/$LOAN_ID" "$CLIENT_TOKEN"
  assert_status "Get loan details" "200" "$STATUS" "$BODY"
fi

echo -e "\n--- 11.6 GET /loans/{id}/installments (rate) ---"
if [ -n "$LOAN_ID" ] && [ "$LOAN_ID" != "null" ]; then
  api GET "/loans/$LOAN_ID/installments" "$CLIENT_TOKEN"
  assert_status "Get loan installments" "200" "$STATUS" "$BODY"
  INSTALLMENT_COUNT=$(echo "$BODY" | grep -o '"id"' | wc -l)
  echo "       Installments: $INSTALLMENT_COUNT (expected 24)"
fi

echo -e "\n--- 11.7 POST /loans + reject ---"
api POST "/loans" "$CLIENT_TOKEN" "{\"loanType\":\"STUDENT\",\"interestType\":\"VARIABLE\",\"amount\":50000,\"currency\":\"RSD\",\"loanPurpose\":\"Studije\",\"repaymentPeriod\":12,\"accountNumber\":\"$FIRST_ACCOUNT\",\"phoneNumber\":\"+381601234567\"}"
assert_status "Client applies for student loan" "201" "$STATUS" "$BODY"
REJECT_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')
if [ -n "$REJECT_ID" ] && [ "$REJECT_ID" != "null" ]; then
  api PATCH "/loans/requests/$REJECT_ID/reject" "$ADMIN_TOKEN"
  assert_status "Admin rejects loan" "200" "$STATUS" "$BODY"
fi

echo -e "\n--- 11.8 GET /loans (admin vidi sve kredite) ---"
api GET "/loans" "$ADMIN_TOKEN"
assert_status "Admin lists all loans" "200" "$STATUS" "$BODY"

echo -e "\n--- 11.9 Klijent ne moze da pristupi /loans/requests ---"
api GET "/loans/requests" "$CLIENT_TOKEN"
assert_status "Client cannot access loan requests" "403" "$STATUS" "$BODY"

echo ""
echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  REZULTATI${NC}"
echo -e "${YELLOW}============================================${NC}"
echo -e "  ${GREEN}PASSED: $PASS${NC}"
echo -e "  ${RED}FAILED: $FAIL${NC}"
if [ $FAIL -gt 0 ]; then
  echo -e "\n  Failed tests:$ERRORS"
fi
echo ""
