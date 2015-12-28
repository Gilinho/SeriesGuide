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

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NotificationManagerCompat;
import com.battlelancer.seriesguide.SeriesGuideApplication;
import com.battlelancer.seriesguide.enums.TraktAction;
import com.battlelancer.seriesguide.service.NotificationService;
import com.battlelancer.seriesguide.ui.dialogs.CheckInDialogFragment;
import com.battlelancer.seriesguide.ui.dialogs.GenericCheckInDialogFragment;
import com.battlelancer.seriesguide.util.TraktTask;
import com.uwetrottmann.androidutils.AndroidUtils;
import de.greenrobot.event.EventBus;

/**
 * Blank activity, just used to quickly check into a show/episode on GetGlue/trakt.
 */
public class QuickCheckInActivity extends FragmentActivity {

    @SuppressWarnings("FieldCanBeLocal") private CheckInDialogFragment checkInDialogFragment;

    public interface InitBundle {

        String EPISODE_TVDBID = "episode_tvdbid";
    }

    @Override
    protected void onCreate(Bundle arg0) {
        // make the activity show the wallpaper, nothing else
        if (AndroidUtils.isHoneycombOrHigher()) {
            setTheme(android.R.style.Theme_Holo_Wallpaper_NoTitleBar);
        } else {
            setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        }
        super.onCreate(arg0);

        int episodeTvdbId = getIntent().getIntExtra(InitBundle.EPISODE_TVDBID, 0);
        if (episodeTvdbId == 0) {
            finish();
            return;
        }

        checkInDialogFragment = CheckInDialogFragment.newInstance(this, episodeTvdbId);
        if (checkInDialogFragment == null) {
            finish();
            return;
        }
        // show check-in dialog
        checkInDialogFragment.show(getSupportFragmentManager(), "checkin-dialog");
    }

    @Override
    protected void onStart() {
        super.onStart();

        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        EventBus.getDefault().unregister(this);
    }

    @SuppressWarnings("unused")
    public void onEvent(GenericCheckInDialogFragment.CheckInDialogDismissedEvent event) {
        // if check-in dialog is dismissed, finish ourselves as well
        finish();
    }

    @SuppressWarnings("unused")
    public void onEvent(TraktTask.TraktActionCompleteEvent event) {
        if (event.mTraktAction != TraktAction.CHECKIN_EPISODE) {
            return;
        }
        // display status toast about trakt action
        event.handle(this);

        // dismiss notification on successful check-in
        if (event.mWasSuccessful) {
            NotificationManagerCompat manager = NotificationManagerCompat.from(
                    getApplicationContext());
            manager.cancel(SeriesGuideApplication.NOTIFICATION_EPISODE_ID);
            // replicate delete intent
            NotificationService.handleDeleteIntent(this, getIntent());
        }
    }
}
