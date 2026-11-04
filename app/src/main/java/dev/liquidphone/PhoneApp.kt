// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.app.Application
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.util.Strings

class PhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.app = this
        Stores.init(this)
        Repo.init(this)
    }
}
