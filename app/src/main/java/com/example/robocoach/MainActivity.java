package com.example.robocoach;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.robocoach.helper.SummaryInfo;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.formats.proto.RectProto.NormalizedRect;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Arrays;
import java.util.List;

import static com.example.robocoach.helper.SummaryInfo.DANDASANA;
import static com.example.robocoach.helper.SummaryInfo.DOWNDOG;
import static com.example.robocoach.helper.SummaryInfo.POSTURE;
import static com.example.robocoach.helper.SummaryInfo.REPETITION;
import static com.example.robocoach.helper.SummaryInfo.TREE_POSE;
import static com.example.robocoach.helper.SummaryInfo.WARRIOR_2;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Flips the camera-preview frames vertically by default, before sending them into FrameProcessor
    // to be processed in a MediaPipe graph, and flips the processed frames back when they are
    // displayed. This maybe needed because OpenGL represents images assuming the image origin is at
    // the bottom-left corner, whereas MediaPipe in general assumes the image origin is at the
    // top-left corner.
    // NOTE: use "flipFramesVertically" in manifest metadata to override this behavior.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    // Number of output frames allocated in ExternalTextureConverter.
    // NOTE: use "converterNumBuffers" in manifest metadata to override number of buffers. For
    // example, when there is a FlowLimiterCalculator in the graph, number of buffers should be at
    // least `max_in_flight + max_in_queue + 1` (where max_in_flight and max_in_queue are used in
    // FlowLimiterCalculator options). That's because we need buffers for all the frames that are in
    // flight/queue plus one for the next frame from the camera.
    private static final int NUM_BUFFERS = 2;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        try {
            System.loadLibrary("opencv_java3");
        } catch (java.lang.UnsatisfiedLinkError e) {
            // Some example apps (e.g. template matching) require OpenCV 4.
            System.loadLibrary("opencv_java4");
        }
    }

    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    protected FrameProcessor processor;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    protected CameraXPreviewHelper cameraHelper;

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;

    private boolean useFront = false;
    public Typeface typeface;

    private static final int SELECTED_YOGA_MODE = 0;
    private static final int SELECTED_POSE_MODE = 1;
    private static final int SELECTED_TRAINING_MODE = 2;
    private static final List<String> SELECTED_TEXT = Arrays.asList("Yoga Mode", "Posture Mode", "Training Mode");

    private static final double SHOULDER_LENGTH = 0.6;
    private static final double MATCHING_LENGTH = 0.5;
    private static final double DISCARDED_FRAME = 10;

    private static final String DEFAULT_COLOR = "#ffffff";
    private static final String INCORRECT_COLOR = "#ff3333";
    private static final String CORRECT_COLOR = "#008577";
    private static final String COUNT_BUTTON_TEXT = "START";
    private static final String STOP_BUTTON_TEXT = "STOP";
    private static final String RIGHT_POSTURE_TEXT = "Correct posture";
    private static final String WRONG_POSTURE_TEXT = "Bad posture";
    private static final String RECORDINGTEXT = "Recording";
    private long YOGA_SUMMARY_COUNTER[] = null;
    private List<List<Long>> LOG_TIMES = null;
    private long currentCounter = 0;

    private int selectedEffectId = SELECTED_YOGA_MODE;
    private int currentYogaPose = -1;

    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "pose_landmarks";
    private long prev_time = 0, next_time = 0;
    private long fps = 0;
    private long startTime = 0;

    private TextView poseName;
    private TextView counter;
    private ImageButton modeChanger;
    private ImageButton cameraFlipper;
    private Button startCounting;

    //    private ImageButton videoRecording;
    private TextView recordingText;
    private boolean isRecording = false;

    private float rotationAngle = 0.0f;
    private boolean countingStarted = false;
    private NormalizedLandmarkList referencePose = null;
    // int numberOfRepetition = 0;
    private int nextStateCount = 0;
    private boolean nextState = false;

    private boolean usingFrontCamera = false;

    private View.OnClickListener ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (selectedEffectId) {
                case SELECTED_YOGA_MODE: {
                    selectedEffectId = SELECTED_POSE_MODE;
                    modeChanger.setImageResource(R.drawable.sitting);
                    break;
                }

                case SELECTED_POSE_MODE: {
                    selectedEffectId = SELECTED_TRAINING_MODE;
                    modeChanger.setImageResource(R.drawable.workout);
                    DisplayMetrics displaymetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
                    int height = displaymetrics.heightPixels;
                    int width = displaymetrics.widthPixels;
                    LinearLayout.LayoutParams startCountingLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    startCounting = new Button(getBaseContext());
                    startCounting.setTextColor(Color.parseColor(DEFAULT_COLOR));
                    startCounting.setTextSize((float) 12);
                    startCounting.setText(COUNT_BUTTON_TEXT);
                    startCounting.setOnClickListener(CountingListener);
                    startCounting.getBackground().setAlpha(50);
                    //summary.setBackgroundColor(Color.TRANSPARENT);
                    startCountingLayout.setMargins(width - 1050, height - 1800, 8, 0);
                    addContentView(startCounting, startCountingLayout);
                    startCounting.getLayoutParams().width = 500;
                    startCounting.getLayoutParams().height = 120;
                    startCounting.requestLayout();
                    break;
                }

                case SELECTED_TRAINING_MODE: {
                    selectedEffectId = SELECTED_YOGA_MODE;
                    modeChanger.setImageResource(R.drawable.yoga);
                    ((ViewGroup) startCounting.getParent()).removeView(startCounting);
                    break;
                }

                default:
                    break;
            }
            counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
            counter.setText("--:--");
            // Log.v(TAG, "THE VALUE IS " + selectedEffectId);
            poseName.setTextColor(Color.parseColor(DEFAULT_COLOR));
            poseName.setText(SELECTED_TEXT.get(selectedEffectId));
        }
    };

    private View.OnClickListener ToggleClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            usingFrontCamera = !usingFrontCamera;
            flipCamera(usingFrontCamera);
        }
    };

    private View.OnClickListener CountingListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (countingStarted == false) {
                countingStarted = true;
                startCounting.setText(STOP_BUTTON_TEXT);
            } else {
                startCounting.setText(COUNT_BUTTON_TEXT);
                referencePose = null;
                nextStateCount = 0;
                nextState = false;
                countingStarted = false;
            }

            return;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_summary) {

            // Calculate remaining times
            if (currentYogaPose != -1) {
                // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                if (currentCounter > 1000) {
                    LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                }
            }
            currentYogaPose = -1;
            currentCounter = 0;
            counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
            counter.setText("--:--");
            Log.v(TAG, "NO DATA");
            poseName.setTextColor(Color.parseColor(DEFAULT_COLOR));
            poseName.setText(SELECTED_TEXT.get(selectedEffectId));

            Intent intent = new Intent(getBaseContext(), SummaryActivity.class);
            Log.v(TAG, "DANDA " + YOGA_SUMMARY_COUNTER[DANDASANA] / 1000);
            Log.v(TAG, "DOWNDOG " + YOGA_SUMMARY_COUNTER[DOWNDOG] / 1000);
            Log.v(TAG, "TREE_POSE " + YOGA_SUMMARY_COUNTER[TREE_POSE] / 1000);
            Log.v(TAG, "WARRIOR_2 " + YOGA_SUMMARY_COUNTER[WARRIOR_2] / 1000);
            Log.v(TAG, "POSTURE " + YOGA_SUMMARY_COUNTER[POSTURE] / 1000);
            Log.v(TAG, "REPETITION " + YOGA_SUMMARY_COUNTER[REPETITION] / 1000);
            intent.putExtra("RUNNINGMODE", "LIVE");

            startActivity(intent);
        }
        if (item.getItemId() == R.id.action_openvideo) {
            Intent intent = new Intent(getBaseContext(), SelectorActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());
        typeface = Typeface.createFromAsset(getAssets(), "font/RobotoBold.ttf");

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        "pose_tracking_gpu.binarypb",
                        "input_video",
                        "output_video");
        processor
                .getVideoSurfaceOutput()
                .setFlipY(true);

        PermissionHelper.checkAndRequestCameraPermissions(this);

        // To show verbose logging, run:
        // adb shell setprop log.tag.MainActivity VERBOSE
        startTime = System.currentTimeMillis();
        poseName = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 50;
        params.topMargin = 10;
        poseName.setTextColor(Color.parseColor(DEFAULT_COLOR));
        poseName.setText(SELECTED_TEXT.get(selectedEffectId));
        // poseName.getBackground().setAlpha(50);
        poseName.setTextSize((float) 20);
        poseName.setTypeface(typeface);
        poseName.setPadding(20, 50, 20, 50);
        poseName.setLayoutParams(params);
        addContentView(poseName, params);
        poseName.getLayoutParams().width = 1000;
        poseName.getLayoutParams().height = 250;

        counter = new TextView(this);
        params.leftMargin = 50;
        params.topMargin = 150;
        counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
        counter.setText("--:--");
        counter.setTextSize((float) 18);
        counter.setTypeface(typeface);
        // counter.getBackground().setAlpha(50);
        counter.setPadding(20, 50, 20, 50);
        counter.setLayoutParams(params);
        addContentView(counter, params);
        counter.getLayoutParams().width = 600;
        counter.getLayoutParams().height = 250;

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int height = displaymetrics.heightPixels;
        int width = displaymetrics.widthPixels;

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        modeChanger = new ImageButton(this);
        modeChanger.setImageResource(R.drawable.yoga);
        modeChanger.setScaleType(ImageView.ScaleType.CENTER_CROP);
        modeChanger.setOnClickListener(ClickListener);
        modeChanger.setBackgroundColor(Color.TRANSPARENT);
        modeChanger.getBackground().setAlpha(80);
        lp.setMargins(width - 200, height - 2050, 8, 0);
        addContentView(modeChanger, lp);
        modeChanger.getLayoutParams().width = 150;
        modeChanger.getLayoutParams().height = 220;
        modeChanger.requestLayout();

        LinearLayout.LayoutParams flipperLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        cameraFlipper = new ImageButton(this);
        cameraFlipper.setImageResource(R.drawable.toggle);
        cameraFlipper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cameraFlipper.setOnClickListener(ToggleClickListener);
        cameraFlipper.setBackgroundColor(Color.TRANSPARENT);
        cameraFlipper.getBackground().setAlpha(50);
        flipperLayout.setMargins(width - 200, height - 500, 8, 0);
        addContentView(cameraFlipper, flipperLayout);
        cameraFlipper.getLayoutParams().width = 150;
        cameraFlipper.getLayoutParams().height = 220;
        cameraFlipper.requestLayout();

        recordingText = new TextView(this);
        LinearLayout.LayoutParams recordingTextParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        recordingTextParams.leftMargin = 220;
        recordingTextParams.topMargin = 1600;
        recordingText.setTextColor(Color.parseColor(INCORRECT_COLOR));
        recordingText.setText(RECORDINGTEXT);
        recordingText.setTextSize((float) 18);
        recordingText.setTypeface(typeface);
        recordingText.setPadding(20, 50, 20, 50);
        recordingText.setLayoutParams(recordingTextParams);
        addContentView(recordingText, recordingTextParams);
        recordingText.setVisibility(View.INVISIBLE);

        processor.addPacketCallback(OUTPUT_LANDMARKS_STREAM_NAME, (packet) -> {
            next_time = packet.getTimestamp();
            fps = (next_time - prev_time) / 1000;
            prev_time = next_time;
            // Log.v(TAG, "Received pose landmarks packet.");
            // Log.v(TAG, "FPS: " + fps);
            try {
                byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                NormalizedLandmarkList poseLandmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                if (selectedEffectId == SELECTED_YOGA_MODE) {
                    yogaDetection(poseLandmarks);
                } else if (selectedEffectId == SELECTED_POSE_MODE) {
                    poseCorrection(poseLandmarks);
                } else {
                    trainingCount(poseLandmarks);
                }

            } catch (InvalidProtocolBufferException exception) {
                poseName.setTextColor(Color.parseColor(DEFAULT_COLOR));
                poseName.setText(SELECTED_TEXT.get(selectedEffectId));
                Log.e(TAG, "Failed to get proto.", exception);
            }
        });

        processor.addPacketCallback("roi_from_landmarks", (packet) -> {
            next_time = packet.getTimestamp();

            // fps = (next_time - prev_time);
            // prev_time = next_time;
            // Log.v(TAG, "Received ROI landmarks packet.");
            // Log.v(TAG, "FPS: " + fps);
            try {
                byte[] rect = PacketGetter.getProtoBytes(packet);
                NormalizedRect poseROI = NormalizedRect.parseFrom(rect);
                rotationAngle = poseROI.getRotation();
            } catch (InvalidProtocolBufferException exception) {
                Log.e(TAG, "Failed to get proto.", exception);
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        YOGA_SUMMARY_COUNTER = SummaryInfo.getYogaSummaryCounterLive();
        LOG_TIMES = SummaryInfo.getLogTimesLive();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    // Used to obtain the content view for this application. If you are extending this class, and
    // have a custom layout, override this method and return the custom layout.
    protected int getContentViewLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), NUM_BUFFERS);
        converter.setFlipY(true);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
        YOGA_SUMMARY_COUNTER = SummaryInfo.getYogaSummaryCounterLive();
        LOG_TIMES = SummaryInfo.getLogTimesLive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();
        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing =
                useFront
                        ? CameraHelper.CameraFacing.FRONT
                        : CameraHelper.CameraFacing.BACK;
        cameraHelper.startCamera(
                this, cameraFacing, /*unusedSurfaceTexture=*/ null, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    public void flipCamera(boolean frontCamera) {
        useFront = frontCamera;
        converter.close();

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
        converter = new ExternalTextureConverter(eglManager.getContext(), NUM_BUFFERS);
        converter.setFlipY(true);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }


    public void yogaDetection(NormalizedLandmarkList poseLandmarks) {
        if (poseLandmarks.getLandmarkList().size() == 0) {
            if (currentYogaPose != -1) {
                // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter/1000;
                if (currentCounter > 1000) {
                    LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                }
                currentYogaPose = -1;
            }
            currentCounter = 0;
            counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
            counter.setText("--:--");
            Log.v(TAG, "NO DATA");
            poseName.setTextColor(Color.parseColor(DEFAULT_COLOR));
            poseName.setText(SELECTED_TEXT.get(selectedEffectId));
            return;
        }
        double twoFeetDistance = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(29).getX(),
                (double) poseLandmarks.getLandmarkList().get(29).getY(),
                (double) poseLandmarks.getLandmarkList().get(30).getX(),
                (double) poseLandmarks.getLandmarkList().get(30).getY());
        double hipLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(23).getX(),
                (double) poseLandmarks.getLandmarkList().get(23).getY(),
                (double) poseLandmarks.getLandmarkList().get(24).getX(),
                (double) poseLandmarks.getLandmarkList().get(24).getY());
        double shoulderLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(11).getX(),
                (double) poseLandmarks.getLandmarkList().get(11).getY(),
                (double) poseLandmarks.getLandmarkList().get(12).getX(),
                (double) poseLandmarks.getLandmarkList().get(12).getY());
        double twoLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(27).getX(),
                (double) poseLandmarks.getLandmarkList().get(27).getY(),
                (double) poseLandmarks.getLandmarkList().get(28).getX(),
                (double) poseLandmarks.getLandmarkList().get(28).getY());
        double twoHandLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(19).getX(),
                (double) poseLandmarks.getLandmarkList().get(19).getY(),
                (double) poseLandmarks.getLandmarkList().get(20).getX(),
                (double) poseLandmarks.getLandmarkList().get(20).getY());
        double twoElbowLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(13).getX(),
                (double) poseLandmarks.getLandmarkList().get(13).getY(),
                (double) poseLandmarks.getLandmarkList().get(14).getX(),
                (double) poseLandmarks.getLandmarkList().get(14).getY());
        double lhipToLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(23).getX(),
                (double) poseLandmarks.getLandmarkList().get(23).getY(),
                (double) poseLandmarks.getLandmarkList().get(29).getX(),
                (double) poseLandmarks.getLandmarkList().get(29).getY());
        double rhipToLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(24).getX(),
                (double) poseLandmarks.getLandmarkList().get(24).getY(),
                (double) poseLandmarks.getLandmarkList().get(30).getX(),
                (double) poseLandmarks.getLandmarkList().get(30).getY());
        double lhandToLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(19).getX(),
                (double) poseLandmarks.getLandmarkList().get(19).getY(),
                (double) poseLandmarks.getLandmarkList().get(29).getX(),
                (double) poseLandmarks.getLandmarkList().get(29).getY());
        double rhandToLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(20).getX(),
                (double) poseLandmarks.getLandmarkList().get(20).getY(),
                (double) poseLandmarks.getLandmarkList().get(30).getX(),
                (double) poseLandmarks.getLandmarkList().get(30).getY());
        double lhandToHipLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(15).getX(),
                (double) poseLandmarks.getLandmarkList().get(15).getY(),
                (double) poseLandmarks.getLandmarkList().get(23).getX(),
                (double) poseLandmarks.getLandmarkList().get(23).getY());
        double rhandToHipLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(16).getX(),
                (double) poseLandmarks.getLandmarkList().get(16).getY(),
                (double) poseLandmarks.getLandmarkList().get(24).getX(),
                (double) poseLandmarks.getLandmarkList().get(24).getY());
        // Not distance, but position in y
        // Not distance, but position in y
        double headToHip = poseLandmarks.getLandmarkList().get(0).getY() - poseLandmarks.getLandmarkList().get(23).getY();

        // Head is below hip
        if (headToHip > 0.0) {
            Log.v(TAG, "HEAD BELOW " + twoFeetDistance + " shoulder: " + shoulderLength);
            // Not distance, but position in y
            double lhipToLeg = poseLandmarks.getLandmarkList().get(23).getY()
                    - poseLandmarks.getLandmarkList().get(29).getY();
            double rhipToLeg = poseLandmarks.getLandmarkList().get(24).getY()
                    - poseLandmarks.getLandmarkList().get(30).getY();

            double lHandToLeg = poseLandmarks.getLandmarkList().get(19).getY()
                    - poseLandmarks.getLandmarkList().get(29).getY();
            double rHandToLeg = poseLandmarks.getLandmarkList().get(20).getY()
                    - poseLandmarks.getLandmarkList().get(30).getY();

            double lhipToLegRatio = Math.abs(lhipToLeg / lhipToLegLength);
            double rhipToLegRatio = Math.abs(rhipToLeg / rhipToLegLength);
            double lHandToLegRatio = Math.abs(lHandToLeg / lhandToLegLength);
            double rHandToLegRatio = Math.abs(rHandToLeg / rhandToLegLength);
            Log.v(TAG, "lhipToLegRatio " + lhipToLeg / lhipToLegLength);
            Log.v(TAG, "rhipToLegRatio " + rhipToLeg / rhipToLegLength);
            Log.v(TAG, "lHandToLegRatio " + lHandToLegRatio);
            Log.v(TAG, "rHandToLegRatio " + rHandToLegRatio);

            if ((lhipToLegRatio > 0.003) && (rhipToLegRatio > 0.003)) // Standing
            {
                Log.v(TAG, "STANDING left: " + lhipToLeg + " right: " + rhipToLeg);
                if ((lHandToLegRatio > 0.003) && (rHandToLegRatio > 0.003)) // Hand on the air
                {
                    if (currentYogaPose != -1) {
                        // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                        Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                        if (currentCounter > 1000) {
                            LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                        }

                        currentYogaPose = -1;
                    }
                    currentCounter = 0;
                    counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                    counter.setText("--:--");
                    Log.v(TAG, "HAND ON THE AIR left: " + lHandToLeg + " right: " + rHandToLeg);
                    poseName.setTextColor(Color.parseColor(INCORRECT_COLOR));
                    poseName.setText(SELECTED_TEXT.get(selectedEffectId));

                } else // Hand on the floor
                {
                    Log.v(TAG, "HAND ON THE FLOOR left: " + lHandToLeg + " right: " + rHandToLeg);
                    double lhandToLegDistance = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(19).getX(),
                            (double) poseLandmarks.getLandmarkList().get(19).getY(),
                            (double) poseLandmarks.getLandmarkList().get(29).getX(),
                            (double) poseLandmarks.getLandmarkList().get(29).getY());
                    double rhandToLegDistance = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(20).getX(),
                            (double) poseLandmarks.getLandmarkList().get(20).getY(),
                            (double) poseLandmarks.getLandmarkList().get(30).getX(),
                            (double) poseLandmarks.getLandmarkList().get(30).getY());
                    double lhipToShoulderDistance = distanceBetweenTwoPoints(
                            (double) poseLandmarks.getLandmarkList().get(23).getX(),
                            (double) poseLandmarks.getLandmarkList().get(23).getY(),
                            (double) poseLandmarks.getLandmarkList().get(11).getX(),
                            (double) poseLandmarks.getLandmarkList().get(11).getY());
                    double rhipToShoulderDistance = distanceBetweenTwoPoints(
                            (double) poseLandmarks.getLandmarkList().get(24).getX(),
                            (double) poseLandmarks.getLandmarkList().get(24).getY(),
                            (double) poseLandmarks.getLandmarkList().get(12).getX(),
                            (double) poseLandmarks.getLandmarkList().get(12).getY());
                    Log.v(TAG, "lHand to leg: " + lhandToLegDistance + " right hand to leg: " + rhandToLegDistance);
                    Log.v(TAG,
                            "lHip to shoulder: " + lhipToShoulderDistance + " right Hip to shoulder: " + rhipToShoulderDistance);
                    if (lhandToLegDistance > lhipToShoulderDistance && rhandToLegDistance > rhipToShoulderDistance) {
                        if (currentYogaPose != DOWNDOG) {
                            if (currentYogaPose != -1) {
                                // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                                Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                                if (currentCounter > 1000) {
                                    LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                                }
                            }
                            currentYogaPose = DOWNDOG;
                            currentCounter = 0;
                            counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                            counter.setText("--:--");
                        } else {
                            YOGA_SUMMARY_COUNTER[currentYogaPose] += fps;
                            currentCounter += fps;
                            // if (currentCounter > 1000) {
                            if (YOGA_SUMMARY_COUNTER[currentYogaPose] > 1000) {
                                counter.setTextColor(Color.parseColor(CORRECT_COLOR));
                                counter.setText(YOGA_SUMMARY_COUNTER[currentYogaPose] / 1000 + "s");
                            }
                            Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);
                        }
                        Log.v(TAG, "DOWNDOG DETECTED");
                        poseName.setTextColor(Color.parseColor(CORRECT_COLOR));
                        poseName.setText("DOWNDOG DETECTED");
                    } else {
                        if (currentYogaPose != -1) {
                            // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                            Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                            if (currentCounter > 1000) {
                                LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                            }

                            currentYogaPose = -1;
                        }
                        currentCounter = 0;
                        counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                        counter.setText("--:--");
                        poseName.setTextColor(Color.parseColor(INCORRECT_COLOR));
                        poseName.setText(SELECTED_TEXT.get(selectedEffectId));
                    }
                }
            } else // Sitting
            {
                if (currentYogaPose != -1) {
                    // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                    Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                    if (currentCounter > 1000) {
                        LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                    }

                    currentYogaPose = -1;
                }
                counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                counter.setText("--:--");
                currentCounter = 0;
                poseName.setTextColor(Color.parseColor(INCORRECT_COLOR));
                poseName.setText(SELECTED_TEXT.get(selectedEffectId));
            }
        } else // HEAD ABOVE THE HIP
        {
            Log.v(TAG, "HEAD ABOVE " + twoFeetDistance + " shoulder: " + shoulderLength);
            // Not distance, but position in y
            double lhipToLeg = poseLandmarks.getLandmarkList().get(23).getY()
                    - poseLandmarks.getLandmarkList().get(29).getY();
            double rhipToLeg = poseLandmarks.getLandmarkList().get(24).getY()
                    - poseLandmarks.getLandmarkList().get(30).getY();

            double lHandToLeg = poseLandmarks.getLandmarkList().get(19).getY()
                    - poseLandmarks.getLandmarkList().get(29).getY();
            double rHandToLeg = poseLandmarks.getLandmarkList().get(20).getY()
                    - poseLandmarks.getLandmarkList().get(30).getY();

            double lhipToLegRatio = Math.abs(lhipToLeg / lhipToLegLength);
            double rhipToLegRatio = Math.abs(rhipToLeg / rhipToLegLength);
            double lHandToLegRatio = Math.abs(lHandToLeg / lhandToLegLength);
            double rHandToLegRatio = Math.abs(rHandToLeg / rhandToLegLength);
            Log.v(TAG, "lhipToLegRatio " + lhipToLeg / lhipToLegLength);
            Log.v(TAG, "rhipToLegRatio " + rhipToLeg / rhipToLegLength);
            Log.v(TAG, "lHandToLegRatio " + lHandToLegRatio);
            Log.v(TAG, "rHandToLegRatio " + rHandToLegRatio);

            if ((lhipToLegRatio > 0.003) && (rhipToLegRatio > 0.003)) // Standing
            {
                Log.v(TAG, "HEAD ABOVE " + twoFeetDistance + " shoulderLength: " + shoulderLength);
                double rightLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(24).getX(),
                        (double) poseLandmarks.getLandmarkList().get(24).getY(),
                        (double) poseLandmarks.getLandmarkList().get(28).getX(),
                        (double) poseLandmarks.getLandmarkList().get(28).getY());
                double rightUpperLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(24).getX(),
                        (double) poseLandmarks.getLandmarkList().get(24).getY(),
                        (double) poseLandmarks.getLandmarkList().get(26).getX(),
                        (double) poseLandmarks.getLandmarkList().get(26).getY());
                double leftLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(23).getX(),
                        (double) poseLandmarks.getLandmarkList().get(23).getY(),
                        (double) poseLandmarks.getLandmarkList().get(27).getX(),
                        (double) poseLandmarks.getLandmarkList().get(27).getY());
                double leftUpperLegLength = distanceBetweenTwoPoints((double) poseLandmarks.getLandmarkList().get(23).getX(),
                        (double) poseLandmarks.getLandmarkList().get(23).getY(),
                        (double) poseLandmarks.getLandmarkList().get(25).getX(),
                        (double) poseLandmarks.getLandmarkList().get(25).getY());
                Log.v(TAG, "RIGHT LEG DETECTED " + rightLegLength);
                Log.v(TAG, "RIGHT UPPER LEG DETECTED " + rightUpperLegLength);
                if (rightUpperLegLength >= rightLegLength || leftUpperLegLength >= leftLegLength) // one leg straight, one leg
                // curved
                {
                    if (currentYogaPose != TREE_POSE) {
                        if (currentYogaPose != -1) {
                            // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                            Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                            if (currentCounter > 1000) {
                                LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                            }
                        }
                        currentYogaPose = TREE_POSE;
                        currentCounter = 0;
                        counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                        counter.setText("--:--");
                    } else {
                        YOGA_SUMMARY_COUNTER[currentYogaPose] += fps;
                        currentCounter += fps;
                        // if (currentCounter > 1000) {
                        if (YOGA_SUMMARY_COUNTER[currentYogaPose] > 1000) {
                            counter.setTextColor(Color.parseColor(CORRECT_COLOR));
                            // counter.setText(currentCounter / 1000 + "s");
                            counter.setText(YOGA_SUMMARY_COUNTER[currentYogaPose] / 1000 + "s");
                        }

                        Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);
                    }
                    Log.v(TAG, "TREE POSE DETECTED");
                    poseName.setTextColor(Color.parseColor(CORRECT_COLOR));
                    poseName.setText("TREE POSE DETECTED");
                } else // both legs straight
                {
                    if (twoLegLength > twoElbowLength && twoLegLength < twoHandLength) {
                        if (currentYogaPose != WARRIOR_2) {
                            if (currentYogaPose != -1) {
                                // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                                Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                                if (currentCounter > 1000) {
                                    LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                                }
                            }
                            currentYogaPose = WARRIOR_2;
                            currentCounter = 0;
                            counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                            counter.setText("--:--");
                        } else {
                            YOGA_SUMMARY_COUNTER[currentYogaPose] += fps;
                            currentCounter += fps;
                            // if (currentCounter > 1000) {
                            if (YOGA_SUMMARY_COUNTER[currentYogaPose] > 1000) {
                                counter.setTextColor(Color.parseColor(CORRECT_COLOR));
                                counter.setText(YOGA_SUMMARY_COUNTER[currentYogaPose] / 1000 + "s");
                            }
                            Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);
                        }
                        Log.v(TAG, "WARRIOR 2 POSE DETECTED");
                        poseName.setTextColor(Color.parseColor(CORRECT_COLOR));
                        poseName.setText("WARRIOR 2 POSE DETECTED");
                    } else {
                        if (currentYogaPose != -1) {
                            // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                            Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                            if (currentCounter > 1000) {
                                LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                            }

                            currentYogaPose = -1;
                        }
                        currentCounter = 0;
                        counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                        counter.setText("--:--");
                        poseName.setTextColor(Color.parseColor(INCORRECT_COLOR));
                        poseName.setText(SELECTED_TEXT.get(selectedEffectId));
                    }
                }
            } else // Sitting
            {
                Log.v(TAG, "Sitting left: " + lhipToLeg + " right: " + rhipToLeg);
                double lHandToHip = poseLandmarks.getLandmarkList().get(15).getY()
                        - poseLandmarks.getLandmarkList().get(23).getY();
                double rHandToHip = poseLandmarks.getLandmarkList().get(16).getY()
                        - poseLandmarks.getLandmarkList().get(24).getY();

                double lHandToHipRatio = Math.abs(lHandToHip / lhandToHipLength);
                double rHandToHipRatio = Math.abs(rHandToHip / rhandToHipLength);
                double lFingerToToe = poseLandmarks.getLandmarkList().get(19).getY()
                        - poseLandmarks.getLandmarkList().get(31).getY();
                if (lFingerToToe < 0.0) // Hand on the air
                {
                    if (currentYogaPose != -1) {
                        // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                        Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                        if (currentCounter > 1000) {
                            LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                        }

                        currentYogaPose = -1;
                    }
                    currentCounter = 0;
                    Log.v(TAG, "HAND TO HIP left: " + lHandToHipRatio + " right: " + rHandToHipRatio);
                    counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                    counter.setText("--:--");
                    poseName.setTextColor(Color.parseColor(INCORRECT_COLOR));
                    poseName.setText(SELECTED_TEXT.get(selectedEffectId));
                } else // Hand on the floor, near hip
                {
                    if (currentYogaPose != DANDASANA) {
                        if (currentYogaPose != -1) {
                            // YOGA_SUMMARY_COUNTER[currentYogaPose] += currentCounter;
                            Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);

                            if (currentCounter > 1000) {
                                LOG_TIMES.get(currentYogaPose).add(currentCounter / 1000);
                            }
                        }
                        currentYogaPose = DANDASANA;
                        currentCounter = 0;
                        counter.setTextColor(Color.parseColor(DEFAULT_COLOR));
                        counter.setText("--:--");
                    } else {
                        YOGA_SUMMARY_COUNTER[currentYogaPose] += fps;
                        currentCounter += fps;
                        // if (currentCounter > 1000) {
                        if (YOGA_SUMMARY_COUNTER[currentYogaPose] > 1000) {
                            counter.setTextColor(Color.parseColor(CORRECT_COLOR));
                            counter.setText(YOGA_SUMMARY_COUNTER[currentYogaPose] / 1000 + "s");
                        }
                        // YOGA_SUMMARY_COUNTER[currentYogaPose] += fps;
                        Log.v(TAG, "CURRENT POSE " + currentYogaPose + " time: " + YOGA_SUMMARY_COUNTER[currentYogaPose]);
                    }
                    Log.v(TAG, "HAND TO HIP left: " + lHandToHipRatio + " right: " + rHandToHipRatio);
                    Log.v(TAG, "DANASANA DETECTED");
                    poseName.setTextColor(Color.parseColor(CORRECT_COLOR));
                    poseName.setText("DANASANA DETECTED");
                }
            }
        }
    }

    public void poseCorrection(NormalizedLandmarkList poseLandmarks) {
        float hipAngle = 0.0f;
        hipAngle = angleBetween2Lines(poseLandmarks.getLandmarkList().get(12).getX(),
                poseLandmarks.getLandmarkList().get(12).getY(), poseLandmarks.getLandmarkList().get(26).getX(),
                poseLandmarks.getLandmarkList().get(26).getX(), poseLandmarks.getLandmarkList().get(24).getX(),
                poseLandmarks.getLandmarkList().get(24).getY(), rotationAngle);
        if ((110 < hipAngle && hipAngle < 250) || (60 > hipAngle) || (300 < hipAngle)) {
            Log.v(TAG, "Almost There");
            YOGA_SUMMARY_COUNTER[POSTURE] += fps;
            if (YOGA_SUMMARY_COUNTER[POSTURE] > 1000) {
                counter.setTextColor(Color.parseColor(CORRECT_COLOR));
                counter.setText(YOGA_SUMMARY_COUNTER[POSTURE] / 1000 + "s");
            }
            poseName.setTextColor(Color.parseColor(INCORRECT_COLOR));
            poseName.setText(WRONG_POSTURE_TEXT);
        } else {
            poseName.setTextColor(Color.parseColor(CORRECT_COLOR));
            poseName.setText(RIGHT_POSTURE_TEXT);
        }
    }

    public void trainingCount(NormalizedLandmarkList poseLandmarks) {
        if (countingStarted == true) {
            if (referencePose == null) {
                referencePose = poseLandmarks;
                counter.setTextColor(Color.parseColor(INCORRECT_COLOR));
                counter.setText("" + YOGA_SUMMARY_COUNTER[REPETITION]);
                return;
            } else {
                double scale = distanceBetweenTwoPoints(
                        referencePose.getLandmarkList().get(11).getX(),
                        referencePose.getLandmarkList().get(11).getY(),
                        referencePose.getLandmarkList().get(12).getX(),
                        referencePose.getLandmarkList().get(12).getY()
                ) / SHOULDER_LENGTH;
                double measure_length = scale * MATCHING_LENGTH;
                Log.v(TAG, "MEASURE LENGTH IS " + measure_length);

                for (int i = 0; i < poseLandmarks.getLandmarkList().size() - 1; i++) {
                    double distanceRef = distanceBetweenTwoPoints(
                            referencePose.getLandmarkList().get(i).getX(),
                            referencePose.getLandmarkList().get(i).getY(),
                            referencePose.getLandmarkList().get(i + 1).getX(),
                            referencePose.getLandmarkList().get(i + 1).getY()
                    );
                    double distanceActual = distanceBetweenTwoPoints(
                            poseLandmarks.getLandmarkList().get(i).getX(),
                            poseLandmarks.getLandmarkList().get(i).getY(),
                            poseLandmarks.getLandmarkList().get(i + 1).getX(),
                            poseLandmarks.getLandmarkList().get(i + 1).getY()
                    );
                    if (distanceRef * scale - distanceActual * scale > measure_length) {
                        Log.v(TAG, "DISTANCE REF IS " + distanceRef * scale);
                        Log.v(TAG, "DISTANCE ACTUAL IS " + distanceActual * scale);
                        Log.v(TAG, "MEASURE LENGTH IS " + measure_length);
                        if (nextState == true) {
                            nextStateCount++;
                            if (nextStateCount >= DISCARDED_FRAME) {
                                nextState = false;
                            }
                        }
                        return;
                    }
                }
                if (nextState == false) {
                    YOGA_SUMMARY_COUNTER[REPETITION] += 1;
                    nextState = true;
                    nextStateCount = 0;
                }
                counter.setTextColor(Color.parseColor(INCORRECT_COLOR));
                counter.setText("" + YOGA_SUMMARY_COUNTER[REPETITION]);
            }
        }
        return;
    }

    public static float angleBetween2Lines(float x1, float y1, float x2, float y2, float x3, float y3, float angle) {
        // convert angle first
        float reverse_angle = angle * -1;
        float x1_convert = (float) (y1 * Math.cos(reverse_angle) - x1 * Math.sin(reverse_angle));
        float y1_convert = (float) (y1 * Math.sin(reverse_angle) + x1 * Math.cos(reverse_angle));
        float x2_convert = (float) (y2 * Math.cos(reverse_angle) - x2 * Math.sin(reverse_angle));
        float y2_convert = (float) (y2 * Math.sin(reverse_angle) + x2 * Math.cos(reverse_angle));
        float x3_convert = (float) (y3 * Math.cos(reverse_angle) - x3 * Math.sin(reverse_angle));
        float y3_convert = (float) (y3 * Math.sin(reverse_angle) + x3 * Math.cos(reverse_angle));
        float angle1 = (float) Math.atan2(y1_convert - y3_convert, x1_convert - x3_convert);
        float angle2 = (float) Math.atan2(y2_convert - y3_convert, x2_convert - x3_convert);
        float calculatedAngle = (float) Math.toDegrees(angle1 - angle2);
        if (calculatedAngle < 0)
            calculatedAngle += 360;
        return calculatedAngle;
    }

    public double distanceBetweenTwoPoints(double x1, double y1, double x2, double y2) {
        // zoom in
        double x1_convert = x1 * 100;
        double y1_convert = y1 * 100;
        double x2_convert = x2 * 100;
        double y2_convert = y2 * 100;
        // Log.v(TAG, "x1 " + x1 + " y1 " + y1 + " x2 " + x2 + " y2 " + y2);
        return Math.sqrt(Math.pow(x2_convert - x1_convert, 2) + Math.pow(y2_convert - y1_convert, 2));
    }

}