package com.example.sony_camera_link_test; // ← change to your actual package name

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // ── UI references ──────────────────────────────────────────────────────
    private ImageView imageView;
    private Spinner filterSpinner;
    private SeekBar seekBar;
    private TextView seekValueLabel;
    private Button buttonPhoto;
    private Button buttonProcess;
    private ProgressBar progressBar;

    // ── State ──────────────────────────────────────────────────────────────
    private String selectedFilter = "K-means";
    private int currentIntensity = 10;

    // Filter options shown in the spinner
    private static final String[] FILTER_OPTIONS = {
            "K-means clustering",
            "Pixelate",
            "Grayscale",
            "Interlaced"
    };

    // ──────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupFilterSpinner();
        setupSeekBar();
        setupButtons();
    }

    // ── Bind all views from the layout ────────────────────────────────────
    private void bindViews() {
        imageView      = findViewById(R.id.image_view);
        filterSpinner  = findViewById(R.id.filter_spinner);
        seekBar        = findViewById(R.id.seekBar2);
        seekValueLabel = findViewById(R.id.seek_value_label);
        buttonPhoto    = findViewById(R.id.button_photo);
        buttonProcess  = findViewById(R.id.button_process);
        progressBar    = findViewById(R.id.progressBar);
    }

    // ── Spinner setup ─────────────────────────────────────────────────────
    private void setupFilterSpinner() {
        // Dark-themed adapter: use simple_spinner_item and override text color in code
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                FILTER_OPTIONS
        ) {
            // Style the closed spinner text
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(0xFFDDDDDD); // light gray
                ((TextView) view).setTextSize(13);
                return view;
            }

            // Style the dropdown list items
            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(0xFFDDDDDD);
                ((TextView) view).setBackgroundColor(0xFF1E1E2A);
                ((TextView) view).setPadding(24, 20, 24, 20);
                return view;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(adapter);

        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFilter = FILTER_OPTIONS[position];

                // Update the seekbar label to reflect what the value means
                // for each filter type (clusters for k-means, block size for pixelation, etc.)
                switch (position) {
                    case 0: // K-means
                        // clusters range 2–22 makes sense, keep as-is
                        seekBar.setMin(2);
                        seekBar.setMax(22);
                        break;
                    case 1: // Pixelation
                        // block size: 2–40px is more useful
                        seekBar.setMin(2);
                        seekBar.setMax(40);
                        break;
                    case 2;
                        seekBar.setMin(1);
                        seekBar.setMax(2);
                        break;
                    default:
                        seekBar.setMin(2);
                        seekBar.setMax(22);
                        break;
                }

                // Reset to midpoint when filter changes
                int mid = (seekBar.getMin() + seekBar.getMax()) / 2;
                seekBar.setProgress(mid);
                seekValueLabel.setText(String.valueOf(mid));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ── SeekBar: update live label on every move ──────────────────────────
    private void setupSeekBar() {
        // Set initial label to match the XML default progress of 10
        seekValueLabel.setText(String.valueOf(seekBar.getProgress()));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Update the large purple number in real time
                currentIntensity = progress;
                seekValueLabel.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ── Buttons ───────────────────────────────────────────────────────────
    private void setupButtons() {

        // Camera button: launch camera or photo picker
        buttonPhoto.setOnClickListener(v -> {
            // TODO: replace with your existing camera / gallery intent logic
            // Example:
            // Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        });

        // Apply filter button
        buttonProcess.setOnClickListener(v -> {
            applyFilter(selectedFilter, currentIntensity);
        });
    }

    // ── Filter application ────────────────────────────────────────────────
    private void applyFilter(String filter, int intensity) {
        // Show spinner, disable button while processing
        progressBar.setVisibility(View.VISIBLE);
        buttonProcess.setEnabled(false);

        // TODO: run your actual filter processing here (ideally in an AsyncTask
        // or coroutine so the UI thread isn't blocked).
        //
        // Example stub using a Handler to simulate async work:
        imageView.postDelayed(() -> {
            // -- swap in your processed bitmap here --
            // imageView.setImageBitmap(processedBitmap);

            progressBar.setVisibility(View.GONE);
            buttonProcess.setEnabled(true);
        }, 500);
    }
}