package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.CarPlaySize
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.transport.EvChargingConnectors

internal fun CarPlaySize.localizedLabel(context: Context): String = context.getString(when (this) {
    CarPlaySize.LARGE -> R.string.option_size_large
    CarPlaySize.MEDIUM -> R.string.option_size_medium
    CarPlaySize.SMALL -> R.string.option_size_small
})

internal fun EvChargingConnectors.localizedLabel(context: Context): String = context.getString(when (this) {
    EvChargingConnectors.CCS2_TYPE2 -> R.string.connectors_ccs2_type2
    EvChargingConnectors.GB_T -> R.string.connectors_gb_t
    EvChargingConnectors.CCS1_J1772 -> R.string.connectors_ccs1_j1772
})

internal fun ManualHotspotValidation.Error.messageResource(): Int = when (this) {
    ManualHotspotValidation.Error.EMPTY_NAME -> R.string.hotspot_error_empty_name
    ManualHotspotValidation.Error.LONG_NAME -> R.string.hotspot_error_long_name
    ManualHotspotValidation.Error.INVALID_CHARACTER -> R.string.hotspot_error_invalid_character
    ManualHotspotValidation.Error.PASSWORD_LENGTH -> R.string.hotspot_error_password_length
}
