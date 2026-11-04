// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.app.Application
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.util.AppIcons
import dev.liquidphone.util.Strings

class PhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.app = this
        Stores.init(this)
        if (AppIcons.migrate(this, Stores.settings.state.value.appIcon)) Stores.settings.update { it.copy(appIcon = 0) }
        Repo.init(this)
    }
}
