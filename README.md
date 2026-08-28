# pushgenius-react-native

Standalone React Native package. Native modules wrap `pushgenius-ios` and `pushgenius-android`.

```ts
import { OneSignal } from "pushgenius-react-native";

OneSignal.setHost("https://api.pushgeni.us");
OneSignal.initialize(APP_ID);
await OneSignal.Notifications.requestPermission(true);
OneSignal.login("user-123");
OneSignal.User.addTag("plan", "pro");
```

Publish to npm, or depend on this git repo. Link the iOS Swift package and Android Maven/module from those repos when building the host app.
