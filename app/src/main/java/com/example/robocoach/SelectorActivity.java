package com.example.robocoach;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.robocoach.helper.MediaHelper;
import com.example.robocoach.helper.MyPermissionHelper;

import java.util.ArrayList;

public class SelectorActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "SelectorActivity";
    public static final int SELECT_FILE_REQ = 0xf3;
    public static final int SEGMENTATION_START_REQ = 0xf4;
    private ImageButton selectButton;
    private Button startButton;
    private TextView label;
    private ArrayList<Uri> files = new ArrayList<Uri>();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_selection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_summary) {
            Intent intent = new Intent(getBaseContext(), SummaryActivity.class);
            intent.putExtra("RUNNINGMODE", "VIDEO");

            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selector);
        initViews();
    }

    private void initViews() {
        selectButton = findViewById(R.id.select_button);
        startButton = findViewById(R.id.start_button);
        label = findViewById(R.id.label);
        selectButton.setOnClickListener(this);
        startButton.setOnClickListener(this);
    }

    private void requestPermission() {
        MyPermissionHelper.checkAndRequestReadPermissions(this);
        MyPermissionHelper.checkAndRequestreadWritePermissions(this);
    }


    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.start_button:
                cur_index = 0;
                startSegmentation();

                break;
            case R.id.select_button:
                selectFiles();
                break;
        }
    }

    private void selectFiles() {
        selectImages();
    }


    public void selectImages() {
        if (MyPermissionHelper.readPermissionsGranted(this)) {
            files.clear();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.setType("video/*");
            startActivityForResult(intent, SELECT_FILE_REQ);
        } else {
            requestPermission();
        }

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_FILE_REQ) {
                if (data != null) {
                    if (data.getClipData() != null) {
                        for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                            Uri uri = data.getClipData().getItemAt(i).getUri();
                            files.add(uri);
                        }
                    } else {
                        Uri uri = data.getData();
                        files.add(uri);
                    }
                    onFilesSelected();
                }
            } else if (requestCode == SEGMENTATION_START_REQ) {
                Log.d(TAG, "Path is : " + data.getStringExtra("OUTPUT_PATH"));
                cur_index = cur_index + 1;
                startSegmentation();
            }
        }

    }

    private void onFilesSelected() {
        if (files.size() != 0) {
            Log.d(TAG, "FILE_PATH: " + MediaHelper.getPath(this, files.get(0)));
            label.setText(files.size() + " files selected. Click on START button to start tracking!");
//            label.setText("Selected " + MediaHelper.getPath(this,files.get(0)));
        } else {
            label.setText("Click on the icon above to select video");
        }

    }

    int cur_index = 0;

    private void startSegmentation() {
        try {
            if (cur_index < files.size()) {
                startButton.setVisibility(View.GONE);
                Intent intent = new Intent(this, SegmenterActivity.class);
                intent.putExtra("FILE_URI", MediaHelper.getPath(this, files.get(cur_index)));
                startActivityForResult(intent, SEGMENTATION_START_REQ);
            } else {
                startButton.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }

    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MyPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
