package com.jackbhai.omnitools.nativ.tools;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.jackbhai.omnitools.nativ.Glass;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * QR / Barcode Scanner (new tool id "qrscan"). Camera2 preview +
 * ImageReader YUV frames decoded with ZXing core (libs/zxing-core —
 * no ML Kit, no Google services). Falls back to a 90-degree-rotated
 * frame when the straight frame does not decode, which covers most
 * 1D barcodes held sideways. Camera behaviour is on-device only.
 */
public final class QrActivity extends BaseToolActivity {

    private static final int REQ_CAM = 43;

    private TextureView preview;
    private TextView resultLabel, scanAgainBtn;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private String cameraId;
    private Size frameSize;

    private final MultiFormatReader zxing = new MultiFormatReader();
    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private volatile boolean found;

    {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        zxing.setHints(hints);
    }

    @Override protected String toolTitle() { return "QR / Barcode Scanner"; }

    @Override protected void buildUi() {
        preview = new TextureView(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Glass.dp(this, 380));
        preview.setLayoutParams(plp);
        preview.setSurfaceTextureListener(surfaceListener);
        body().addView(preview);

        resultLabel = Glass.text(this, "Point the camera at a QR code or barcode.",
                15, Glass.TEXT, false);
        Glass.pad(resultLabel, this, 2, 12, 2, 4);
        body().addView(resultLabel);

        scanAgainBtn = pill("Copy result", Glass.NEON_CYAN);
        scanAgainBtn.setVisibility(android.view.View.GONE);
        scanAgainBtn.setOnClickListener(v -> {
            String t = resultLabel.getText().toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("scan result", t));
            toast("Copied");
        });
        body().addView(scanAgainBtn);

