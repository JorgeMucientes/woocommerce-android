package com.woocommerce.android.ui.prefs.cardreader.scan

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.woocommerce.android.di.ViewModelAssistedFactory
import com.woocommerce.android.ui.prefs.cardreader.scan.CardReaderScanViewModel.Factory
import com.woocommerce.android.viewmodel.ViewModelKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap

@Module
abstract class CardReaderScanModule {
    companion object {
        @Provides
        fun provideDefaultArgs(fragment: CardReaderScanFragment): Bundle? {
            return fragment.arguments
        }
    }

    @Binds
    abstract fun bindSavedStateRegistryOwner(fragment: CardReaderScanFragment): SavedStateRegistryOwner

    @Binds
    @IntoMap
    @ViewModelKey(CardReaderScanViewModel::class)
    abstract fun bindFactory(factory: Factory): ViewModelAssistedFactory<out ViewModel>
}
