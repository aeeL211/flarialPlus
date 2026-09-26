package aeeL;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class Hook extends Application {
  private static final String TAG = "Hook";

  private static final String DISCORD_ID = "123456789012345678";
  private static final String DISCORD_NAME = "aeeL.java";
  private static final String PREFS_NAME = "xyz.flarial.client_preferences";

  @Override public void attachBaseContext(Context base) {
    super.attachBaseContext(base); setupCrashHandler(this);
  }

  @Override public void onCreate() { super.onCreate(); install(this); }

  public static void install(final Application app) {
    setupCrashHandler(app);
    injectPrefs(app);

    new Thread(() -> {
      try {
        executeHook();
      } catch (Throwable t) {
        Log.e(TAG, "Background hook error", t);
      }
    }).start();
  }

  private static void injectPrefs(Context ctx) {
    try {
      SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
      if (sp.getBoolean("flarial_plus_active", false)) return;

      sp.edit()
        .putString("flarial_plus_discord_username", DISCORD_NAME)
        .putString("flarial_plus_discord_id", DISCORD_ID)
        .putBoolean("flarial_plus_active", true)
        .putString("flarial_plus_discord_display_name", DISCORD_NAME)
        .putBoolean("flarial_plus_tester_role_active", true)
        .putString("flarial_plus_discord_avatar_url", "")
        .putString("hook_by_aeeL", "t.me/navalabs")
        .apply();

      Log.i(TAG, "Premium preferences injected.");
    } catch (Exception e) {
      Log.e(TAG, "Failed to write preferences", e);
    }
  }

  private static void executeHook() {
    try {
      Class<?> spImpl = Class.forName("android.app.SharedPreferencesImpl");
      Class<?> editorImpl = Class.forName("android.app.SharedPreferencesImpl$EditorImpl");

      hookGet(spImpl, "getString", String.class);
      hookGet(spImpl, "getBoolean", boolean.class);
      hookGet(spImpl, "getInt", int.class);
      hookGet(spImpl, "getLong", long.class);
      hookGet(spImpl, "getFloat", float.class);
      hookGetSet(spImpl, "getStringSet");

      hookPut(editorImpl, "putString", String.class);
      hookPut(editorImpl, "putBoolean", boolean.class);
      hookPut(editorImpl, "putInt", int.class);
      hookPut(editorImpl, "putLong", long.class);
      hookPut(editorImpl, "putFloat", float.class);
      hookPutSet(editorImpl, "putStringSet");

      hookJson();

      Log.i(TAG, "Flarial successfully hooked!");
    } catch (Exception e) {
      Log.e(TAG, "Hook execution failed", e);
    }
  }

  private static void hookGet(Class<?> clazz, String name, Class<?> type) {
    try {
      Method m = clazz.getDeclaredMethod(name, String.class, type);
      m.setAccessible(true);
      Pine.hook(m, new MethodHook() {
        @Override public void beforeCall(Pine.CallFrame cf) {
          Object val = value(cf.args[0], type);
          if (val != null) cf.setResult(val);
        }
      });
    } catch (Throwable e) {
      Log.e(TAG, "Failed to hook " + name, e);
    }
  }

  private static void hookGetSet(Class<?> clazz, String name) {
    try {
      Method m = clazz.getDeclaredMethod(name, String.class, Set.class);
      m.setAccessible(true);
      Pine.hook(m, new MethodHook() {
        @Override public void beforeCall(Pine.CallFrame cf) {
          Object val = value(cf.args[0], Set.class);
          if (val != null) cf.setResult(val);
        }
      });
    } catch (Throwable e) {
      Log.e(TAG, "Failed to hook " + name, e);
    }
  }

  private static void hookPut(Class<?> clazz, String name, Class<?> type) {
    try {
      Method m = clazz.getDeclaredMethod(name, String.class, type);
      m.setAccessible(true);
      Pine.hook(m, new MethodHook() {
        @Override public void beforeCall(Pine.CallFrame cf) {
          Object val = value(cf.args[0], type);
          if (val != null) cf.args[1] = val;
        }
      });
    } catch (Throwable e) {
      Log.e(TAG, "Failed to hook " + name, e);
    }
  }

  private static void hookPutSet(Class<?> clazz, String name) {
    try {
      Method m = clazz.getDeclaredMethod(name, String.class, Set.class);
      m.setAccessible(true);
      Pine.hook(m, new MethodHook() {
        @Override public void beforeCall(Pine.CallFrame cf) {
          Object val = value(cf.args[0], Set.class);
          if (val != null) cf.args[1] = val;
        }
      });
    } catch (Throwable e) {
      Log.e(TAG, "Failed to hook " + name, e);
    }
  }

  private static void hookJson() {
    try {
      Class<?> json = Class.forName("org.json.JSONObject");

      Method optBool = json.getDeclaredMethod("optBoolean", String.class, boolean.class);
      optBool.setAccessible(true);
      Pine.hook(optBool, new MethodHook() {
        @Override public void beforeCall(Pine.CallFrame cf) {
          String k = (String) cf.args[0];
          if ("hasFlarialPlus".equals(k) || "hasTesterRole".equals(k))
            cf.setResult(true);
        }
      });

      Method optStr = json.getDeclaredMethod("optString", String.class, String.class);
      optStr.setAccessible(true);
      Pine.hook(optStr, new MethodHook() {
        @Override public void beforeCall(Pine.CallFrame cf) {
          String k = (String) cf.args[0];
          if ("discordId".equals(k)) cf.setResult(DISCORD_ID);
          else if ("username".equals(k)) cf.setResult(DISCORD_NAME);
          else if ("displayName".equals(k)) cf.setResult(DISCORD_NAME);
          else if ("avatar".equals(k)) cf.setResult("");
        }
      });
    } catch (Throwable e) {
      Log.e(TAG, "Failed to hook JSON", e);
    }
  }

  private static Object value(Object key, Class<?> type) {
    if (!(key instanceof String)) return null;
    Object val;
    switch ((String) key) {
      case "flarial_plus_active": val = true; break;
      case "flarial_plus_tester_role_active": val = true; break;
      case "flarial_plus_discord_id": val = DISCORD_ID; break;
      case "flarial_plus_discord_username": val = DISCORD_NAME; break;
      case "flarial_plus_discord_display_name": val = DISCORD_NAME; break;
      case "flarial_plus_discord_avatar_url": val = ""; break;
      case "hook_by_aeeL": val = "t.me/navalabs"; break;
      default: return null;
    }
    return type.isInstance(val) ? val : null;
  }

  private static void setupCrashHandler(final Context ctx) {
    Thread.UncaughtExceptionHandler currentHandler =
        Thread.getDefaultUncaughtExceptionHandler();

    if (currentHandler != null && currentHandler.getClass().getName().contains(TAG)) return;

    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      try {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) dir = ctx.getFilesDir();

        File logFile = new File(dir, "crash_log.txt");

        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
          pw.println("=== CRASH at " + Instant.now() + " ===");
          pw.println("Thread: " + thread.getName());
          throwable.printStackTrace(pw);
          pw.println();
        }
        Log.e(TAG, "Crash log saved to: " + logFile.getAbsolutePath());
      } catch (Exception ignored) {}

      if (currentHandler != null) {
        currentHandler.uncaughtException(thread, throwable);
      }
    });
  }
}
