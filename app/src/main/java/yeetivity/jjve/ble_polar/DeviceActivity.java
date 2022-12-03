package yeetivity.jjve.ble_polar;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import java.util.List;
import java.util.UUID;

import yeetivity.jjve.ble_polar.ui_utils.MsgUtils;
import yeetivity.jjve.ble_polar.utils.TypeConverter;

public class DeviceActivity extends AppCompatActivity {

    // Polar 2.0 UUIDs (should be placed in resources file)
    // Todo: check if correct -> they are
    public static final UUID POLAR_SERVICE =
            UUID.fromString("FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID POLAR_CONTROL =
            UUID.fromString("FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID POLAR_DATA =
            UUID.fromString("FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8");

    private final byte POLAR_REQUEST = 1, POLAR_RESPONSE = 2, REQUEST_ID = 99;

    private final byte[] ACC_STREAM_REQUEST = new byte[] {0x02, 0x02, 0x00, 0x01, 0x34, 0x00, 0x01, 0x01, 0x10, 0x00, 0x02, 0x01, 0x08, 0x00, 0x04, 0x01, 0x03};

    private BluetoothDevice mSelectedDevice = null;
    private BluetoothGatt mBluetoothGatt = null;

    private Handler mHandler;

    private TextView mDeviceView;
    private TextView mDataView;

    private static final String LOG_TAG = "DeviceActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        mDeviceView = findViewById(R.id.device_view);
        mDataView = findViewById(R.id.data_view);

        Intent intent = getIntent();
        // Get the selected device from the intent
        mSelectedDevice = intent.getParcelableExtra(ScanningActivity.SELECTED_DEVICE);
        if (mSelectedDevice == null) {
            MsgUtils.createDialog("Error", "No device found", this).show();
            mDeviceView.setText(R.string.no_devices_found);
        } else {
            mDeviceView.setText(mSelectedDevice.getName());
        }

        mHandler = new Handler();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mSelectedDevice != null) {
            // Connect and register call backs for bluetooth gatt
            mBluetoothGatt =
                    mSelectedDevice.connectGatt(this, false, mBtGattCallback);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            try {
                mBluetoothGatt.close();
            } catch (Exception e) {
                // ugly, but this is to handle a bug in some versions in the Android BLE API
            }
        }
    }

    /**
     * Callbacks for bluetooth gatt changes/updates
     * The documentation is not always clear, but most callback methods seems to
     * be executed on a worker thread - hence use a Handler when updating the ui.
     */
    private final BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt;
                mHandler.post(() -> mDataView.setText(R.string.connected));
                // Discover services
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Close connection and display info in ui
                mBluetoothGatt = null;
                mHandler.post(() -> mDataView.setText(R.string.disconnected));
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Debug: list discovered services
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    Log.i(LOG_TAG, service.getUuid().toString());
                }

                // Get the Polar service
                BluetoothGattService polarService = gatt.getService(POLAR_SERVICE);
                if (polarService != null) {
                    // debug: service present, list characteristics
                    List<BluetoothGattCharacteristic> characteristics =
                            polarService.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        Log.i(LOG_TAG, characteristic.getUuid().toString());
                    }

                    // Write a command, as a byte array, to the control characteristic
                    // Callback: onCharacteristicWrite
                    BluetoothGattCharacteristic controlCharacteristic = polarService.getCharacteristic(POLAR_CONTROL);
                    controlCharacteristic.setValue(ACC_STREAM_REQUEST);
                    boolean wasSuccess = mBluetoothGatt.writeCharacteristic(controlCharacteristic);
                    Log.i("writeCharacteristic", "was success=" + wasSuccess);

                    // Read from the controlCharacteristic -> Callback: onCharacteristicRead
                    // If code is okay -> startNotifications for data characteristic
                    // gatt.readCharacteristic(controlCharacteristic);


                } else {
                    mHandler.post(() -> MsgUtils.createDialog("Alert!",
                                    getString(R.string.service_not_found),
                                    DeviceActivity.this)
                            .show());
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i(LOG_TAG, "onCharacteristicWrite " + characteristic.getUuid().toString());

            if (characteristic.getUuid().equals(POLAR_CONTROL)){
                // Response from polar control

                // Callback -> onCharacteristicsRead
                gatt.readCharacteristic(characteristic);

            } else {
                // response from data
                Log.i(LOG_TAG, "onCharacteristicWrite: unexpected Uuid");
            }

            // Enable notifications on data from the sensor. First: Enable receiving
            // notifications on the client side, i.e. on this Android device.
//            BluetoothGattService polarService = gatt.getService(POLAR_SERVICE);
//            BluetoothGattCharacteristic dataCharacteristic =
//                    polarService.getCharacteristic(POLAR_DATA);
            // second arg: true, notification; false, indication
//            boolean success = gatt.setCharacteristicNotification(dataCharacteristic, true);
//            if (success) {
//                Log.i(LOG_TAG, "setCharactNotification success");
//
//                BluetoothGattDescriptor descriptor = dataCharacteristic.getDescriptor(POLAR_CONTROL);
//                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
//
////                // Second: set enable notification server side (sensor). Why isn't
////                // this done by setCharacteristicNotification - a flaw in the API?
////                BluetoothGattDescriptor descriptor =
////                        dataCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
////                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
////                gatt.writeDescriptor(descriptor); // callback: onDescriptorWrite
//            } else {
//                Log.i(LOG_TAG, "setCharacteristicNotification failed");
//            }
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, BluetoothGattDescriptor
                descriptor, int status) {
            //Todo: don't think we need this function with polar
            Log.i(LOG_TAG, "onDescriptorWrite, status " + status);
        }

        /**
         * Callback called on characteristic changes, e.g. when a sensor data value is changed.
         * This is where we receive notifications on new sensor data.
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic) {
            // debug
            Log.i(LOG_TAG, "onCharacteristicChanged " + characteristic.getUuid());

            // if response and id matches
            if (POLAR_DATA.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                //Todo: parse and interpret the data

                // Todo: do something fun with the data

                mHandler.post(() -> {
                    //Todo: post to views
                    mDeviceView.setText("" + " ms");
                    mDataView.setText("");
                });
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i(LOG_TAG, "onCharacteristicRead " + characteristic.getUuid().toString());

            // Check if error code is succes
            if (characteristic.getValue()[3] == 0){
                //Start listening to data characteristic

//                //debugging purposes
//                BluetoothGattCharacteristic chara = gatt.getService(POLAR_SERVICE).getCharacteristic(POLAR_DATA);
//                boolean success = gatt.setCharacteristicNotification(chara, true);
//                //Callback -> onCharacteristicsChanged
//                //boolean success = gatt.setCharacteristicNotification((gatt.getService(POLAR_SERVICE).getCharacteristic(POLAR_DATA)), true);
//                if (success){
//                    Log.i(LOG_TAG, "succesfully set characteristic notification");
//                }

                gatt.readCharacteristic(gatt.getService(POLAR_SERVICE).getCharacteristic(POLAR_DATA));

            } else {
                Log.i(LOG_TAG, "no succes response from control point");
            }




        }
    };
}