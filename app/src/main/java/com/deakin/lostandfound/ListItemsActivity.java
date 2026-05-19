package com.deakin.lostandfound;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Shows every advert that's currently in the database.
 *
 * Hooks up the subtask requirement for filtering by category through a
 * Spinner at the top of the screen, plus a free-text search box for
 * matching against the item name / description.
 */
public class ListItemsActivity extends AppCompatActivity
        implements ItemAdapter.OnItemClickListener {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private Spinner spinnerFilter;
    private EditText etSearch;
    private ItemAdapter adapter;
    private DatabaseHelper db;

    private String currentCategory = "All";
    private String currentSearch = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_items);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.lost_and_found_items);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = new DatabaseHelper(this);

        recyclerView  = findViewById(R.id.recyclerView);
        tvEmpty       = findViewById(R.id.tvEmpty);
        spinnerFilter = findViewById(R.id.spinnerFilter);
        etSearch      = findViewById(R.id.etSearch);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ItemAdapter(this, db.getAllItems(null, null), this);
        recyclerView.setAdapter(adapter);

        // Filter spinner uses the same string-array as the create screen,
        // but with "All" prepended via a separate array.
        ArrayAdapter<CharSequence> spAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.categories_with_all,
                android.R.layout.simple_spinner_item
        );
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(spAdapter);

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentCategory = parent.getItemAtPosition(position).toString();
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                currentSearch = s.toString();
                refresh();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh when coming back from create / detail screens so newly
        // added or removed items appear without a manual reload.
        refresh();
    }

    private void refresh() {
        List<Item> filtered = db.getAllItems(currentCategory, currentSearch);
        adapter.setItems(filtered);
        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onItemClicked(Item item) {
        Intent i = new Intent(this, ItemDetailActivity.class);
        i.putExtra(ItemDetailActivity.EXTRA_ITEM_ID, item.getId());
        startActivity(i);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
