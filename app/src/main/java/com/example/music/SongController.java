package com.example.music;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;

import com.example.music.Model.SongModel;
import com.example.music.Storage.StorageUtil;
import com.example.music.ui.library.fragmentTabs.Playlist.FavouriteSongs;
import com.squareup.picasso.Picasso;

public class SongController extends Fragment implements SeekBar.OnSeekBarChangeListener {
    private ImageView imageView;
    private TextView textViewTitle;
    private TextView textViewArtist;
    private ImageView imageViewFavourite;
    private TextView textViewCurrentPosition;
    private TextView textViewCurrentDuration;
    private SeekBar seekBar;
    private ProgressBar progressBar;
    private ImageView playPauseButton;

    private SongModel activeSong;
    private String activeImageUrl = "";
    private int currentDuration;
    private String currentDurationInMinutes;
    private int currentPosition;
    private String currentPositionInMinutes;

    private StorageUtil storage;

    private FavouriteSongs favouriteSongs;

    private ConstraintLayout layout;
    private ConstraintSet constraintSetCollapsed = new ConstraintSet();
    private ConstraintSet constraintSetExpanded = new ConstraintSet();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_song_controller_collapsed, container, false);
        layout = v.findViewById(R.id.song_controller_layout);
        constraintSetCollapsed.clone(layout);
        constraintSetExpanded.clone(getActivity(), R.layout.fragment_song_controller_expanded);

        imageView = v.findViewById(R.id.image_view_song_controller_song_image);
        textViewTitle = v.findViewById(R.id.text_view_song_controller_title);
        textViewArtist = v.findViewById(R.id.text_view_song_controller_artist);
        imageViewFavourite = v.findViewById(R.id.image_view_song_controller_favourite);
        textViewCurrentPosition = v.findViewById(R.id.text_view_song_controller_live_time);
        textViewCurrentDuration = v.findViewById(R.id.text_view_song_controller_total_time);
        seekBar = v.findViewById(R.id.seek_bar_song_controller);
        progressBar = v.findViewById(R.id.progress_bar_song_controller);
        playPauseButton = v.findViewById(R.id.image_view_song_controller_play_pause);

        seekBar.setOnSeekBarChangeListener(this);

        favouriteSongs = FavouriteSongs.getInstance(getActivity());

        intiController();

        register_receiver();

        return v;
    }

    private void intiController() {
        storage = new StorageUtil(getActivity());
        activeSong = storage.loadCurrentSong();
        currentDuration = storage.loadCurrentDuration();
        currentPosition = storage.loadCurrentPosition() / 1000;
        currentDurationInMinutes = inMinutes(currentDuration);
        currentPositionInMinutes = inMinutes(currentPosition);

        if (activeSong != null) {
            loadMetadata();
        }
    }


    public void expandController(View v) {
        TransitionManager.beginDelayedTransition(layout);
        constraintSetExpanded.applyTo(layout);
        v.setBackgroundColor(Color.BLACK);
    }

    public void collapseController(View v) {
        TransitionManager.beginDelayedTransition(layout);
        constraintSetCollapsed.applyTo(layout);
        View view = getActivity().findViewById(R.id.song_controller_layout);
        view.setBackgroundColor(getResources().getColor(R.color.songControllerBackground));
    }

    public void openOptions(View v) {

    }

    public void openArtist(View v) {

    }

    public void likeSong(View v) {
        boolean isFavourite = favouriteSongs.favouriteOrUnFavourite(activeSong, getActivity());
        if (isFavourite) {
            imageViewFavourite.setImageResource(R.drawable.ic_baseline_favorite_24);
        } else {
            imageViewFavourite.setImageResource(R.drawable.ic_baseline_not_favorite);
        }

    }

    public void repeatSong(View v) {

    }

    public void previousSong(View v) {

    }

    public void updatePlayButton(View v) {
        if (storage.loadPlaybackStatus() == PlaybackStatus.PLAYING) {
            playPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
        } else if (storage.loadPlaybackStatus() == PlaybackStatus.PAUSED) {
            playPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        }
    }

    public void playPauseSong(View v) {

    }

    public void nextSong(View v) {

    }

    public void shufflePlaylist(View v) {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        if (b) {
            switch (seekBar.getId()) {
                case R.id.seek_bar_song_controller:
                    ((MainActivity) getActivity()).seekTo(i);
                    break;
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private void loadMetadata() {
        Picasso.with(getActivity())
                .load(activeSong.getImageUrl())
                .fit()
                .centerCrop()
                .into(imageView);
        textViewTitle.setText(activeSong.getTitle());
        textViewArtist.setText(activeSong.getArtist());

        seekBar.setMax(currentDuration);
        textViewCurrentDuration.setText(currentDurationInMinutes);

        seekBar.setProgress(currentPosition);
        progressBar.setProgress(100 * currentPosition / currentDuration);
        textViewCurrentPosition.setText(currentPositionInMinutes);

        updatePlayButton(playPauseButton);
        if (storage.loadFavouriteSongs() != null) {
            if (favouriteSongs.isFavourite(activeSong)) {
                imageViewFavourite.setImageResource(R.drawable.ic_baseline_favorite_24);
            }else {
                imageViewFavourite.setImageResource(R.drawable.ic_baseline_not_favorite);
            }
        }
    }

    private void updateMetadata(Intent intent) {
        activeSong = (SongModel) intent.getSerializableExtra("activeSong");

        if (!activeImageUrl.equals(activeSong.getImageUrl())) {
            activeImageUrl = activeSong.getImageUrl();
            Picasso.with(getActivity())
                    .load(activeImageUrl)
                    .fit()
                    .centerCrop()
                    .into(imageView);
        }

        textViewTitle.setText(activeSong.getTitle());
        textViewArtist.setText(activeSong.getArtist());

        if (storage.loadFavouriteSongs() != null) {
            if (favouriteSongs.isFavourite(activeSong)) {
                imageViewFavourite.setImageResource(R.drawable.ic_baseline_favorite_24);
            }else {
                imageViewFavourite.setImageResource(R.drawable.ic_baseline_not_favorite);
            }
        }
        storage.storeCurrentSong(activeSong);
    }

    private void updateCurrentDuration(Intent intent) {
        currentDuration = intent.getIntExtra("currentDuration", 1);
        currentDurationInMinutes = inMinutes(currentDuration);
        seekBar.setMax(currentDuration);
        textViewCurrentDuration.setText(currentDurationInMinutes);
        storage.storeCurrentDuration(currentDuration);
    }

    private void updateCurrentPosition(Intent intent) {
        currentPosition = intent.getIntExtra("currentPosition", 1);
        currentPositionInMinutes = inMinutes(currentPosition);
        seekBar.setProgress(currentPosition);
        progressBar.setProgress(100 * currentPosition / currentDuration);
        textViewCurrentPosition.setText(currentPositionInMinutes);
    }

    private void resetCurrentPosition(){
        seekBar.setProgress(0);
        progressBar.setProgress(0);
        textViewCurrentPosition.setText("0:00");
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MediaPlayerService.UPDATE_METADATA:
                    updateMetadata(intent);
                    break;
                case MediaPlayerService.UPDATE_CURRENT_DURATION:
                    updateCurrentDuration(intent);
                    break;
                case MediaPlayerService.UPDATE_CURRENT_POSITION:
                    updateCurrentPosition(intent);
                    break;
                case MediaPlayerService.RESET_CURRENT_POSITION:
                    resetCurrentPosition();
                    break;
                case MediaPlayerService.UPDATE_PLAY_BUTTON:
                    updatePlayButton(playPauseButton);
                    break;
            }
        }
    };

    private void register_receiver() {
        IntentFilter filter1 = new IntentFilter(MediaPlayerService.UPDATE_METADATA);
        ((MainActivity) getActivity()).registerReceiver(receiver, filter1);

        IntentFilter filter2 = new IntentFilter(MediaPlayerService.UPDATE_CURRENT_DURATION);
        ((MainActivity) getActivity()).registerReceiver(receiver, filter2);

        IntentFilter filter3 = new IntentFilter(MediaPlayerService.UPDATE_CURRENT_POSITION);
        ((MainActivity) getActivity()).registerReceiver(receiver, filter3);

        IntentFilter filter4 = new IntentFilter(MediaPlayerService.RESET_CURRENT_POSITION);
        ((MainActivity) getActivity()).registerReceiver(receiver, filter4);

        IntentFilter filter5 = new IntentFilter(MediaPlayerService.UPDATE_PLAY_BUTTON);
        ((MainActivity) getActivity()).registerReceiver(receiver, filter5);
    }

    private String inMinutes(int currentSeekBarPosition) {
        String totalNew;
        String totalOut;
        String seconds = String.valueOf(currentSeekBarPosition % 60);
        String minutes = String.valueOf(currentSeekBarPosition / 60);
        totalNew = minutes + ":0" + seconds;
        totalOut = minutes + ":" + seconds;
        if (seconds.length() == 1) {
            return totalNew;
        } else {
            return totalOut;
        }
    }
}