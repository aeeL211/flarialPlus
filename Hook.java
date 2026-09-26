package aeeL;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import dalvik.system.BaseDexClassLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Enumeration;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class Hook extends Application {
  private static final String TAG = "Hook";
  
  private static final String DISCORD_ID = "123456789012345678";
  private static final String DISCORD_NAME = "aeeL.cpp";
  private static final String PREFS_NAME = "xyz.flarial.client_preferences";

  // Diurutkan dari yang paling sering muncul agar pengecekan lebih cepat (fail-fast)
  private static final String[] IGNORED_PACKAGES = {
    "com.", "android.", "java.", "kotlin.", "androidx.", "javax.",
    "dalvik.", "org.", "okhttp3.", "okio.", "sun.", "jdk.", "aeeL.",
    "bin.", "coelho.", "moe.", "rikka.", "top."
  };

  @Override
  public void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    setupCrashHandler(this);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    install(this);
  }

  public static void install(final Application app) {
    setupCrashHandler(app);
    injectPrefs(app);

    new Thread(() -> {
      try {
        executeHook(app.getClassLoader());
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

  private static void executeHook(ClassLoader cl) {
    try {
      Class<?> targetClass = findTargetClass(cl);
      if (targetClass == null) {
        Log.e(TAG, "Target class not found.");
        return;
      }
      Log.i(TAG, "Target found: " + targetClass.getName());

      Method targetMethod = null;
      Class<?> dataClass = null;
      Class<?> userClass = null;

      for (Method m : targetClass.getDeclaredMethods()) {
        if (!Modifier.isStatic(m.getModifiers())) continue;

        Class<?>[] params = m.getParameterTypes();
        Class<?> retType = m.getReturnType();
        int paramCount = params.length;

        if (targetMethod == null && paramCount == 1 && params[0] == Object.class && retType == boolean.class) {
          targetMethod = m;
        } else if (paramCount == 1 && params[0] == String.class && !retType.isPrimitive() && retType != String.class && retType != Object.class) {
          Class<?> extractedUserClass = extractUserClass(retType);
          if (extractedUserClass != null) {
            dataClass = retType;
            userClass = extractedUserClass;
          }
        }

        if (targetMethod != null && dataClass != null && userClass != null) break;
      }

      if (targetMethod == null || dataClass == null || userClass == null) {
        Log.e(TAG, "Required methods or classes missing in target.");
        return;
      }

      final Constructor<?> userCtor = userClass.getConstructor(String.class, String.class, String.class, String.class);
      final Constructor<?> dataCtor = dataClass.getConstructor(userClass, boolean.class, boolean.class);

      Pine.hook(targetMethod, new MethodHook() {
        @Override
        public void beforeCall(Pine.CallFrame cf) {
          try {
            Object fakeUser = userCtor.newInstance(DISCORD_ID, DISCORD_NAME, DISCORD_NAME, "");
            Object fakeData = dataCtor.newInstance(fakeUser, true, true);
            cf.args[0] = fakeData;
          } catch (Exception e) {
            Log.e(TAG, "Failed to inject fake data", e);
          }
        }
      });
      Log.i(TAG, "Flarial successfully hooked!");
      
    } catch (Exception e) {
      Log.e(TAG, "Hook execution failed", e);
    }
  }

  /**
   * Memindai Dex secara Lazy. Langsung berhenti (short-circuit) begitu target ditemukan.
   * Ini menghemat memori secara drastis karena tidak perlu membuat List<String> raksasa.
   */
  private static Class<?> findTargetClass(ClassLoader cl) {
    try {
      Field pathListField = BaseDexClassLoader.class.getDeclaredField("pathList");
      pathListField.setAccessible(true);
      Object pathList = pathListField.get(cl);
      
      Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
      dexElementsField.setAccessible(true);
      Object[] dexElements = (Object[]) dexElementsField.get(pathList);
      
      for (Object element : dexElements) {
        try {
          Field dexFileField = element.getClass().getDeclaredField("dexFile");
          dexFileField.setAccessible(true);
          Object dexFile = dexFileField.get(element);
          
          if (dexFile != null) {
            Method entriesMethod = dexFile.getClass().getDeclaredMethod("entries");
            entriesMethod.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Enumeration<String> entries = (Enumeration<String>) entriesMethod.invoke(dexFile);
            
            if (entries != null) {
              while (entries.hasMoreElements()) {
                String className = entries.nextElement();
                if (isIgnoredPkg(className)) continue;
                
                try {
                  Class<?> clazz = Class.forName(className, false, cl);
                  if (isTargetMatch(clazz)) return clazz; // Target ketemu, langsung hentikan semua proses scan!
                } catch (Throwable ignored) {}
              }
            }
          }
        } catch (Exception ignored) {}
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to scan dex elements", e);
    }
    return null;
  }

  private static boolean isTargetMatch(Class<?> clazz) {
    boolean hasTargetMethod = false, hasEmptyMethod = false, hasHelperMethod = false;
    
    for (Method m : clazz.getDeclaredMethods()) {
      if (!Modifier.isStatic(m.getModifiers())) continue;
      
      Class<?>[] params = m.getParameterTypes();
      Class<?> retType = m.getReturnType();
      int paramCount = params.length;

      if (!hasTargetMethod && paramCount == 1 && params[0] == Object.class && retType == boolean.class) {
        hasTargetMethod = true;
      } else if (!hasEmptyMethod && paramCount == 0 && retType == boolean.class) {
        hasEmptyMethod = true;
      } else if (!hasHelperMethod && paramCount == 1 && params[0] == String.class && !retType.isPrimitive() && retType != String.class && retType != Object.class) {
        if (extractUserClass(retType) != null) hasHelperMethod = true;
      }

      if (hasTargetMethod && hasEmptyMethod && hasHelperMethod) return true;
    }
    return false;
  }

  private static Class<?> extractUserClass(Class<?> dataClass) {
    try {
      for (Constructor<?> c : dataClass.getDeclaredConstructors()) {
        Class<?>[] params = c.getParameterTypes();
        if (params.length == 3 && !params[0].isPrimitive() && params[0] != String.class && params[1] == boolean.class && params[2] == boolean.class) {
          return params[0];
        }
      }
    } catch (Throwable ignored) {}
    return null;
  }

  private static boolean isIgnoredPkg(String className) {
    for (String pkg : IGNORED_PACKAGES) {
      if (className.startsWith(pkg)) return true;
    }
    return false;
  }

  private static void setupCrashHandler(final Context ctx) {
    Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
    
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
