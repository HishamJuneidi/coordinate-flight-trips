package com.example.location;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
//AWS
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;

import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.simple.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyStore;
import java.util.ArrayList;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks
        , GoogleApiClient.OnConnectionFailedListener, SensorEventListener,LocationListener {

    /*
    https://developer.android.com/training/location/receive-location-updates
    src https://medium.com/@droidbyme/get-current-location-using-fusedlocationproviderclient-in-android-cb7ebf5ab88e
     */
    //private BufferedWriter writer;
    private Location location;
    private Location old_location;
    private long now;
    private long old;
    private TextView locationTv;
    private GoogleApiClient googleApiClient;
    private static final int PLAY_SERVICES_REQUEST = 9000;
    private LocationRequest locationRequest;
    private long UPDATE_INTERVAL = 1000, FASTEST_INTERVAL = 1000;
    private int pas = 100;

    //
    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissionsRejected = new ArrayList<>();
    private ArrayList<String> permissions = new ArrayList<>();
    private SensorManager mSensorManager;
    private float currDegree;
    //
    private static final int ALL_PERMISSIONS_RESULT = 1011;
    private File file;

    //AWS INTEGRATION
    private static final String CLIENT_ENDPOINT = "a36j8m0te8qq4v-ats.iot.us-east-1.amazonaws.com";       // replace <prefix> and <region> with your own
    private static final String CLIENT_ID = "AppV1";                              // replace with your own client ID. Use unique client IDs for concurrent connections.
    private static final String KEYSTORE_NAME = "iot-keystore.bks";
    private static final String KEYSTORE_PASS = "ilovesystems";
    private static final String CERTIFICATE_ID = "appv1";

    private AWSIotMqttManager mqttManager;
    private EditText editText;
    private Button but;
    private Button but2;
    private Button but3;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);//Test
        locationTv = findViewById(R.id.location);
        //
        permissions.add(ACCESS_FINE_LOCATION);
        permissions.add(READ_EXTERNAL_STORAGE);
        permissions.add(WRITE_EXTERNAL_STORAGE);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        permissionsToRequest = permissionsToRequest(permissions);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size() > 0) {
                requestPermissions(permissionsToRequest.
                        toArray(new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
            }
        }
        now = System.currentTimeMillis()/1000;
        googleApiClient = new GoogleApiClient.Builder(this).
                addApi(LocationServices.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        //etUpCSV("data-"+now);

        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {

            //If it isn't mounted - we can't write into it.
            return;
        }
        String filenameExternal = "wind_data_" + now + ".csv";
        //Create a new file that points to the root directory, with the given name:
        file = new File(getExternalFilesDir(null), filenameExternal);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }


        //AWS SETUP
        mqttManager = new AWSIotMqttManager(CLIENT_ID, CLIENT_ENDPOINT);
        mqttManager.setReconnectRetryLimits(5,30);
        mqttManager.setMaxAutoReconnectAttempts(-1);
        String keystorePath = getFilesDir().getPath();
        KeyStore clientKeyStore = null;

        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, KEYSTORE_NAME)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(CERTIFICATE_ID,
                        keystorePath, KEYSTORE_NAME, KEYSTORE_PASS)) {
                    //load keystore from file into memory
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(CERTIFICATE_ID, keystorePath,
                            KEYSTORE_NAME, KEYSTORE_PASS);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        //FIXME keystore could be null
       mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback() {
           @Override
            public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {
                System.out.printf("Status = %s\n", status);
            }
        });


        editText = (EditText) findViewById(R.id.editText);
        but = (Button) findViewById(R.id.getLocation);
        but.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String t = editText.getText().toString();
                pas = Integer.parseInt(t);

            }
        });
        but3 = (Button) findViewById(R.id.button3);
        but3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {

                webView.setVisibility(View.VISIBLE);
                editText.setVisibility(View.GONE);
                but.setVisibility(View.GONE);
                but3.setVisibility(View.GONE);
                locationTv.setVisibility(View.GONE);
            }
        });
        but2 = (Button) findViewById(R.id.button2);
        but2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {

                webView.setVisibility(View.GONE);
                editText.setVisibility(View.VISIBLE);
                but.setVisibility(View.VISIBLE);
                but3.setVisibility(View.VISIBLE);
                locationTv.setVisibility(View.VISIBLE);
            }
        });

        webView = (WebView) findViewById(R.id.webview);
        webView.loadUrl("https://markjstein.github.io/aviation/");
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setVisibility(View.GONE);
    }


    private ArrayList<String> permissionsToRequest(ArrayList<String> wantedPermissions) {
        ArrayList<String> result = new ArrayList<>();

        for (String perm : wantedPermissions) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!checkPlayServices()) {
            locationTv.setText("Need to Install Google Play Services!");
        }
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_GAME);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch(requestCode) {
            case ALL_PERMISSIONS_RESULT:
                for (String perm : permissionsToRequest) {
                    if (!hasPermission(perm)) {
                        permissionsRejected.add(perm);
                    }
                }

                if (permissionsRejected.size() > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                            new AlertDialog.Builder(MainActivity.this).
                                    setMessage("These permissions are mandatory to get your location. " +
                                            "You need to allow them.").
                                    setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                requestPermissions(permissionsRejected.
                                                        toArray(new String[permissionsRejected.size()]),
                                                        ALL_PERMISSIONS_RESULT);
                                            }
                                        }
                                    }).setNegativeButton("Cancel", null).create().show();

                            return;
                        }
                    }

                } else {
                    if (googleApiClient != null) {
                        googleApiClient.connect();
                    }
                }

                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(googleApiClient != null && googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient,this);
            googleApiClient.disconnect();
        }
        mSensorManager.unregisterListener((SensorEventListener) this);
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_REQUEST);
            } else {
                finish();
            }

            return false;
        }

        return true;
    }
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if(ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if(location != null) {
            locationTv.setText(location.toString());
        }
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        if(ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Enable permissions.",Toast.LENGTH_SHORT).show();
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient,locationRequest,this);

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if(location != null) {
            this.old_location = this.location;
            this.location = location;
            old = now;
            now = System.currentTimeMillis()/1000;
            float dist = this.old_location.distanceTo(this.location);
            float time_taken = (float) (now-old);
            float speed = dist / (time_taken);
            float bearing = location.getBearing();
            double mph = speed*2.2369;
            bearing = bearing - 90;
            if (bearing < 0) {
                bearing = bearing + 360;
            }
            float degree = 0f;
            degree = currDegree;
            double[] wind_info = calculateAeroInfo(mph,bearing,degree,pas);
            //time, lat, long, alt, ground speed, mag direction, ground heading,airspeed, wind direction, wind speed
            String[] temp = {Double.toString(now),Double.toString(location.getLatitude()),
                    Double.toString(location.getLongitude()),
                    Double.toString(location.getAltitude()*3.28084),Double.toString(mph), Float.toString(degree),
                    Float.toString(bearing),Double.toString(wind_info[0]),Double.toString(wind_info[1]),Double.toString(wind_info[2])};
            locationTv.setText(String.format("Ground Speed: %.2f MPH\nAirspeed: %.2f\nGPS Heading: %.2f\nMagnetic Heading: " +
                   "%.2f\nWind Direction: %.2f\nWind Speed: %.2f",mph,wind_info[0],bearing,degree,wind_info[1],wind_info[2]));
            //https://stackoverflow.com/questions/51565897/saving-files-in-android-for-beginners-internal-external-storage
            {
                //Checking the availability state of the External Storage.
                //This point and below is responsible for the write operation
                FileOutputStream outputStream = null;
                try {

                    //second argument of FileOutputStream constructor indicates whether
                    //to append or create new file if one exists
                    outputStream = new FileOutputStream(file, true);
                    for(int i=0;i<temp.length;i++) {
                        outputStream.write(temp[i].getBytes());
                        outputStream.write(',');

                    }
                    outputStream.write('\n');
                    outputStream.flush();
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                JSONObject json = new JSONObject();
                json.put("altitude", location.getAltitude());
                json.put("latitude", location.getLatitude());
                json.put("longitude", location.getLongitude());
                json.put("time", Long.toString(now));
                json.put("uid", "example");
                json.put("wind_speed", wind_info[2]);
                json.put("wind_direction",wind_info[1]);

               mqttManager.publishString(json.toJSONString(), "flightdata/periodic", AWSIotMqttQos.QOS0);
            }
        }


    }

    /*
Calculates the useful information from things.
*/
    public double[] calculateAeroInfo(double mph, float bearing, float degree, double airspeedin) {
        // mph = speed
        // bearing = gps heading
        // degree = magnetic heading
        double airspeed = airspeedin;
        float trueHeadingFromMag = (450-degree)%360;
        float trueHeadingFromGps = (450-bearing)%360;

        double w_y = (mph*Math.cos(Math.toRadians(trueHeadingFromGps))) - (airspeed*Math.cos(Math.toRadians(trueHeadingFromMag)));
        double w_x = (mph*Math.sin(Math.toRadians(trueHeadingFromGps))) - (airspeed*Math.sin(Math.toRadians(trueHeadingFromMag)));
        double windSpeed = Math.sqrt((w_y*w_y)+(w_x*w_x));

        double w_a = (Math.toDegrees(Math.atan2(w_x,w_y)))%360;
        if(w_a<0) w_a+=360;
        w_a = (450-w_a) %360;
        double[] ret = {airspeed,w_a,windSpeed};

        return ret;
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        float degree = Math.round(event.values[0]);
        currDegree = degree;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    
}
