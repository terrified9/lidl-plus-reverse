package lol.hypixel.lidlapp.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lol.hypixel.lidlapp.network.LidlApi
import lol.hypixel.lidlapp.network.PaymentMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class QrReady(val qrData: String, val paymentMethod: PaymentMethod) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel : ViewModel() {

    private val api = LidlApi()

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _paymentMethods = MutableLiveData<List<PaymentMethod>>()
    val paymentMethods: LiveData<List<PaymentMethod>> = _paymentMethods

    var selectedMethodIndex = 0

    /** Called after OAuth succeeds. Pre-fetches payment methods so the UI can show them. */
    fun onBearerAvailable(bearer: String) {
        viewModelScope.launch {
            try {
                val methods = withContext(Dispatchers.IO) { api.fetchPaymentMethods(bearer) }
                _paymentMethods.value = methods
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Could not load payment methods: ${e.message}")
            }
        }
    }

    /** Full flow: loyalty id → pin token → QR */
    fun generateQr(bearer: String, pin: String) {
        val methods = _paymentMethods.value
        if (methods.isNullOrEmpty()) {
            _uiState.value = UiState.Error("No payment methods loaded")
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val (loyaltyId, pinToken, qrData) = withContext(Dispatchers.IO) {
                    val loyalty = api.getLoyaltyId(bearer)
                    val token = api.getPinToken(bearer, pin)
                    val qr = api.generateQrCode(
                        bearer, token, loyalty,
                        methods[selectedMethodIndex].id
                    )
                    Triple(loyalty, token, qr)
                }
                val method = methods[selectedMethodIndex]
                _uiState.value = UiState.QrReady(qrData, method)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }
}
