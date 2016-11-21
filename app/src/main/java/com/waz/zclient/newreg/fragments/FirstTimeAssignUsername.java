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
package com.waz.zclient.newreg.fragments;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.waz.api.ImageAsset;
import com.waz.api.Self;
import com.waz.api.UpdateListener;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.appentry.AppEntryState;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.BitmapUtils;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetImageView;


public class FirstTimeAssignUsername extends BaseFragment<FirstLaunchAfterLoginFragment.Container> {

    public static final String TAG = FirstTimeAssignUsername.class.getName();

    TypefaceTextView nameTextView;
    TypefaceTextView usernameTextView;
    ImageAssetImageView backgroundImageView;
    Self self;
    UpdateListener selfUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            ImageAsset imageAsset = self.getPicture();
            backgroundImageView.connectImageAsset(imageAsset);
            nameTextView.setText(self.getName());
            usernameTextView.setText(StringUtils.formatUsername(self.getUsername()));
        }
    };

    public static Fragment newInstance() {
        return new FirstTimeAssignUsername();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_username_first_launch, container, false);
        nameTextView = ViewUtils.getView(view, R.id.ttv__name);
        usernameTextView = ViewUtils.getView(view, R.id.ttv__username);
        backgroundImageView = ViewUtils.getView(view, R.id.iaiv__user_photo);
        final ImageView vignetteOverlay = ViewUtils.getView(view, R.id.iv_background_vignette_overlay);
        ZetaButton chooseYourOwnButton = ViewUtils.getView(view, R.id.zb__username_first_assign__choose);
        ZetaButton keepButton = ViewUtils.getView(view, R.id.zb__username_first_assign__keep);
        self = getStoreFactory().getZMessagingApiStore().getApi().getSelf();
        final int darkenColor = ColorUtils.injectAlpha(ResourceUtils.getResourceFloat(getResources(), R.dimen.background_solid_black_overlay_opacity),
            Color.BLACK);

        backgroundImageView.setDisplayType(ImageAssetImageView.DisplayType.REGULAR);

        vignetteOverlay.setImageBitmap(BitmapUtils.getVignetteBitmap(getResources()));
        vignetteOverlay.setColorFilter(darkenColor, PorterDuff.Mode.DARKEN);

        self.addUpdateListener(selfUpdateListener);

        chooseYourOwnButton.setIsFilled(true);
        chooseYourOwnButton.setAccentColor(getControllerFactory().getAccentColorController().getAccentColor().getColor());
        chooseYourOwnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO: Change username dialog fragment from feature/username-edit branch
                getStoreFactory().getAppEntryStore().setState(AppEntryState.LOGGED_IN);
            }
        });

        keepButton.setIsFilled(false);
        keepButton.setAccentColor(getControllerFactory().getAccentColorController().getAccentColor().getColor());
        keepButton.setTextColor(getResources().getColor(R.color.white));
        keepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getStoreFactory().getAppEntryStore().setState(AppEntryState.LOGGED_IN);
            }
        });

        return view;
    }

    public interface Container {
    }
}
