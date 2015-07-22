package taxi.city.citytaxidriver;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.pedant.SweetAlert.SweetAlertDialog;
import io.fabric.sdk.android.services.common.Crash;
import taxi.city.citytaxidriver.core.Client;
import taxi.city.citytaxidriver.core.GlobalParameters;
import taxi.city.citytaxidriver.core.Order;
import taxi.city.citytaxidriver.core.Tariff;
import taxi.city.citytaxidriver.core.User;
import taxi.city.citytaxidriver.enums.OStatus;
import taxi.city.citytaxidriver.service.ApiService;
import taxi.city.citytaxidriver.utils.Helper;

public class MapsActivity extends BaseActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, View.OnClickListener,
        GoogleMap.OnInfoWindowClickListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    String SENDER_ID = "363431602762";
    String mRegId;
    GoogleCloudMessaging gcm;

    private static final int FINISH_ORDER_ID = 2;
    private static final int MAKE_ORDER_ID = 1;
    private static final int ORDER_DETAILS_ID = 3;
    private static final int SOS_DURATION = 10 * 60; //minutes

    private SendPostRequestTask sendTask;
    private GetUsersLocationTask locationTask;
    private CreateOrderTask createTask;

    private SweetAlertDialog pDialog;
    LinearLayout llMain;
    TextView tvDistance;
    TextView tvTime;
    TextView tvFeeTime;
    TextView tvTotalSum;


    Order order = Order.getInstance();
    ApiService api = ApiService.getInstance();
    GlobalParameters gp = GlobalParameters.getInstance();
    private ArrayList<Marker> userMarkers = new ArrayList<>();
    private HashMap<Marker, Client> mOrderMarkerMap = new HashMap<>();
    User user;

    Location prev;
    double distance;
    double price;
    double freeMeters = 0;
    long time;

    Button btnOkAction;
    Button btnSettingsCancel;
    Button btnInfo;
    Button btnWaitCancel;
    Button btnSOS;
    Button btnCustomTrip;
    LinearLayout llButtonTop;
    LinearLayout llButtonBottom;
    LinearLayout llCustomTrip;
    Dialog sosDialog;

    Location location;
    List<Polyline> polylines = new ArrayList<>();

    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    DecimalFormat df = new DecimalFormat("#.##");

    long startTime = 0;
    long pauseTotalTime = 0;
    long pauseSessionTime = 0;
    long pauseStartTime = 0;

    Handler globalTimerHandler = new Handler();
    Runnable globalTimerRunnable = new Runnable() {

        @Override
        public void run() {
            long millis = System.currentTimeMillis();
            double seconds = (double) (millis / 1000);

            if (Helper.isOrderActive(order)) {
                Helper.saveOrderPreferences(MapsActivity.this, order);
                if (seconds % 30 < 1) {
                    OStatus status = order.status;
                    if (order.sosStartTime != 0 && (long)seconds - order.sosStartTime <= SOS_DURATION) {
                        status = OStatus.SOS;
                    } else {
                        order.sosStartTime = 0;
                        sosDialog.dismiss();
                    }
                    SendPostRequest(status, order.id);
                }
            }
            if(seconds % 30 < 1) {
                getUsersLocation();
            }
            globalTimerHandler.postDelayed(this, 1000);
        }
    };

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {
            long millis = System.currentTimeMillis() - startTime;
            double seconds = (double) (millis / 1000);
            time = (long)seconds;
            order.time = time;
            updateLabels();
            timerHandler.postDelayed(this, 1000);
        }
    };

    Handler pauseHandler = new Handler();
    Runnable pauseRunnable = new Runnable() {
        @Override
        public void run() {
        long millis = System.currentTimeMillis() - pauseStartTime;

        if (order != null && order.id != 0) {
            pauseSessionTime = (long) (millis / 1000);
            long tempTime = pauseTotalTime + pauseSessionTime;
            order.waitSum = Helper.getWaitSumFromOrder(tempTime, order.tariffInfo.waitTime, order.tariffInfo.waitRatio);
            order.waitTime = pauseTotalTime + pauseSessionTime;
            tvFeeTime.setText(Helper.getTimeFromLong(pauseTotalTime + pauseSessionTime));
        }

        pauseHandler.postDelayed(this, 1000);
        }
    };

    private void updateLabels() {
        tvTime.setText(Helper.getTimeFromLong(order.time));
        tvTotalSum.setText(df.format(order.getTotalSum()));
        tvDistance.setText(df.format(distance / 1000));
        tvFeeTime.setText(Helper.getTimeFromLong(pauseTotalTime + pauseSessionTime));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        user = User.getInstance();

        if (user == null || user.id == 0) {
            Helper.getUserPreferences(this);
            ApiService.getInstance().setToken(User.getInstance().getToken());
            if (user == null || user.id == 0) {
                Toast.makeText(getApplicationContext(), "Сессия вышла, пожалуйста перезайдите", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        }
        Helper.getOrderPreferences(this, user.id);
        setUpMapIfNeeded();
        CheckEnableGPS();
        SetGooglePlayServices();
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            registerInBackground();
        }

        Initialize();
        order = Order.getInstance();
        getPreferences();

        globalTimerHandler.postDelayed(globalTimerRunnable, 0);
        updateViews();

        App.getDefaultTracker().send(new HitBuilders.EventBuilder()
                .setCategory("ui_views")
                .setLabel("main_view")
                .build());

        SetLocationRequest();
    }

    private void getPreferences() {
        if (order == null || order.id == 0) return;
        if (mMap != null && order.startPoint != null && order.clientPhone != null) {
            setClientLocation();
        }
        distance = 1000*order.distance;
        startTime = System.currentTimeMillis() - order.time*1000;
        pauseTotalTime = order.waitTime;
        if (distance > freeMeters)
            price = Math.round(order.tariffInfo.startPrice +  order.tariffInfo.ratio*(distance-freeMeters)/1000);
        else
            price = order.tariffInfo.startPrice;
        order.sum = price;
        order.waitSum = Helper.getWaitSumFromOrder(order);
        pauseStartTime = System.currentTimeMillis() - order.waitTime;
        pauseSessionTime = 0;

        if (order.status == OStatus.PENDING || order.status == OStatus.ONTHEWAY) {
            timerHandler.postDelayed(timerRunnable, 0);
            if (order.status == OStatus.PENDING) {
                pauseHandler.postDelayed(pauseRunnable, 0);
            }
        }
        tvTime.setText(Helper.getTimeFromLong(order.time));
        tvTotalSum.setText(df.format(order.getTotalSum()));
        updateLabels();
        if (System.currentTimeMillis()/1000 - order.sosStartTime < SOS_DURATION) {
            sosDialog.show();
        }
    }

    private void CheckEnableGPS(){
        String provider = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        if(!provider.equals("")){
            Toast.makeText(this, "GPS Enabled: " + provider,
                    Toast.LENGTH_LONG).show();
        }else{
            displayPromptForEnablingGPS();
        }
    }

    public void displayPromptForEnablingGPS()
    {
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(MapsActivity.this);
        final String action = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        final String message = "Активируйте геолокацию.";

        builder.setMessage(message)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int id) {
                                MapsActivity.this.startActivity(new Intent(action));
                                d.dismiss();
                            }
                        })
                .setNegativeButton("Отмена",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int id) {
                                mMap.animateCamera(CameraUpdateFactory.zoomTo(Integer.parseInt(MapsActivity.this.getString(R.string.map_default_zoom))));
                                d.cancel();
                            }
                        });
        builder.create().show();
    }

    private void SetGooglePlayServices() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void SetLocationRequest() {
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setSmallestDisplacement(15)
                .setInterval(1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1000); // 1 second, in milliseconds
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST).show();
            } else {
                finish();
            }
            return false;
        }
        return true;
    }

    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
                    }
                    mRegId = gcm.register(SENDER_ID);
                    JSONObject data = new JSONObject();
                    data.put("android_token", mRegId);
                    JSONObject result = api.patchRequest(data, "users/" + user.id + "/");
                } catch (IOException ex) {
                    Crashlytics.logException(ex);
                    msg = "err";
                } catch (JSONException ignored) {
                    Crashlytics.logException(ignored);
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {}
        }.execute(null, null, null);
    }


    private void Initialize() {
        prev = null;
        tvDistance = (TextView) findViewById(R.id.textViewDistance);
        llMain = (LinearLayout) findViewById(R.id.mainLayout);
        tvTime = (TextView) findViewById(R.id.textViewOrderTime);
        tvFeeTime = (TextView) findViewById(R.id.textViewWaitTime);
        tvTotalSum = (TextView) findViewById(R.id.textViewOrderTotalSum);

        btnInfo = (Button) findViewById(R.id.buttonAdditionalInfo);
        btnOkAction = (Button) findViewById(R.id.buttonStartAction);
        btnSettingsCancel = (Button)findViewById(R.id.buttonSettings);
        btnWaitCancel = (Button)findViewById(R.id.buttonWaitTrip);
        btnSOS = (Button)findViewById(R.id.buttonSos);
        btnCustomTrip = (Button)findViewById(R.id.buttonCustomTrip);

        llButtonTop = (LinearLayout) findViewById(R.id.linearLayoutWaitInfo);
        llButtonBottom = (LinearLayout) findViewById(R.id.linearLayoutStartCancelMap);
        llCustomTrip = (LinearLayout) findViewById(R.id.linearLayoutCustomTrip);

        btnSOS.setOnClickListener(this);
        btnInfo.setOnClickListener(this);
        btnOkAction.setOnClickListener(this);
        btnSettingsCancel.setOnClickListener(this);
        btnWaitCancel.setOnClickListener(this);
        btnCustomTrip.setOnClickListener(this);
        createSosDialog();
    }

    private void updateViews() {
        if (order == null || order.id == 0 || order.status == OStatus.NEW) {
            if (mMap != null)
                mMap.clear();
            llButtonTop.setVisibility(View.GONE);
            llCustomTrip.setVisibility(View.VISIBLE);
            btnSOS.setVisibility(View.INVISIBLE);
            btnOkAction.setText("Заказы");
            btnOkAction.setBackgroundResource(R.drawable.button_shape_dark_blue);
            btnOkAction.setTextColor(Color.WHITE);
            btnWaitCancel.setBackgroundResource(R.drawable.button_shape_dark_blue);
            llMain.setVisibility(View.GONE);
        } else {
            llCustomTrip.setVisibility(View.GONE);
            if (order.status == OStatus.ACCEPTED) {
                btnOkAction.setBackgroundResource(R.drawable.button_shape_dark_blue);
                btnOkAction.setText("На месте");
                btnSOS.setVisibility(View.INVISIBLE);
                btnInfo.setText("Доп. инфо");
                btnWaitCancel.setText("Отказ");
                btnWaitCancel.setBackgroundResource(R.drawable.button_shape_red);
                llButtonTop.setVisibility(View.VISIBLE);
            } else if (order.status == OStatus.WAITING) {
                btnOkAction.setBackgroundResource(R.drawable.button_shape_dark_blue);
                btnOkAction.setText("На борту");
                btnSOS.setVisibility(View.INVISIBLE);
                btnInfo.setText("Доп. инфо");
                btnWaitCancel.setText("Отказ");
                btnWaitCancel.setBackgroundResource(R.drawable.button_shape_red);
                llButtonTop.setVisibility(View.VISIBLE);
            } else if (order.status == OStatus.PENDING) {
                btnOkAction.setBackgroundResource(R.drawable.button_shape_dark_blue);
                btnOkAction.setText("Доставил");
                btnSOS.setVisibility(View.VISIBLE);
                btnInfo.setText("Доп. инфо");
                btnWaitCancel.setText("Продолжить");
                btnWaitCancel.setBackgroundResource(R.drawable.button_shape_dark_blue);
                btnSettingsCancel.setText("Настройки");
                btnSettingsCancel.setBackgroundResource(R.drawable.button_shape_dark_blue);
                llButtonTop.setVisibility(View.VISIBLE);
            } else if (order.status == OStatus.ONTHEWAY) {
                llMain.setVisibility(View.VISIBLE);
                btnWaitCancel.setVisibility(View.VISIBLE);
                btnWaitCancel.setText("Ожидание");
                btnWaitCancel.setBackgroundResource(R.drawable.button_shape_dark_blue);
                btnInfo.setText("Доп. инфо");
                btnOkAction.setBackgroundResource(R.drawable.button_shape_dark_blue);
                btnOkAction.setText("Доставил");
                btnSOS.setVisibility(View.VISIBLE);
                btnSettingsCancel.setText("Настройки");
                btnSettingsCancel.setBackgroundResource(R.drawable.button_shape_dark_blue);
                llButtonTop.setVisibility(View.VISIBLE);
            } else {
                mMap.clear();
                btnOkAction.setText("Заказы");
                btnOkAction.setBackgroundResource(R.drawable.button_shape_dark_blue);
                btnSOS.setVisibility(View.INVISIBLE);
                llMain.setVisibility(View.GONE);
                llButtonTop.setVisibility(View.GONE);
                llCustomTrip.setVisibility(View.VISIBLE);
            }
        }
    }

    private void SetDefaultValues() {
        pauseTotalTime = 0;
        pauseSessionTime = 0;
        distance = 0;
        price = order.tariffInfo.startPrice;
        order.sum = order.tariffInfo.startPrice;
        order.waitTime = 0;
        resetTimer();
        tvFeeTime.setText(null);
        tvDistance.setText(null);
        tvTime.setText(null);
        tvTotalSum.setText(null);

        updateLabels();
    }

    private void resetTimer() {
        order.time = 0;
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateViews();
        getUsersLocation();

        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        startLocationUpdates();

        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        this.location = location;

        if (location != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 15));
            handleNewLocation(location);
        }
    }

    private void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    private void handleNewLocation(Location location) {
        location.setAltitude(0);
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        boolean ifSession = (order.id != 0 && order.status == OStatus.ONTHEWAY);
        int zoom = ifSession ? 17 : 15;
        int bearing = ifSession ? (int)location.getBearing() : 0;
        int tilt = ifSession ? 45 : 0;

        if (ifSession && prev != null){
            distance += prev.distanceTo(location);
            if (distance > freeMeters)
                price = Math.round(order.tariffInfo.startPrice +  order.tariffInfo.ratio*(distance-freeMeters)/1000);

            order.distance = distance/1000;
            order.sum = price;
            order.endPoint = latLng;

            if (prev != null) {
                Polyline line = mMap.addPolyline(new PolylineOptions()
                        .add(new LatLng(prev.getLatitude(), prev.getLongitude()), latLng)
                        .width(12)
                        .color(0x7F0000FF)
                        .geodesic(true));

                polylines.add(line);
            }
        }

        order.endPoint = latLng;
        gp.currPosition = latLng;
        if (prev == null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        }
        prev = location;

        if (ifSession) {
            CameraPosition cp = new CameraPosition.Builder()
                    .target(latLng)
                    .zoom(zoom)
                    .bearing(bearing)
                    .tilt(tilt)
                    .build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cp));
        }
    }

    @Override
    public void onConnectionSuspended(int cause) { }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (result.hasResolution()) {
            try {
                result.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                Crashlytics.logException(e);
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateViews();
        setUpMapIfNeeded();
        if (mMap != null && order != null && order.startPoint != null && order.clientPhone != null) {
            if (order.status != OStatus.ONTHEWAY && order.status != OStatus.PENDING) setClientLocation();
        }
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        /*if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }*/
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
        handleNewLocation(this.location);
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();

            if (mMap != null) {
                if (order.startPoint != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(order.startPoint, 15));
                }
                mMap.setMyLocationEnabled(true);
                mMap.setOnInfoWindowClickListener(this);
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.buttonStartAction:
                if (order == null || order.id == 0 || order.status == OStatus.FINISHED) {
                    OpenOrder();
                } else if (order.status == OStatus.ACCEPTED) {
                    order.status = OStatus.WAITING;
                    SendPostRequest(OStatus.WAITING, order.id);
                } else if (order.status == OStatus.WAITING) {
                    SetDefaultValues();
                    order.status = OStatus.ONTHEWAY;
                    timerHandler.postDelayed(timerRunnable, 0);
                    SendPostRequest(OStatus.ONTHEWAY, order.id);
                } else if (order.status == OStatus.ONTHEWAY || order.status == OStatus.PENDING) {
                    EndTrip();
                }
                break;
            case R.id.buttonWaitTrip:
                if (order.status == OStatus.WAITING || order.status == OStatus.ACCEPTED) {
                    cancelOrder();
                } else if (order.status == OStatus.ONTHEWAY) {
                    order.status = OStatus.PENDING;
                    SendPostRequest(OStatus.PENDING, order.id);
                    pauseSessionTime = 0;
                    pauseStartTime = System.currentTimeMillis();
                    pauseHandler.postDelayed(pauseRunnable, 0);
                } else if (order.status == OStatus.PENDING) {
                    order.status = OStatus.ONTHEWAY;
                    SendPostRequest(OStatus.ONTHEWAY, order.id);
                    pauseHandler.removeCallbacks(pauseRunnable);
                    pauseTotalTime += pauseSessionTime;
                    pauseSessionTime = 0;
                }
                updateViews();
                break;
            case R.id.buttonSettings:
                goToSettings();
                break;
            case R.id.buttonAdditionalInfo:
                App.getDefaultTracker().send(new HitBuilders.EventBuilder()
                        .setCategory("ui_views")
                        .setLabel("additional_info_view")
                        .setAction("addition_info_click")
                        .build());
                if (order.status == OStatus.ACCEPTED || order.status == OStatus.WAITING) {
                    goToOrderDetails();
                } else {
                    goToFinishOrderDetails();
                }
                break;
            case R.id.buttonSos:
                makeSos();
                break;
            case R.id.buttonCustomTrip:
                createOrder();
                break;

        }
        updateViews();
    }

    private void createSosDialog() {
        sosDialog = new Dialog(this);
        sosDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sosDialog.setContentView(R.layout.fragment_sos);
        sosDialog.setCancelable(false);

        Window window = sosDialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(0xC0000000));
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.dimAmount = 0.7f;
        wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(wlp);
    }

    private void makeSos() {
        order.sosStartTime = System.currentTimeMillis()/1000;
        sosDialog.show();
        SendPostRequest(OStatus.SOS, order.id);
    }

    private void cancelOrder() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.alertdialog_decline_order);

        Window window = dialog.getWindow();
        WindowManager.LayoutParams wlp = window.getAttributes();

        wlp.gravity = Gravity.BOTTOM;
        wlp.dimAmount = 0.7f;
        wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(wlp);

        EditText reason = (EditText) dialog.findViewById(R.id.editTextDeclineReason);
        Button btnOkDialog = (Button) dialog.findViewById(R.id.buttonOkDecline);
        Button btnCancelDialog = (Button) dialog.findViewById(R.id.buttonCancelDecline);

        btnOkDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                order.status = OStatus.NEW;
                showProgress(true);
                timerHandler.removeCallbacks(timerRunnable);
                SendPostRequest(order.status, order.id);
                updateViews();
                dialog.dismiss();
            }
        });

        btnCancelDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void goToSettings() {
        checkPreviousOrder();
        Intent intent = new Intent(this, GarajActivity.class);
        startActivity(intent);
    }

    private void goToOrderDetails() {
        Intent intent = new Intent(this, OrderDetailsActivity.class);
        intent.putExtra("DATA", new Client(order, user.id, true));
        startActivityForResult(intent, ORDER_DETAILS_ID);
    }

    private void goToFinishOrderDetails() {
        Intent intent = new Intent(this, FinishOrderDetailsActivity.class);
        Client client = new Client(order, user.id, true);
        client.distance = String.valueOf(Helper.round(order.distance, 2));
        intent.putExtra("DATA", client);
        startActivity(intent);
    }

    private void EndTrip() {
        Intent intent = new Intent(this, FinishOrderDetailsActivity.class);
        Client client = new Client(order, user.id, true);
        client.distance = String.valueOf(Helper.round(order.distance, 2));
        intent.putExtra("DATA", client);
        startActivityForResult(intent, FINISH_ORDER_ID);
    }

    private void setClientLocation() {
        if (order == null || order.id == 0 || order.startPoint == null) return;
        //mMap.clear();
        mMap.addMarker(new MarkerOptions()
                .position(order.startPoint)
                .title(order.clientPhone)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.client)));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(order.startPoint, 15));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FINISH_ORDER_ID) {
            if (data != null) {
                if (data.getBooleanExtra("returnCode", false)) {
                    Toast.makeText(getApplicationContext(), "Заказ завершен", Toast.LENGTH_SHORT).show();
                } else {
                    Helper.saveOrderPreferences(MapsActivity.this, order);
                    order.clear();
                    Toast.makeText(getApplicationContext(), "Заказ завершен. Ошибка при отправке данных на сервер", Toast.LENGTH_SHORT).show();
                }
                ClearMapFromLines();
                timerHandler.removeCallbacks(timerRunnable);
                pauseHandler.removeCallbacks(pauseRunnable);
                mMap.clear();
            }
        }
        if (requestCode == MAKE_ORDER_ID) {
            if (data != null && data.getBooleanExtra("returnCode", false)) {
                if (order.status == OStatus.ONTHEWAY) {
                    SetDefaultValues();
                    timerHandler.postDelayed(timerRunnable, 0);
                }
                order.endPoint = new LatLng(location.getLatitude(), location.getLongitude());
                setClientLocation();
            }
        }
        if (requestCode == ORDER_DETAILS_ID) {
            if (order.status == OStatus.ONTHEWAY) {
                setClientLocation();
                SetDefaultValues();
                timerHandler.postDelayed(timerRunnable, 0);
            }
        }
        updateViews();
    }

    private void ClearMapFromLines() {
        for(Polyline line : polylines)
        {
            line.remove();
        }
    }

    @Override
    public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    private void checkPreviousOrder() {
        if (order.id == 0 || order.status == OStatus.FINISHED) {
            if (Helper.isOrderPreferenceActive(MapsActivity.this, user.id)) {
                getPreferences();
                SendPostRequest(order.status, order.id);
            }
        }
    }

    private void OpenOrder() {
        checkPreviousOrder();
        Intent intent = new Intent(this, OrderActivity.class);
        intent.putExtra("NEW", true);
        startActivityForResult(intent, MAKE_ORDER_ID);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void SendPostRequest(OStatus status, int orderId) {
        if (sendTask != null || status == null || orderId == 0) {
            return;
        }

        if (location != null) {
            order.endPoint = new LatLng(location.getLatitude(), location.getLongitude());
        }

        if (order.status == OStatus.ACCEPTED || order.status == OStatus.WAITING) {
            order.time = 0;
        }

        sendTask = new SendPostRequestTask(status, orderId);
        sendTask.execute((Void) null);
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Client client = mOrderMarkerMap.get(marker);

        if (client == null) {


            final String title = marker.getTitle();
            if (title.length() != 13) return;

            SweetAlertDialog pDialog = new SweetAlertDialog(MapsActivity.this, SweetAlertDialog.WARNING_TYPE);
            pDialog.setTitleText("Вы хотите позвонить?")
                    .setContentText(title)
                    .setConfirmText("Позвонить")
                    .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                        @Override
                        public void onClick(SweetAlertDialog sDialog) {
                            Intent callIntent = new Intent(Intent.ACTION_CALL);
                            callIntent.setData(Uri.parse("tel:" + title));
                            startActivity(callIntent);
                            sDialog.dismissWithAnimation();
                        }
                    })
                    .setCancelText("Отмена")
                    .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                        @Override
                        public void onClick(SweetAlertDialog sDialog) {
                            sDialog.dismissWithAnimation();
                        }
                    })
                    .show();
        } else {
            Intent intent = new Intent(this, OrderDetailsActivity.class);
            intent.putExtra("DATA", client);
            intent.putExtra("ACTIVE", false);
            startActivityForResult(intent, ORDER_DETAILS_ID);
        }

    }

    private class SendPostRequestTask extends AsyncTask<Void, Void, JSONObject> {
        private OStatus status;
        private int driver;
        private int mOrderId = 0;

        SendPostRequestTask(OStatus type, int orderId) {
            status = type;
            driver = user.id;
            mOrderId = orderId;
        }

        @Override
        protected JSONObject doInBackground(Void... params) {

            JSONObject data = new JSONObject();
            JSONObject result = null;
            try {
                String travelTime = Helper.getTimeFromLong(order.time, order.status);
                data.put("status", status);
                data.put("driver", driver);
                data.put("order_sum", status == OStatus.NEW ? 0 : order.getTravelSum());
                data.put("wait_time_price", status == OStatus.NEW ? 0 : order.getWaitSum());
                data.put("address_stop", status == OStatus.NEW ? JSONObject.NULL : Helper.getFormattedLatLng(order.endPoint));
                data.put("wait_time", Helper.getTimeFromLong(order.waitTime));
                data.put("order_distance", Helper.round(order.distance, 2));
                data.put("order_travel_time", travelTime);
                result = api.patchRequest(data, "orders/" + mOrderId + "/");

            } catch (JSONException e) {
                Crashlytics.logException(e);
                e.printStackTrace();
                result = null;
            }
            return result;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            sendTask = null;
            showProgress(false);
            try {
                if (Helper.isSuccess(result)) {
                    Toast.makeText(getApplicationContext(), "Заказ обновлён", Toast.LENGTH_SHORT).show();
                    if (status == OStatus.FINISHED || status == OStatus.NEW) {
                        Helper.destroyOrderPreferences(MapsActivity.this, user.id);
                        order.clear();
                    }
                } else if (Helper.isBadRequest(result)) {
                    new SweetAlertDialog(MapsActivity.this, SweetAlertDialog.WARNING_TYPE)
                            .setTitleText("Клиент отменил заказ")
                            .setContentText("")
                            .setConfirmText("Ок")
                            .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                                @Override
                                public void onClick(SweetAlertDialog sweetAlertDialog) {
                                    sweetAlertDialog.dismissWithAnimation();
                                }
                            })
                            .show();
                    Helper.destroyOrderPreferences(MapsActivity.this, user.id);
                    order.clear();
                    updateViews();
                } else {
                    Toast.makeText(MapsActivity.this, "Не удалось отправить данные на сервер", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Crashlytics.logException(e);
                e.printStackTrace();
                Toast.makeText(MapsActivity.this, "Внутрення ошибка", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onCancelled() {
            sendTask = null;
        }
    }

    private void getUsersLocation() {
        if (locationTask != null) {
            return;
        }

        locationTask = new GetUsersLocationTask();
        locationTask.execute((Void) null);
    }

    private class GetUsersLocationTask extends AsyncTask<Void, Void, JSONArray> {
        GetUsersLocationTask() {}

        @Override
        protected JSONArray doInBackground(Void... params) {
            JSONArray array = new JSONArray();
            try {
                if (gp.currPosition != null) {
                    JSONObject data = new JSONObject();
                    data.put("cur_position", Helper.getFormattedLatLng(gp.currPosition));
                    JSONObject updateDriverLocation = api.patchRequest(data, "users/" + user.id + "/");
                }

            JSONObject sosObject = api.getArrayRequest(null, "info_orders/?status=sos");
                if (Helper.isSuccess(sosObject)) {
                    JSONArray sosArray = sosObject.getJSONArray("result");
                    for (int j = 0; j < sosArray.length(); j++) {
                        array.put(sosArray.getJSONObject(j));
                    }
                }
                if (order.id == 0) {
                    JSONObject newObject = api.getArrayRequest(null, "info_orders/?status=new");
                    if (Helper.isSuccess(newObject)) {
                        JSONArray newArray = newObject.getJSONArray("result");
                        for (int j = 0; j < newArray.length(); j++) {
                            JSONObject object = newArray.getJSONObject(j);
                            array.put(parseTariff(object));
                        }
                    }
                }
            } catch (JSONException ignored) {
                Crashlytics.logException(ignored);
            }
            return array;
        }

        @Override
        protected void onPostExecute(JSONArray result) {
            locationTask = null;
            displayUsersOnMap(result);
        }

        @Override
        protected void onCancelled() {
            locationTask = null;
        }
    }

    /** TODO: Remove this shit in future **/
    private JSONObject parseTariff(JSONObject object) {
        int tariff = 1;
        try {
            tariff = object.getJSONObject("tariff").getInt("id");
        } catch (JSONException e) {
            tariff = 1;
        }
        try {
            object.put("tariff", tariff);
        } catch (JSONException ignored) {
            Crashlytics.logException(ignored);
        }

        return object;
    }

    private void displayUsersOnMap(JSONArray usersList) {
        if (usersList.length() < 0) return;
        cleanMapFromMarkers();

        LatLng userLocation;
        OStatus userStatus;
        String phone;

        for (int i = 0; i < usersList.length(); ++i) {
            try {
                JSONObject userJson = usersList.getJSONObject(i);
                userStatus = Helper.getStatus(userJson.getString("status"));
                userLocation = userStatus == OStatus.NEW ? Helper.getLatLng(userJson.getString("address_start")) :
                        Helper.getLatLng(userJson.getString("address_stop"));


                Client client = new Client(userJson, user.id, true);
                if (userLocation == null) continue;

                if (userStatus == OStatus.SOS) {
                    phone = userJson.getJSONObject("driver").getString("phone");
                    if (phone.equals(user.phone)) continue;
                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(userLocation)
                            .title(phone)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.sos_icon)));
                    userMarkers.add(marker);
                    mOrderMarkerMap.put(marker, null);
                } else if (order.id == 0) {
                    boolean isClient;
                    if (!userJson.has("client") || !userJson.getString("client").equals("null") || userJson.getString("client").isEmpty()) {
                        isClient = true;
                    } else {
                        isClient = false;
                    }
                    LatLng driverPosition = GlobalParameters.getInstance().currPosition;
                    LatLng clientPosition = Helper.getLatLng(client.startPoint);
                    Location driverLocation = new Location("");
                    String distance = "";
                    if (driverPosition != null) {
                        driverLocation.setLatitude(driverPosition.latitude);
                        driverLocation.setLongitude(driverPosition.longitude);

                        Location clientLocation = new Location("");
                        clientLocation.setLatitude(clientPosition.latitude);
                        clientLocation.setLongitude(clientPosition.longitude);
                        distance = Helper.getFormattedDistance(driverLocation.distanceTo(clientLocation)/1000) + "км";
                    }
                    final String title = client.addressStart + " \n" + distance;
                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(userLocation)
                            .title(title)
                            .icon(isClient ? BitmapDescriptorFactory.fromResource(R.drawable.client) :
                                    BitmapDescriptorFactory.fromResource(R.drawable.client_site)));
                            userMarkers.add(marker);
                    mOrderMarkerMap.put(marker, client);
                }

            } catch (JSONException ignored) {
                Crashlytics.logException(ignored);
            }
        }

        if (order.id != 0 && order.clientPhone != null && order.startPoint != null) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(order.startPoint)
                    .title(order.clientPhone)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.client)));
            userMarkers.add(marker);
        }
    }

    private void cleanMapFromMarkers() {
        for (int i = 0; i < userMarkers.size() ; i++){
            Marker marker = userMarkers.get(i);
            marker.remove();
        }
        userMarkers.clear();
        mOrderMarkerMap.clear();
    }

    public void showProgress(final boolean show) {
        if (show) {
            pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
            pDialog.getProgressHelper()
                    .setBarColor(Color.parseColor("#A5DC86"));
            pDialog.setTitleText("Обновление");
            pDialog.setCancelable(true);
            pDialog.show();
        } else {
            if (pDialog != null) pDialog.dismissWithAnimation();
        }
    }

    private void createOrder() {
        if (createTask != null) {
            return;
        }

        showProgress(true);
        createTask = new CreateOrderTask();
        createTask.execute((Void) null);
    }

    private class CreateOrderTask extends AsyncTask<Void, Void, JSONObject> {
        CreateOrderTask() {}

        @Override
        protected JSONObject doInBackground(Void... params) {
            try {
                int tariffId = 2;
                JSONObject tariffObject = null;
                for (int i = 0; i < 10; ++i) {
                    tariffObject = api.getRequest("", "tariffs/" + String.valueOf(tariffId));
                    if (Helper.isSuccess(tariffObject)) {
                        break;
                    }
                }
                if (!Helper.isSuccess(tariffObject))
                    return null;

                JSONObject data = new JSONObject();
                data.put("tariff", tariffId);
                data.put("driver", user.id);
                data.put("client", JSONObject.NULL);
                data.put("address_start", Helper.getFormattedLatLng(gp.currPosition));
                data.put("address_stop", Helper.getFormattedLatLng(gp.currPosition));
                data.put("status", OStatus.ONTHEWAY.toString());
                data.put("client_phone", user.phone);

                JSONObject orderObject = api.createOrder(data, "orders/");
                if (Helper.isSuccess(orderObject)) {
                    orderObject.put("tariff_info", tariffObject);
                }

                return orderObject;

            } catch (JSONException e) {
                Crashlytics.logException(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            createTask = null;
            showProgress(false);
            try {
                if (Helper.isSuccess(result)) {
                    Helper.setOrder(result);
                    SetDefaultValues();
                    timerHandler.postDelayed(timerRunnable, 0);
                    updateViews();
                } else {
                    Toast.makeText(MapsActivity.this, "Не удалось создать заказ. Попробуйте еще раз", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Crashlytics.logException(e);
            }
        }

        @Override
        protected void onCancelled() {
            locationTask = null;
            showProgress(false);
        }
    }
}