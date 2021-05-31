package com.woocommerce.android.di

import com.woocommerce.android.support.HelpActivity
import com.woocommerce.android.support.HelpModule
import com.woocommerce.android.ui.login.LoginActivity
import com.woocommerce.android.ui.login.MagicLinkInterceptActivity
import com.woocommerce.android.ui.login.WooLoginFragmentModule
import com.woocommerce.android.ui.sitepicker.SitePickerActivity
import com.woocommerce.android.ui.sitepicker.SitePickerModule
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.wordpress.android.login.di.LoginFragmentModule

@InstallIn(SingletonComponent::class)
@Module
abstract class ActivityBindingModule {
    @ActivityScope
    @ContributesAndroidInjector(modules = [
        LoginFragmentModule::class,
        WooLoginFragmentModule::class])
    abstract fun provideLoginActivityInjector(): LoginActivity

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun provideMagicLinkInterceptActivityInjector(): MagicLinkInterceptActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [HelpModule::class])
    abstract fun provideHelpActivity(): HelpActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [SitePickerModule::class])
    abstract fun provideSitePickerActivityInjector(): SitePickerActivity
}
