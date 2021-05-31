package com.woocommerce.android.ui.orders.shippinglabels

import android.os.Parcelable
import com.woocommerce.android.R.string
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.di.ViewModelAssistedFactory
import com.woocommerce.android.media.FileUtils
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewPrintShippingLabelInfo
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewShippingLabelFormatOptions
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewShippingLabelPaperSizes
import com.woocommerce.android.ui.orders.shippinglabels.ShippingLabelPaperSizeSelectorDialog.ShippingLabelPaperSize
import com.woocommerce.android.util.CoroutineDispatchers
import com.woocommerce.android.viewmodel.LiveDataDelegateWithArgs
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.SavedStateWithArgs
import com.woocommerce.android.viewmodel.DaggerScopedViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.Date
import java.util.Locale

class PrintShippingLabelViewModel @AssistedInject constructor(
    @Assisted savedState: SavedStateWithArgs,
    private val repository: ShippingLabelRepository,
    private val networkStatus: NetworkStatus,
    dispatchers: CoroutineDispatchers
) : DaggerScopedViewModel(savedState, dispatchers) {
    private val arguments: PrintShippingLabelFragmentArgs by savedState.navArgs()
    private val label
        get() = repository.getShippingLabelByOrderIdAndLabelId(
            orderId = arguments.orderId,
            shippingLabelId = arguments.shippingLabelId
        )

    val viewStateData = LiveDataDelegateWithArgs(savedState, PrintShippingLabelViewState(
        isLabelExpired = label?.isAnonymized == true ||
            label?.expiryDate?.let { Date().after(it) } ?: false
    ))
    private var viewState by viewStateData

    fun onPrintShippingLabelInfoSelected() {
        triggerEvent(ViewPrintShippingLabelInfo)
    }

    fun onViewLabelFormatOptionsClicked() {
        triggerEvent(ViewShippingLabelFormatOptions)
    }

    fun onPaperSizeOptionsSelected() {
        triggerEvent(ViewShippingLabelPaperSizes(viewState.paperSize))
    }

    fun onPaperSizeSelected(paperSize: ShippingLabelPaperSize) {
        viewState = viewState.copy(paperSize = paperSize)
    }

    fun onSaveForLaterClicked() {
        triggerEvent(ExitWithResult(Unit))
    }

    fun onPrintShippingLabelClicked() {
        if (networkStatus.isConnected()) {
            AnalyticsTracker.track(Stat.SHIPPING_LABEL_PRINT_REQUESTED)
            viewState = viewState.copy(isProgressDialogShown = true)
            launch {
                val requestResult = repository.printShippingLabel(
                    viewState.paperSize.name.toLowerCase(Locale.US), arguments.shippingLabelId
                )

                viewState = viewState.copy(isProgressDialogShown = false)
                if (requestResult.isError) {
                    triggerEvent(ShowSnackbar(string.shipping_label_preview_error))
                } else {
                    viewState = viewState.copy(previewShippingLabel = requestResult.model)
                }
            }
        } else {
            triggerEvent(ShowSnackbar(string.offline_error))
        }
    }

    fun writeShippingLabelToFile(
        storageDir: File,
        shippingLabelPreview: String
    ) {
        launch(dispatchers.io) {
            val tempFile = FileUtils.createTempFile(storageDir)
            if (tempFile != null) {
                FileUtils.writeToTempFile(tempFile, shippingLabelPreview)?.let {
                    withContext(dispatchers.main) { viewState = viewState.copy(tempFile = it) }
                } ?: handlePreviewError()
            } else {
                handlePreviewError()
            }
        }
    }

    fun onPreviewLabelCompleted() {
        viewState = viewState.copy(tempFile = null, previewShippingLabel = null)
    }

    private suspend fun handlePreviewError() {
        withContext(dispatchers.main) {
            triggerEvent(ShowSnackbar(string.shipping_label_preview_error))
        }
    }

    @Parcelize
    data class PrintShippingLabelViewState(
        val paperSize: ShippingLabelPaperSize = ShippingLabelPaperSize.LABEL,
        val isProgressDialogShown: Boolean? = null,
        val previewShippingLabel: String? = null,
        val isLabelExpired: Boolean = false,
        val tempFile: File? = null
    ) : Parcelable

    @AssistedFactory
    interface Factory : ViewModelAssistedFactory<PrintShippingLabelViewModel>
}
