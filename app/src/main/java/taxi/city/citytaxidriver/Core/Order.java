package taxi.city.citytaxidriver.Core;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;

import taxi.city.citytaxidriver.Enums.OStatus;

/**
 * Created by Daniyar on 3/27/2015.
 */
public class Order {


    private static Order mInstance = null;
    public int id;
    public String orderTime;
    public LatLng startPoint;
    public LatLng endPoint;
    public String clientPhone;
    public OStatus status;
    public long waitTime;
    public int tariff;
    public int driver;
    public String addressStart;
    public String addressEnd;
    public String description;

    public double distance;
    public double sum;
    public long time;
    public double waitSum;

    private Order() {

    }

    public static Order getInstance() {
        if (mInstance == null) {
            mInstance = new Order();
        }
        return mInstance;
    }

    private String getFormattedLatLng(LatLng location) {
        if (location != null) {
            return "POINT (" + location.latitude + " " + location.longitude + ")";
        }
        return null;
    }

    private String getTimeFromLong(long seconds) {
        int hr = (int)seconds/3600;
        int rem = (int)seconds%3600;
        int mn = rem/60;
        int sec = rem%60;
        String hrStr = (hr<10 ? "0" : "")+hr;
        String mnStr = (mn<10 ? "0" : "")+mn;
        String secStr = (sec<10 ? "0" : "")+sec;
        return String.format("%s:%s:%s", hrStr, mnStr, secStr);
    }

    public JSONObject getOrderAsJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", this.id);
        obj.put("client_phone", this.clientPhone);
        obj.put("status", this.status);
        obj.put("address_start", getFormattedLatLng(this.startPoint));
        obj.put("address_stop", getFormattedLatLng(this.endPoint));
        obj.put("wait_time", getTimeFromLong(this.waitTime));
        obj.put("wait_time_price", this.waitSum);
        obj.put("tariff", this.tariff);
        obj.put("driver", this.driver);
        obj.put("order_time", this.orderTime);
        obj.put("order_distance", (double)Math.round(this.distance*100)/100);
        obj.put("order_sum", this.sum + this.waitSum);
        obj.put("order_travel_time", getTimeFromLong(this.time));
        obj.put("address_start_name", this.addressStart);
        obj.put("address_stop_name", this.addressEnd);
        obj.put("description", this.description);

        return obj;
    }


    public void clear() {
        this.id = 0;
        this.waitTime = 0;
        this.startPoint = null;
        this.endPoint = null;
        this.status = null;
        this.tariff = 0;
        this.driver = 0;
        this.orderTime = null;
        this.clientPhone = null;
        this.distance = 0;
        this.time = 0;
        this.sum = 0;
        this.addressEnd = null;
        this.addressStart = null;
        this.description = null;
        this.waitSum = 0;
    }
}
