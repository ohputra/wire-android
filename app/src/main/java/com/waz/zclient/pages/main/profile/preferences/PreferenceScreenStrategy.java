/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.pages.main.profile.preferences;

import android.annotation.SuppressLint;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;

public abstract class PreferenceScreenStrategy {

    private PreferenceScreenStrategy() {}

    public static class ReplaceFragment extends PreferenceScreenStrategy {

        private final int animEnter;
        private final int animExit;
        private final int animPopEnter;
        private final int animPopExit;
        private final boolean customAnimations;
        private final Callbacks callbacks;

        public ReplaceFragment(Callbacks callbacks,
                               final int animEnter,
                               final int animExit,
                               final int animPopEnter,
                               final int animPopExit) {
            this.callbacks = callbacks;
            this.animEnter = animEnter;
            this.animExit = animExit;
            this.animPopEnter = animPopEnter;
            this.animPopExit = animPopExit;
            this.customAnimations = true;
        }

        private PreferenceFragmentCompat buildFragment(PreferenceScreen preferenceScreen) {
            return callbacks.onBuildPreferenceFragment(preferenceScreen);
        }

        @SuppressLint("CommitTransaction") // lint not seeing commit
        public void onPreferenceStartScreen(final FragmentManager fragmentManager,
                                            final PreferenceFragmentCompat preferenceFragmentCompat,
                                            final PreferenceScreen preferenceScreen) {
            PreferenceFragmentCompat f = buildFragment(preferenceScreen);
            FragmentTransaction ft = fragmentManager.beginTransaction();
            if (customAnimations) {
                ft.setCustomAnimations(animEnter, animExit, animPopEnter, animPopExit);
            }
            ft.replace(preferenceFragmentCompat.getId(), f, preferenceFragmentCompat.getTag())
              .addToBackStack(preferenceScreen.getKey())
              .commit();
        }

        public interface Callbacks {
            PreferenceFragmentCompat onBuildPreferenceFragment(PreferenceScreen rootKey);
        }
    }
}
