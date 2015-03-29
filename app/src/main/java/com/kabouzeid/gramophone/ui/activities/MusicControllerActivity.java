package com.kabouzeid.gramophone.ui.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kabouzeid.gramophone.App;
import com.kabouzeid.gramophone.R;
import com.kabouzeid.gramophone.helper.AddToPlaylistDialogHelper;
import com.kabouzeid.gramophone.helper.MusicPlayerRemote;
import com.kabouzeid.gramophone.helper.SongDetailDialogHelper;
import com.kabouzeid.gramophone.lastfm.artist.LastFMArtistImageUrlLoader;
import com.kabouzeid.gramophone.loader.SongFilePathLoader;
import com.kabouzeid.gramophone.misc.AppKeys;
import com.kabouzeid.gramophone.model.MusicRemoteEvent;
import com.kabouzeid.gramophone.model.Song;
import com.kabouzeid.gramophone.model.UIPreferenceChangedEvent;
import com.kabouzeid.gramophone.service.MusicService;
import com.kabouzeid.gramophone.ui.activities.base.AbsFabActivity;
import com.kabouzeid.gramophone.ui.activities.tageditor.SongTagEditorActivity;
import com.kabouzeid.gramophone.util.MusicUtil;
import com.kabouzeid.gramophone.util.NavigationUtil;
import com.kabouzeid.gramophone.util.PreferenceUtils;
import com.kabouzeid.gramophone.util.Util;
import com.kabouzeid.gramophone.util.ViewUtil;
import com.nineoldandroids.view.ViewPropertyAnimator;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;

public class MusicControllerActivity extends AbsFabActivity {
    public static final String TAG = MusicControllerActivity.class.getSimpleName();

    private static final int DEFAULT_DELAY = 350;
    private static final int DEFAULT_ANIMATION_TIME = 1000;

    private Song song;
    private ImageView albumArt;
    private ImageView artistArt;
    private TextView songTitle;
    private TextView songArtist;
    private TextView currentSongProgress;
    private TextView totalSongDuration;
    private View footer;
    private SeekBar progressSlider;
    private ImageButton nextButton;
    private ImageButton prevButton;
    private ImageButton repeatButton;
    private ImageButton shuffleButton;
    private View mediaControllerContainer;

    private int lastFooterColor = -1;

