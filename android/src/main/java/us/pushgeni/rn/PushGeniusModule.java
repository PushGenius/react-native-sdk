package us.pushgeni.rn;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import us.pushgeni.sdk.OneSignal;
import us.pushgeni.sdk.PushGenius;
import java.util.HashMap;
import java.util.Map;

public class PushGeniusModule extends ReactContextBaseJavaModule {
  PushGeniusModule(ReactApplicationContext ctx) { super(ctx); }

  @Override public String getName() { return "PushGeniusNative"; }

  @ReactMethod public void setHost(String url) { PushGenius.setHost(url); }
  @ReactMethod public void initialize(String appId) { OneSignal.initialize(appId); }
  @ReactMethod public void login(String externalId) { OneSignal.login(externalId); }
  @ReactMethod public void logout() { OneSignal.logout(); }

  @ReactMethod
  public void addTags(ReadableMap tags) {
    Map<String, String> map = new HashMap<>();
    for (String k : tags.toHashMap().keySet()) map.put(k, String.valueOf(tags.getString(k)));
    OneSignal.getUser().addTags(map);
  }

  @ReactMethod
  public void removeTags(ReadableArray keys) {
    java.util.List<String> list = new java.util.ArrayList<>();
    for (int i = 0; i < keys.size(); i++) list.add(keys.getString(i));
    OneSignal.getUser().removeTags(list);
  }

  @ReactMethod public void addAlias(String label, String id) { OneSignal.getUser().addAlias(label, id); }
  @ReactMethod public void optIn() { OneSignal.getUser().getPushSubscription().optIn(); }
  @ReactMethod public void optOut() { OneSignal.getUser().getPushSubscription().optOut(); }
  @ReactMethod public void requestPermission(boolean fallback, Promise promise) {
    OneSignal.getNotifications().requestPermission(fallback, promise::resolve);
  }
}
