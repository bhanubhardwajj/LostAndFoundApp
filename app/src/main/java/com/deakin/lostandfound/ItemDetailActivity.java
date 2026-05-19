package com.deakin.lostandfound;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;

/**
 * Detail screen for a single advert. Shows everything the user entered plus
 * the relative time stamp, and gives them a REMOVE button so they can take
 * the post down once their item has been reunited with its owner.
 */
public class ItemDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";

    private TextView tvTitle, tvWhen, tvLocation, tvCategory,
                     tvPostType, tvPhone, tvDate, tvDescription;
    private ImageView imgFull;
    private Button btnRemove;

    private long itemId;
    private DatabaseHelper db;
    private Item item;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.item_details);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvTitle       = findViewById(R.id.tvTitle);
        tvWhen        = findViewById(R.id.tvWhen);
        tvLocation    = findViewById(R.id.tvLocation);
        tvCategory    = findViewById(R.id.tvCategory);
        tvPostType    = findViewById(R.id.tvPostType);
        tvPhone       = findViewById(R.id.tvPhone);
        tvDate        = findViewById(R.id.tvDate);
        tvDescription = findViewById(R.id.tvDescription);
        imgFull       = findViewById(R.id.imgFull);
        btnRemove     = findViewById(R.id.btnRemove);

        db = new DatabaseHelper(this);
        itemId = getIntent().getLongExtra(EXTRA_ITEM_ID, -1L);

        if (itemId == -1L) {
            // Shouldn't really happen, but fail loudly rather than silently
            Toast.makeText(this, "No item id was passed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadAndDisplayItem();

        btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRemove();
            }
        });
    }

    private void loadAndDisplayItem() {
        item = db.getItem(itemId);
        if (item == null) {
            Toast.makeText(this, "Item could not be loaded", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvTitle.setText(item.getPostType() + " " + item.getName());
        tvWhen.setText(DateUtils.getRelativeTimeSpanString(
                item.getTimestamp(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS));
        tvLocation.setText(item.getLocation());
        tvCategory.setText(getString(R.string.label_category) + " " + item.getCategory());
        tvPostType.setText(getString(R.string.label_post_type) + " " + item.getPostType());
        tvPhone.setText(getString(R.string.label_phone) + " " + item.getPhone());
        tvDate.setText(getString(R.string.label_date) + " " + item.getDate());
        tvDescription.setText(item.getDescription());

        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            File f = new File(item.getImagePath());
            if (f.exists()) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                imgFull.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath(), opts));
            } else {
                imgFull.setImageResource(R.drawable.ic_image_placeholder);
            }
        } else {
            imgFull.setImageResource(R.drawable.ic_image_placeholder);
        }
    }

    /**
     * Quick "are you sure" dialog so we don't accidentally nuke a post on a
     * stray tap.
     */
    private void confirmRemove() {
        new AlertDialog.Builder(this)
                .setTitle("Remove advert")
                .setMessage("Take this advert down? You can't undo this.")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int rows = db.deleteItem(itemId);
                        if (rows > 0) {
                            // Best-effort: also delete the image file from
                            // disk to keep things tidy.
                            if (item != null && item.getImagePath() != null) {
                                File f = new File(item.getImagePath());
                                if (f.exists()) f.delete();
                            }
                            Toast.makeText(ItemDetailActivity.this,
                                    "Advert removed", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(ItemDetailActivity.this,
                                    "Could not remove advert", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
