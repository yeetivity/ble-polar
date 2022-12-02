package yeetivity.jjve.ble_polar;

import static yeetivity.jjve.ble_polar.ui_utils.MsgUtils.showToast;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import yeetivity.jjve.ble_polar.ui_utils.BtDeviceAdapter;

public class ScanningActivity extends AppCompatActivity {

    public static final String MOVESENSE = "Polar";

    public static final int REQUEST_ENABLE_BT = 1000;
    public static final int REQUEST_ACCESS_LOCATION = 1001;

    public static String SELECTED_DEVICE = "Selected device";

    private static final long SCAN_PERIOD = 5000; // milliseconds

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private ArrayList<BluetoothDevice> mDeviceList;
    private BtDeviceAdapter mBtDeviceAdapter;
    private TextView mScanInfoView;

    private static final String LOG_TAG = "ScanActivity";

    /**
     * Below: Manage bluetooth initialization and life cycle
     * via Activity.onCreate, onStart and onStop.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanning);

        mDeviceList = new ArrayList<>();

        mHandler = new Handler();

        // ui stuff
        mScanInfoView = findViewById(R.id.scan_info);

        Button startScanButton = findViewById(R.id.start_scan_button);
        startScanButton.setOnClickListener(v -> {
            mDeviceList.clear();
            scanForDevices(true);
        });

        // more ui stuff, the recycler view
        RecyclerView recyclerView = findViewById(R.id.scan_list_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        mBtDeviceAdapter = new BtDeviceAdapter(mDeviceList,
                this::onDeviceSelected);
        recyclerView.setAdapter(mBtDeviceAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mScanInfoView.setText(R.string.no_devices_found);
        initBLE();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // stop scanning
        scanForDevices(false);
        mDeviceList.clear();
        mBtDeviceAdapter.notifyDataSetChanged();
    }

    // Check BLE permissions and turn on BT (if turned off) - user interaction(s)
    private void initBLE() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showToast("BLE is not supported", this);
            finish();
        } else {
            showToast("BLE is supported", this);
            // Access Location is a "dangerous" permission
            int hasAccessLocation = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION);
            if (hasAccessLocation != PackageManager.PERMISSION_GRANTED) {
                // ask the user for permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_ACCESS_LOCATION);
                // the callback method onRequestPermissionsResult gets the result of this request
            }
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // turn on BT
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    /*
     * Device selected, start DeviceActivity (displaying data)
     */
    private void onDeviceSelected(int position) {
        BluetoothDevice selectedDevice = mDeviceList.get(position);
        // BluetoothDevice objects are parceable, i.e. we can "send" the selected device
        // to the DeviceActivity packaged in an intent.
        Intent intent = new Intent(ScanningActivity.this, DeviceActivity.class);
        intent.putExtra(SELECTED_DEVICE, selectedDevice);
        startActivity(intent);
    }

    /*
     * Scan for BLE devices.
     */
    private void scanForDevices(final boolean enable) {
        final BluetoothLeScanner scanner =
                mBluetoothAdapter.getBluetoothLeScanner();
        if (enable) {
            if (!mScanning) {
                // stop scanning after a pre-defined scan period, SCAN_PERIOD
                mHandler.postDelayed(() -> {
                    if (mScanning) {
                        mScanning = false;
                        scanner.stopScan(mScanCallback);
                        showToast("BLE scan stopped", ScanningActivity.this);
                    }
                }, SCAN_PERIOD);

                mScanning = true;
                // TODO: Add a filter, e.g. for heart rate service, scan settings
                scanner.startScan(mScanCallback);
                mScanInfoView.setText(R.string.no_devices_found);
                showToast("BLE scan started", this);
            }
        } else {
            if (mScanning) {
                mScanning = false;
                scanner.stopScan(mScanCallback);
                showToast("BLE scan stopped", this);
            }
        }
    }

    /*
     * Implementation of scan callback methods
     */
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            //Log.i(LOG_TAG, "onScanResult");
            final BluetoothDevice device = result.getDevice();
            final String name = device.getName();

            mHandler.post(() -> {
                if (name != null
                        && name.contains(MOVESENSE)
                        && !mDeviceList.contains(device)) {
                    mDeviceList.add(device);
                    mBtDeviceAdapter.notifyDataSetChanged();
                    String info = "Found " + mDeviceList.size() + " device(s)\n"
                            + "Touch to connect";
                    mScanInfoView.setText(info);
                    Log.i(LOG_TAG, device.toString());
                }
            });
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.i(LOG_TAG, "onBatchScanResult");
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(LOG_TAG, "onScanFailed");
        }
    };


    // callback for Activity.requestPermissions
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ACCESS_LOCATION) {
            // if request is cancelled, the results array is empty
            if (grantResults.length == 0
                    || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                // stop this activity
                this.finish();
            }
        }
    }

    // callback for request to turn on BT
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // if user chooses not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
