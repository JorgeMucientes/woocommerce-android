package com.woocommerce.android.ui.prefs.cardreader.connect

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.BuildConfig
import com.woocommerce.android.R
import com.woocommerce.android.cardreader.CardReader
import com.woocommerce.android.cardreader.CardReaderDiscoveryEvents
import com.woocommerce.android.cardreader.CardReaderDiscoveryEvents.Failed
import com.woocommerce.android.cardreader.CardReaderDiscoveryEvents.ReadersFound
import com.woocommerce.android.cardreader.CardReaderDiscoveryEvents.Started
import com.woocommerce.android.cardreader.CardReaderDiscoveryEvents.Succeeded
import com.woocommerce.android.cardreader.CardReaderManager
import com.woocommerce.android.model.UiString
import com.woocommerce.android.model.UiString.UiStringRes
import com.woocommerce.android.model.UiString.UiStringText
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.CardReaderConnectEvent.CheckBluetoothEnabled
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.CardReaderConnectEvent.CheckLocationEnabled
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.CardReaderConnectEvent.CheckLocationPermissions
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.CardReaderConnectEvent.InitializeCardReaderManager
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.CardReaderConnectEvent.OpenLocationSettings
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.CardReaderConnectEvent.OpenPermissionsSettings
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.CardReaderConnectEvent.RequestEnableBluetooth
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.CardReaderConnectEvent.RequestLocationPermissions
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ListItemViewState.ScanningInProgressListItem
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ListItemViewState.CardReaderListItem
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.BluetoothDisabledError
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.ConnectingFailedState
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.ConnectingState
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.LocationDisabledError
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.MissingPermissionsError
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.ReaderFoundState
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.ScanningFailedState
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.ScanningState
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ViewState.MultipleReadersFoundState
import com.woocommerce.android.util.CoroutineDispatchers
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.wordpress.android.fluxc.utils.AppLogWrapper
import org.wordpress.android.util.AppLog.T
import javax.inject.Inject

private const val SHOW_LAST_N_DIGITS_OF_CARD_READERS_ID = 8

