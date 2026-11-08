package com.astra.launcher;

import android.app.admin.DeviceAdminReceiver;

/**
 * Minimal device-admin receiver. Astra only uses the force-lock policy so the
 * Sleep row can turn the display off without root.
 */
public class AstraAdmin extends DeviceAdminReceiver {
}
