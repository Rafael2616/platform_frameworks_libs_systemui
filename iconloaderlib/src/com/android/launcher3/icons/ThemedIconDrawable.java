/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.icons;

import static android.content.res.Resources.ID_NULL;

import static com.android.launcher3.icons.GraphicsUtils.getExpectedBitmapSize;
import static com.android.launcher3.icons.IconProvider.ICON_TYPE_CALENDAR;
import static com.android.launcher3.icons.IconProvider.ICON_TYPE_CLOCK;

import android.annotation.ColorInt;
import android.annotation.DrawableRes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;

import com.android.launcher3.icons.cache.BaseIconCache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import android.os.Process;

import app.lawnchair.icons.CustomAdaptiveIconDrawable;
import app.lawnchair.icons.ExtendedBitmapDrawable;
import app.lawnchair.icons.IconPreferencesKt;

/**
 * Class to handle monochrome themed app icons
 */
@SuppressWarnings("NewApi")
public class ThemedIconDrawable extends FastBitmapDrawable {

    public static final String TAG = "ThemedIconDrawable";

    final BitmapInfo bitmapInfo;
    final int colorFg, colorBg;

    // The foreground/monochrome icon for the app
    private final Bitmap mMonoIcon;
    private final Paint mMonoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final Bitmap mBgBitmap;
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final ColorFilter mBgFilter, mMonoFilter;

    protected ThemedIconDrawable(ThemedConstantState constantState) {
        super(constantState.mBitmap, constantState.colorFg);
        bitmapInfo = constantState.bitmapInfo;
        colorBg = constantState.colorBg;
        colorFg = constantState.colorFg;

        mMonoIcon = bitmapInfo.mMono;
        mMonoFilter = new BlendModeColorFilter(colorFg, BlendMode.SRC_IN);
        mMonoPaint.setColorFilter(mMonoFilter);

        mBgBitmap = bitmapInfo.mWhiteShadowLayer;
        mBgFilter = new BlendModeColorFilter(colorBg, BlendMode.SRC_IN);
        mBgPaint.setColorFilter(mBgFilter);
    }

    @Override
    protected void drawInternal(Canvas canvas, Rect bounds) {
        canvas.drawBitmap(mBgBitmap, null, bounds, mBgPaint);
        canvas.drawBitmap(mMonoIcon, null, bounds, mMonoPaint);
    }

    @Override
    protected void updateFilter() {
        super.updateFilter();
        int alpha = mIsDisabled ? (int) (mDisabledAlpha * FULLY_OPAQUE) : FULLY_OPAQUE;
        mBgPaint.setAlpha(alpha);
        mBgPaint.setColorFilter(mIsDisabled ? new BlendModeColorFilter(
                getDisabledColor(colorBg), BlendMode.SRC_IN) : mBgFilter);

        mMonoPaint.setAlpha(alpha);
        mMonoPaint.setColorFilter(mIsDisabled ? new BlendModeColorFilter(
                getDisabledColor(colorFg), BlendMode.SRC_IN) : mMonoFilter);
    }

    @Override
    public boolean isThemed() {
        return true;
    }

    @Override
    public FastBitmapConstantState newConstantState() {
        return new ThemedConstantState(bitmapInfo, colorBg, colorFg);
    }

    static class ThemedConstantState extends FastBitmapConstantState {

        final BitmapInfo bitmapInfo;
        final int colorFg, colorBg;

        public ThemedConstantState(BitmapInfo bitmapInfo, int colorBg, int colorFg) {
            super(bitmapInfo.icon, bitmapInfo.color);
            this.bitmapInfo = bitmapInfo;
            this.colorBg = colorBg;
            this.colorFg = colorFg;
        }

        @Override
        public FastBitmapDrawable createDrawable() {
            return new ThemedIconDrawable(this);
        }
    }

    public static FastBitmapDrawable newDrawable(BitmapInfo info, Context context) {
        int[] colors = getColors(context);
        return new ThemedConstantState(info, colors[0], colors[1]).newDrawable();
    }

    public static class ThemedBitmapInfo extends BitmapInfo {

