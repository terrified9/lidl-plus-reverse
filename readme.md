# lidl-plus-reverse
An attempt at reversing the LIDL Plus app, which is a shopping app for the LIDL supermarket chain. Currently focusing on the Lidl Pay feature, which allows users to pay for their purchases using the app.

## Why
Lidl Pay stopped working on my phone and I wanted to see if I could fix it.

## How
I used [HTTP Toolkit](https://httptoolkit.tech/) to intercept the network traffic between the app and the server. The endpoints are documented in the [endpoints.md](endpoints.md) file.

## App
You can find the tested working Android app in the [app branch](https://github.com/terrified9/lidl-plus-reverse/tree/app). It is a very basic app that allows you to log in and display your Lidl Pay QR code. Note that is it currently adapted for the Spain region however it should be very easy to adapt it for other regions by changing the endpoints.

## TODO
- [x] Make a basic Android app
- [ ] Add coupon support
- [ ] Add other regions
  
## Contributing
Feel free to contribute to this project by submitting pull requests or opening issues. Any help is appreciated as the Android app is fully vibe coded and I have no experience with Android development. Currently looking for:
- Android app improvements
- Adding support for other regions
- Adding support for coupons