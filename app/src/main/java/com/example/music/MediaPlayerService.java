package com.example.music;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.music.Model.SongModel;
import com.example.music.Storage.StorageUtil;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

import java.util.ArrayList;
import java.util.List;

import static com.example.music.Notification.CHANNEL_ID;

public class MediaPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener {

    public static final String UPDATE_METADATA = "UPDATE_METADATA";
    public static final String UPDATE_CURRENT_DURATION = "UPDATE_CURRENT_DURATION";
    public static final String UPDATE_CURRENT_POSITION = "UPDATE_CURRENT_POSITION";
    public static final String RESET_CURRENT_POSITION = "RESET_CURRENT_POSITION";
    public static final String UPDATE_PLAY_BUTTON = "UPDATE_PLAY_BUTTON";

    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PREVIOUS = "ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_STOP = "ACTION_STOP";

    private StorageUtil storage;
    private SimpleExoPlayer player;

    private boolean playWhenReady;
    private int currentWindow;
    private long currentPosition;
    private boolean durationSet;

    private List<MediaItem> mediaItems;
    private PlayerListener playerListener;
    private ArrayList<SongModel> songsList;
    private SongModel currentSong;

    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    private static final int NOTIFICATION_ID = 101;

    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private AudioManager audioManager;

    private final IBinder iBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        callStateListener();
        registerBecomingNoisyReceiver();

