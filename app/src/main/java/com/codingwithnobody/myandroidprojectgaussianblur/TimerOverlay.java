package com.codingwithnobody.myandroidprojectgaussianblur;


import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.TextureOverlay;

import java.util.Locale;

/**
 * A {@link TextureOverlay} that displays a "time elapsed" timer in the bottom left corner of the
 * frame.
 */
/* package */
@UnstableApi
final class TimerOverlay extends TextOverlay {

    private final StaticOverlaySettings overlaySettings;

    public TimerOverlay() {
        overlaySettings =
                new StaticOverlaySettings.Builder()
                        .setRotationDegrees(90f)
                        // Place the timer in the bottom left corner of the screen with some padding from the
                        // edges.
                        .setOverlayFrameAnchor(0f, 0f) // Top-left
                        .setBackgroundFrameAnchor(0.05f, 0.05f) // Slight padding from top-left
                        .build();
    }

    @NonNull
    @Override
    public SpannableString getText(long presentationTimeUs) {
        SpannableString text =
                new SpannableString(
                        String.format(Locale.US, "%.02f", presentationTimeUs / (float) C.MICROS_PER_SECOND));
        text.setSpan(
                new ForegroundColorSpan(Color.RED),
                /* start= */ 0,
                text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        text.setSpan(
                new AbsoluteSizeSpan(200, false), // false = size in pixels, true = in SP
                0,
                text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return text;
    }

    @NonNull
    @Override
    public StaticOverlaySettings getOverlaySettings(long presentationTimeUs) {
        return overlaySettings;
    }
}