package com.example.robocoach;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.robocoach.helper.SummaryInfo;

import java.util.ArrayList;
import java.util.List;

import static com.example.robocoach.helper.SummaryInfo.DANDASANA;
import static com.example.robocoach.helper.SummaryInfo.DOWNDOG;
import static com.example.robocoach.helper.SummaryInfo.POSTURE;
import static com.example.robocoach.helper.SummaryInfo.REPETITION;
import static com.example.robocoach.helper.SummaryInfo.TREE_POSE;
import static com.example.robocoach.helper.SummaryInfo.WARRIOR_2;

public class SummaryActivity extends AppCompatActivity {

    int[] images = {R.drawable.dandasana, R.drawable.downward_dog, R.drawable.tree, R.drawable.warrior2, R.drawable.correct_sitting, R.drawable.pushup};

    String[] version = {"Dandasana Pose", "DownDog Pose", "Tree Pose", "Warrior 2 Pose", "Posture Correction", "Workout Repetition"};

    String[] versionNumber = {"Total Time: ", "Total Time: ", "Total Time: ", "Total Time: ", "Total Incorrect Time: ", "Total Repetition: "};

    ListView lView;

    ListAdapter lAdapter;
    long YOGA_SUMMARY_COUNTER[] = null;
    List<List<Long>> LOG_TIMES = null;
    String mode = "LIVE";

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_clearsummary, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clearbutton) {
            for (int i = 0; i < YOGA_SUMMARY_COUNTER.length; i++) {
                YOGA_SUMMARY_COUNTER[i] = 0;
            }
            LOG_TIMES = new ArrayList<List<Long>>();
            SummaryInfo.reset(mode);

            lView = (ListView) findViewById(R.id.androidList);
            versionNumber[0] = "Total Time: 0s";
            versionNumber[1] = "Total Time: 0s";
            versionNumber[2] = "Total Time: 0s";
            versionNumber[3] = "Total Time: 0s";
            versionNumber[4] = "Total Incorrect Time: 0s";
            versionNumber[5] = "Total Repetition: 0 times";

            lAdapter = new ListAdapter(SummaryActivity.this, version, versionNumber, images, LOG_TIMES);

            lView.setAdapter(lAdapter);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.summary);
        mode = getIntent().getStringExtra("RUNNINGMODE");
        if (mode.equals("LIVE")) {
            YOGA_SUMMARY_COUNTER = SummaryInfo.getYogaSummaryCounterLive();
            LOG_TIMES = SummaryInfo.getLogTimesLive();
        } else {
            YOGA_SUMMARY_COUNTER = SummaryInfo.getYogaSummaryCounterVideo();
            LOG_TIMES = SummaryInfo.getLogTimesVideo();
        }
        String dandaTime = "" + YOGA_SUMMARY_COUNTER[DANDASANA] / 1000;
        String downdogTime = "" + YOGA_SUMMARY_COUNTER[DOWNDOG] / 1000;
        String treeTime = "" + YOGA_SUMMARY_COUNTER[TREE_POSE] / 1000;
        String warrior2Time = "" + YOGA_SUMMARY_COUNTER[WARRIOR_2] / 1000;
        String postureTime = "" + YOGA_SUMMARY_COUNTER[POSTURE] / 1000;
        String repetitionTime = "" + YOGA_SUMMARY_COUNTER[REPETITION] / 1000;

        versionNumber[0] = versionNumber[0] + dandaTime + "s";
        versionNumber[1] = versionNumber[1] + downdogTime + "s";
        versionNumber[2] = versionNumber[2] + treeTime + "s";
        versionNumber[3] = versionNumber[3] + warrior2Time + "s";
        versionNumber[4] = versionNumber[4] + postureTime + "s";
        versionNumber[5] = versionNumber[5] + repetitionTime + " times";

        lView = (ListView) findViewById(R.id.androidList);

        lAdapter = new ListAdapter(SummaryActivity.this, version, versionNumber, images, LOG_TIMES);

        lView.setAdapter(lAdapter);
    }
}