package com.robotpajamas.android.bgscriptdebugger.Blueteeth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.robotpajamas.android.bgscriptdebugger.Blueteeth.Callback.ConnectionCallback;
import com.robotpajamas.android.bgscriptdebugger.Blueteeth.Callback.ReadCallback;
import com.robotpajamas.android.bgscriptdebugger.Blueteeth.Callback.ScanCallback;
import com.robotpajamas.android.bgscriptdebugger.Blueteeth.Callback.ServicesDiscoveredCallback;
import com.robotpajamas.android.bgscriptdebugger.Blueteeth.Callback.WriteCallback;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import timber.log.Timber;


public class BlueteethManager {

    // TODO: This is temporary, just so I can test quickly
    public BlueteethDevice connectedDevice;

    private BluetoothManager mBLEManager;
    private BluetoothAdapter mBLEAdapter;
    private Handler mHandler = new Handler();

    private List<BlueteethDevice> mScannedDevices = new ArrayList<>();
    private Queue<ScanCallback> mScanCallbackQueue = new LinkedList<>();
    private Queue<ConnectionCallback> mConnectionCallbackQueue = new LinkedList<>();
    private Queue<ServicesDiscoveredCallback> mServicesDiscoveredCallbackQueue = new LinkedList<>();
    private Queue<ReadCallback> mReadCallbackQueue = new LinkedList<>();
    private Queue<WriteCallback> mWriteCallbackQueue = new LinkedList<>();

    // TODO: Figure out how to deal with notification callbacks which are persistent
    private ReadCallback mNotifyCallback;

    private static BlueteethManager singleton = null;
    Context context;

    public static BlueteethManager getInstance() {
        if (singleton == null) {
            synchronized (BlueteethManager.class) {
                if (singleton == null) {
                    singleton = new BlueteethManager();
                }
            }
        }
        return singleton;
    }

    public boolean initialize(Context context) {
        this.context = context.getApplicationContext();

        Timber.d("Initializing BluetoothManager");
        if (mBLEManager == null) {
            mBLEManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBLEManager == null) {
                Timber.e("Unable to initialize BluetoothManager.");
                return false;
            }
        }

        Timber.d("Initializing BLEAdapter");
        mBLEAdapter = mBLEManager.getAdapter();
        if (mBLEAdapter == null) {
            Timber.e("Unable to obtain a BluetoothAdapter.");
            return false;
        }

        if (!mBLEAdapter.isEnabled()) {
            Timber.e("Bluetooth is not enabled.");
            return false;
        }

        return true;
    }

    public void addDevice(BlueteethDevice device) {
        mScannedDevices.add(device);
    }

    public void scanForDevices(ScanCallback callback) {
        mScannedDevices.clear();
        mScanCallbackQueue.add(callback);
        beginScan(3000);
    }

    protected void scanComplete() {
        ScanCallback callback = mScanCallbackQueue.remove();
        callback.call(mScannedDevices);
    }

    public void connect(BlueteethDevice device, ConnectionCallback callback) {
        mConnectionCallbackQueue.add(callback);

        if (device.gatt != null) {
            device.gatt.disconnect();
            device.gatt = null;
        }
        device.gatt = device.bluetoothDevice.connectGatt(context, false, mGattCallback);
        connectedDevice = device;
    }

    public void disconnect(BlueteethDevice device) {
        if (device.gatt != null) {
            device.gatt.disconnect();
            device.gatt = null;
        }
        reset();
    }

    // TODO: Temp, just to clear out my Android cache
    private boolean refreshDeviceCache(BluetoothGatt gatt){
        try {
            Method localMethod = gatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                return (Boolean) localMethod.invoke(gatt);
            }
        }
        catch (Exception localException) {
            Log.e("refreshDeviceCache", "An exception occured while refreshing device");
        }
        return false;
    }

    public void discoverServices(BlueteethDevice device, ServicesDiscoveredCallback callback) {
        BluetoothGatt gatt = device.gatt;
        if (gatt == null) {
            return;
        }

        mServicesDiscoveredCallbackQueue.add(callback);
//        refreshDeviceCache(gatt);
        device.gatt.discoverServices();
    }

    public void reset() {
        mScannedDevices.clear();
    }

    public void notifyCharacteristic(UUID characteristic, UUID service, BlueteethDevice device, ReadCallback callback) {
        BluetoothGatt gatt = device.gatt;
        if (gatt == null) {
            return;
        }

        Boolean enabled = callback != null;
        mNotifyCallback = callback;

        BluetoothGattCharacteristic gattCharacteristic = gatt.getService(service).getCharacteristic(characteristic);
        gatt.setCharacteristicNotification(gattCharacteristic, enabled);
        gattCharacteristic.getDescriptors();

        final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
        descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    public void readCharacteristic(UUID characteristic, UUID service, BlueteethDevice device, ReadCallback callback) {
        BluetoothGatt gatt = device.gatt;
        if (gatt == null) {
            return;
        }

        mReadCallbackQueue.add(callback);

        BluetoothGattCharacteristic gattCharacteristic = gatt.getService(service).getCharacteristic(characteristic);
        gatt.readCharacteristic(gattCharacteristic);
    }

    public void writeCharacteristic(byte[] data, UUID characteristic, UUID service, BlueteethDevice device, WriteCallback callback) {
        BluetoothGatt gatt = device.gatt;
        if (gatt == null) {
            return;
        }

        mWriteCallbackQueue.add(callback);

        BluetoothGattCharacteristic gattCharacteristic = gatt.getService(service).getCharacteristic(characteristic);
        gattCharacteristic.setValue(data);
        gatt.writeCharacteristic(gattCharacteristic);
    }

    private BluetoothAdapter.LeScanCallback mBLEScanCallback = (device, rssi, scanRecord) -> addDevice(new BlueteethDevice(device));

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        mConnectionCallbackQueue.remove().call();
                        break;

                    case BluetoothProfile.STATE_DISCONNECTED:
                        //TODO: Handle this
                        break;
                }
            } else {
                //TODO: Handle this
                gatt.disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                mServicesDiscoveredCallbackQueue.remove().call();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            mReadCallbackQueue.remove().call(characteristic.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            mWriteCallbackQueue.remove().call();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            if (mNotifyCallback != null) {
                mNotifyCallback.call(characteristic.getValue());
            }
        }
    };

    private void beginScan(int scanDuration) {
        mHandler.postDelayed(this::endScan, scanDuration);
        mBLEAdapter.startLeScan(mBLEScanCallback);
    }

    private void endScan() {
        mBLEAdapter.stopLeScan(mBLEScanCallback);
        scanComplete();
    }


}

