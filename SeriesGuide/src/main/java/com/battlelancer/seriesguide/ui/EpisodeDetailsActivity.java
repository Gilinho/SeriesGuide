/*
 * Copyright 2014 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.battlelancer.seriesguide.ui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.ImageView;
import com.battlelancer.seriesguide.Constants;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.items.Episode;
import com.battlelancer.seriesguide.provider.SeriesGuideContract.Episodes;
import com.battlelancer.seriesguide.provider.SeriesGuideContract.Seasons;
import com.battlelancer.seriesguide.provider.SeriesGuideContract.Shows;
import com.battlelancer.seriesguide.settings.DisplaySettings;
import com.battlelancer.seriesguide.util.SeasonTools;
import com.battlelancer.seriesguide.util.ThemeUtils;
import com.battlelancer.seriesguide.util.Utils;
import com.battlelancer.seriesguide.widgets.SlidingTabLayout;
import java.util.ArrayList;

/**
 * Hosts a {@link ViewPager} displaying an episode per fragment of a complete season. Used on
 * smaller screens which do not allow for multi-pane layouts or if coming from a search result
 * selection.
 */
public class EpisodeDetailsActivity extends BaseNavDrawerActivity {

    protected static final String TAG = "Episode Details";

    private int mSeasonId;

    private int mShowId;

    /**
     * Data which has to be passed when creating this activity. All Bundle extras are integer.
     */
    public interface InitBundle {

        String EPISODE_TVDBID = "episode_tvdbid";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_episode);
        setupActionBar();
        setupNavDrawer();

        setupViews();

        if (mShowId != 0) {
            updateShowDelayed(mShowId);
        }
    }

    @Override
    protected void setCustomTheme() {
        // use a special immersive theme
        ThemeUtils.setImmersiveTheme(this);
    }

    private void setupViews() {
        // get episode id
        final int episodeId = getIntent().getIntExtra(InitBundle.EPISODE_TVDBID, 0);
        if (episodeId == 0) {
            // nothing to display
            finish();
            return;
        }

        // get show and season id, poster path
        final Cursor episode = getContentResolver().query(
                Episodes.buildEpisodeWithShowUri(String.valueOf(episodeId)), new String[] {
                        Seasons.REF_SEASON_ID, Shows.POSTER, Shows.REF_SHOW_ID, Shows.TITLE,
                        Episodes.SEASON
                }, null, null, null
        );
        if (episode == null || !episode.moveToFirst()) {
            // nothing to display
            if (episode != null) {
                episode.close();
            }
            finish();
            return;
        }

        int seasonNumber = episode.getInt(4);
        setupActionBar(episode.getString(3), SeasonTools.getSeasonString(this, seasonNumber));

        // set show poster as background
        Utils.loadPosterBackground(this, (ImageView) findViewById(R.id.background),
                episode.getString(1));

        mShowId = episode.getInt(2);
        mSeasonId = episode.getInt(0);
        episode.close();

        // get episodes of season
        Constants.EpisodeSorting sortOrder = DisplaySettings.getEpisodeSortOrder(this);
        Cursor episodesOfSeason = getContentResolver().query(
                Episodes.buildEpisodesOfSeasonUri(String.valueOf(mSeasonId)), new String[] {
                        Episodes._ID, Episodes.NUMBER
                }, null, null, sortOrder.query()
        );

        ArrayList<Episode> episodes = new ArrayList<>();
        int startPosition = 0;
        if (episodesOfSeason != null) {
            int i = 0;
            while (episodesOfSeason.moveToNext()) {
                Episode ep = new Episode();
                int curEpisodeId = episodesOfSeason.getInt(0);
                // look for episode to show initially
                if (curEpisodeId == episodeId) {
                    startPosition = i;
                }
                ep.episodeId = curEpisodeId;
                ep.episodeNumber = episodesOfSeason.getInt(1);
                ep.seasonNumber = seasonNumber;
                episodes.add(ep);
                i++;
            }
            episodesOfSeason.close();
        }

        // setup adapter
        EpisodePagerAdapter adapter = new EpisodePagerAdapter(this, getSupportFragmentManager(),
                episodes, false);

        // setup view pager
        ViewPager pager = (ViewPager) findViewById(R.id.pagerEpisodeDetails);
        pager.setAdapter(adapter);

        // setup tabs
        SlidingTabLayout tabs = (SlidingTabLayout) findViewById(R.id.tabsEpisodeDetails);
        tabs.setCustomTabView(R.layout.tabstrip_item_transparent, R.id.textViewTabStripItem);
        tabs.setSelectedIndicatorColors(getResources().getColor(
                SeriesGuidePreferences.THEME == R.style.Theme_SeriesGuide_DarkBlue ? R.color.white
                        : Utils.resolveAttributeToResourceId(getTheme(), R.attr.colorPrimary)));
        tabs.setViewPager(pager);

        // set current item
        pager.setCurrentItem(startPosition, false);
    }

    private void setupActionBar(String showTitle, String season) {
        setTitle(getString(R.string.episodes) + " " + showTitle + " " + season);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(showTitle);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            Intent upIntent = new Intent(this, EpisodesActivity.class);
            upIntent.putExtra(EpisodesActivity.InitBundle.SEASON_TVDBID, mSeasonId);
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so
                // create a new task with a synthesized back stack.
                TaskStackBuilder
                        .create(this)
                        .addNextIntent(new Intent(this, ShowsActivity.class))
                        .addNextIntent(
                                new Intent(this, OverviewActivity.class).putExtra(
                                        OverviewFragment.InitBundle.SHOW_TVDBID, mShowId)
                        )
                        .addNextIntent(upIntent)
                        .startActivities();
                finish();
            } else {
                /*
                 * This activity is part of the application's task, so simply
                 * navigate up to the hierarchical parent activity.
                 * NavUtils.navigateUpTo() does not seem to work here.
                 */
                upIntent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(upIntent);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class EpisodePagerAdapter extends FragmentStatePagerAdapter {

        private ArrayList<Episode> mEpisodes;

        private Context mContext;

        private final boolean mIsMultiPane;

        public EpisodePagerAdapter(Context context, FragmentManager fm,
                ArrayList<Episode> episodes, boolean isMultiPane) {
            super(fm);
            mEpisodes = episodes;
            mContext = context;
            mIsMultiPane = isMultiPane;
        }

        @Override
        public Fragment getItem(int position) {
            return EpisodeDetailsFragment.newInstance(mEpisodes.get(position).episodeId,
                    mIsMultiPane);
        }

        @Override
        public int getItemPosition(Object object) {
            /*
             * This breaks the FragmentStatePagerAdapter (see
             * http://code.google.com/p/android/issues/detail?id=37990), so we
             * just destroy everything!
             */
            // EpisodeDetailsFragment fragment = (EpisodeDetailsFragment)
            // object;
            // int episodeId = fragment.getEpisodeId();
            // for (int i = 0; i < mEpisodes.size(); i++) {
            // if (episodeId == mEpisodes.get(i).episodeId) {
            // return i;
            // }
            // }
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            if (mEpisodes != null) {
                return mEpisodes.size();
            } else {
                return 0;
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Episode episode = mEpisodes.get(position);
            return Utils.getEpisodeNumber(mContext, episode.seasonNumber, episode.episodeNumber);
        }

        public void updateEpisodeList(ArrayList<Episode> list) {
            if (list != null) {
                mEpisodes = list;
                notifyDataSetChanged();
            }
        }
    }
}
