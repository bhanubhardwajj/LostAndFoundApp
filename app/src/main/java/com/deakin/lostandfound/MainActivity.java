package com.deakin.lostandfound;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Home screen — three buttons matching the 9.1 wireframe:
 *   - Create a new advert
 *   - Show all lost & found items (list)
 *   - Show on map  ← NEW in 9.1
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnCreate  = findViewById(R.id.btnCreateAdvert);
        Button btnShowAll = findViewById(R.id.btnShowAll);
        Button btnShowMap = findViewById(R.id.btnShowMap);  // NEW

        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CreateAdvertActivity.class));
            }
        });

        btnShowAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ListItemsActivity.class));
            }
        });

        btnShowMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MapActivity.class));
            }
        });
    }
}
