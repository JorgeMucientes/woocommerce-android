package com.woocommerce.android.ui.common

import com.woocommerce.android.AppPrefs
import com.woocommerce.android.tools.SelectedSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wordpress.android.fluxc.store.WCUserStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class UserEligibilityFetcher @Inject constructor(
    private val appPrefs: AppPrefs,
    private val userStore: WCUserStore,
    private val selectedSite: SelectedSite
) : CoroutineScope {
    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    fun fetchUserEligibility() {
        launch {
            val requestResult = userStore.fetchUserRole(selectedSite.get())
            requestResult.model.let { user ->
                val isUserEligible = user.getUserRoles().none { !it.isSupported() }
                appPrefs.setIsUserEligible(isUserEligible)
                appPrefs.setUserEmail(user.email)
            }
        }
    }
}
