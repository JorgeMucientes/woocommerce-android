package com.woocommerce.android.util.crashlogging

import com.automattic.android.tracks.crashlogging.CrashLoggingDataProvider
import com.automattic.android.tracks.crashlogging.CrashLoggingUser
import com.automattic.android.tracks.crashlogging.EventLevel
import com.automattic.android.tracks.crashlogging.ExtraKnownKey
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.BuildConfig
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.locale.LocaleProvider
import org.wordpress.android.fluxc.store.AccountStore
import java.util.Locale
import javax.inject.Inject

class WCCrashLoggingDataProvider @Inject constructor(
    private val localeProvider: LocaleProvider,
    private val accountStore: AccountStore,
    private val selectedSite: SelectedSite,
    private val appPrefs: AppPrefs
) : CrashLoggingDataProvider {
    override val buildType: String = BuildConfig.BUILD_TYPE

    override val enableCrashLoggingLogs: Boolean = BuildConfig.DEBUG

    override val locale: Locale?
        get() = localeProvider.provideLocale()

    override val releaseName: String = BuildConfig.VERSION_NAME

    override val sentryDSN: String = BuildConfig.SENTRY_DSN

    override fun applicationContextProvider(): Map<String, String> {
        return selectedSite.getIfExists()?.let {
            mapOf(
                SITE_ID_KEY to it.siteId.toString(),
                SITE_URL_KEY to it.url
            )
        }.orEmpty()
    }

    override fun crashLoggingEnabled(): Boolean {
        return appPrefs.isCrashReportingEnabled()
    }

    override fun extraKnownKeys(): List<ExtraKnownKey> {
        return emptyList()
    }

    override fun provideExtrasForEvent(
        currentExtras: Map<ExtraKnownKey, String>,
        eventLevel: EventLevel
    ): Map<ExtraKnownKey, String> {
        return emptyMap()
    }

    override fun shouldDropWrappingException(module: String, type: String, value: String): Boolean {
        return false
    }

    override fun userProvider(): CrashLoggingUser? {
        return accountStore.account?.let { accountModel ->
            CrashLoggingUser(
                userID = accountModel.userId.toString(),
                email = accountModel.email,
                username = accountModel.userName
            )
        }
    }

    companion object {
        private const val SITE_ID_KEY = "site_id"
        private const val SITE_URL_KEY = "site_url"
    }
}
