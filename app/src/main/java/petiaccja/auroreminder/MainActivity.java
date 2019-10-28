package petiaccja.auroreminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Calendar;


public class MainActivity extends AppCompatActivity {
    private Geomap m_intensityMap = null;
    private Location m_location = null;

    private Messenger m_serviceMessenger = null;
    private boolean m_isServiceBound = false;
    private final Messenger m_incomingMessenger = new Messenger(new IncomingHandler());

    private double m_chance = 0;
    private double m_coverage = 0;

    public final double EARTH_RADIUS = 6371000;
    public final double AURORA_ALTITUDE = 100000;

    private final String CHANNEL_ID = "mainChannel";
    private int m_notificationId = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set layout.
        setContentView(R.layout.activity_main);

        // Start background service.
        Intent myIntent = new Intent(this, AuroraService.class);
        startService(myIntent);

        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 1, myIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 15 * 60 * 1000, 15 * 60 * 1000, pendingIntent);

        // Create a notification channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel( 	CHANNEL_ID, "defChannel", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("default channel");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        drawMap();
        connectService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        disconnectService();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && m_serviceMessenger != null) {
            refreshCoordinates();
        }
    }


    public void onRefresh(View v) {
        refreshCoordinates();
        refreshMap();
    }

    public void onShowSettings(View v) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }


    public void refreshMap() {
        if (m_serviceMessenger != null) {
            Message msg;
            try {
                msg = Message.obtain(null, AuroraService.MSG_FORCE_REFRESH_MAP);
                m_serviceMessenger.send(msg);

                msg = Message.obtain(null, AuroraService.MSG_REQUEST_MAP);
                msg.replyTo = m_incomingMessenger;
                m_serviceMessenger.send(msg);
            }
            catch (RemoteException ex) {
                // Service crashed on requesting data.
            }
        }
    }


    public void refreshCoordinates() {
        if (m_serviceMessenger != null) {
            Message msg;
            try {
                msg = Message.obtain(null, AuroraService.MSG_FORCE_REFRESH_COORDINATES);
                m_serviceMessenger.send(msg);

                msg = Message.obtain(null, AuroraService.MSG_REQUEST_COORDINATES);
                msg.replyTo = m_incomingMessenger;
                m_serviceMessenger.send(msg);
            }
            catch (RemoteException ex) {
                // Service crashed on requesting data.
            }
        }
    }


    public void recalcMeasures() {
        if (m_intensityMap != null && m_location != null) {
            m_coverage = 0.0f;
            m_chance = 0.0f;

            double latitudeSweep = getSweepAngle(10.f, AURORA_ALTITUDE);
            double latitude = m_location.getLatitude();
            double longitude = m_location.getLongitude();
            double sumWeights = 0;

            // Sweep through a vertical line on earth's surface. Since auroras are at 100km, we can see them up to
            // 6 latitude degrees away from us near the horizon.
            for (double lateff = Math.max(0, latitude-latitudeSweep); lateff <= Math.min(90, latitude+latitudeSweep); lateff+=0.2f) {
                double chance = m_intensityMap.At((float)lateff, (float)longitude);

                double latOffset = Math.abs(lateff - latitude);
                double l = latOffset/360.0*2*Math.PI*EARTH_RADIUS;
                double b = AURORA_ALTITUDE;
                double weight = Math.atan(l/b) - Math.atan((l-5000)/b);

                m_chance = Math.max(chance, m_chance);
                m_coverage += chance * weight;
                sumWeights += weight;
            }
            m_coverage /= sumWeights;
        }

        if (m_chance > 0.05) {
            showNotification(m_chance, m_coverage);
        }

        TextView chanceText = findViewById(R.id.textChance);
        TextView coverageText = findViewById(R.id.textCoverage);
        chanceText.setText(String.format("%.2f", 100*m_chance) + "%");
        coverageText.setText(String.format("%.2f", 100*m_coverage) + "%");
    }


    public double getSweepAngle(double horizonAngleDegrees, double auroraHeightMeters) {
        final double R = EARTH_RADIUS;
        double m = Math.tan(Math.toRadians(horizonAngleDegrees));
        double h = auroraHeightMeters;
        double x1 = (-2*m*R + Math.sqrt(4*m*m*R*R + 4*(m*m + 1)*(2*R*h + h*h))) / (2*m*m + 2);
        double dtheta = Math.PI/2 - Math.atan((R + m*x1) / x1);
        return Math.toDegrees(dtheta);
    }

    private void showNotification(double chance, double coverage) {
        String text = String.format("Chance: %.2f%%, sky coverage: %.2f%%.", 100*m_chance, 100*m_coverage);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icons8_northern_lights_96)
                .setContentTitle("Aurora may be visible!")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancelAll();
        notificationManager.notify(++m_notificationId, builder.build());
    }


    public class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AuroraService.MSG_REQUEST_MAP:
                    Geomap map = (Geomap)msg.obj;
                    TextView progressText = findViewById(R.id.progressText);
                    if (msg.arg1 == AuroraService.ERROR_CONNECTION) {
                        progressText.setText("No internet");
                        Toast.makeText(getApplicationContext(), "No internet connection", Toast.LENGTH_SHORT).show();
                    }
                    else if (msg.arg1 == AuroraService.ERROR_PARSE) {
                        progressText.setText("Invalid data");
                        Toast.makeText(getApplicationContext(), "Server sent invalid intensity data", Toast.LENGTH_SHORT).show();
                    }
                    else if (msg.arg1 == AuroraService.ERROR_WIFIONLY) {
                        progressText.setText("3G disabled");
                        Toast.makeText(getApplicationContext(), "Enable fetching through cellular network", Toast.LENGTH_SHORT).show();
                    }
                    if (map != null) {
                        if (map != m_intensityMap) {
                            Toast.makeText(getApplicationContext(), "Map updated", Toast.LENGTH_SHORT).show();
                            Calendar now = Calendar.getInstance();
                            int hr = now.get(Calendar.HOUR_OF_DAY);
                            int min = now.get(Calendar.MINUTE);
                            progressText.setText("Updated " + Integer.toString(hr) + ":" + Integer.toString(min));
                        }
                        else {
                            Toast.makeText(getApplicationContext(), "No new map", Toast.LENGTH_SHORT).show();
                        }
                        m_intensityMap = map;
                        drawMap();
                        recalcMeasures();
                    }
                    break;
                case AuroraService.MSG_REQUEST_COORDINATES:
                    Location location = (Location)msg.obj;
                    m_location = location;
                    TextView textLatitude = findViewById(R.id.textLatitude);
                    TextView textLongitude = findViewById(R.id.textLongitude);
                    textLatitude.setText(String.format("%.5f", m_location.getLatitude()));
                    textLongitude.setText(String.format("%.5f", m_location.getLongitude()));
                    drawMap();
                    recalcMeasures();
                    break;
            }
        }
    }


    private final ServiceConnection m_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            m_serviceMessenger = new Messenger(service);

            // Register ourselves as a client for the service.
            try {
                Message msg = Message.obtain(null, AuroraService.MSG_REGISTER_CLIENT);
                msg.replyTo = m_incomingMessenger;
                m_serviceMessenger.send(msg);
            } catch (RemoteException ex) {
                // Service crashed before we could communicate.
                // We'll get a disconnect soon.
            }

            // Refresh views.
            refreshMap();

            // Request permission for location services.
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
            else {
                refreshCoordinates();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            // We get this when the service crashes.
            m_serviceMessenger = null;
        }
    };

    public void connectService() {
        bindService(new Intent(this, AuroraService.class), m_connection, Context.BIND_AUTO_CREATE);
        m_isServiceBound = true;
    }


    public void disconnectService() {
        if (m_isServiceBound) {
            // Unregister ourselves.
            if (m_serviceMessenger != null) {
                try {
                    Message msg = Message.obtain(null, AuroraService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = m_incomingMessenger;
                    m_serviceMessenger.send(msg);
                } catch (RemoteException ex) {
                    // If the service crashed, we are unregistered, right?
                }
            }

            unbindService(m_connection);
            m_isServiceBound = false;
        }
    }


    private void drawMap() {
        final int WIDTH = 512, HEIGHT = 512;

        // Background and final pixels.
        int[] pixels = new int[WIDTH * HEIGHT];
        int[] bgPixels = new int[WIDTH * HEIGHT];

        // Load background image.
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.outWidth = WIDTH;
        decodeOptions.outHeight = HEIGHT;
        decodeOptions.inScaled = false;
        Bitmap background = BitmapFactory.decodeResource(getResources(), R.drawable.northern_hemisphere, decodeOptions);
        background.getPixels(bgPixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);

        // Create bitmap for final image.
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);


        // Overlay aurora information if available.
        if (m_intensityMap != null) {
            for (int ix = 0; ix < WIDTH; ++ix) {
                for (int iy = 0; iy < HEIGHT; ++iy) {
                    double x = (2.f * ix / (float) (WIDTH - 1) - 1.f);
                    double y = -(2.f * iy / (float) (HEIGHT - 1) - 1.f);

                    double latitude = 0;
                    double longitude = 0;
                    float value = 0.0f;

                    double[] spherical = CartesianToSpherical(-y, x);
                    if (spherical[0] != Double.POSITIVE_INFINITY) {
                        latitude = Math.toDegrees(spherical[0]);
                        longitude = Math.toDegrees(spherical[1]);
                        value = m_intensityMap.At((float)latitude, (float)longitude);
                    }
                    value = Math.min(1.f, Math.max(0.f, value));

                    int bgPixel = bgPixels[iy * WIDTH + ix];
                    int[] bgChannels = {0xff & (bgPixel >>> 16), 0xff & (bgPixel >>> 8), 0xFF & bgPixel};
                    //int[] bgChannels = {(int)(latitude/90.f*255.f + 0.5f), (int)(m_intensityMap.DbgInterpolLong((float)longitude)*255.f), (int)((longitude < 0 ? longitude+360.f : longitude)/360.f*255.f)};

                    float alpha = (float)(Math.atan(2.5f*value)/Math.atan(2.5f));
                    float red = value;
                    float green = (float)Math.pow(1.0f-value, 0.2f);
                    float blue = 0.0f;

                    int[] channels = {
                            (int) (0.5f + bgChannels[0] * 0.8f*(1.f - alpha) + 255.f*red*alpha),
                            (int) (0.5f + bgChannels[1] * 0.8f*(1.f - alpha) + 255.f*green*alpha),
                            (int) ((0.8f*bgChannels[2]) + 255.f*blue*alpha)
                    };

                    int pixel = 0xFF000000 | (channels[0] << 16) | (channels[1] << 8) | channels[2];


                    pixels[iy * WIDTH + ix] = pixel;
                }
            }
        }
        // Use only background if no aurora information.
        else {
            // Display a red X to indicate no info.
            int xsize = Math.min(40, Math.min(WIDTH, HEIGHT));
            for (int i = 0; i<xsize; ++i) {
                pixels[i*WIDTH + i] = 0xFFFF0000;
                pixels[(xsize - i - 1)*WIDTH + i] = 0xFFFF0000;
            }
            pixels = bgPixels;
        }

        // Draw location indicator point and sweep line.
        if (m_location != null) {
            double latitude = m_location.getLatitude();
            double longitude = m_location.getLongitude();
            double[] cartesian = SphericalToCartesian(Math.toRadians(latitude), Math.toRadians(longitude));
            double x = cartesian[1]; // View is rotated by -90 degrees.
            double y = cartesian[0];

            int px = (int)((x+1)/2 * WIDTH);
            int py = (int)((y+1)/2 * HEIGHT);

            for (int ix = Math.max(0, px-5); ix < Math.min(WIDTH, px+5); ++ix) {
                for (int iy = Math.max(0, py-5); iy < Math.min(HEIGHT, py+5); ++iy) {
                    int dx = (px-ix), dy = (py-iy);
                    if (dx*dx + dy*dy < 25)
                        pixels[iy*WIDTH + ix] = 0xFFFF0000;
                }
            }

            double latitudeSweep = getSweepAngle(10.f, 100000);
            for (double lateff = Math.max(0, latitude-latitudeSweep); lateff <= Math.min(90, latitude+latitudeSweep); lateff+=0.2f) {
                cartesian = SphericalToCartesian(Math.toRadians(lateff), Math.toRadians(longitude));
                x = cartesian[1]; // View is rotated by -90 degrees.
                y = cartesian[0];
                px = (int)((x+1)/2 * WIDTH);
                py = (int)((y+1)/2 * HEIGHT);
                if (0 < px && px < WIDTH && 0 < py && py < HEIGHT)
                    pixels[py*WIDTH + px] = 0xFFFF0000;
            }
        }


        // Display image
        bitmap.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        ImageView view = findViewById(R.id.imageView);
        view.setImageBitmap(bitmap);
    }

    private static double[] CartesianToSpherical(double x, double y) {
        double zsq = 1 - x * x - y * y;
        if (zsq < 0.0000001) {
            return new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        }
        double z = Math.sqrt(zsq);
        double theta = Math.PI / 2.0 - Math.acos(z);
        double phi = Math.atan2(y, x);
        return new double[]{theta, phi};
    }

    private static double[] SphericalToCartesian(double theta, double phi) {
        double x = Math.sin(Math.PI/2 - theta)*Math.cos(phi);
        double y = Math.sin(Math.PI/2 - theta)*Math.sin(phi);
        return new double[]{x, y};
    }
}
