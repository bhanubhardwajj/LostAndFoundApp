package com.deakin.lostandfound;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

/**
 * RecyclerView adapter that renders a list of {@link Item} cards.
 * Each card shows the post type, name, category, a thumbnail and a relative
 * "x minutes ago" timestamp.
 */
public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ItemViewHolder> {

    public interface OnItemClickListener {
        void onItemClicked(Item item);
    }

    private final Context context;
    private List<Item> items;
    private final OnItemClickListener listener;

    public ItemAdapter(Context context, List<Item> items, OnItemClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<Item> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_card, parent, false);
        return new ItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder h, int position) {
        final Item item = items.get(position);

        // Prefix the name so the user can spot at a glance whether the post
        // is a Lost or a Found one.
        String prefixed = item.getPostType() + " " + item.getName();
        h.tvTitle.setText(prefixed);

        // "x minutes ago" / "yesterday" / etc - DateUtils handles all the
        // tedious wording for us.
        CharSequence relative = DateUtils.getRelativeTimeSpanString(
                item.getTimestamp(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
        );
        h.tvWhen.setText(relative);
        h.tvCategory.setText(item.getCategory());
        h.tvLocation.setText(item.getLocation());

        // Load thumbnail from disk if we have one. Using BitmapFactory.Options
        // to downsample so we don't blow up the heap on big photos.
        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            File f = new File(item.getImagePath());
            if (f.exists()) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;     // good enough for a thumb
                h.imgThumb.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath(), opts));
            } else {
                h.imgThumb.setImageResource(R.drawable.ic_image_placeholder);
            }
        } else {
            h.imgThumb.setImageResource(R.drawable.ic_image_placeholder);
        }

        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onItemClicked(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvWhen, tvCategory, tvLocation;
        ImageView imgThumb;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle    = itemView.findViewById(R.id.tvTitle);
            tvWhen     = itemView.findViewById(R.id.tvWhen);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            imgThumb   = itemView.findViewById(R.id.imgThumb);
        }
    }
}
