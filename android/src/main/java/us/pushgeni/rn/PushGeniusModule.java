package us.pushgeni.rn;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.Unit;
import us.pushgeni.sdk.BigBrain;
import us.pushgeni.sdk.NotificationClickEvent;
import us.pushgeni.sdk.PushGenius;

public class PushGeniusModule extends ReactContextBaseJavaModule {
  private static final String CLICK_EVENT = "BigBrain-notificationClicked";

  // React Native recreates modules on reload, so register one SDK listener and point it at the
  // newest module instead of adding a listener per instance.
  private static volatile PushGeniusModule current;
  // Clicks wait here until JS subscribes, so the tap that cold-started the app still reaches JS.
  private static final List<NotificationClickEvent> pending = new ArrayList<>();
  private static boolean jsListening = false;

  static {
    BigBrain.getNotifications().addClickListener(event -> {
      deliver(event);
      return Unit.INSTANCE;
    });
  }

  PushGeniusModule(ReactApplicationContext ctx) {
    super(ctx);
    synchronized (pending) {
      current = this;
      jsListening = false;
    }
  }

  private static void deliver(NotificationClickEvent event) {
    PushGeniusModule module;
    synchronized (pending) {
      module = current;
      if (module == null || !jsListening) {
        pending.add(event);
        return;
      }
    }
    module.emitClick(event);
  }

  @Override public String getName() { return "PushGeniusNative"; }

  private void emitClick(NotificationClickEvent event) {
    ReactApplicationContext ctx = getReactApplicationContext();
    if (!ctx.hasActiveReactInstance()) return;
    WritableMap body = Arguments.createMap();
    body.putString("notificationId", event.getNotificationId());
    body.putMap("additionalData", Arguments.makeNativeMap(event.getAdditionalData()));
    ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(CLICK_EVENT, body);
  }

  // Called by NativeEventEmitter when JS subscribes; the subscriptions themselves live in JS.
  @ReactMethod
  public void addListener(String eventName) {
    if (!CLICK_EVENT.equals(eventName)) return;
    List<NotificationClickEvent> queued;
    synchronized (pending) {
      jsListening = true;
      queued = new ArrayList<>(pending);
      pending.clear();
    }
    for (NotificationClickEvent event : queued) emitClick(event);
  }

  @ReactMethod public void removeListeners(double count) {}

  @ReactMethod public void setHost(String url) { PushGenius.setHost(url); }
  @ReactMethod public void initialize(String appId) { BigBrain.initialize(appId); }
  @ReactMethod public void login(String externalId) { BigBrain.login(externalId); }
  @ReactMethod public void logout() { BigBrain.logout(); }

  @ReactMethod
  public void addTags(ReadableMap tags) {
    Map<String, String> map = new HashMap<>();
    for (Map.Entry<String, Object> entry : tags.toHashMap().entrySet()) {
      map.put(entry.getKey(), String.valueOf(entry.getValue()));
    }
    BigBrain.getUser().addTags(map);
  }

  @ReactMethod
  public void removeTags(ReadableArray keys) {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) list.add(keys.getString(i));
    BigBrain.getUser().removeTags(list);
  }

  @ReactMethod public void addAlias(String label, String id) { BigBrain.getUser().addAlias(label, id); }
  @ReactMethod public void optIn() { BigBrain.getUser().getPushSubscription().optIn(); }
  @ReactMethod public void optOut() { BigBrain.getUser().getPushSubscription().optOut(); }

  @ReactMethod
  public void requestPermission(boolean fallback, Promise promise) {
    BigBrain.getNotifications().requestPermission(fallback, granted -> {
      promise.resolve(granted);
      return Unit.INSTANCE;
    });
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getSubscriptionId() { return BigBrain.getUser().getPushSubscription().getId(); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getToken() { return BigBrain.getUser().getPushSubscription().getToken(); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public boolean getOptedIn() { return BigBrain.getUser().getPushSubscription().getOptedIn(); }
}
