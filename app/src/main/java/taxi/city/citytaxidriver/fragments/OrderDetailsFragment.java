package taxi.city.citytaxidriver.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.maps.CameraUpdateFactory;

import org.json.JSONException;
import org.json.JSONObject;

import cn.pedant.SweetAlert.SweetAlertDialog;
import taxi.city.citytaxidriver.OrderActivity;
import taxi.city.citytaxidriver.R;
import taxi.city.citytaxidriver.core.Client;
import taxi.city.citytaxidriver.core.GlobalParameters;
import taxi.city.citytaxidriver.core.Order;
import taxi.city.citytaxidriver.core.User;
import taxi.city.citytaxidriver.enums.OStatus;
import taxi.city.citytaxidriver.service.ApiService;
import taxi.city.citytaxidriver.utils.Helper;

public class OrderDetailsFragment extends Fragment implements View.OnClickListener {

    public static final String CLIENT_KEY = "DATA";
    public static final String IS_ACTIVE_ORDER_KEY = "ACTIVE";
    public static final String CLIENT_EXTRA_KEY = "CLIENT";


    private Client mClient;
    private boolean isActive;
    private ApiService api = ApiService.getInstance();
    private Order order = Order.getInstance();
    private User user = User.getInstance();
    private GlobalParameters gp = GlobalParameters.getInstance();
    private SendPostRequestTask mTask = null;
    SweetAlertDialog pDialog;

    TextView tvClientPhone;
    TextView tvClientPhoneLabel;
    ImageButton imgBtnCallClient;

    Button btnTakeMap;
    Button btnCancel;
    Button btnOk;
    Button btnShowOnMap;

    LinearLayout llBtnMap;