        TextView again = pill("Scan again", Glass.NEON_VIOLET);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = (int) Glass.dp(this, 8);
        again.setLayoutParams(alp);
        again.setOnClickListener(v -> {
            found = false;
            resultLabel.setText("Point the camera at a QR code or barcode.");
            scanAgainBtn.setVisibility(android.view.View.GONE);
        });
        body().addView(again);
    }

    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    openCamera();
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                    closeCamera();
                    return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
            };

    @Override protected void onResume() {
        super.onResume();
        bgThread = new HandlerThread("qr-camera");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
        if (preview != null && preview.isAvailable()) openCamera();
    }

    @Override protected void onPause() {
        closeCamera();
        if (bgThread != null) { bgThread.quitSafely(); bgThread = null; bgHandler = null; }
        super.onPause();
    }

    private void openCamera() {
        if (!ensurePerm(Manifest.permission.CAMERA, REQ_CAM)) return;
        CameraManager mgr = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (mgr == null) { resultLabel.setText("No camera service on this device."); return; }
        try {
            cameraId = null;
            for (String id : mgr.getCameraIdList()) {
                Integer facing = mgr.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
                if (cameraId == null) cameraId = id;
            }
            if (cameraId == null) { resultLabel.setText("No camera found."); return; }
            StreamConfigurationMap map = mgr.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            frameSize = chooseFrameSize(map != null
                    ? map.getOutputSizes(ImageFormat.YUV_420_888) : null);
            reader = ImageReader.newInstance(frameSize.getWidth(), frameSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onFrame, bgHandler);
            mgr.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice cd) {
                    camera = cd;
                    startPreview();
                }
                @Override public void onDisconnected(CameraDevice cd) { cd.close(); camera = null; }
                @Override public void onError(CameraDevice cd, int error) {
                    cd.close(); camera = null;
                    runOnUiThread(() -> resultLabel.setText("Camera error " + error));
                }
            }, bgHandler);
        } catch (Exception e) {
            resultLabel.setText("Could not open the camera: " + e.getMessage());
        }
    }

    /** Largest YUV size at or under 1280x960 (plenty for ZXing, cheap to copy). */
    private static Size chooseFrameSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1280, 720);
        Size best = sizes[0];
        long bestArea = 0;
        for (Size s : sizes) {
            long area = (long) s.getWidth() * s.getHeight();
            if (s.getWidth() <= 1280 && s.getHeight() <= 960 && area > bestArea) {
                best = s; bestArea = area;
            }
        }
        return best;
    }

    private void startPreview() {
        if (camera == null || !preview.isAvailable()) return;
        try {
            SurfaceTexture st = preview.getSurfaceTexture();
            st.setDefaultBufferSize(frameSize.getWidth(), frameSize.getHeight());
            Surface surface = new Surface(st);
            final CaptureRequest.Builder req =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            req.addTarget(surface);
            req.addTarget(reader.getSurface());
            req.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            camera.createCaptureSession(Arrays.asList(surface, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try { s.setRepeatingRequest(req.build(), null, bgHandler); }
                            catch (Exception ignored) { }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            runOnUiThread(() -> resultLabel.setText("Camera preview failed to start."));
                        }
                    }, bgHandler);
        } catch (Exception e) {
            runOnUiThread(() -> resultLabel.setText("Camera preview failed: " + e.getMessage()));
        }
    }

    private void closeCamera() {
        try { if (session != null) session.close(); } catch (Exception ignored) { }
        try { if (camera != null) camera.close(); } catch (Exception ignored) { }
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        session = null; camera = null; reader = null;
    }

    // ------------------------------------------------------------ decoding

    private void onFrame(ImageReader r) {
        Image img;
        try { img = r.acquireLatestImage(); } catch (Exception e) { return; }
        if (img == null) return;
        try {
            if (found || !decoding.compareAndSet(false, true)) return;
            byte[] y = copyYPlane(img);
            int w = img.getWidth(), h = img.getHeight();
            Result res = tryDecode(y, w, h);
            if (res == null) {
                // Sideways barcodes: rotate the luminance plane 90 degrees.
                res = tryDecode(rotate90(y, w, h), h, w);
            }
            if (res != null) {
                found = true;
                final String text = res.getText();
                final String format = res.getBarcodeFormat() != null
                        ? res.getBarcodeFormat().name() : "";
                runOnUiThread(() -> {
                    resultLabel.setText(text);
                    scanAgainBtn.setVisibility(android.view.View.VISIBLE);
                    toast("Scanned " + format);
                    Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (vib != null) {
                        try { vib.vibrate(VibrationEffect.createOneShot(60,
                                VibrationEffect.DEFAULT_AMPLITUDE)); }
                        catch (Exception ignored) { }
                    }
                });
            }
        } catch (Exception ignored) {
            // A bad frame must never kill the preview loop.
        } finally {
            decoding.set(false);
            try { img.close(); } catch (Exception ignored) { }
        }
    }

    private Result tryDecode(byte[] y, int w, int h) {
        try {
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(
                    y, w, h, 0, 0, w, h, false);
            return zxing.decodeWithState(new BinaryBitmap(new HybridBinarizer(src)));
        } catch (Exception e) {
            return null; // NotFoundException is the normal "nothing here" case
        } finally {
            zxing.reset();
        }
    }

    /** Copy the Y plane of a YUV_420_888 image into a tight byte[]. */
    private static byte[] copyYPlane(Image img) {
        Image.Plane plane = img.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int w = img.getWidth(), h = img.getHeight();
        int rowStride = plane.getRowStride(), pixelStride = plane.getPixelStride();
        byte[] out = new byte[w * h];
        if (pixelStride == 1 && rowStride == w) {
            buf.get(out, 0, Math.min(out.length, buf.remaining()));
            return out;
        }
        byte[] row = new byte[rowStride];
        for (int y = 0; y < h; y++) {
            buf.position(y * rowStride);
            buf.get(row, 0, Math.min(rowStride, buf.remaining()));
            if (pixelStride == 1) {
                System.arraycopy(row, 0, out, y * w, w);
            } else {
                for (int x = 0; x < w; x++) out[y * w + x] = row[x * pixelStride];
            }
        }
        return out;
    }

    private static byte[] rotate90(byte[] in, int w, int h) {
        byte[] out = new byte[in.length];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[x * h + (h - 1 - y)] = in[y * w + x];
        return out;
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_CAM) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) openCamera();
            else resultLabel.setText("Camera permission denied — the scanner needs it.");
        }
    }
}