        final ThemeData mThemeData;
        final float mNormalizationScale;
        final Bitmap mUserBadge;

        public ThemedBitmapInfo(Bitmap icon, int color, ThemeData themeData,
                                float normalizationScale, Bitmap userBadge) {
            super(icon, color);
            mThemeData = themeData;
            mNormalizationScale = normalizationScale;
            mUserBadge = userBadge;
        }

        public FastBitmapDrawable newThemedIcon(Context context) {
            int[] colors = getThemedColors(context);
            FastBitmapDrawable drawable = new ThemedConstantState(this, colors[0], colors[1])
                    .newDrawable();
            drawable.mDisabledAlpha = GraphicsUtils.getFloat(context, R.attr.disabledIconAlpha, 1f);
            return drawable;
        }

        @Nullable
        public byte[] toByteArray() {
            if (isNullOrLowRes()) {
                return null;
            }
            String resName = mThemeData.mResources.getResourceName(mThemeData.mResID);
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    getExpectedBitmapSize(icon) + 3 + resName.length());
            try {
                DataOutputStream dos = new DataOutputStream(out);
                dos.writeByte(TYPE_THEMED_V2);
                dos.writeFloat(mNormalizationScale);
                dos.writeUTF(mThemeData.mPackageName);
                dos.writeUTF(resName);
                icon.compress(Bitmap.CompressFormat.PNG, 100, dos);

                dos.flush();
                dos.close();
                return out.toByteArray();
            } catch (IOException e) {
                Log.w(TAG, "Could not write bitmap");
                return null;
            }
        }

