package io

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings

actual fun createSettings(name: String): Settings = NSUserDefaultsSettings.Factory().create(name)
