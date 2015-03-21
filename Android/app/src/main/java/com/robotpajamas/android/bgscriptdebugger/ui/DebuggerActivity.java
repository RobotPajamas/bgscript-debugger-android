package com.robotpajamas.android.bgscriptdebugger.ui;

import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.widget.Button;
import android.widget.TextView;

import com.robotpajamas.android.bgscriptdebugger.Blueteeth.BlueteethDevice;
import com.robotpajamas.android.bgscriptdebugger.Blueteeth.BlueteethManager;
import com.robotpajamas.android.bgscriptdebugger.Blueteeth.BlueteethUtils;
import com.robotpajamas.android.bgscriptdebugger.Blueteeth.Callback.ReadCallback;
import com.robotpajamas.android.bgscriptdebugger.R;
import com.robotpajamas.android.bgscriptdebugger.utils.StringUtils;

import java.util.UUID;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;


public class DebuggerActivity extends ActionBarActivity {

    /* Debugging Service */
    private static final UUID DEBUGGER_SERVICE = UUID.fromString("DEADBEEF-CDCD-CDCD-CDCD-CDCDCDCDCDCD");
    private static final UUID DEBUGGER_CHAR = UUID.fromString("DEADBEEF-0000-0000-0000-000000000000");

    // Using standard 16bit UUIDs, transformed into the correct 128-bit UUID
    private static final UUID DEVICE_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_MANUFACTURER_NAME = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_MANUFACTURER_MODEL = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_MANUFACTURER_HARDWARE = UUID.fromString("00002A27-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_MANUFACTURER_FIRMWARE = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_MANUFACTURER_SOFTWARE = UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb");

    private IntentFilter mIntentFilter;
    BlueteethDevice mDevice;

    @InjectView(R.id.firmware_version)
    TextView mFirmwareVersion;

    @InjectView(R.id.textview_debug)
    TextView mDebugData;

    @InjectView(R.id.debugger_button)
    Button mUpdateButton;

    @OnClick(R.id.debugger_button)
    void onDebugClicked() {
        BlueteethUtils.notifyData(DEBUGGER_CHAR, DEBUGGER_SERVICE, mDevice, new ReadCallback() {
            @Override
            public void call(byte[] data) {
                String hexString = new String(data);
                mDebugData.append(StringUtils.hexToString(hexString) + "\n");
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debugger);
        ButterKnife.inject(this);

        mDevice = BlueteethManager.getInstance().connectedDevice;
        mDevice.discoverServices(() -> BlueteethUtils.readData(DEVICE_MANUFACTURER_FIRMWARE, DEVICE_SERVICE, mDevice, data -> mFirmwareVersion.setText(new String(data))));

    }



}
