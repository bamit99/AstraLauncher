package com.astra.launcher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/**
 * One launchable app, resolved once per {@link AppRepository} load.
 *
 * <p>Field values never change after construction, so a list snapshot can be handed to the UI thread
 * without copying icons or re-querying the package manager.
 */
final class AppEntry {

    final String label;
    final String pkg;
    final Drawable icon;
    final Intent intent;
    final boolean system;
    final AppRepository.Category category;

    AppEntry(String label, String pkg, Drawable icon, Intent intent, boolean system,
             AppRepository.Category category) {
        this.label = label;
        this.pkg = pkg;
        this.icon = icon;
        this.intent = intent;
        this.system = system;
        this.category = category;
    }
}
