package hu.engard.appsearcht9;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener, AppInfoService.AppInfoChangeListener {
  private static String STAT_PREF_KEY = "appStats";
  private static String[] charBtns = {"ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};
  private static int[] buttonIds = {R.id.Button1, R.id.Button2, R.id.Button3,
      R.id.Button4, R.id.Button5, R.id.Button6,
      R.id.Button7, R.id.Button8, R.id.Button9,
      R.id.ButtonClr, R.id.ButtonLaunch, R.id.ButtonBack
  };

  private List<AppInfoService.AppInfo> appinfos = new ArrayList<>();
  private String searchString = new String();
  Map<String, AppStat> appStats = new HashMap<>();
  ArrayAdapter<SearchHit> adapter;
  private final List<SearchHit> currentList = new ArrayList<>();

  @Override
  public void appInfoChanged() {
    Log.i("MA", "appInfoChanged() called");
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        updateAppInfoService();
      }
    });
  }

  private static class SearchHit {
    public AppInfoService.AppInfo appInfo;
    public int hitPosition;

    public SearchHit(AppInfoService.AppInfo appInfo, int hitPosition) {
      this.appInfo = appInfo;
      this.hitPosition = hitPosition;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.i("MA", "onCreate");
    super.onCreate(savedInstanceState);

    startService(new Intent(this, AppInfoService.class));
    bindService(new Intent(this, AppInfoService.class), serviceConnection, BIND_AUTO_CREATE);

    setContentView(R.layout.activity_main);
    PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    updateLayout();

    // register button handlers
    for (int i = 0; i < 9; i++) {
      Button b = (Button) findViewById(buttonIds[i]);
      if (i > 0) b.setText(charBtns[i - 1]);
      final int index = i + 1;
      b.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          addToSearchString(index);
        }
      });
    }
    (findViewById(R.id.ButtonLaunch)).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View arg0) {
        if (currentList.size() > 0) onItemClick(0);
      }
    });
    (findViewById(R.id.ButtonClr)).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View arg0) {
        searchString = new String();
        updateList();
      }
    });
    (findViewById(R.id.ButtonBack)).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View arg0) {
        if (searchString.length() > 0) {
          searchString = searchString.substring(0, searchString.length() - 1);
          updateList();
        }
      }
    });

    // setup gridview
    adapter = new CustomAdapter(this, R.layout.row_grid, currentList);
    final GridView view = (GridView) findViewById(R.id.gridView1);
    view.setAdapter(adapter);
    view.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MainActivity.this.onItemClick(position);
      }
    });
    Log.i("MA", "onCreate() finished");
  }

  private void updateLayout() {
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

    int keyPadding = (int) dip2px(sharedPref.getInt("keyPadding", 0) + 11);
    Log.i("updateLayout", "keyPadding: " + sharedPref.getInt("keyPadding", 0) + ", px: " + keyPadding);

    // left & right paddings max value (100) means half screen
    int leftPadding = sharedPref.getInt("leftPadding", 0);
    int rightPadding = sharedPref.getInt("rightPadding", 0);
    if (leftPadding + rightPadding > 150) {
      int delta=(leftPadding + rightPadding-150)/2;
      leftPadding-=delta;
      rightPadding-=delta;
    }
    TableLayout table = (TableLayout) findViewById(R.id.keyboardTable);
    table.setPadding(leftPadding *getResources().getDisplayMetrics().widthPixels/200,
        0,
        rightPadding *getResources().getDisplayMetrics().widthPixels/200,
        (int) dip2px(sharedPref.getInt("bottomPadding", 0)*2));
    // layout setup
    for (int id : buttonIds) {
      Button b = ((Button) findViewById(id));
      b.setBackgroundColor(Color.BLACK);
      b.setTextColor(Color.WHITE);
      b.setPadding(0, keyPadding, 0, keyPadding);
      ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) b.getLayoutParams();
      if (params != null) {
        params.width = 0;
        params.setMargins(1, 1, 1, 1);
      }
      b.requestLayout();
    }
  }

  @Override
  protected void onStart() {
    Log.i("MA", "onStart");
    super.onStart();
    searchString = new String();
    initAppStats();
  }

  @Override
  protected void onStop() {
    Log.i("MA", "onStop");
    super.onStop();
    saveAppStats();
    if (bound) {
      unbindService(serviceConnection);
      bound=false;
    }
  }

  private void updateAppInfoService() {
    appinfos = appInfoService.getAppinfos();
    if (appinfos.size()>0) {
      findViewById(R.id.loadingPanel).setVisibility(View.GONE);
    } else {
      findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
    }
    Collections.sort(appinfos, new AppStatComparator(appStats));
    updateList();
  }

  void addToSearchString(int i) {
    if (currentList.size() == 0 && searchString.length() > 0) return;
    searchString = searchString + String.valueOf(i);
    updateList();
  }

  public void onItemClick(int position) {
    AppInfoService.AppInfo appInfo = adapter.getItem(position).appInfo;

    if (appStats.get(appInfo.activityName) == null)
      appStats.put(appInfo.activityName, new AppStat(appInfo.activityName));
    appStats.get(appInfo.activityName).started();

    ComponentName name = new ComponentName(appInfo.packageName, appInfo.activityName);
    Log.i("onItemClick", "componentname: " + name.toString());
    Intent i = new Intent(Intent.ACTION_MAIN);
    i.addCategory(Intent.CATEGORY_LAUNCHER);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    i.setComponent(name);
    startActivity(i);
    finish();
  }

  void updateList() {
    currentList.clear();
    if (searchString.length() == 0) {
      for (AppInfoService.AppInfo app : appinfos) {
        currentList.add(new SearchHit(app, 0));
      }
    } else {
      for (AppInfoService.AppInfo app : appinfos) {
        int hit = app.matches(searchString);
        if (hit >= 0) currentList.add(new SearchHit(app, hit));
      }
    }
    adapter.notifyDataSetChanged();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onMenuItemSelected(int featureId, MenuItem item) {
    //		return super.onMenuItemSelected(featureId, item);
    switch (item.getItemId()) {
      case R.id.action_settings: {
        startActivity(new Intent(this, SettingsActivity.class));
        break;
      }
    }
    return true;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                        String key) {
    updateLayout();
  }

  /**
   * Custom adapter for labeled icon
   * based on http://www.androidhub4you.com/2013/07/custom-grid-view-example-in-android.html
   */
  public class CustomAdapter extends ArrayAdapter<SearchHit> {
    Context context;
    int layoutResourceId;
    List<SearchHit> data;

    public CustomAdapter(Context context, int layoutResourceId,
                         List<SearchHit> data) {
      super(context, layoutResourceId, data);
      this.layoutResourceId = layoutResourceId;
      this.context = context;
      this.data = data;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View row = convertView;
      RecordHolder holder;

      if (row == null) {
        LayoutInflater inflater = ((Activity) context).getLayoutInflater();
        row = inflater.inflate(layoutResourceId, parent, false);
        holder = new RecordHolder();
        holder.txtTitle = (TextView) row.findViewById(R.id.item_text);
        holder.imageItem = (ImageView) row.findViewById(R.id.item_image);
        row.setTag(holder);
      } else {
        holder = (RecordHolder) row.getTag();
      }

      SearchHit item = data.get(position);
      SpannableString txt = new SpannableString(item.appInfo.label);
      txt.setSpan(new ForegroundColorSpan(0xff33b5e5), item.hitPosition, item.hitPosition + MainActivity.this.searchString.length(), 0);
      holder.txtTitle.setText(txt);
      holder.imageItem.setImageBitmap(item.appInfo.bitmap);
      return row;

    }

    class RecordHolder {
      TextView txtTitle;
      ImageView imageItem;

    }
  }

  public float dip2px(float dp) {
//    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    return dp*getResources().getDisplayMetrics().density;
  }

  // Service connection

  private ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      Log.i("MA", "onServiceConnected()");
      AppInfoService.LocalBinder binder = (AppInfoService.LocalBinder) service;
      appInfoService = binder.getService();
      appInfoService.setOnChangeListener(MainActivity.this);

      updateAppInfoService();
      bound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      bound = false;
    }
  };
  private AppInfoService appInfoService;
  private boolean bound;

  // App statistics

  private void initAppStats() {
    appStats.clear();
    String[] statLines = getPreferences(MODE_PRIVATE).getString(STAT_PREF_KEY, "").split("\\n");
    for (String line : statLines) {
      if (line.trim().length() > 0) {
        AppStat stat = AppStat.fromString(line);
        Log.i("stats", "Loading stat: " + stat);
        if (stat!=null) appStats.put(stat.activityName, stat);
      }
    }
  }

  private void saveAppStats() {
    List<String> lines = new ArrayList<>(appStats.size());
    for (AppStat appStat : appStats.values()) {
      lines.add(appStat.toString());
    }
    Log.i("stats", "Saving stats content:\n" + TextUtils.join("\n", lines));
    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(STAT_PREF_KEY, TextUtils.join("\n", lines));
    editor.apply();

  }

  private static class AppStat {
    public final String activityName;
    // the list is because of historic reasons -- currently only the last item is used
    private final List<Long> startDates = new ArrayList<>();
    private int counter=0;

    public AppStat(String activityName) {
      this.activityName = activityName.replace(':', '_');
    }

    public void started() {
      counter++;
      startDates.add(new Date().getTime());
    }

    public String toString() {
      return activityName + ": " + counter + (startDates.size()==0 ? "" : ", " + startDates.get(startDates.size() - 1));
    }

    public static int compare(AppStat asa, AppStat asb) {
      if (asa.counter != asb.counter) {
        return asa.counter > asb.counter ? -1 : 1;
      }
      if (asa.startDates.size()>0 && asb.startDates.size()>0) {
        return asa.startDates.get(asa.startDates.size()-1) > asb.startDates.get(asb.startDates.size()-1) ? -1 : 1;
      }
      return 0;
    }

    public static AppStat fromString(String from) {
      int colonPos = from.indexOf(':');
      if (colonPos < 0) return null;
      AppStat ret = new AppStat(from.substring(0, colonPos));
      from = from.substring(colonPos + 1);
      String[] arr=from.split(", ");
      if (arr.length<2) {
        return ret;
      }
      try {
        ret.counter=Integer.valueOf(arr[0].trim());
        arr= Arrays.copyOfRange(arr, 1, arr.length);
        for (String s: arr) {
          ret.startDates.add(Long.valueOf(s.trim()));
        }
      } catch (NumberFormatException e) {
        Log.w("stats", "Invalid AppStat shared prefs", e);
      }
      return ret;
    }
  }

  // T9

  public static class T9 {
    public static class T9Data implements Serializable {
      public String string;
      public int start; // start in the original string

      public T9Data(String string, int start) {
        super();
        this.string = string;
        this.start = start;
      }

      public String toString() {
        return string + "{" + start + "}";
      }
    }

    public static List<T9Data> labelToT9(String label) {
      List<Integer> startPositions = new ArrayList<>();
      startPositions.add(0);

      Collator coll = Collator.getInstance();
      coll.setStrength(Collator.PRIMARY);
      StringBuilder t9 = new StringBuilder(label.length() + 1);
      char prevChar = 0;
      char prevT9 = 0;

      for (int i = 0; i < label.length(); i++) {
        String one = label.substring(i, i + 1);
        char c = label.charAt(i);
        if (Character.isDigit(c)) {
          // the digit 0 goes together with 1
          t9.append(c == '0' ? '1' : c);
        } else if (coll.compare(one, "a") < 0) {
          t9.append('1');
        } else {
          boolean found = false;
          for (int j = 0; j < charBtns.length; j++) {
            if (coll.compare(one, charBtns[j].substring(charBtns[j].length() - 1)) <= 0) {
              t9.append(String.valueOf(j + 2));
              found = true;
              break;
            }
          }
          if (!found) t9.append('1');
        }

        if (i > 0) {
          char curT9 = t9.charAt(t9.length() - 1);
          if ((prevT9 == '1' || Character.isDigit(prevChar)) && (curT9 > '1' && !Character.isDigit(c))) {
            // after space & punctuation & number
            startPositions.add(i);
          } else if (Character.isUpperCase(c)
              && label.length() > i + 1
              && (Character.isLowerCase(prevChar) || Character.isLowerCase(label.charAt(i + 1)))) {
            // CamelCase
            startPositions.add(i);
          }
        }
        prevChar = c;
        prevT9 = t9.charAt(t9.length() - 1);
      }

      List<T9Data> res = new ArrayList<>();
      for (int start : startPositions) {
        res.add(new T9Data(t9.substring(start), start));
      }
      return res;
    }
  }

  public static class AppStatComparator implements Comparator<AppInfoService.AppInfo> {
    public AppStatComparator(Map<String, AppStat> appStats) {
      this.appStats = appStats;
      mCollator.setStrength(Collator.PRIMARY);
    }

    public final int compare(AppInfoService.AppInfo a, AppInfoService.AppInfo b) {
      AppStat asa = appStats.get(a.activityName);
      AppStat asb = appStats.get(b.activityName);
      if (asa != null && asb != null) {
        int ret=AppStat.compare(asa, asb);
        if (ret!=0) return ret;
      }
      if (asa != null && asb == null) return -1;
      if (asb != null && asa == null) return 1;

      return mCollator.compare(a.label, b.label);
    }

    private final Collator mCollator = Collator.getInstance();
    private Map<String, AppStat> appStats;
  }

}
