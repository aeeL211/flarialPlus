package aeeL;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import top.canyie.pine.Pine;
import top.canyie.pine.PineConfig;
import top.canyie.pine.callback.MethodHook;
import top.canyie.pine.callback.MethodReplacement;

public class Hook extends Application {
  static final String PKG = "com.android.vending";
  static final String KEY = "\"entitlements\"";
  static final String VAL = KEY + ":{\"flarial_plus\":true,\"tester\":true}";

  @Override
  public void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    crashHandler();
    PineConfig.debuggable = false;
    Pine.ensureInitialized();
    Pine.disableProfileSaver();
    hookLicense();
    hookDebuggable();
    hookInstaller();
    hookJson();
  }

  void hookLicense() {
    try {
      Method m = Class.forName("com.pairip.licensecheck.LicenseClient", false, getClassLoader())
          .getDeclaredMethod("checkLicense", Context.class);
      Pine.hook(m, MethodReplacement.DO_NOTHING);
    } catch (Throwable ignored) {}
  }

  void hookDebuggable() {
    try {
      Pine.hook(Context.class.getMethod("getApplicationInfo"), new MethodHook() {
        @Override public void afterCall(Pine.CallFrame cf) {
          ApplicationInfo info = (ApplicationInfo) cf.getResult();
          if (info != null) info.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
        }
      });
    } catch (Throwable ignored) {}
  }

  void hookInstaller() {
    MethodHook spoof = new MethodHook() {
      @Override public void afterCall(Pine.CallFrame cf) {
        cf.setResult(PKG);
      }
    };
    try {
      Pine.hook(getPackageManager().getClass().getMethod("getInstallerPackageName", String.class), spoof);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Class<?> isi = Class.forName("android.content.pm.InstallSourceInfo");
        Pine.hook(isi.getDeclaredMethod("getInstallingPackageName"), spoof);
        Pine.hook(isi.getDeclaredMethod("getInitiatingPackageName"), spoof);
      }
    } catch (Throwable ignored) {}
  }

  void hookJson() {
    try {
      Constructor<JSONObject> ctor = JSONObject.class.getConstructor(String.class);
      Pine.hook(ctor, new MethodHook() {
        @Override public void beforeCall(Pine.CallFrame cf) {
          Object arg = cf.args[0];
          if (!(arg instanceof String)) return;
          String json = (String) arg;
          if (json.indexOf(KEY) < 0 || json.indexOf("\"sub\"") < 0) return;
          String patched = json.replaceAll("\"entitlements\"\\s*:\\s*\\{[^}]*\\}", VAL);
          cf.args[0] = patched.equals(json) ? json.replace(KEY + ":", VAL + ",") : patched;
        }
      });
    } catch (Throwable ignored) {}
  }

  void crashHandler() {
    Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
      try {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(dir, "crash_log.txt"), true))) {
          w.println("=== " + Instant.now() + " [" + t.getName() + "] ===");
          e.printStackTrace(w);
        }
      } catch (Throwable ignored) {}
      if (prev != null) prev.uncaughtException(t, e);
    });
  }
}