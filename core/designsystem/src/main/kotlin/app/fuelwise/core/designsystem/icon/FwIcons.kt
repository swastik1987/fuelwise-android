package app.fuelwise.core.designsystem.icon

import androidx.annotation.DrawableRes
import app.fuelwise.core.designsystem.R

/**
 * Material Symbols Rounded, weight 300 (docs/design.md §4.4), bundled as vector drawables.
 * `*Filled` variants are only for the active bottom-nav item. Add new icons to res/drawable as `fw_ic_<name>.xml`.
 */
object FwIcons {
    @DrawableRes val Home = R.drawable.fw_ic_home

    @DrawableRes val HomeFilled = R.drawable.fw_ic_home_filled

    @DrawableRes val Logbook = R.drawable.fw_ic_receipt_long

    @DrawableRes val LogbookFilled = R.drawable.fw_ic_receipt_long_filled

    @DrawableRes val Insights = R.drawable.fw_ic_insights

    @DrawableRes val InsightsFilled = R.drawable.fw_ic_insights_filled

    @DrawableRes val Garage = R.drawable.fw_ic_garage_home

    @DrawableRes val GarageFilled = R.drawable.fw_ic_garage_home_filled

    @DrawableRes val Add = R.drawable.fw_ic_add

    @DrawableRes val Info = R.drawable.fw_ic_info

    @DrawableRes val ExpandMore = R.drawable.fw_ic_expand_more

    @DrawableRes val ChevronRight = R.drawable.fw_ic_chevron_right

    @DrawableRes val Close = R.drawable.fw_ic_close

    @DrawableRes val ArrowBack = R.drawable.fw_ic_arrow_back

    @DrawableRes val Notifications = R.drawable.fw_ic_notifications

    @DrawableRes val Visibility = R.drawable.fw_ic_visibility

    @DrawableRes val VisibilityOff = R.drawable.fw_ic_visibility_off

    @DrawableRes val Settings = R.drawable.fw_ic_settings

    @DrawableRes val Fuel = R.drawable.fw_ic_local_gas_station

    @DrawableRes val Charge = R.drawable.fw_ic_ev_station

    @DrawableRes val Toll = R.drawable.fw_ic_toll

    @DrawableRes val Service = R.drawable.fw_ic_build
}
