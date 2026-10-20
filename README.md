# @pushgenius/react-native

PushGenius push notifications for React Native (iOS and Android). Requires React Native 0.73+ and iOS 15.1+.

## Install

```sh
npm install @pushgenius/react-native
cd ios && pod install
```

- **iOS:** in Xcode, enable the Push Notifications capability and Background Modes → Remote notifications. The SDK picks up the APNs device token automatically; no AppDelegate changes are needed.
- **Android:** add your `google-services.json` to `android/app` and apply the `com.google.gms.google-services` Gradle plugin.

## Usage

```ts
import { BigBrain } from "@pushgenius/react-native";

BigBrain.setHost("https://api.pushgeni.us");
BigBrain.initialize(APP_ID);
await BigBrain.Notifications.requestPermission(true);
BigBrain.login("user-123");
BigBrain.User.addTag("plan", "pro");

const subscription = BigBrain.Notifications.addClickListener((event) => {
  console.log(event.notificationId, event.additionalData);
});
// later: subscription?.remove();
```

## Developing

The native iOS and Android SDK sources are vendored into `ios/PushGeniusCore` and `android/src/main/java/us/pushgeni/sdk`. After changing either native SDK, run `npm run sync-native` (expects the `ios` and `android` SDK repos next to this one) and commit the result. Don't edit the vendored copies directly.

## Publishing

```sh
npm version <patch|minor|major>
npm publish
```

`prepack` builds `lib/` from `src/` first.
