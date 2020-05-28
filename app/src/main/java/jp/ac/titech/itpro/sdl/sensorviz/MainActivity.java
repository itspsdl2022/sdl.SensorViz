package jp.ac.titech.itpro.sdl.sensorviz;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener, Runnable {
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static long GRAPH_REFRESH_PERIOD_MS = 20;

    private static List<Integer> DELAYS = new ArrayList<>();
    static {
        DELAYS.add(SensorManager.SENSOR_DELAY_FASTEST);
        DELAYS.add(SensorManager.SENSOR_DELAY_GAME);
        DELAYS.add(SensorManager.SENSOR_DELAY_UI);
        DELAYS.add(SensorManager.SENSOR_DELAY_NORMAL);
    }

    private static final float ALPHA = 0.75f;

    private TextView typeView;
    private TextView infoView;
    private GraphView xView, yView, zView;

    private SensorManager manager;
    private Sensor sensor;

    private final Handler handler = new Handler();
    private final Timer timer = new Timer();

    private float rx, ry, rz;
    private float vx, vy, vz;

    private int rate;
    private int accuracy;
    private long prevTimestamp;

    private int delay = SensorManager.SENSOR_DELAY_NORMAL;
    private int type = Sensor.TYPE_ACCELEROMETER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        typeView = findViewById(R.id.type_view);
        infoView = findViewById(R.id.info_view);
        xView = findViewById(R.id.x_view);
        yView = findViewById(R.id.y_view);
        zView = findViewById(R.id.z_view);

        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager == null) {
            Toast.makeText(this, R.string.toast_no_sensor_manager, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        sensor = manager.getDefaultSensor(type);
        if (sensor == null) {
            String text = getString(R.string.toast_no_sensor_available, sensorTypeName(type));
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        manager.registerListener(this, sensor, delay);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(MainActivity.this);
            }
        }, 0, GRAPH_REFRESH_PERIOD_MS);
    }

    @Override
    public void run() {
        infoView.setText(getString(R.string.info_format, accuracy, rate));
        xView.addData(rx, vx);
        yView.addData(ry, vy);
        zView.addData(rz, vz);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        timer.cancel();
        manager.unregisterListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
        int title = 0;
        switch (delay) {
        case SensorManager.SENSOR_DELAY_FASTEST:
            title = R.string.menu_delay_fastest;
            break;
        case SensorManager.SENSOR_DELAY_GAME:
            title = R.string.menu_delay_game;
            break;
        case SensorManager.SENSOR_DELAY_UI:
            title = R.string.menu_delay_ui;
            break;
        case SensorManager.SENSOR_DELAY_NORMAL:
            title = R.string.menu_delay_normal;
            break;
        }
        menu.findItem(R.id.menu_delay).setTitle(title);
        menu.findItem(R.id.menu_accelerometer).setEnabled(type != Sensor.TYPE_ACCELEROMETER);
        menu.findItem(R.id.menu_gyroscope).setEnabled(type != Sensor.TYPE_GYROSCOPE);
        menu.findItem(R.id.menu_magnetic_field).setEnabled(type != Sensor.TYPE_MAGNETIC_FIELD);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
        case R.id.menu_delay:
            Log.d(TAG, "menu_delay");
            int index = DELAYS.indexOf(delay);
            delay = DELAYS.get((index + 1) % DELAYS.size());
            break;
        case R.id.menu_accelerometer:
            type = Sensor.TYPE_ACCELEROMETER;
            typeView.setText(R.string.menu_accelerometer);
            break;
        case R.id.menu_gyroscope:
            type = Sensor.TYPE_GYROSCOPE;
            typeView.setText(R.string.menu_gyroscope);
            break;
        case R.id.menu_magnetic_field:
            type = Sensor.TYPE_MAGNETIC_FIELD;
            typeView.setText(R.string.menu_magnetic_field);
            break;
        }
        invalidateOptionsMenu();
        changeConfig();
        return super.onOptionsItemSelected(item);
    }

    private void changeConfig() {
        manager.unregisterListener(this);
        Sensor sensor = manager.getDefaultSensor(type);
        manager.registerListener(this, sensor, delay);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        rx = event.values[0];
        ry = event.values[1];
        rz = event.values[2];
        Log.i(TAG, "x=" + rx + ", y=" + ry + ", z=" + rz);

        vx = ALPHA * vx + (1 - ALPHA) * rx;
        vy = ALPHA * vy + (1 - ALPHA) * ry;
        vz = ALPHA * vz + (1 - ALPHA) * rz;
        long ts = event.timestamp;
        rate = (int) (ts - prevTimestamp) / 1000;
        prevTimestamp = ts;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "onAccuracyChanged");
        this.accuracy = accuracy;
    }

    private String sensorTypeName(int sensorType) {
        try {
            Class klass = Sensor.class;
            for (Field field : klass.getFields()) {
                String fieldName = field.getName();
                if (fieldName.startsWith("TYPE_") && field.getInt(klass) == sensorType)
                    return fieldName;
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}