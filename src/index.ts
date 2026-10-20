import { NativeModules, NativeEventEmitter, Platform } from "react-native";

type ClickEvent = { notificationId: string; additionalData?: Record<string, unknown> };

const { PushGeniusNative } = NativeModules;
const emitter = PushGeniusNative ? new NativeEventEmitter(PushGeniusNative) : null;

class PushSubscription {
  get id(): string | null {
    return PushGeniusNative?.getSubscriptionId?.() ?? null;
  }
  get token(): string | null {
    return PushGeniusNative?.getToken?.() ?? null;
  }
  get optedIn(): boolean {
    return PushGeniusNative?.getOptedIn?.() ?? false;
  }
  optIn() {
    PushGeniusNative?.optIn?.();
  }
  optOut() {
    PushGeniusNative?.optOut?.();
  }
}

class UserNamespace {
  pushSubscription = new PushSubscription();
  addTag(key: string, value: string) {
    this.addTags({ [key]: value });
  }
  addTags(tags: Record<string, string>) {
    PushGeniusNative?.addTags(tags);
  }
  removeTag(key: string) {
    this.removeTags([key]);
  }
  removeTags(keys: string[]) {
    PushGeniusNative?.removeTags(keys);
  }
  addAlias(label: string, id: string) {
    PushGeniusNative?.addAlias(label, id);
  }
}

class NotificationsNamespace {
  requestPermission(fallbackToSettings = true): Promise<boolean> {
    return PushGeniusNative?.requestPermission(fallbackToSettings) ?? Promise.resolve(false);
  }
  addClickListener(listener: (event: ClickEvent) => void) {
    emitter?.addListener("BigBrain-notificationClicked", listener);
  }
}

export const BigBrain = {
  User: new UserNamespace(),
  Notifications: new NotificationsNamespace(),
  initialize(appId: string) {
    PushGeniusNative?.initialize(appId);
  },
  login(externalId: string) {
    PushGeniusNative?.login(externalId);
  },
  logout() {
    PushGeniusNative?.logout();
  },
  setHost(url: string) {
    PushGeniusNative?.setHost(url);
  },
};

export const PushGenius = BigBrain;
export default BigBrain;