        static ThemedBitmapInfo decode(byte[] data, int color,
                                       BitmapFactory.Options decodeOptions, UserHandle user, BaseIconCache iconCache,
                                       Context context) {
            try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream (data))) {
                dis.readByte(); // type
                float normalizationScale = dis.readFloat();

                String packageName = dis.readUTF();
                Resources res;
                if (packageName.equals(context.getPackageName())) {
                    res = context.getResources();
                } else {
                    res = context.getPackageManager().getResourcesForApplication(packageName);
                }

                String resName = dis.readUTF();
                int resId = res.getIdentifier(resName, "drawable", packageName);
                if (resId == ID_NULL) {
                    return null;
                }

                Bitmap userBadgeBitmap = null;
                if (!Process.myUserHandle().equals(user)) {
                    try (BaseIconFactory iconFactory = iconCache.getIconFactory()) {
                        userBadgeBitmap = iconFactory.getUserBadgeBitmap(user);
                    }
                }

                ThemeData themeData = new ThemeData(res, packageName, resId);
                Bitmap icon = BitmapFactory.decodeStream(dis, null, decodeOptions);
                return new ThemedBitmapInfo(icon, color, themeData, normalizationScale,
                        userBadgeBitmap);
            } catch (IOException | PackageManager.NameNotFoundException e) {
                return null;
            }
        }
    }

    public static class ThemeData {

        final Resources mResources;
        final String mPackageName;
        final int mResID;

        public ThemeData(Resources resources, String packageName, @DrawableRes int resID) {
            mResources = resources;
            mPackageName = packageName;
            mResID = resID;
        }

        Drawable loadMonochromeDrawable(int accentColor) {
            Drawable d = mResources.getDrawable(mResID).mutate();
            d.setTint(accentColor);
            d = new InsetDrawable (d, .2f);
            return d;
        }

        public Drawable wrapDrawable(Drawable original, int iconType) {
            if (!(original instanceof AdaptiveIconDrawable) && !(original instanceof BitmapDrawable)) {
                return original;
            }
            String resourceType = mResources.getResourceTypeName(mResID);
            if (iconType == ICON_TYPE_CALENDAR && "array".equals(resourceType)) {
                TypedArray ta = mResources.obtainTypedArray(mResID);
                int id = ta.getResourceId(IconProvider.getDay(), ID_NULL);
                ta.recycle();
                return id == ID_NULL ? original
                        : wrapWithThemeData(original, new ThemeData(mResources, mPackageName, id));
            } else if (iconType == ICON_TYPE_CLOCK && "array".equals(resourceType)) {
                if (original instanceof ClockDrawableWrapper) {
                    ((ClockDrawableWrapper) original).mThemeData = this;
                }
                return original;
            } else if ("drawable".equals(resourceType)) {
                return wrapWithThemeData(original, this);
            } else {
                return original;
            }
        }

        private Drawable wrapWithThemeData(Drawable original, ThemeData themeData) {
            if (original instanceof AdaptiveIconDrawable) {
                return new ThemedAdaptiveIcon((AdaptiveIconDrawable) original, themeData);
            } else if (original instanceof BitmapDrawable) {
                return new ThemedBitmapIcon(mResources, (BitmapDrawable) original, themeData);
            }
            throw new IllegalArgumentException("original must be AdaptiveIconDrawable or BitmapDrawable");
        }
    }

    static class ThemedAdaptiveIcon extends CustomAdaptiveIconDrawable implements BitmapInfo.Extender {

        protected final ThemeData mThemeData;

        public ThemedAdaptiveIcon(AdaptiveIconDrawable parent, ThemeData themeData) {
            super(parent.getBackground(), parent.getForeground());
            mThemeData = themeData;
        }

        @Override
        public BitmapInfo getExtendedInfo(Bitmap bitmap ,
                                          int color , BaseIconFactory iconFactory, float normalizationScale) {
            Bitmap userBadge = iconFactory.getUserBadgeBitmap(Process.myUserHandle());
            return new ThemedBitmapInfo(bitmap, color, mThemeData, normalizationScale, userBadge);
        }

        @Override
        public void drawForPersistence(Canvas canvas) {
            draw(canvas);
        }

        public Drawable getThemedDrawable(Context context) {
            int[] colors = getThemedColors(context);
            Drawable bg = new ColorDrawable (colors[0]);
            float inset = getExtraInsetFraction() / (1 + 2 * getExtraInsetFraction());
            Drawable fg = new InsetDrawable(mThemeData.loadMonochromeDrawable(colors[1]), inset);
            return new CustomAdaptiveIconDrawable(bg, fg);
        }
    }

    static class ThemedBitmapIcon extends ExtendedBitmapDrawable implements BitmapInfo.Extender {

        protected final ThemeData mThemeData;

        public ThemedBitmapIcon(Resources res, BitmapDrawable parent, ThemeData themeData) {
            super(res, parent.getBitmap(), ExtendedBitmapDrawable.isFromIconPack(parent));
            mThemeData = themeData;
        }

        @Override
        public BitmapInfo getExtendedInfo(Bitmap bitmap, int color, BaseIconFactory iconFactory,
                                          float normalizationScale) {
            Bitmap userBadge = iconFactory.getUserBadgeBitmap(Process.myUserHandle());
            return new ThemedBitmapInfo(bitmap, color, mThemeData, normalizationScale, userBadge);
        }

        @Override
        public void drawForPersistence(Canvas canvas) {
            draw(canvas);
        }

        public Drawable getThemedDrawable(Context context) {
            int[] colors = getThemedColors(context);
            Drawable bg = new ColorDrawable(colors[0]);
            float extraInsetFraction = CustomAdaptiveIconDrawable.getExtraInsetFraction();
            float inset = extraInsetFraction / (1 + 2 * extraInsetFraction);
            Drawable fg = new InsetDrawable(mThemeData.loadMonochromeDrawable(colors[1]), inset);
            return new CustomAdaptiveIconDrawable(bg, fg);
        }
    }

    @ColorInt
    public static int[] getThemedColors(Context context) {
        final int[] result = getColors(context);
        if (!IconPreferencesKt.shouldTransparentBGIcons(context)) {
            return result;
        }
        result[0] = 0;
        return result;
    }

    /**
     * Get an int array representing background and foreground colors for themed icons
     */
    public static int[] getColors(Context context) {
        Resources res = context.getResources();
        int[] colors = new int[2];
        colors[0] = res.getColor(R.color.themed_icon_background_color);
        colors[1] = res.getColor(R.color.themed_icon_color);
        return colors;
    }

    @Override
    public int getIconColor() {
        return colorFg;
    }

    public static Function<Context, int[]> COLORS_LOADER;
}
