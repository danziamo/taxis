package taxi.city.citytaxidriver.fragments;

import android.media.Rating;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import taxi.city.citytaxidriver.core.User;
import taxi.city.citytaxidriver.R;
import taxi.city.citytaxidriver.service.ApiService;
import taxi.city.citytaxidriver.utils.Helper;

public class AccountFragment extends Fragment {

    TextView tvAccountNumber;
    TextView tvAccountBalance;
    TextView tvRating;
    RatingBar ratingBar;
    User user;
    private FetchAccountTask mTask = null;

    public static AccountFragment newInstance() {
        return new AccountFragment();
    }

    public AccountFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_account, container, false);
        user = User.getInstance();

        tvAccountNumber = (TextView)rootView.findViewById(R.id.textViewAccountNumber);
        tvAccountBalance = (TextView) rootView.findViewById(R.id.textViewAccountBalance);
        tvRating = (TextView) rootView.findViewById(R.id.textViewRating);
        ratingBar = (RatingBar) rootView.findViewById(R.id.ratingBar);

        tvAccountNumber.setText(User.getInstance().phone);
        tvAccountBalance.setText(String.valueOf((int)User.getInstance().balance) + "  сом");
        tvRating.setText(getRatingText(user.rating));
        ratingBar.setRating((float)user.rating);

        fetchTask();
        return rootView;
    }

    private String getRatingText(double rating) {
        if (rating <= 1) return "1 звезда";
        if (rating <= 1.5) return  "1.5 звезды";
        if (rating <= 2) return  "2 звезды";
        if (rating <= 2.5) return "2.5 звезды";
        if (rating <= 3) return  "3 звезды";
        if (rating <= 3.5) return  "3.5 звезды";
        if (rating <= 4) return "4 звезды";
        if (rating <= 4.5) return "4.5 здвезды";
        if (rating <= 5) return "5 звёзд";
        return null;
    }

    private void fetchTask(){
        if (mTask != null) return;

        mTask = new FetchAccountTask();
        mTask.execute((Void) null);
    }

    private class FetchAccountTask extends AsyncTask<Void, Void, JSONObject> {

        FetchAccountTask() {}

        @Override
        protected JSONObject doInBackground(Void... params) {
            return ApiService.getInstance().getRequest(null, "users/" + User.getInstance().id + "/");
        }

        @Override
        protected void onPostExecute(final JSONObject result) {
            mTask = null;
            int statusCode = -1;
            try {
                if(Helper.isSuccess(result)) {
                    statusCode = result.getInt("status_code");
                }
                if (Helper.isSuccess(statusCode)) {
                    fillForms(result);
                } else {
                    Toast.makeText(getActivity(), "Сервис недоступен", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void onCancelled() {
            mTask = null;
        }
    }

    private void fillForms(JSONObject object) throws JSONException{
        String balance = object.getString("balance");
        double b = balance == null ? 0 : Double.valueOf(balance);
        tvAccountBalance.setText(String.valueOf((int)b) + "  сом");
        user.balance = b;

        String ratingSumString = object.getJSONObject("rating").getString("votes__sum");
        double ratingSum = ratingSumString == null || ratingSumString.equals("null") ? 0 : Double.valueOf(ratingSumString);
        int ratingCount = object.getJSONObject("rating").getInt("votes__count");
        user.rating = ratingCount == 0 ? 0 : (int)Math.round(ratingSum/ratingCount);
        ratingBar.setRating((float)user.rating);
        tvRating.setText(getRatingText(user.rating));
    }
}