    private boolean killThreads = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setUpTranslucence(true, false);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_music_controller);

        App.bus.register(this);

        initViews();

        moveSeekBarIntoPlace();

        setUpMusicControllers();

        prepareViewsForOpenAnimation();

        setUpToolBar();
    }

    private void moveSeekBarIntoPlace() {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) progressSlider.getLayoutParams();
        progressSlider.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        lp.setMargins(0, 0, 0, -(progressSlider.getMeasuredHeight() / 2));
        progressSlider.setLayoutParams(lp);
    }

    private void initViews() {
        nextButton = (ImageButton) findViewById(R.id.next_button);
        prevButton = (ImageButton) findViewById(R.id.prev_button);
        repeatButton = (ImageButton) findViewById(R.id.repeat_button);
        shuffleButton = (ImageButton) findViewById(R.id.shuffle_button);
        albumArt = (ImageView) findViewById(R.id.album_art);
        artistArt = (ImageView) findViewById(R.id.artist_image);
        songTitle = (TextView) findViewById(R.id.song_title);
        songArtist = (TextView) findViewById(R.id.song_artist);
        currentSongProgress = (TextView) findViewById(R.id.song_current_progress);
        totalSongDuration = (TextView) findViewById(R.id.song_total_time);
        footer = findViewById(R.id.footer);
        progressSlider = (SeekBar) findViewById(R.id.progress_slider);
        mediaControllerContainer = findViewById(R.id.media_controller_container);
    }

    private void setUpMusicControllers() {
        setUpPrevNext();
        setUpRepeatButton();
        setUpShuffleButton();
        setUpProgressSlider();
        setUpBox(PreferenceUtils.getInstance(this).playbackControllerBoxEnabled());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setUpBox(boolean boxEnabled) {
        if (boxEnabled) {
            if (Util.hasLollipopSDK()) {
                mediaControllerContainer.setElevation(getResources().getDimensionPixelSize(R.dimen.cardview_default_elevation));
            }
            mediaControllerContainer.setBackgroundColor(Util.resolveColor(this, R.attr.music_controller_container_color));
        } else {
            if (Util.hasLollipopSDK() && !Util.isInPortraitMode(this)) {
                mediaControllerContainer.setElevation(getResources().getDimensionPixelSize(R.dimen.cardview_default_elevation));
                mediaControllerContainer.setBackgroundColor(Util.resolveColor(this, R.attr.music_controller_container_color));
            } else {
                mediaControllerContainer.setBackground(null);
            }
        }
    }

    private void setUpProgressSlider() {
        progressSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MusicPlayerRemote.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setUpPrevNext() {
        nextButton.setImageDrawable(Util.getTintedDrawable(getResources(), R.drawable.ic_skip_next_white_48dp, Util.resolveColor(this, R.attr.themed_drawable_color)));
        prevButton.setImageDrawable(Util.getTintedDrawable(getResources(), R.drawable.ic_skip_previous_white_48dp, Util.resolveColor(this, R.attr.themed_drawable_color)));
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayerRemote.playNextSong();
            }
        });
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayerRemote.back();
            }
        });
    }

    private void setUpShuffleButton() {
        updateShuffleState();
        shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayerRemote.toggleShuffleMode();
            }
        });
    }

    private void updateShuffleState() {
        switch (MusicPlayerRemote.getShuffleMode()) {
            case MusicService.SHUFFLE_MODE_SHUFFLE:
                shuffleButton.setImageDrawable(Util.getTintedDrawable(getResources(), R.drawable.ic_shuffle_white_48dp, Util.resolveColor(this, R.attr.themed_drawable_activated_color)));
                break;
            default:
                shuffleButton.setImageDrawable(Util.getTintedDrawable(getResources(), R.drawable.ic_shuffle_white_48dp, Util.resolveColor(this, R.attr.themed_drawable_color)));
                break;
        }
    }

    private void setUpRepeatButton() {
        updateRepeatState();
        repeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayerRemote.cycleRepeatMode();
            }
        });
    }

    private void updateRepeatState() {
        switch (MusicPlayerRemote.getRepeatMode()) {
            case MusicService.REPEAT_MODE_NONE:
                repeatButton.setImageDrawable(Util.getTintedDrawable(getResources(), R.drawable.ic_repeat_white_48dp, Util.resolveColor(this, R.attr.themed_drawable_color)));
                break;
            case MusicService.REPEAT_MODE_ALL:
                repeatButton.setImageDrawable(Util.getTintedDrawable(getResources(), R.drawable.ic_repeat_white_48dp, Util.resolveColor(this, R.attr.themed_drawable_activated_color)));
                break;
            default:
                repeatButton.setImageDrawable(Util.getTintedDrawable(getResources(), R.drawable.ic_repeat_one_white_48dp, Util.resolveColor(this, R.attr.themed_drawable_activated_color)));
                break;
        }
    }

    private void prepareViewsForOpenAnimation() {
        footer.setPivotY(0);
        footer.setScaleY(0);
    }

    private void setUpToolBar() {
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setTitle(null);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMusicControllerStateUpdateThread();
        updateCurrentSong();
    }

    private void updateCurrentSong() {
        getCurrentSong();
        setHeadersText();
        setUpArtistArt();
        setUpAlbumArtAndApplyPalette();
        totalSongDuration.setText(MusicUtil.getReadableDurationString(song.duration));
        currentSongProgress.setText(MusicUtil.getReadableDurationString(-1));
    }

    private void setHeadersText() {
        songTitle.setText(song.title);
        songArtist.setText(song.artistName);
    }

    private void setUpAlbumArtAndApplyPalette() {
        Picasso.with(this)
                .load(MusicUtil.getAlbumArtUri(song.albumId))
                .placeholder(R.drawable.default_album_art)
                .into(albumArt, new Callback.EmptyCallback() {
                    @Override
                    public void onSuccess() {
                        super.onSuccess();
                        final Bitmap bitmap = ((BitmapDrawable) albumArt.getDrawable()).getBitmap();
                        if (bitmap != null) applyPalette(bitmap);
                    }

                    @Override
                    public void onError() {
                        super.onError();
                        setStandardColors();
                    }
                });
    }

    private void applyPalette(Bitmap bitmap) {
        Palette.generateAsync(bitmap, new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                Palette.Swatch swatch = palette.getVibrantSwatch();
                if (swatch != null) {
                    animateColorChange(swatch.getRgb());
                    songTitle.setTextColor(swatch.getTitleTextColor());
                    songArtist.setTextColor(swatch.getBodyTextColor());
                } else {
                    setStandardColors();
                }
            }
        });
    }

    private void setStandardColors() {
        int songTitleTextColor = Util.resolveColor(this, R.attr.title_text_color);
        int artistNameTextColor = Util.resolveColor(this, R.attr.caption_text_color);
        int defaultBarColor = Util.resolveColor(this, R.attr.default_bar_color);

        animateColorChange(defaultBarColor);

        songTitle.setTextColor(songTitleTextColor);
        songArtist.setTextColor(artistNameTextColor);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void animateColorChange(final int newColor) {
        if (lastFooterColor != -1 && lastFooterColor != newColor) {
            ViewUtil.animateViewColor(footer, lastFooterColor, newColor, 300);
        } else {
            footer.setBackgroundColor(newColor);
        }
        if (Util.hasLollipopSDK() && PreferenceUtils.getInstance(this).coloredNavigationBarCurrentPlayingEnabled())
            getWindow().setNavigationBarColor(newColor);
        lastFooterColor = newColor;
    }

    private void setUpArtistArt() {
        if (artistArt != null) {
            artistArt.setImageResource(R.drawable.default_artist_image);
            LastFMArtistImageUrlLoader.loadArtistImageUrl(this, song.artistName, false, new LastFMArtistImageUrlLoader.ArtistImageUrlLoaderCallback() {
                @Override
                public void onArtistImageUrlLoaded(String url) {
                    Picasso.with(MusicControllerActivity.this)
                            .load(url)
                            .placeholder(R.drawable.default_artist_image)
                            .error(R.drawable.default_artist_image)
                            .into(artistArt);
                }
            });
        }
    }

    private void getCurrentSong() {
        song = MusicPlayerRemote.getCurrentSong();
        if (song.id == -1) {
            finish();
        }
    }

    private void startMusicControllerStateUpdateThread() {
        killThreads = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int currentPosition = 0;
                int total = 0;
                while (!killThreads) {
                    try {
                        total = MusicPlayerRemote.getSongDurationMillis();
                        currentPosition = MusicPlayerRemote.getSongProgressMillis();
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Exception e) {
                    }
                    final int finalTotal = total;
                    final int finalCurrentPosition = currentPosition;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressSlider.setMax(finalTotal);
                            progressSlider.setProgress(finalCurrentPosition);
                            currentSongProgress.setText(MusicUtil.getReadableDurationString(finalCurrentPosition));
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void updateControllerState() {
        super.updateControllerState();
        updateRepeatState();
        updateShuffleState();
    }

    @Override
    public void onMusicRemoteEvent(MusicRemoteEvent event) {
        super.onMusicRemoteEvent(event);
        switch (event.getAction()) {
            case MusicRemoteEvent.TRACK_CHANGED:
                updateCurrentSong();
                break;
            case MusicRemoteEvent.REPEAT_MODE_CHANGED:
                updateRepeatState();
                break;
            case MusicRemoteEvent.SHUFFLE_MODE_CHANGED:
                updateShuffleState();
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        killThreads = true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            animateActivityOpened(DEFAULT_DELAY);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_music_playing, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_shuffle_all:
                MusicPlayerRemote.shuffleAllSongs(this);
                return true;
            case R.id.action_settings:
                Toast.makeText(this, "This feature is not available yet", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_add_to_playlist:
                AddToPlaylistDialogHelper.getDialog(this, song).show();
                return true;
            case android.R.id.home:
                super.onBackPressed();
                return true;
            case R.id.action_playing_queue:
                NavigationUtil.openPlayingQueueDialog(this);
                return true;
            case R.id.action_tag_editor:
                Intent intent = new Intent(this, SongTagEditorActivity.class);
                intent.putExtra(AppKeys.E_ID, song.id);
                startActivity(intent);
                return true;
            case R.id.action_details:
                String songFilePath = SongFilePathLoader.getSongFilePath(this, song.id);
                File songFile = new File(songFilePath);
                SongDetailDialogHelper.getDialog(this, songFile).show();
                return true;
            case R.id.action_go_to_album:
                NavigationUtil.goToAlbum(this, song.albumId, getSharedViewsWithFab(null));
                return true;
            case R.id.action_go_to_artist:
                NavigationUtil.goToArtist(this, song.artistId, getSharedViewsWithFab(null));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void animateActivityOpened(int startDelay) {
        ViewPropertyAnimator.animate(footer)
                .scaleX(1)
                .scaleY(1)
                .setInterpolator(new DecelerateInterpolator(4))
                .setDuration(DEFAULT_ANIMATION_TIME)
                .setStartDelay(startDelay)
                .start();
    }

    @Subscribe
    public void onUIPrefsChanged(UIPreferenceChangedEvent event) {
        switch (event.getAction()) {
            case UIPreferenceChangedEvent.PLAYBACK_CONTROLLER_CARD_CHANGED:
                setUpBox((boolean) event.getValue());
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        App.bus.unregister(this);
    }
}
