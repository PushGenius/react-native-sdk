# pushgenius-react-native

Standalone React Native package. Native modules wrap `pushgenius-ios` and `pushgenius-android`.

```ts
import { BigBrain } from "pushgenius-react-native";

BigBrain.setHost("https://api.pushgeni.us");
BigBrain.initialize(APP_ID);
await BigBrain.Notifications.requestPermission(true);
BigBrain.login("user-123");
BigBrain.User.addTag("plan", "pro");
```

Publish to npm, or depend on this git repo. Link the iOS Swift package and Android Maven/module from those repos when building the host app.
