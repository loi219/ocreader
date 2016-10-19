package email.schaal.ocreader.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;
import android.util.LruCache;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import java.util.Locale;

import email.schaal.ocreader.R;
import email.schaal.ocreader.database.model.Feed;

/**
 * Load favicons
 */
public class FaviconLoader {

    private final static LruCache<Long, FeedColors> feedColorsCache = new LruCache<>(32);
    private final static LruCache<Long, Drawable> faviconCache = new LruCache<>(32);

    private final int placeholder;
    private final ImageView imageView;
    private final Feed feed;
    private final boolean generateFallbackImage;

    private FaviconLoader(Builder builder) {
        placeholder = builder.placeholder;
        imageView = builder.imageView;
        feed = builder.feed;
        generateFallbackImage = builder.generateFallbackImage;
    }

    public static String getCssColor(int color) {
        // Use US locale so we always get a . as decimal separator for a valid css value
        return String.format(Locale.US,"rgba(%d,%d,%d,%.2f)",
                Color.red(color),
                Color.green(color),
                Color.blue(color),
                Color.alpha(color) / 255.0);
    }

    public static Drawable getDrawable(Context context, @Nullable Feed feed) {
        Drawable drawable;

        if(feed != null && feed.getFaviconLink() == null) {
            drawable = faviconCache.get(feed.getId());

            if(drawable == null) {
                drawable = new TextDrawable.Builder().build(feed.getName().substring(0, 1), getFeedColor(context, feed));
                faviconCache.put(feed.getId(), drawable);
            }
        } else {
            drawable = ContextCompat.getDrawable(context, R.drawable.ic_feed_icon);
        }
        return drawable;
    }

    private static int getFeedColor(Context context, @NonNull Feed feed) {

        final ColorGenerator generator;

        if(getCurrentNightMode(context) == Configuration.UI_MODE_NIGHT_YES)
            generator = ColorGenerator.MATERIAL_NIGHT;
        else
            generator = ColorGenerator.MATERIAL;

        return generator.getColor(feed.getUrl());
    }

    public static int getCurrentNightMode(Context context) {
        return context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
    }

    public void load(Context context, @NonNull FeedColorsListener listener) {
        listener.onStart();
        if(feed == null) {
            listener.onGenerated(new FeedColors((Integer)null));
            return;
        }

        if(feed.getFaviconLink() != null) {
            TypedArray typedArray = context.obtainStyledAttributes(new int[] { android.R.attr.colorBackground });
            final int colorBackground = typedArray.getColor(0, Color.WHITE);
            typedArray.recycle();
            // load favicon
            RequestCreator requestCreator = Picasso.with(context).load(feed.getFaviconLink());
            MyTarget myTarget = new MyTarget(feed, listener, colorBackground);

            if(imageView != null) {
                requestCreator.placeholder(placeholder).into(imageView, myTarget);
            } else {
                requestCreator.into(myTarget);
            }
        } else {
            // feed has no favicon
            if (imageView != null) {
                if (generateFallbackImage) {
                    // generate image
                    imageView.setImageDrawable(getDrawable(context, feed));
                } else {
                    // use placeholder
                    imageView.setImageResource(placeholder);
                }
            }
            listener.onGenerated(new FeedColors(getFeedColor(context, feed)));
        }
    }

    private void generatePalette(Bitmap bitmap, Palette.Filter filter, Palette.PaletteAsyncListener paletteAsyncListener) {
        new Palette.Builder(bitmap)
                .addFilter(filter)
                .generate(paletteAsyncListener);
    }

    public interface FeedColorsListener {
        void onGenerated(@NonNull FeedColors feedColors);
        void onStart();
    }

    public static class Builder {
        @DrawableRes
        private int placeholder = R.drawable.ic_feed_icon;
        private ImageView imageView;
        private final Feed feed;
        private boolean generateFallbackImage = true;

        public Builder(Feed feed) {
            this.feed = feed;
        }

        public Builder(@NonNull ImageView imageView, Feed feed) {
            this.imageView = imageView;
            this.feed = feed;
        }

        public FaviconLoader build() {
            return new FaviconLoader(this);
        }

        public Builder withPlaceholder(@DrawableRes int drawable) {
            this.placeholder = drawable;
            return this;
        }

        public Builder withGenerateFallbackImage(boolean withGenerateFallbackImage) {
            this.generateFallbackImage = withGenerateFallbackImage;
            return this;
        }
    }

    private class MyTarget implements Target, Callback {
        private final Feed feed;
        private final FeedColorsListener listener;
        private final Palette.Filter contrastFilter;

        public MyTarget(@NonNull Feed feed, @NonNull FeedColorsListener listener, final @ColorInt int backgroundColor) {
            this.feed = feed;
            this.listener = listener;
            contrastFilter = new Palette.Filter() {
                @Override
                public boolean isAllowed(int rgb, float[] hsl) {
                    return ColorUtils.calculateContrast(rgb, backgroundColor) >= 4;
                }
            };
        }

        @Override
        public void onBitmapLoaded(final Bitmap bitmap, Picasso.LoadedFrom from) {
            final FeedColors cachedFeedColors = feedColorsCache.get(feed.getId());
            if(cachedFeedColors == null) {
                generatePalette(bitmap, contrastFilter, new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(Palette palette) {
                        FeedColors feedColors = new FeedColors(palette);
                        feedColorsCache.put(feed.getId(), feedColors);
                        listener.onGenerated(feedColors);
                    }
                });
            } else {
                listener.onGenerated(cachedFeedColors);
            }
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            listener.onGenerated(new FeedColors((Integer)null));
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {

        }

        @Override
        public void onSuccess() {
            if(imageView != null)
                onBitmapLoaded(((BitmapDrawable)(imageView).getDrawable()).getBitmap(), null);
        }

        @Override
        public void onError() {
            onBitmapFailed(null);
        }
    }
}
