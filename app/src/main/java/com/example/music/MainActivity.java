package com.example.music;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.Toast;

import com.example.music.Model.SongModel;
import com.example.music.Storage.StorageUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    public static final String SERVICE_STATUS = "SERVICE_STATUS";

    private MediaPlayerService player;
    private boolean serviceBound = false;

    private ArrayList<SongModel> currentSongList;
    private StorageUtil storage;

    private View navHostFragment;
    private SongController songControllerFragment;
    private BottomNavigationView navView;
    private boolean expanded = false;

    public static FirebaseAuth firebaseAuth;
    public static FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = new StorageUtil(getApplicationContext());

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        navView = findViewById(R.id.nav_view);
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupWithNavController(navView, navController);

        navHostFragment = findViewById(R.id.nav_host_fragment);
        songControllerFragment = new SongController();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.song_controller_container, songControllerFragment)
                .commit();

        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        currentSongList = storage.loadSongsList();
        if (currentSongList != null && !currentSongList.isEmpty()) {
            startMusicService();
        } else {
            View v = findViewById(R.id.song_controller_container);
            v.setVisibility(View.GONE);
        }
    }

    private void startMusicService() {
        Intent playerIntent = new Intent(this, MediaPlayerService.class);
        startService(playerIntent);
        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void playSong(ArrayList<SongModel> newSongList, int position) {
        if (storage.loadSettingFirstStart()) {
            storage.storeSongsList(newSongList);
            storage.storeCurrentWindow(position);

            startMusicService();

            View v = findViewById(R.id.song_controller_container);
            v.setVisibility(View.VISIBLE);
            return;
        }

        currentSongList = storage.loadSongsList();
        if (!newSongList.equals(currentSongList)) {
            player.updateSongList(newSongList);
        }
        player.updateCurrentWindow(position);
    }

    public void expandController(View v) {
        navView.setVisibility(View.GONE);
        songControllerFragment.expandController(v);
        navHostFragment.setVisibility(View.GONE);
        expanded = true;
    }

    public void collapseController(View v) {
        songControllerFragment.collapseController(v);
        navView.setVisibility(View.VISIBLE);
        navHostFragment.setVisibility(View.VISIBLE);
        expanded = false;
    }

    public void openOptions(View v) {
        Toast.makeText(MainActivity.this, "Menu", Toast.LENGTH_SHORT).show();
    }

    public void openArtist(View v) {
        if (expanded) {
            Toast.makeText(MainActivity.this, "Artist", Toast.LENGTH_SHORT).show();
        }
    }

    public void seekTo(int position) {
        player.seekToSong(position);
    }

    public void likeSong(View v) {
        songControllerFragment.likeSong(v);
    }

    public void repeatSong(View v) {
        Toast.makeText(MainActivity.this, "Repeat", Toast.LENGTH_SHORT).show();
    }

    public void previousSong(View v) {
        player.previousSong();
    }

    public void playPauseSong(View v) {
        player.playPause();
        songControllerFragment.updatePlayButton(v);
    }

    public void nextSong(View v) {
        player.nextSong();
    }

    public void shufflePlaylist(View v) {
        Toast.makeText(MainActivity.this, "Shuffle Playlist", Toast.LENGTH_SHORT).show();
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;
            if (storage.loadSettingFirstStart()){
                storage.storeSettingFirstStart(false);
                player.playPause();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle
            outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean(SERVICE_STATUS, serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean(SERVICE_STATUS);
    }

    @Override
    public void onBackPressed() {
        if (expanded) {
            View v = findViewById(R.id.song_controller_layout);
            collapseController(v);
        } else {
            super.onBackPressed();
        }
    }

    public static void signOut() {
        firebaseAuth.signOut();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
        }
    }
}