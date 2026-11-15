package com.jackbhai.omnitools.nativ.tools;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jackbhai.omnitools.nativ.Glass;

/**
 * Flashlight + SOS (new tool id "flashlight"). Uses CameraManager
 * torch mode; SOS plays the pure {@link SosPattern} timing table.
 * Devices without a camera flash fall back to a full-screen white
 * blink, and the UI says so. Torch behaviour is on-device only.
 */
public final class FlashlightActivity extends BaseToolActivity {

    private CameraManager camMgr;
    private String torchCameraId;
    private boolean torchOn;
    private boolean sosOn;
    private boolean screenMode;   // true when blinking the screen instead of the torch
    private long sosStartedAt;
    private TextView torchBtn, sosBtn, stateLabel;
    private View screenFlash;
    private Float oldBrightness;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable sosTick = new Runnable() {
        @Override public void run() {
            if (!sosOn) return;
            boolean on = SosPattern.stateAt(SystemClock.uptimeMillis() - sosStartedAt);
            applyLight(on);
            ui.postDelayed(this, 40);
        }
    };

    @Override protected String toolTitle() { return "Flashlight + SOS"; }

    @Override protected void buildUi() {
        camMgr = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        findTorchCamera();

        stateLabel = Glass.text(this, "", 15, Glass.TEXT, false);
        body().addView(stateLabel);

        screenFlash = new View(this);
        screenFlash.setBackgroundColor(0xFFFFFFFF);
        screenFlash.setVisibility(View.GONE);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(this, 180));
        flp.topMargin = (int) Glass.dp(this, 10);
        screenFlash.setLayoutParams(flp);
        body().addView(screenFlash);

        torchBtn = pill("Turn flashlight on", Glass.NEON_CYAN);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = (int) Glass.dp(this, 14);
        torchBtn.setLayoutParams(blp);
        torchBtn.setOnClickListener(v -> { stopSos(); setTorch(!torchOn); });
        body().addView(torchBtn);

        sosBtn = pill("Start SOS", Glass.NEON_VIOLET);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = (int) Glass.dp(this, 10);
        sosBtn.setLayoutParams(slp);
        sosBtn.setOnClickListener(v -> { if (sosOn) stopSos(); else startSos(); });
        body().addView(sosBtn);

        if (torchCameraId == null) {
            screenMode = true;
            body().addView(note("No camera flash was found on this device, so the "
                    + "flashlight and SOS will blink this screen at full brightness instead."));
        } else {
            body().addView(note("SOS blinks the international Morse pattern "
                    + "(... --- ...) on the camera flash until you stop it."));
        }
        updateLabels();
    }

    private void findTorchCamera() {
        if (camMgr == null) return;
        try {
            for (String id : camMgr.getCameraIdList()) {
                CameraCharacteristics ch = camMgr.getCameraCharacteristics(id);
                Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (flash != null && flash
                        && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id;
                    return;
                }
            }
            // Any flash unit will do if there is no back camera entry.
            for (String id : camMgr.getCameraIdList()) {
                Boolean flash = camMgr.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { torchCameraId = id; return; }
            }
        } catch (Exception ignored) { }
    }

    private void applyLight(boolean on) {
        if (screenMode) {
            screenFlash.setVisibility(on ? View.VISIBLE : View.GONE);
            setScreenBrightness(on ? 1f : -1f);
        } else {
            setTorchRaw(on);
        }
    }

    private void setScreenBrightness(float v) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (v < 0) {
            lp.screenBrightness = oldBrightness != null ? oldBrightness
                    : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            if (oldBrightness == null) oldBrightness = lp.screenBrightness;
            lp.screenBrightness = v;
        }
        getWindow().setAttributes(lp);
    }

    private void setTorchRaw(boolean on) {
        if (camMgr == null || torchCameraId == null) return;
        try { camMgr.setTorchMode(torchCameraId, on); } catch (Exception ignored) { }
    }

    private void setTorch(boolean on) {
        torchOn = on;
        if (screenMode) {
            screenFlash.setVisibility(on ? View.VISIBLE : View.GONE);
            setScreenBrightness(on ? 1f : -1f);
        } else {
            setTorchRaw(on);
        }
        updateLabels();
    }

    private void startSos() {
        if (torchOn) setTorch(false);
        sosOn = true;
        sosStartedAt = SystemClock.uptimeMillis();
        ui.post(sosTick);
        updateLabels();
    }

    private void stopSos() {
        if (!sosOn) return;
        sosOn = false;
        ui.removeCallbacks(sosTick);
        applyLight(false);
        if (screenMode) setScreenBrightness(-1f);
        updateLabels();
    }

    private void updateLabels() {
        torchBtn.setText(torchOn ? "Turn flashlight off" : "Turn flashlight on");
        sosBtn.setText(sosOn ? "Stop SOS" : "Start SOS");
        stateLabel.setText(sosOn ? "SOS is blinking (... --- ...)"
                : torchOn ? "Flashlight is on" : "Flashlight is off");
    }

    @Override protected void onPause() {
        stopSos();
        if (torchOn) setTorch(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopSos();
        if (torchOn) setTorch(false);
        super.onDestroy();
    }
}