@HiltViewModel
class CardReaderConnectViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val dispatchers: CoroutineDispatchers,
    private val appLogWrapper: AppLogWrapper
) : ScopedViewModel(savedState) {
    /**
     * This is a workaround for a bug in MultiLiveEvent, which can't be fixed without vital changes.
     * When multiple events are send synchronously to MultiLiveEvent only the first one gets handled
     * as MultiLiveEvent.pending field gets set to false when the first events is handled and all the other events
     * are ignored.
     * Example: Imagine VM sends CheckPermissions event -> the view layer synchronously checks the permissions and
     * invokes vm.permissionChecked(true), the vm sends CheckBluetoothEvent, but this event is never observed by the
     * view layer, since `MultiLiveEvent.pending` was set to false by the previous event.
     * Since this VM doesn't need to have support for MultiLiveEvent, it overrides _event from the parent
     * with SingleLiveEvent.
     */
    override val _event = SingleLiveEvent<Event>()
    override val event: LiveData<Event> = _event

    private lateinit var cardReaderManager: CardReaderManager

    // The app shouldn't store the state as connection flow gets canceled when the vm dies
    private val viewState = MutableLiveData<ViewState>(ScanningState(::onCancelClicked))
    val viewStateData: LiveData<ViewState> = viewState

    init {
        startFlow()
    }

    private fun startFlow() {
        viewState.value = ScanningState(::onCancelClicked)
        triggerEvent(CheckLocationPermissions(::onCheckLocationPermissionsResult))
    }

    private fun onCheckLocationPermissionsResult(granted: Boolean) {
        if (granted) {
            onLocationPermissionsVerified()
        } else if (viewState.value !is MissingPermissionsError) {
            triggerEvent(RequestLocationPermissions(::onRequestLocationPermissionsResult))
        }
    }

    private fun onRequestLocationPermissionsResult(granted: Boolean) {
        if (granted) {
            onLocationPermissionsVerified()
        } else {
            viewState.value = MissingPermissionsError(
                onPrimaryActionClicked = ::onOpenPermissionsSettingsClicked,
                onSecondaryActionClicked = ::onCancelClicked
            )
        }
    }

    private fun onCheckLocationEnabledResult(enabled: Boolean) {
        if (enabled) {
            onLocationStateVerified()
        } else {
            viewState.value = LocationDisabledError(
                onPrimaryActionClicked = ::onOpenLocationProviderSettingsClicked,
                onSecondaryActionClicked = ::onCancelClicked
            )
        }
    }

    private fun onLocationSettingsClosed() {
        triggerEvent(CheckLocationEnabled(::onCheckLocationEnabledResult))
    }

    private fun onCheckBluetoothResult(enabled: Boolean) {
        if (enabled) {
            onBluetoothStateVerified()
        } else {
            triggerEvent(RequestEnableBluetooth(::onRequestEnableBluetoothResult))
        }
    }

    private fun onRequestEnableBluetoothResult(enabled: Boolean) {
        if (enabled) {
            onBluetoothStateVerified()
        } else {
            viewState.value = BluetoothDisabledError(
                onPrimaryActionClicked = ::onOpenBluetoothSettingsClicked,
                onSecondaryActionClicked = ::onCancelClicked
            )
        }
    }

    private fun onCardReaderManagerInitialized(cardReaderManager: CardReaderManager) {
        this.cardReaderManager = cardReaderManager
        launch {
            startScanning()
        }
    }

    private fun onLocationPermissionsVerified() {
        triggerEvent(CheckLocationEnabled(::onCheckLocationEnabledResult))
    }

    private fun onLocationStateVerified() {
        triggerEvent(CheckBluetoothEnabled(::onCheckBluetoothResult))
    }

    private fun onBluetoothStateVerified() {
        triggerEvent(InitializeCardReaderManager(::onCardReaderManagerInitialized))
    }

    private suspend fun startScanning() {
        cardReaderManager
            .discoverReaders(isSimulated = BuildConfig.USE_SIMULATED_READER)
            // TODO cardreader should we move flowOn to CardReaderModule?
            .flowOn(dispatchers.io)
            .collect { discoveryEvent ->
                handleScanEvent(discoveryEvent)
            }
    }

    private fun handleScanEvent(discoveryEvent: CardReaderDiscoveryEvents) {
        when (discoveryEvent) {
            Started -> {
                if (viewState.value !is ScanningState) {
                    viewState.value = ScanningState(::onCancelClicked)
                }
            }
            is ReadersFound -> onReadersFound(discoveryEvent)
            Succeeded -> {
                // noop
            }
            is Failed -> {
                appLogWrapper.e(T.MAIN, "Scanning failed: ${discoveryEvent.msg}")
                viewState.value = ScanningFailedState(::startFlow, ::onCancelClicked)
            }
        }
    }

    private fun onReadersFound(discoveryEvent: ReadersFound) {
        if (viewState.value is ConnectingState) return
        val availableReaders = discoveryEvent.list.filter { it.id != null }
        viewState.value = when {
            availableReaders.isEmpty() -> ScanningState(::onCancelClicked)
            availableReaders.size == 1 -> buildSingleReaderFoundState(availableReaders[0])
            availableReaders.size > 1 -> buildMultipleReadersFoundState(availableReaders)
            else -> throw IllegalStateException("Unreachable code")
        }
    }

    private fun buildSingleReaderFoundState(reader: CardReader) =
        ReaderFoundState(
            onPrimaryActionClicked = { onConnectToReaderClicked(reader) },
            onSecondaryActionClicked = ::onCancelClicked,
            readerId = reader.id.orEmpty()
        )

    private fun buildMultipleReadersFoundState(availableReaders: List<CardReader>): MultipleReadersFoundState {
        val listItems: MutableList<ListItemViewState> = availableReaders
            .map { mapReaderToListItem(it) }
            .toMutableList()
            .also { it.add(ScanningInProgressListItem) }
        return MultipleReadersFoundState(listItems, ::onCancelClicked)
    }

    private fun mapReaderToListItem(reader: CardReader): ListItemViewState =
        CardReaderListItem(
            readerId = reader.id?.takeLast(SHOW_LAST_N_DIGITS_OF_CARD_READERS_ID).orEmpty(),
            onConnectClicked = {
                onConnectToReaderClicked(reader)
            })

    private fun onConnectToReaderClicked(cardReader: CardReader) {
        viewState.value = ConnectingState(::onCancelClicked)
        launch {
            val success = cardReaderManager.connectToReader(cardReader)
            if (success) {
                onReaderConnected()
            } else {
                appLogWrapper.e(T.MAIN, "Connecting to reader failed.")
                viewState.value = ConnectingFailedState({ onConnectToReaderClicked(cardReader) }, ::onCancelClicked)
            }
        }
    }

    private fun onOpenPermissionsSettingsClicked() {
        triggerEvent(OpenPermissionsSettings)
    }

    private fun onOpenLocationProviderSettingsClicked() {
        triggerEvent(OpenLocationSettings(::onLocationSettingsClosed))
    }

    private fun onOpenBluetoothSettingsClicked() {
        triggerEvent(RequestEnableBluetooth(::onRequestEnableBluetoothResult))
    }

    private fun onCancelClicked() {
        appLogWrapper.e(T.MAIN, "Connection flow interrupted by the user.")
        exitFlow(connected = false)
    }

    private fun onReaderConnected() {
        appLogWrapper.e(T.MAIN, "Connecting to reader succeeded.")
        exitFlow(connected = true)
    }

    private fun exitFlow(connected: Boolean) {
        triggerEvent(ExitWithResult(connected))
    }

    fun onScreenResumed() {
        if (viewState.value is MissingPermissionsError) {
            triggerEvent(CheckLocationPermissions(::onCheckLocationPermissionsResult))
        }
    }

    sealed class CardReaderConnectEvent : Event() {
        data class InitializeCardReaderManager(val onCardManagerInitialized: (manager: CardReaderManager) -> Unit) :
            CardReaderConnectEvent()

        data class CheckLocationPermissions(val onPermissionsCheckResult: (Boolean) -> Unit) : CardReaderConnectEvent()

        data class CheckLocationEnabled(val onLocationEnabledCheckResult: (Boolean) -> Unit) : CardReaderConnectEvent()

        data class CheckBluetoothEnabled(val onBluetoothCheckResult: (Boolean) -> Unit) : CardReaderConnectEvent()

        data class RequestEnableBluetooth(val onEnableBluetoothRequestResult: (Boolean) -> Unit) :
            CardReaderConnectEvent()

        data class RequestLocationPermissions(val onPermissionsRequestResult: (Boolean) -> Unit) :
            CardReaderConnectEvent()

        object OpenPermissionsSettings : CardReaderConnectEvent()

        data class OpenLocationSettings(val onLocationSettingsClosed: () -> Unit) : CardReaderConnectEvent()
    }

    sealed class ViewState(
        val headerLabel: UiString? = null,
        @DrawableRes val illustration: Int? = null,
        @StringRes val hintLabel: Int? = null,
        val primaryActionLabel: Int? = null,
        val secondaryActionLabel: Int? = null,
        open val listItems: List<ListItemViewState>? = null
    ) {
        open val onPrimaryActionClicked: (() -> Unit)? = null
        open val onSecondaryActionClicked: (() -> Unit)? = null

        data class ScanningState(override val onSecondaryActionClicked: (() -> Unit)) : ViewState(
            headerLabel = UiStringRes(R.string.card_reader_connect_scanning_header),
            illustration = R.drawable.img_card_reader_scanning,
            hintLabel = R.string.card_reader_connect_scanning_hint,
            secondaryActionLabel = R.string.cancel
        )

        data class ReaderFoundState(
            override val onPrimaryActionClicked: (() -> Unit),
            override val onSecondaryActionClicked: (() -> Unit),
            val readerId: String
        ) : ViewState(
            headerLabel = UiStringRes(
                stringRes = R.string.card_reader_connect_reader_found_header,
                params = listOf(UiStringText("<b>$readerId</b>")),
                containsHtml = true
            ),
            illustration = R.drawable.img_card_reader,
            primaryActionLabel = R.string.card_reader_connect_to_reader,
            secondaryActionLabel = R.string.cancel
        )

        data class MultipleReadersFoundState(
            override val listItems: List<ListItemViewState>,
            override val onSecondaryActionClicked: () -> Unit
        ) : ViewState(
            headerLabel = UiStringRes(R.string.card_reader_connect_multiple_readers_found_header),
            secondaryActionLabel = R.string.cancel
        )

        data class ConnectingState(override val onSecondaryActionClicked: (() -> Unit)) : ViewState(
            headerLabel = UiStringRes(R.string.card_reader_connect_connecting_header),
            illustration = R.drawable.img_card_reader_connecting,
            hintLabel = R.string.card_reader_connect_connecting_hint,
            secondaryActionLabel = R.string.cancel
        )

        data class ScanningFailedState(
            override val onPrimaryActionClicked: () -> Unit,
            override val onSecondaryActionClicked: () -> Unit
        ) : ViewState(
            headerLabel = UiStringRes(R.string.card_reader_connect_failed_header),
            illustration = R.drawable.img_products_error,
            hintLabel = R.string.card_reader_connect_scanning_failed_hint,
            primaryActionLabel = R.string.retry,
            secondaryActionLabel = R.string.cancel
        )

        data class ConnectingFailedState(
            override val onPrimaryActionClicked: () -> Unit,
            override val onSecondaryActionClicked: () -> Unit
        ) : ViewState(
            headerLabel = UiStringRes(R.string.card_reader_connect_failed_header),
            illustration = R.drawable.img_products_error,
            hintLabel = R.string.card_reader_connect_connecting_failed_hint,
            primaryActionLabel = R.string.retry,
            secondaryActionLabel = R.string.cancel
        )

        data class MissingPermissionsError(
            override val onPrimaryActionClicked: () -> Unit,
            override val onSecondaryActionClicked: () -> Unit
        ) : ViewState(
            headerLabel = UiStringRes(R.string.card_reader_connect_failed_header),
            illustration = R.drawable.img_products_error,
            hintLabel = R.string.card_reader_connect_missing_permissions_hint,
            primaryActionLabel = R.string.card_reader_connect_open_permission_settings,
            secondaryActionLabel = R.string.cancel
        )

        data class LocationDisabledError(
            override val onPrimaryActionClicked: () -> Unit,
            override val onSecondaryActionClicked: () -> Unit
        ) : ViewState(
            headerLabel = UiStringRes(R.string.card_reader_connect_failed_header),
            illustration = R.drawable.img_products_error,
            hintLabel = R.string.card_reader_connect_location_provider_disabled_hint,
            primaryActionLabel = R.string.card_reader_connect_open_location_settings,
            secondaryActionLabel = R.string.cancel
        )

        data class BluetoothDisabledError(
            override val onPrimaryActionClicked: () -> Unit,
            override val onSecondaryActionClicked: () -> Unit
        ) : ViewState(
            headerLabel = UiStringRes(R.string.card_reader_connect_failed_header),
            illustration = R.drawable.img_products_error,
            hintLabel = R.string.card_reader_connect_bluetooth_disabled_hint,
            primaryActionLabel = R.string.card_reader_connect_open_bluetooth_settings,
            secondaryActionLabel = R.string.cancel
        )
    }

    sealed class ListItemViewState {
        object ScanningInProgressListItem : ListItemViewState() {
            val label = UiStringRes(R.string.card_reader_connect_scanning_progress)
            @DrawableRes val scanningIcon = R.drawable.ic_loop_24px
        }

        data class CardReaderListItem(
            val readerId: String,
            val onConnectClicked: () -> Unit
        ) : ListItemViewState() {
            val connectLabel: UiString = UiStringRes(R.string.card_reader_connect_connect_button)
        }
    }
}
