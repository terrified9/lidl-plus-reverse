# lidl-plus-reverse
An attempt at reversing the LIDL Plus app, which is a shopping app for the LIDL supermarket chain. Currently focusing on the Lidl Pay feature, which allows users to pay for their purchases using the app.

## Why
Lidl Pay stopped working on my phone and I wanted to see if I could fix it.

## How
I used [HTTP Toolkit](https://httptoolkit.tech/) to intercept the network traffic between the app and the server. The endpoints are documented in the [endpoints.md](endpoints.md) file.

## TODO
- [ ] Add coupon support
- [ ] Add other regions