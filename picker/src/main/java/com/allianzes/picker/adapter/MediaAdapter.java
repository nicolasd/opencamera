package com.allianzes.picker.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.allianzes.picker.utils.MediaFileInfo;
import com.allianzes.picker.R;
import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class MediaAdapter extends MediaSelectableAdapter<MediaAdapter.MediaListRowHolder> {
    private final List<MediaFileInfo> itemList;
    private final Context mContext;
    private final MediaListRowHolder.ClickListener clickListener;

    public MediaAdapter(Context context, List<MediaFileInfo> itemList, MediaListRowHolder.ClickListener clickListener) {
        this.itemList = itemList;
        this.mContext = context;
        this.clickListener = clickListener;
    }

    @Override
    public MediaAdapter.MediaListRowHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        @SuppressLint("InflateParams") View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_row, null);
        return new MediaListRowHolder(v,clickListener);
    }

    @Override
    public void onBindViewHolder(MediaAdapter.MediaListRowHolder mediaListRowHolder, int i) {
        try{
            final MediaFileInfo item = itemList.get(i);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ){
                mediaListRowHolder.title.setText(Html.fromHtml(item.getFileName(),Html.FROM_HTML_MODE_LEGACY));
            }else{
                //noinspection deprecation
                mediaListRowHolder.title.setText(Html.fromHtml(item.getFileName()));
            }
            Uri uri = Uri.fromFile(new File(item.getFilePath()));
            Glide.with(mContext)
                    .load(uri)
                    .centerCrop()
                    .into(mediaListRowHolder.thumbnail);
            mediaListRowHolder.imageview_tick.setVisibility(isSelected(i) ? View.VISIBLE : View.INVISIBLE);
            mediaListRowHolder.selected_overlay.setVisibility(isSelected(i) ? View.VISIBLE : View.INVISIBLE);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return (null != itemList ? itemList.size() : 0);
    }


    public static class MediaListRowHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        final ImageView thumbnail;
        final TextView title;
        final View selected_overlay;
        final ImageView imageview_tick;

        private final ClickListener listener;

        MediaListRowHolder(View view, ClickListener listener) {
            super(view);
            this.thumbnail = (ImageView) view.findViewById(R.id.thumbnail);
            this.title = (TextView) view.findViewById(R.id.title);
            this.selected_overlay = view.findViewById(R.id.selected_overlay);
            this.imageview_tick = (ImageView)view.findViewById(R.id.imageview_tick);
            this.listener = listener;
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (listener != null) {
                listener.onItemClicked(getAdapterPosition());
            }
        }

        public interface ClickListener {
            void onItemClicked(int position);
        }
    }
}
