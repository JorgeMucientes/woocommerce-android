package com.woocommerce.android.di

import android.app.Application
import com.woocommerce.android.push.FCMServiceModule
import com.woocommerce.android.ui.login.LoginAnalyticsModule
import com.woocommerce.android.util.crashlogging.CrashLoggingModule
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import org.wordpress.android.fluxc.module.OkHttpClientModule
import org.wordpress.android.fluxc.module.ReleaseNetworkModule
import org.wordpress.android.login.di.LoginServiceModule
import javax.inject.Singleton

@Singleton
@Component(modules = [
        AndroidInjectionModule::class,
        ThreadModule::class,
        ApplicationModule::class,
        AppConfigModule::class,
        ReleaseNetworkModule::class,
        OkHttpClientModule::class,
        InterceptorModuleTest::class,
        ActivityBindingModule::class,
        SelectedSiteModule::class,
        FCMServiceModule::class,
        LoginAnalyticsModule::class,
        LoginServiceModule::class,
        NetworkStatusModule::class,
        CurrencyModule::class,
        SupportModule::class,
        OrderFetcherModule::class,
        CrashLoggingModule::class
])
interface AppComponentTest : AppComponent {
    @Component.Builder
    interface Builder : AppComponent.Builder {
        @BindsInstance
        override fun application(application: Application): Builder

        override fun build(): AppComponentTest
    }
}
