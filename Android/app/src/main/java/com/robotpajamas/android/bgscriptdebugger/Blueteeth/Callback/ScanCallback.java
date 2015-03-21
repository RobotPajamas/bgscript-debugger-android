package com.robotpajamas.android.bgscriptdebugger.Blueteeth.Callback;

import com.robotpajamas.android.bgscriptdebugger.Blueteeth.BlueteethDevice;

import java.util.List;

public interface ScanCallback {
    public void call(List<BlueteethDevice> bleDevices);
}
