package taxi.city.citytaxidriver;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

import taxi.city.citytaxidriver.Core.Client;
import taxi.city.citytaxidriver.Core.Order;
import taxi.city.citytaxidriver.Enums.OStatus;


public class FinishOrderDetailsActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finish_order_details);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new FinishOrderDetailsFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_finish_order_details, menu);
        return true;
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class FinishOrderDetailsFragment extends Fragment implements View.OnClickListener {

        private Client mClient;
        private Order order;

        private Button btnMap;
        private Button btnWait;
        private Button btnFinish;

        public FinishOrderDetailsFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_finish_order_details, container, false);

            Intent intent = getActivity().getIntent();
            mClient = (Client)intent.getExtras().getSerializable("DATA");

            EditText etAddressStart = (EditText)rootView.findViewById(R.id.editTextStartAddress);
            TextView tvWaitTime = (TextView)rootView.findViewById(R.id.textViewWaitingTime);
            TextView tvWaitSum = (TextView)rootView.findViewById(R.id.textViewWaitingSum);
            TextView tvDistance = (TextView)rootView.findViewById(R.id.textViewDistance);
            TextView tvSum = (TextView)rootView.findViewById(R.id.textViewSum);
            TextView tvTotalSum = (TextView)rootView.findViewById(R.id.textViewTotalSum);
            TextView tvFixedPrice = (TextView)rootView.findViewById(R.id.textViewFixedPrice);
            EditText etAddressStop = (EditText)rootView.findViewById(R.id.editTextStopAddress);
            LinearLayout llFixedPrice = (LinearLayout)rootView.findViewById(R.id.linearLayoutFixedPrice);
            llFixedPrice.setVisibility(View.GONE);

            double totalSum = 0;
            double waitSum = 0;
            double sum = 0;
            try {
                waitSum = Double.valueOf(mClient.waitSum);
                sum = Double.valueOf(mClient.sum);
                totalSum = waitSum + sum;
            } catch (Exception e) {
                totalSum = 0;
            }

            String waitTime = mClient.waitTime;
            if (waitTime.length() > 5) {
                waitTime = waitTime.substring(0, waitTime.length() - 3);
            }

            etAddressStart.setText(mClient.addressStart);
            tvWaitTime.setText(waitTime);
            tvWaitSum.setText(String.valueOf((int)waitSum));
            tvDistance.setText(mClient.distance);
            tvSum.setText(String.valueOf((int)sum));
            tvTotalSum.setText(String.valueOf((int)totalSum));
            etAddressStop.setText(mClient.addressEnd);

            etAddressStart.setEnabled(false);
            etAddressStop.setEnabled(false);

            double fixedPrice;
            try {
                fixedPrice = Double.valueOf(mClient.fixedPrice);
            } catch (Exception e) {
                fixedPrice = 0;
            }

            if (fixedPrice >= 50) {
                tvFixedPrice.setText((int)fixedPrice + " сом");
                llFixedPrice.setVisibility(View.VISIBLE);
            }

            btnMap = (Button)rootView.findViewById(R.id.buttonMap);
            btnWait = (Button)rootView.findViewById(R.id.buttonWait);
            btnFinish = (Button)rootView.findViewById(R.id.buttonFinish);

            btnMap.setOnClickListener(this);
            btnFinish.setOnClickListener(this);
            btnWait.setOnClickListener(this);

            updateViews();

            return rootView;
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.buttonMap:
                    getActivity().finish();
                    break;
                case R.id.buttonWait:
                    waitOrder();
                    break;
                case R.id.buttonFinish:
                    finishOrder();
                    break;
            }
        }

        private void finishOrder() {
            getActivity().finish();
        }

        private void waitOrder() {

        }

        private void updateViews() {
            if (mClient.status.equals(OStatus.FINISHED.toString())) {
                btnMap.setVisibility(View.GONE);
                btnWait.setVisibility(View.GONE);
                btnFinish.setText("ЗАКРЫТЬ");
            }
        }
    }

}