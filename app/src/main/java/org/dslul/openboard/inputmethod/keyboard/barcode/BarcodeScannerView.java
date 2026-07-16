package org.dslul.openboard.inputmethod.keyboard.barcode;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.Constants;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("UnsafeOptInUsageError")
public class BarcodeScannerView extends LinearLayout {

    private PreviewView mPreviewView;
    private TextView mResultText;
    private Button mCaptureButton;
    private Button mBackButton;

    private ExecutorService mCameraExecutor;
    private BarcodeScanner mScanner;
    private KeyboardActionListener mListener;
    private String mLastScannedValue;

    public BarcodeScannerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        mPreviewView = findViewById(R.id.barcode_preview);
        mResultText = findViewById(R.id.barcode_result);
        mCaptureButton = findViewById(R.id.barcode_capture);
        mBackButton = findViewById(R.id.barcode_back);

        mCaptureButton.setOnClickListener(v -> {
            if (mLastScannedValue != null && mListener != null) {
                mListener.onTextInput(mLastScannedValue);
                mListener.onCodeInput(Constants.CODE_ALPHA_FROM_BARCODE, 0, 0, false);
            }
        });

        mBackButton.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onCodeInput(Constants.CODE_ALPHA_FROM_BARCODE, 0, 0, false);
            }
        });

        mCameraExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        mScanner = BarcodeScanning.getClient(options);
    }

    public void setKeyboardActionListener(KeyboardActionListener listener) {
        mListener = listener;
    }

    public void startScanner(LifecycleOwner lifecycleOwner) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider, lifecycleOwner);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(getContext(), "Error starting camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider, LifecycleOwner lifecycleOwner) {
        Preview preview = new Preview.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(mCameraExecutor, image -> {
            processImageProxy(image);
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void processImageProxy(ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            mScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            if (rawValue != null) {
                                updateResult(rawValue);
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    private void updateResult(String value) {
        post(() -> {
            mLastScannedValue = value;
            mResultText.setText(value);
            mResultText.setVisibility(VISIBLE);
            mCaptureButton.setEnabled(true);
        });
    }

    public void stopScanner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        ProcessCameraProvider cameraProvider;
        try {
            cameraProvider = ProcessCameraProvider.getInstance(getContext()).get();
            cameraProvider.unbindAll();
        } catch (ExecutionException | InterruptedException e) {
            // ignore
        }
        mLastScannedValue = null;
        mResultText.setVisibility(GONE);
        mCaptureButton.setEnabled(false);
    }
}
