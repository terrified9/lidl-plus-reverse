# Lidl Plus API — Basic Reference

## 1) Get loyalty

```http
URL: https://profile.lidlplus.com/api/v1/ES/loyalty
Method: GET

Headers:
  Authorization: Bearer <token>
  Accept-Encoding: gzip
  User-Agent: okhttp/5.3.2
  Version: 3.31.4

Request Body:
  None
```

## 2) Get payment methods

```http
URL: https://payments.lidlplus.com/user-profiles/v5/lidl/ES/store
Method: GET

Headers:
  Authorization: Bearer <token>
  Accept-Encoding: gzip
  User-Agent: okhttp/5.3.2
  Version: 3.31.4

Request Body:
  None
```

## 3) Validate PIN

```http
URL: https://payments.lidlplus.com/user-profiles/v2/lidl/ES/pin/validate
Method: POST

Headers:
  Authorization: Bearer <token>
  Accept-Encoding: gzip
  User-Agent: okhttp/5.3.2
  Version: 3.31.4

Request Body:
{
  "pin": "1234"
}
```

## 4) Generate QR

```http
URL: https://payments.lidlplus.com/payment-methods/v2/lidl/ES/store/qr
Method: POST

Headers:
  Authorization: Bearer <token>
  Pin-Token: <pin_token>
  Accept-Encoding: gzip
  User-Agent: okhttp/5.3.2
  Version: 3.31.4

Request Body:
{
  "loyaltyId": "<loyalty_id>",
  "paymentMethodId": "<payment_method_id>"
}
```