    public OrderDetailsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_order_details_activity, container, false);
        mClient = (Client) getActivity().getIntent().getSerializableExtra(CLIENT_KEY);
        isActive = getActivity().getIntent().getBooleanExtra(IS_ACTIVE_ORDER_KEY, false);

        TextView tvAddressStart = (TextView) rootView.findViewById(R.id.textViewStartAddress);
        tvClientPhone = (TextView) rootView.findViewById(R.id.textViewClientPhone);
        tvClientPhoneLabel = (TextView) rootView.findViewById(R.id.textViewClientPhoneLabel);
        TextView tvAddressStop = (TextView) rootView.findViewById(R.id.textViewStopAddress);
        TextView tvDescription = (TextView) rootView.findViewById(R.id.textViewDescription);
        TextView tvFixedPrice = (TextView) rootView.findViewById(R.id.textViewFixedPrice);
        LinearLayout llFixedPrice = (LinearLayout) rootView.findViewById(R.id.linearLayoutFixedPrice);
        llBtnMap = (LinearLayout) rootView.findViewById(R.id.linearLayoutMapInfo);
        imgBtnCallClient = (ImageButton) rootView.findViewById(R.id.imageButtonCallClient);

        tvAddressStart.setText(mClient.addressStart);
        tvClientPhone.setText(mClient.phone);
        tvAddressStop.setText(mClient.addressEnd);
        tvDescription.setText(mClient.description);
        double fixedPrice = Helper.getDouble(mClient.fixedPrice);
        tvFixedPrice.setText((int) fixedPrice + " сом");
        if (fixedPrice < 50) llFixedPrice.setVisibility(View.GONE);

        btnTakeMap = (Button) rootView.findViewById(R.id.buttonTakeMap);
        btnCancel = (Button) rootView.findViewById(R.id.buttonActionCancel);
        btnOk = (Button) rootView.findViewById(R.id.buttonActionOk);

        btnShowOnMap = (Button) rootView.findViewById(R.id.btnShowOnMap);
        btnShowOnMap.setOnClickListener(this);

        btnTakeMap.setOnClickListener(this);
        btnCancel.setOnClickListener(this);
        btnOk.setOnClickListener(this);
        imgBtnCallClient.setOnClickListener(this);


        updateViews();

        return rootView;
    }

    private void updateViews() {
        if (mClient.status.equals(OStatus.NEW.toString())) {
            //llBtnMap.setVisibility(View.GONE);
            btnOk.setVisibility(View.INVISIBLE);
            btnShowOnMap.setVisibility(View.VISIBLE);
            btnTakeMap.setText("Взять");
            btnCancel.setText("Назад");
            tvClientPhone.setVisibility(View.GONE);
            tvClientPhoneLabel.setVisibility(View.GONE);
            imgBtnCallClient.setVisibility(View.GONE);
            btnCancel.setBackgroundResource(R.drawable.button_shape_yellow);
            btnCancel.setTextColor(getResources().getColor(R.color.blacktext2));
        } else if (mClient.status.equals(OStatus.ACCEPTED.toString())) {
            //llBtnMap.setVisibility(View.VISIBLE);
            btnOk.setVisibility(View.VISIBLE);
            btnShowOnMap.setVisibility(View.INVISIBLE);
            btnTakeMap.setText("На карте");
            btnCancel.setText("Отказ");
            tvClientPhone.setVisibility(View.VISIBLE);
            tvClientPhoneLabel.setVisibility(View.VISIBLE);
            imgBtnCallClient.setVisibility(View.VISIBLE);
            btnCancel.setBackgroundResource(R.drawable.button_shape_red);
            btnCancel.setTextColor(getResources().getColor(R.color.white));
        } else {
            //llBtnMap.setVisibility(View.VISIBLE);
            tvClientPhone.setVisibility(View.VISIBLE);
            tvClientPhoneLabel.setVisibility(View.VISIBLE);
            imgBtnCallClient.setVisibility(View.VISIBLE);
            //llBtnMap.setVisibility(View.GONE);
            btnOk.setVisibility(View.INVISIBLE);
            btnShowOnMap.setVisibility(View.INVISIBLE);
            btnCancel.setText("Отказ");
            btnCancel.setBackgroundResource(R.drawable.button_shape_red);
            btnCancel.setTextColor(getResources().getColor(R.color.white));
        }
    }

    @Override
    public void onClick(View v) {
        if (order == null || order.id == 0 || order.status == null) {
            if (mClient.status.equals(OStatus.ACCEPTED.toString())
                    || mClient.status.equals(OStatus.WAITING.toString())) {
                Toast.makeText(getActivity().getApplicationContext(), "Заказ уже выбран или отменён", Toast.LENGTH_SHORT).show();
                getActivity().finish();
            }
        }
        switch (v.getId()) {
            case R.id.buttonTakeMap:
                if (mClient.status.equals(OStatus.NEW.toString())) {
                    if(!isGPSEnabled()){
                        displayPromptForEnablingGPS();
                        break;
                    }
                    showProgress(true);
                    SendPostRequest(OStatus.ACCEPTED);
                } else {
                    Intent intent = new Intent();
                    intent.putExtra("returnCode", true);
                    getActivity().setResult(isActive ? 3 : 1, intent);
                    getActivity().finish();
                }
                break;
            case R.id.buttonActionOk:
                if (mClient.status.equals(OStatus.ACCEPTED.toString())) {
                    showProgress(true);
                    mClient.status = OStatus.WAITING.toString();
                    order.status = OStatus.WAITING;
                    SendPostRequest(OStatus.WAITING);
                }
                break; /*else if (mClient.status.equals(OStatus.WAITING.toString())) {
                    showProgress(true);
                    mClient.status = OStatus.ONTHEWAY.toString();
                    order.status = OStatus.ONTHEWAY;
                    SendPostRequest(OStatus.ONTHEWAY);
                }
                break;*/
            case R.id.buttonActionCancel:
                if (!mClient.status.equals(OStatus.NEW.toString())) cancelOrder();
                else getActivity().finish();
                break;
            case R.id.imageButtonCallClient:
                callClient();
                break;
            case R.id.btnShowOnMap:
                showOnMap();
                break;
        }
    }


    private void showOnMap(){
        Intent intent = new Intent();
        intent.putExtra(CLIENT_EXTRA_KEY, mClient);
        getActivity().setResult(OrderActivity.RESULT_CODE_SHOW_ON_THE_MAP, intent);
        getActivity().finish();
    }


    private void callClient() {
        new SweetAlertDialog(getActivity(), SweetAlertDialog.WARNING_TYPE)
                .setTitleText("Вы хотите позвонить?")
                .setContentText(mClient.phone)
                .setConfirmText("Позвонить")
                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        Intent callIntent = new Intent(Intent.ACTION_CALL);
                        callIntent.setData(Uri.parse("tel:" + mClient.phone));
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
    }

    private void cancelOrder() {

        new SweetAlertDialog(getActivity(), SweetAlertDialog.WARNING_TYPE)
                .setTitleText("Вы уверены что хотите отменить?")
                        //.setContentText(order.clientPhone)
                .setConfirmText("Отменить")
                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        showProgress(true);
                        order.status = OStatus.NEW;
                        SendPostRequest(OStatus.NEW);
                        sDialog.dismissWithAnimation();
                    }
                })
                .setCancelText("Назад")
                .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        sDialog.dismissWithAnimation();
                    }
                })
                .show();
    }

    public void showProgress(final boolean show) {
        if (show) {
            pDialog = new SweetAlertDialog(getActivity(), SweetAlertDialog.PROGRESS_TYPE);
            pDialog.getProgressHelper()
                    .setBarColor(Color.parseColor("#A5DC86"));
            pDialog.setTitleText("Обновление");
            pDialog.setCancelable(true);
            pDialog.show();
        } else {
            pDialog.dismissWithAnimation();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (pDialog != null) pDialog.dismiss();
    }

    private void SendPostRequest(OStatus status) {
        if (mTask != null) {
            return;
        }

        mTask = new SendPostRequestTask(status, mClient.id);
        mTask.execute((Void) null);
    }

    private class SendPostRequestTask extends AsyncTask<Void, Void, JSONObject> {
        private String mDriver;
        private String mStatus;
        private String mCurrPosition;
        private String mId;

        SendPostRequestTask(OStatus type, int orderId) {
            mStatus = type.toString();
            mDriver = String.valueOf(user.id);
            mCurrPosition = gp.getPosition();
            mId = orderId == 0 ? null : String.valueOf(orderId);
        }

        @Override
        protected JSONObject doInBackground(Void... params) {
            JSONObject res = new JSONObject();
            JSONObject data = new JSONObject();
            try {
                data.put("status", mStatus);
                data.put("driver", mDriver);
                data.put("address_stop", mStatus.equals(OStatus.NEW.toString()) || mCurrPosition == null
                        ? JSONObject.NULL : mCurrPosition);

                res = api.patchRequest(data, "orders/" + mId + "/");
                if (Helper.isSuccess(res)) {
                    String status = res.getString("status");
                    if (status.equals(OStatus.ACCEPTED.toString())) {
                        for (int i = 0; i < 10; ++i) {
                            JSONObject tariffJson = api.getRequest(null, "info_orders/" + mId + "/");
                            if (Helper.isSuccess(tariffJson)) {
                                res.put("tariff_info", tariffJson.getJSONObject("tariff"));
                                break;
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Crashlytics.logException(e);
                e.printStackTrace();
                res = null;
            }
            return res;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            mTask = null;
            showProgress(false);
            try {
                if (Helper.isSuccess(result)) {
                    Toast.makeText(getActivity(), "Заказ обновлён", Toast.LENGTH_LONG).show();
                    if (result.getString("status").equals(OStatus.ACCEPTED.toString())) {
                        mClient.status = OStatus.ACCEPTED.toString();
                        mClient.driver = user.id;
                        order.status = OStatus.ACCEPTED;
                        Helper.setOrder(result);
                    } else if (result.getString("status").equals(OStatus.NEW.toString())) {
                        order.clear();
                        Helper.destroyOrderPreferences(getActivity().getApplicationContext(), user.id);
                        Intent intent = new Intent();
                        intent.putExtra("returnCode", false);
                        getActivity().setResult(isActive ? 3 : 1, intent);
                        getActivity().finish();
                    }
                } else if (Helper.isBadRequest(result)) {
                    String detail = result.has("details") ? result.getString("details") : "";
                    String displayMessage = "Заказ отменён или занят";
                    if (detail.toLowerCase().contains("user have not enough money")) {
                        displayMessage = "Не достатончно денег на балансе";
                    } else if (detail.toLowerCase().contains("canceled")) {
                        displayMessage = "Заказ отменен клиентом";
                    }
                    Toast.makeText(getActivity().getApplicationContext(), displayMessage, Toast.LENGTH_SHORT).show();
                    Helper.destroyOrderPreferences(getActivity().getApplicationContext(), user.id);
                    order.clear();
                    getActivity().finish();
                } else {
                    Toast.makeText(getActivity().getApplicationContext(), "Не удалось связаться с сервером", Toast.LENGTH_SHORT).show();
                }
                if (order.status == OStatus.ONTHEWAY) {
                    Intent intent = new Intent();
                    intent.putExtra("returnCode", true);
                    if (!isActive)
                        getActivity().setResult(1, intent);
                    else
                        getActivity().setResult(3, intent);
                    getActivity().finish();
                }
                updateViews();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {

            }
            updateViews();
        }

        @Override
        protected void onCancelled() {
            mTask = null;
            showProgress(false);
        }
    }


    private boolean isGPSEnabled(){
        String provider = Settings.Secure.getString(getActivity().getContentResolver(),
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        return !provider.equals("");
    }

    public void displayPromptForEnablingGPS()
    {
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity());
        final String action = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        final String message = "Активируйте геолокацию.";

        builder.setMessage(message)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int id) {
                                getActivity().startActivity(new Intent(action));
                                d.dismiss();
                            }
                        })
                .setNegativeButton("Отмена",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int id) {
                                d.cancel();
                            }
                        });
        builder.create().show();
    }
}