package petiaccja.auroreminder;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;


public class AuroraService extends Service implements LocationListener {
    private ArrayList<Messenger> m_clients = new ArrayList<>();
    private final Messenger m_messenger = new Messenger(new IncomingHandler());
    private Geomap m_intensityMap;
    private RefreshMapTask m_refreshTask = null;
    private Location m_location = null;

    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_FORCE_REFRESH_MAP = 101;
    public static final int MSG_FORCE_REFRESH_COORDINATES = 102;
    public static final int MSG_REQUEST_MAP = 111;
    public static final int MSG_REQUEST_COORDINATES = 112;

    protected static final int PHASE_DOWNLOAD = 1;
    protected static final int PHASE_PARSE = 2;

    public static final int ERROR_CONNECTION = 3;
    public static final int ERROR_PARSE = 4;
    public static final int ERROR_WIFIONLY = 5;
    public static final int ERROR_SUCCESS = 0;

    private static final int CONNECTION_WIFI = 1;
    private static final int CONNECTION_CELL = 2;
    private static final int CONNECTION_NONE = 3;



    @Override
    public IBinder onBind(Intent intent) {
        return m_messenger.getBinder();
    }


    @Override
    public void onCreate() {
        //Toast.makeText(this, "Service created!", Toast.LENGTH_SHORT).show();

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            m_location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        //Toast.makeText(this, "Service startcmd.", Toast.LENGTH_SHORT).show();

        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        //Toast.makeText(this, "Service stopped.", Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onLocationChanged(Location location) {
        m_location = location;
        Message msg = Message.obtain(null, MSG_REQUEST_COORDINATES);
        msg.obj = m_location;
        for (Messenger client : m_clients) {
            try { client.send(msg); } catch (RemoteException ignore) {}
        }
    }


    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }


    @Override
    public void onProviderEnabled(String s) {

    }


    @Override
    public void onProviderDisabled(String s) {

    }


    public class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    m_clients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    m_clients.remove(msg.replyTo);
                    break;
                case MSG_FORCE_REFRESH_MAP:
                    if (m_refreshTask == null) {
                        m_refreshTask = new RefreshMapTask();
                        m_refreshTask.execute();
                    }
                    break;
                case MSG_REQUEST_MAP:
                    if (msg.replyTo != null) {
                        if (m_refreshTask != null) {
                            m_refreshTask.m_requesters.add(msg.replyTo);
                        }
                        else {
                            Message reply = Message.obtain(null, MSG_REQUEST_MAP);
                            reply.obj = m_intensityMap;
                            try {
                                msg.replyTo.send(reply);
                            } catch (RemoteException ignore) {
                            }
                        }
                    }
                    break;
                case MSG_REQUEST_COORDINATES:
                    if (msg.replyTo != null) {
                        if (m_location != null) {
                            Message reply = Message.obtain(null, MSG_REQUEST_COORDINATES);
                            reply.obj = m_location;
                            try {
                                msg.replyTo.send(reply);
                            } catch (RemoteException ignore) {
                            }
                        }
                    }
                case MSG_FORCE_REFRESH_COORDINATES:
                    LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    if (ActivityCompat.checkSelfPermission(AuroraService.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, AuroraService.this, null);
                    }
            }
        }
    }


    private int getConnectionType() {
        final ConnectivityManager connMgr = (ConnectivityManager)
                this.getSystemService(Context.CONNECTIVITY_SERVICE);

        final android.net.NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (wifi.isConnectedOrConnecting()) {
            return CONNECTION_WIFI;
        } else if (mobile.isConnectedOrConnecting()) {
            return CONNECTION_CELL;
        } else {
            return CONNECTION_NONE;
        }
    }


    private class RefreshMapTask extends AsyncTask<Void, Integer, Geomap> {
        private int m_error = ERROR_SUCCESS;
        private long m_lastMillis = 0;
        private Messenger m_requestingClient = null;
        public ArrayList<Messenger> m_requesters = new ArrayList<>();
        public boolean m_forceCellular = false; // Set true to force download over cellular network.

        RefreshMapTask() {}
        RefreshMapTask(Messenger requestingClient) {
            m_requestingClient = requestingClient;
        }

        @Override
        protected Geomap doInBackground(Void... args) {
            byte[] text = DownloadMap();
            if (text == null) {
                return null;
            }
            return ParseMap(text);
        }

        @Override
        protected void onPreExecute() {
            m_lastMillis = System.currentTimeMillis();
        }


        @Override
        protected void onProgressUpdate(Integer... progress) {
            // Limit refresh rate to 100ms.
            long millis = System.currentTimeMillis();
            if (millis < m_lastMillis + 100) {
                return;
            }

            // Send progress reports to requesting client.
            if (m_requestingClient != null) {
                // TODO
            }
        }


        @Override
        protected void onPostExecute(Geomap map) {
            if (map != null) {
                m_intensityMap = map;
            }

            for (Messenger target : m_requesters) {
                Message reply = Message.obtain(null, MSG_REQUEST_MAP);
                reply.obj = m_intensityMap;
                reply.arg1 = m_error;
                try {
                    target.send(reply);
                } catch (RemoteException ignore) {
                }
            }

            m_refreshTask = null;
            //Toast.makeText(AuroraService.this, "Forced map refresh.", Toast.LENGTH_SHORT).show();
        }


        private byte[] DownloadMap() {
            int connectionType = getConnectionType();
            if (connectionType == CONNECTION_NONE) {
                m_error = ERROR_CONNECTION;
                return null;
            }
            else if (connectionType == CONNECTION_CELL && !m_forceCellular) {
                m_error = ERROR_WIFIONLY;
                return null;
            }

            URL url;
            DataInputStream dis = null;
            int fileSize = 2098950; // File size is always the same value.

            try {
                url = new URL("http://services.swpc.noaa.gov/text/aurora-nowcast-map.txt");
                dis = new DataInputStream(url.openStream());

                byte[] buffer = new byte[10240];
                int bytesRead = 0;
                int bytesReadTotal = 0;

                ByteArrayOutputStream os = new ByteArrayOutputStream();

                while ((bytesRead = dis.read(buffer)) > 0) {
                    os.write(buffer, 0, bytesRead);
                    bytesReadTotal += bytesRead;
                    publishProgress(100 * bytesReadTotal / fileSize);
                }

                publishProgress(PHASE_DOWNLOAD, 100);
                return os.toByteArray();
            }
            catch (MalformedURLException ex) {
                // Hardcoded URL is never malformed.
            }
            catch (IOException ex) {
                // Set progress to error.
                m_error = ERROR_CONNECTION;
            }
            finally {
                if (dis != null)
                    try {
                        dis.close();
                    }
                    catch (Exception ignore) {
                    }
            }
            return null;
        }


        private Geomap ParseMap(byte[] text) {
            final int HORIZONTAL = 1024;
            final int VERTICAL = 512;
            final int LINE_ENDING = 1;

            float[] data = new float[HORIZONTAL*VERTICAL];

            // Remove leading comment lines.
            int ch = 0;
            while (ch < text.length && text[ch] == '#') {
                while (ch < text.length && text[ch] != '\n') {
                    ++ch;
                }
                ++ch;
            }

            // Check if remaining file contains all vertical and horizontal data.
            if (text.length < VERTICAL*(HORIZONTAL*4 + LINE_ENDING) + ch) {
                m_error = ERROR_PARSE;
                return null;
            }

            // Extract vertical and horizontal data.
            for (int y=0; y<VERTICAL; ++y) {
                for (int x=0; x<HORIZONTAL; ++x) {
                    int idx = y*(HORIZONTAL*4 + LINE_ENDING) + x*4 + ch;
                    int value = 0;
                    if (text[idx + 1] != ' ') {
                        value += 100 * (text[idx + 1] - 0x30); // 0x30 is '0'
                    }
                    if (text[idx + 2] != ' ') {
                        value += 10 * (text[idx + 2] - 0x30); // 0x30 is '0'
                    }
                    if (text[idx + 3] != ' ') {
                        value += (text[idx + 3] - 0x30); // 0x30 is '0'
                    }
                    data[y*HORIZONTAL + (x+HORIZONTAL/2)%HORIZONTAL] = value / 100.f;
                }
                publishProgress(PHASE_PARSE, 100*y/(VERTICAL-1));
            }

            return new Geomap(data, HORIZONTAL, VERTICAL);
        }
    }
}
