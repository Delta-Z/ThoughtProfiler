package delta.humanprofiler;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

import org.achartengine.ChartFactory;
import org.achartengine.model.CategorySeries;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.SimpleSeriesRenderer;

import java.util.Map;

public class ViewDataActivity extends AppCompatActivity {

    private View mChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_data);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Sampled Thought Distribution");
        actionBar.setIcon(R.drawable.ic_notification);

        Map<String, Integer> categories =
                SamplesDBHelper.getInstance(getApplicationContext()).getCategoriesDistribution();

        // Instantiating CategorySeries to plot Pie Chart
        CategorySeries distributionSeries = new CategorySeries("Thought distribution");
        for (Map.Entry<String, Integer> entry : categories.entrySet()) {
            distributionSeries.add(entry.getKey(), entry.getValue());
        }

        //  From Paul Green-Armytage's "A Colour Alphabet and the Limits of Colour Coding".
        int[][] colors = {
                {240,163,255},{0,117,220},{153,63,0},{76,0,92},{25,25,25},{0,92,49},{43,206,72},
                {255,204,153},{128,128,128},{148,255,181},{143,124,0},{157,204,0},{194,0,136},
                {0,51,128},{255,164,5},{255,168,187},{66,102,0},{255,0,16},{94,241,242},{0,153,143},
                {224,255,102},{116,10,255},{153,0,0},{255,255,128},{255,255,0},{255,80,5}
        };
        // Instantiating a renderer for the Pie Chart
        DefaultRenderer defaultRenderer = new DefaultRenderer();
        defaultRenderer.setShowLabels(false);
        defaultRenderer.setShowLegend(true);
        defaultRenderer.setFitLegend(true);
        defaultRenderer.setAntialiasing(true);
        defaultRenderer.setPanEnabled(false);
        defaultRenderer.setZoomEnabled(false);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        defaultRenderer.setLegendTextSize(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20, metrics));
        defaultRenderer.setLabelsColor(Color.BLACK);
        for (int i = 0; i < distributionSeries.getItemCount(); i++) {
            SimpleSeriesRenderer seriesRenderer = new SimpleSeriesRenderer();
            int colorIdx = (i + (i / colors.length)) % colors.length;
            seriesRenderer.setColor(
                    Color.rgb(colors[colorIdx][0], colors[colorIdx][1], colors[colorIdx][2]));
            defaultRenderer.addSeriesRenderer(seriesRenderer);
        }

        LinearLayout chartContainer = (LinearLayout) findViewById(R.id.chart);
        // remove any views before u paint the chart
        chartContainer.removeAllViews();
        // drawing pie chart
        mChart = ChartFactory.getPieChartView(getBaseContext(),
                distributionSeries, defaultRenderer);
        // adding the view to the linearlayout
        chartContainer.addView(mChart);
    }
}
