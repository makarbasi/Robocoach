package com.example.robocoach;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


public class ListAdapter extends BaseAdapter {

    Context context;
    private final String[] values;
    private final String[] numbers;
    private final List<List<Long>> logs;
    private final int[] images;
    private static final String CORRECT_COLOR = "#008577";

    public ListAdapter(Context context, String[] values, String[] numbers, int[] images, List<List<Long>> logs) {
        //super(context, R.layout.single_list_app_item, utilsArrayList);
        this.context = context;
        this.values = values;
        this.numbers = numbers;
        this.images = images;
        this.logs = logs;
    }

    @Override
    public int getCount() {
        return values.length;
    }

    @Override
    public Object getItem(int i) {
        return i;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder viewHolder;

        final View result;

        if (convertView == null) {

            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(context);
//            convertView = inflater.inflate(R.layout.row_item, parent, false);
            convertView = inflater.inflate(R.layout.expandable_item, parent, false);
            viewHolder.txtName = (TextView) convertView.findViewById(R.id.aNametxt);
            viewHolder.txtVersion = (TextView) convertView.findViewById(R.id.aVersiontxt);
            viewHolder.icon = (ImageView) convertView.findViewById(R.id.appIconIV);

            result = convertView;

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
            result = convertView;
        }

        viewHolder.txtName.setText(values[position]);
        viewHolder.txtVersion.setText("" + numbers[position]);
        viewHolder.icon.setImageResource(images[position]);

        long total = Long.parseLong(numbers[position].replaceAll("[^0-9]", ""));

        if (position >= logs.size()) {
            return convertView;
        }
        long sum = 0;
        for (int i = 0; i < logs.get(position).size(); i++) {
            long separate_time = logs.get(position).get(i);

            if (i == logs.get(position).size() - 1) {
                separate_time = total - sum;
            } else {
                sum += separate_time;
            }
            TextView logName = new TextView(this.context);
            LinearLayout linearLayout = (LinearLayout) convertView.findViewById(R.id.container);
            logName.setText("Correct Time: " + separate_time + "s");
            linearLayout.addView(logName);
            logName.setTextSize((float) 12);
            logName.setTextColor(Color.parseColor(CORRECT_COLOR));
            logName.setPadding(20, 15, 0, 10);
            logName.getLayoutParams().height = 80;
            logName.requestLayout();
        }

        if (total > 0 && logs.get(position).size() == 0) {
            TextView logName = new TextView(this.context);
            LinearLayout linearLayout = (LinearLayout) convertView.findViewById(R.id.container);
            logName.setText("Correct Time: " + total + "s");
            linearLayout.addView(logName);
            logName.setTextSize((float) 12);
            logName.setTextColor(Color.parseColor(CORRECT_COLOR));
            logName.setPadding(20, 15, 0, 10);
            logName.getLayoutParams().height = 80;
            logName.requestLayout();
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView txtName;
        TextView txtVersion;
        ImageView icon;
    }

}