        storage = new StorageUtil(getApplicationContext());
        playerListener = new PlayerListener();

        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying()) {
                    currentPosition = player.getCurrentPosition();
                    Intent broadcastCurrentDuration = new Intent(UPDATE_CURRENT_POSITION);
                    broadcastCurrentDuration.putExtra("currentPosition", (int) currentPosition / 1000);
                    sendBroadcast(broadcastCurrentDuration);
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(runnable, 0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mediaSessionManager == null) {
            try {
                initMediaSession();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
        }

        if (player == null) {
            initializePlayer();
            loadSongList();
            loadCurrentWindow();
        }

        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    private void initializePlayer() {
        if (player == null) {
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
            trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSizeSd());
            DefaultLoadControl loadControl = new DefaultLoadControl();

            player = new SimpleExoPlayer.Builder(this)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .build();
        }
        player.addListener(playerListener);
        player.setPlayWhenReady(playWhenReady);
    }

    private void loadSongList() {
        mediaItems = new ArrayList<>();
        songsList = storage.loadSongsList();
        for (SongModel song : songsList) {
            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(song.getSongUrl())
                    .setTag(song)
                    .build();
            mediaItems.add(mediaItem);
        }
        player.addMediaItems(mediaItems);
    }

    private void loadCurrentWindow() {
        currentWindow = storage.loadCurrentWindow();
        currentPosition = storage.loadCurrentPosition();
        player.seekTo(currentWindow, currentPosition);
        player.prepare();
    }

    public void updateSongList(ArrayList<SongModel> songsList) {
        mediaItems = new ArrayList<>();
        for (SongModel song : songsList) {
            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(song.getSongUrl())
                    .setTag(song)
                    .build();
            mediaItems.add(mediaItem);
        }
        player.clearMediaItems();
        player.addMediaItems(mediaItems);
        storage.storeSongsList(songsList);
    }

    public void updateCurrentWindow(int window) {
        currentWindow = window;
        player.seekTo(window, C.TIME_UNSET);
        playMedia();
        storage.storeCurrentWindow(window);
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (!player.isPlaying()) {
                    player.play();
                }
                player.setVolume(1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                if (player.isPlaying()) {
                    player.pause();
                    buildNotification(PlaybackStatus.PAUSED);
                    storage.storeFocus(false);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (player.isPlaying()) {
                    player.pause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player.isPlaying()) {
                    player.setVolume(0.1f);
                }
                break;
        }
    }

    private void focus() {
        if (!storage.loadFocus()) {
            if (requestAudioFocus()) {
                storage.storeFocus(true);
            } else {
                stopSelf();
            }
        }
    }

    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void removeAudioFocus() {
        storage.storeFocus(false);
        audioManager.abandonAudioFocus(this);
    }

    private void playMedia() {
        focus();
        if (!player.isPlaying()) {
            player.play();
        }
        playWhenReady = true;
    }

    private void stopMedia() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.stop();
        }
    }

    private void pauseMedia() {
        if (player.isPlaying()) {
            player.pause();
        }
    }

    private void resumeMedia() {
        focus();
        if (!player.isPlaying()) {
            player.seekTo(currentPosition);
            player.play();
        }
    }

    private void seekTo(long position) {
        player.seekTo(position);
        currentPosition = position;
    }

    private class PlayerListener implements Player.EventListener, Player.MetadataComponent {

        @Override
        public void onTimelineChanged(Timeline timeline, int reason) {

        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            if (playWhenReady) {
                Intent broadcastIntent = new Intent(RESET_CURRENT_POSITION);
                sendBroadcast(broadcastIntent);
            }

            if (mediaItem != null && mediaItem.playbackProperties != null) {
                SongModel currentSong = (SongModel) mediaItem.playbackProperties.tag;
                updateUiMediaItem(currentSong);
            }
            durationSet = false;
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }


        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == ExoPlayer.STATE_READY && !durationSet) {
                int currentDuration = (int) player.getDuration() / 1000;
                Intent broadcastIntent = new Intent(UPDATE_CURRENT_DURATION);
                broadcastIntent.putExtra("currentDuration", currentDuration);
                sendBroadcast(broadcastIntent);
                durationSet = true;
            }
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {

        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                storage.storePlaybackStatus(PlaybackStatus.PLAYING);
                buildNotification(PlaybackStatus.PLAYING);
            } else {
                storage.storePlaybackStatus(PlaybackStatus.PAUSED);
                buildNotification(PlaybackStatus.PAUSED);
            }
            Intent broadcastIntent = new Intent(UPDATE_PLAY_BUTTON);
            sendBroadcast(broadcastIntent);
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {

        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

        }

        @Override
        public void addMetadataOutput(MetadataOutput output) {

        }

        @Override
        public void removeMetadataOutput(MetadataOutput output) {

        }
    }

    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return;
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        transportControls = mediaSession.getController().getTransportControls();
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                playMedia();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                focus();
                player.next();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                focus();
                player.previous();
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
                seekTo(position);
            }
        });
    }

    private void updateUiMediaItem(SongModel currentSong) {
        this.currentSong = currentSong;
        Intent broadcastIntent = new Intent(UPDATE_METADATA);
        broadcastIntent.putExtra("activeSong", currentSong);
        sendBroadcast(broadcastIntent);

        buildNotification(PlaybackStatus.PAUSED);
    }


    private void buildNotification(PlaybackStatus playbackStatus) {
        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent play_pauseAction = null;

        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            play_pauseAction = playbackAction(0);
        }

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_baseline_music_note_24);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setShowWhen(false)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText(currentSong.getArtist())
                .setContentTitle(currentSong.getTitle())
                .setContentInfo(currentSong.getAlbum())
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notificationBuilder.build());

    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }


    private void callStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (player != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (player != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED);
        }
    };

    private void registerBecomingNoisyReceiver() {
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    public void seekToSong(int position) {
        transportControls.seekTo((long) position * 1000);
    }

    public void previousSong() {
        transportControls.skipToPrevious();
    }

    public void playPause() {
        if (storage.loadPlaybackStatus() == PlaybackStatus.PLAYING) {
            transportControls.pause();
        } else if (storage.loadPlaybackStatus() == PlaybackStatus.PAUSED) {
            transportControls.play();
        }
    }

    public void nextSong() {
        transportControls.skipToNext();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaSession.release();
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    private void releasePlayer() {
        if (player != null) {
            storage.storeCurrentPosition((int) currentPosition);
            storage.storeFocus(false);
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            stopMedia();
            releasePlayer();
        }
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        removeNotification();
        unregisterReceiver(becomingNoisyReceiver);
    }
}