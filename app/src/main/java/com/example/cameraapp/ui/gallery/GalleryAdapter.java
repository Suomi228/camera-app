package com.example.cameraapp.ui.gallery;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.cameraapp.R;
import com.example.cameraapp.databinding.ItemGalleryBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder> {

    private final List<MediaItem> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private final SimpleDateFormat dateFormat;

    public interface OnItemClickListener {
        void onItemClick(MediaItem item, int position);
        void onItemLongClick(MediaItem item, int position);
    }

    public GalleryAdapter(OnItemClickListener listener) {
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("dd MMM yyyy", new Locale("ru"));
    }

    public void setItems(List<MediaItem> newItems) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).getId() == newItems.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                MediaItem oldItem = items.get(oldItemPosition);
                MediaItem newItem = newItems.get(newItemPosition);
                return oldItem.getUri().equals(newItem.getUri()) &&
                       oldItem.getType() == newItem.getType();
            }
        });

        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    public void addItem(MediaItem item) {
        items.add(0, item);
        notifyItemInserted(0);
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public MediaItem getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    public List<MediaItem> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGalleryBinding binding = ItemGalleryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new GalleryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
        MediaItem item = items.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class GalleryViewHolder extends RecyclerView.ViewHolder {

        private final ItemGalleryBinding binding;

        GalleryViewHolder(ItemGalleryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MediaItem item, int position) {
            Glide.with(binding.imgThumbnail.getContext())
                    .load(item.getUri())
                    .centerCrop()
                    .placeholder(R.drawable.gallery_item_background)
                    .error(R.drawable.gallery_item_background)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(binding.imgThumbnail);

            if (item.isVideo()) {
                binding.videoIndicator.setVisibility(View.VISIBLE);
                binding.tvDuration.setText(item.getFormattedDuration());
            } else {
                binding.videoIndicator.setVisibility(View.GONE);
            }

            binding.tvDate.setText(dateFormat.format(item.getDate()));

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item, position);
                }
            });

            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onItemLongClick(item, position);
                    return true;
                }
                return false;
            });
        }
    }
}
