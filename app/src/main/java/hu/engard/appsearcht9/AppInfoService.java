package hu.engard.appsearcht9;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AppInfoService extends Service {

  private List<AppInfo> appinfos = new ArrayList<>();
  private final static String APPINFO_CACHE_FILENAME="appinfo_cache.ser";

  // public interface to Activity
  public List<AppInfo> getAppinfos() {
    Log.i("getAppInfos()", "called");
    return appinfos;
  }

  public interface AppInfoChangeListener {
    void appInfoChanged();
  }

  private AppInfoChangeListener onChangeListener;

  // public interface to Activity
  public void setOnChangeListener(AppInfoChangeListener listener) {
    onChangeListener = listener;
  }

  private void updateAppInfoList() {
    List<AppInfo> tempAppinfos = new ArrayList<>();
    PackageManager pm = getPackageManager();
    Log.i("AIS", "updateAppInfoList() - building apps list");
    final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    final List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

    for (ResolveInfo app : apps) {
      tempAppinfos.add(new AppInfo(app, getPackageManager()));
    }
    Log.i("AIS", "updateAppInfoList() - saving cache");

    writeObjectToFile(this, tempAppinfos, APPINFO_CACHE_FILENAME);

    Log.i("AIS", "updateAppInfoList() - cache saved");
    appinfos = tempAppinfos;
    if (onChangeListener != null) {
      onChangeListener.appInfoChanged();
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();

    Log.i("AIS", "onCreate() loading cache file");
    List<AppInfo> tempAppinfos = (List<AppInfo>) readObjectFromFile(this, APPINFO_CACHE_FILENAME);
    if (tempAppinfos!=null) {
      Log.i("AIS", "onCreate() cache file loaded: " + tempAppinfos);
      appinfos=tempAppinfos;
    } else {
      Log.i("AIS", "onCreate() cache file not found");
    }
    Log.i("AIS", "onCreate() finished");
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i("AIS", "onStartCommand");
    super.onStartCommand(intent, flags, startId);
    new Thread(new Runnable() {
      @Override
      public void run() {
        updateAppInfoList();
      }
    }).start();
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    Log.i("AIS", "onDestroy");
    super.onDestroy();
  }

  private final IBinder binder = new LocalBinder();

  public class LocalBinder extends Binder {
    AppInfoService getService() {
      return AppInfoService.this;
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.i("AIS", "onBind");
    return binder;
  }

  public static class AppInfo implements Serializable {
    public String label;
    transient public Bitmap bitmap;
    public List<MainActivity.T9.T9Data> t9list;
    public String packageName;
    public String activityName;

    public AppInfo(ResolveInfo app, PackageManager pm) {
      CharSequence labelSeq = app.loadLabel(pm);
      label = labelSeq != null ? labelSeq.toString() : "";
      Drawable icon = app.loadIcon(pm);
      if (icon != null) {
        bitmap=AppInfoService.drawableToBitmap(icon);
      }
      t9list = MainActivity.T9.labelToT9(label);
      packageName = app.activityInfo.applicationInfo.packageName;
      activityName = app.activityInfo.name;
    }

    // @return position of match, negative if does not match
    public int matches(String beginning) {
      for (MainActivity.T9.T9Data t9 : t9list) {
        if (t9.string.startsWith(beginning)) return t9.start;
      }
      return -1;
    }

    public String dump() {
      return "Label: " + label + "\nT9 list: " + TextUtils.join(", ", t9list);
    }

    public String toString() {
      return label;
    }

    // serializable

    private static final long serialVersionUID = 1L;

    private void readObject(
        ObjectInputStream stream
    ) throws ClassNotFoundException, IOException {
      stream.defaultReadObject();
      bitmap=BitmapFactory.decodeStream(stream);
    }

    private void writeObject(
        ObjectOutputStream stream
    ) throws IOException {
      stream.defaultWriteObject();
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
    }
  }

  // from http://stackoverflow.com/questions/3035692/how-to-convert-a-drawable-to-a-bitmap
  public static Bitmap drawableToBitmap(Drawable drawable) {
    if (drawable instanceof BitmapDrawable) {
      return ((BitmapDrawable) drawable).getBitmap();
    }
    int width = drawable.getIntrinsicWidth();
    width = width > 0 ? width : 1;
    int height = drawable.getIntrinsicHeight();
    height = height > 0 ? height : 1;

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    drawable.draw(canvas);

    return bitmap;
  }

  // persistence

  // copied from http://stackoverflow.com/questions/5816695/android-sharedpreferences-with-serializable-object
  public static void writeObjectToFile(Context context, Object object, String filename) {
    ObjectOutputStream objectOut = null;
    try {
      FileOutputStream fileOut = context.openFileOutput(filename, Activity.MODE_PRIVATE);
      objectOut = new ObjectOutputStream(fileOut);
      objectOut.writeObject(object);
      fileOut.getFD().sync();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (objectOut != null) {
        try {
          objectOut.close();
        } catch (IOException e) {
          // do nowt
        }
      }
    }
  }

  public static Object readObjectFromFile(Context context, String filename) {
    ObjectInputStream objectIn = null;
    Object object = null;
    try {
      FileInputStream fileIn = context.getApplicationContext().openFileInput(filename);
      objectIn = new ObjectInputStream(fileIn);
      object = objectIn.readObject();
    } catch (FileNotFoundException e) {
      // Do nothing
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    } finally {
      if (objectIn != null) {
        try {
          objectIn.close();
        } catch (IOException e) {
          // do nowt
        }
      }
    }
    return object;
  }
}
