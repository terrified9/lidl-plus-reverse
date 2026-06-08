import requests
import qrcode

def get_loyalty_id(bearer):
    url = "https://profile.lidlplus.com/api/v1/ES/loyalty"
    headers = {
        "Accept-Encoding": "gzip",
        "Authorization": "Bearer " + bearer,
        "User-Agent": "okhttp/5.3.2",
        "Version": "3.31.4"
    }

    response = requests.get(url, headers=headers)
    print(response.status_code)
    if response.status_code == 200:
        return response.text

def fetch_payments_methods(bearer):
    url = "https://payments.lidlplus.com/user-profiles/v5/lidl/ES/store"
    headers = {
        "Accept-Encoding": "gzip",
        "Authorization": "Bearer " + bearer,
        "User-Agent": "okhttp/5.3.2",
        "Version": "3.31.4"
    }

    response = requests.get(url, headers=headers)
    if response.status_code == 200:
        return response.json().get("paymentMethods", [])

def get_pin_token(bearer, pin):
    url = "https://payments.lidlplus.com/user-profiles/v2/lidl/ES/pin/validate"
    headers = {
        "Accept-Encoding": "gzip",
        "Authorization": "Bearer " + bearer,
        "User-Agent": "okhttp/5.3.2",
        "Version": "3.31.4"
    }

    response = requests.post(url, headers=headers, json={"pin": str(pin)})
    if response.status_code == 200:
        return response.json().get("token")

def generate_qr_code(bearer, token, loyalty_id, payment_method_id):
    url = "https://payments.lidlplus.com/payment-methods/v2/lidl/ES/store/qr"
    headers = {
        "Accept-Encoding": "gzip",
        "Authorization": "Bearer " + bearer,
        "Pin-Token": token,
        "User-Agent": "okhttp/5.3.2",
        "Version": "3.31.4"
    }
    request_body = {
        "loyaltyId": str(loyalty_id),
        "paymentMethodId": payment_method_id,
    }
    response = requests.post(url, headers=headers, json=request_body)
    if response.status_code == 200:
        return response.json().get("paymentQR")

def show_qr_code(qr_code):
    qr = qrcode.QRCode(version=1, box_size=10, border=5)
    qr.add_data(qr_code)
    qr.make(fit=True)

    img = qr.make_image(fill="black", back_color="white")
    img.show()

def main():
    bearer = input("Bearer: ")
    pin = input("PIN: ")

    loyalty_id = get_loyalty_id(bearer)
    print(loyalty_id)
    
    payments_methods = fetch_payments_methods(bearer)
    print(payments_methods)

    pin_token = get_pin_token(bearer, pin)
    print(pin_token)

    qr_code = generate_qr_code(bearer, pin_token, loyalty_id, payments_methods[0]["id"])
    print(qr_code)

    show_qr_code(qr_code)

if __name__ == "__main__":
    main()