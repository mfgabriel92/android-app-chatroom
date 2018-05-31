package com.example.gabriel.chatroom;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

public class MessageAdapter extends ArrayAdapter<ChatroomMessage> {
    private String username;

    MessageAdapter(@NonNull Context context, int resource, List<ChatroomMessage> objects) {
        super(context, resource, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.item_message, parent, false);
        }

        ImageView imgPhoto = convertView.findViewById(R.id.img_photo);
        TextView tvAuthor = convertView.findViewById(R.id.tv_author);
        TextView tvMessage = convertView.findViewById(R.id.tv_message);
        LinearLayout layoutBalloonMessage = convertView.findViewById(R.id.layout_balloon_message);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

        ChatroomMessage message = getItem(position);

        if (!message.getName().equals(username)) {
            layoutBalloonMessage.setBackground(ContextCompat.getDrawable(convertView.getContext(), R.drawable.balloon_shape_receiving));
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE);
        } else {
            layoutBalloonMessage.setBackground(ContextCompat.getDrawable(convertView.getContext(), R.drawable.balloon_shape_sending));
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE);
        }

        layoutBalloonMessage.setLayoutParams(layoutParams);

        if (message.getPhotoUrl() != null) {
            tvMessage.setVisibility(View.GONE);
            imgPhoto.setVisibility(View.VISIBLE);
            Glide.with(imgPhoto.getContext()).load(message.getPhotoUrl()).into(imgPhoto);
        } else {
            tvMessage.setVisibility(View.VISIBLE);
            imgPhoto.setVisibility(View.GONE);
            tvMessage.setText(message.getText());
        }

        tvAuthor.setText(message.getName());

        return convertView;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
