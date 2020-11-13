package com.woocommerce.android.ui.products.downloads

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import androidx.savedstate.SavedStateRegistryOwner
import com.woocommerce.android.R
import com.woocommerce.android.di.ViewModelAssistedFactory
import com.woocommerce.android.ui.products.ProductDetailViewModel
import com.woocommerce.android.ui.products.ProductDetailViewModel.Factory
import com.woocommerce.android.viewmodel.ViewModelKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap

@Module
abstract class ProductDownloadsModule {
    @Module
    companion object {
        @JvmStatic
        @Provides
        fun provideDefaultArgs(fragment: ProductDownloadsFragment): Bundle? {
            return fragment.arguments
        }

        @JvmStatic
        @Provides
        fun provideSavedStateRegistryOwner(fragment: ProductDownloadsFragment): SavedStateRegistryOwner {
            return fragment.findNavController().getBackStackEntry(R.id.nav_graph_products)
        }
    }

    @Binds
    @IntoMap
    @ViewModelKey(ProductDetailViewModel::class)
    abstract fun bindFactory(factory: Factory): ViewModelAssistedFactory<out ViewModel>
}
