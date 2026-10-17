package app.fuelwise.core.ui.care

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.PolicyType
import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.RepeatUnit
import app.fuelwise.core.ui.R

/** Names and icons for reminders and documents, shared by their screens, Home, Garage and notifications. */
object CareLabels {
    @StringRes
    fun reminder(kind: ReminderKind): Int = when (kind) {
        ReminderKind.Insurance -> R.string.reminder_insurance
        ReminderKind.Puc -> R.string.reminder_puc
        ReminderKind.Service -> R.string.reminder_service
        ReminderKind.EngineOil -> R.string.reminder_engine_oil
        ReminderKind.Tyres -> R.string.reminder_tyres
        ReminderKind.Battery -> R.string.reminder_battery
        ReminderKind.Coolant -> R.string.reminder_coolant
        ReminderKind.Wipers -> R.string.reminder_wipers
        ReminderKind.Rc -> R.string.reminder_rc
        ReminderKind.Fitness -> R.string.reminder_fitness
        ReminderKind.Licence -> R.string.reminder_licence
        ReminderKind.Warranty -> R.string.reminder_warranty
        ReminderKind.FastagLowBalance -> R.string.reminder_fastag
        ReminderKind.Custom -> R.string.reminder_custom
    }

    @DrawableRes
    fun reminderIcon(kind: ReminderKind): Int = when (kind) {
        ReminderKind.Insurance -> FwIcons.Shield
        ReminderKind.Puc -> FwIcons.Eco
        ReminderKind.Service, ReminderKind.EngineOil, ReminderKind.Coolant -> FwIcons.Service
        ReminderKind.Tyres -> FwIcons.Tyres
        ReminderKind.Battery -> FwIcons.BatteryFull
        ReminderKind.Wipers -> FwIcons.CarWash
        ReminderKind.Rc, ReminderKind.Fitness -> FwIcons.Document
        ReminderKind.Licence -> FwIcons.CreditCard
        ReminderKind.Warranty -> FwIcons.Verified
        ReminderKind.FastagLowBalance -> FwIcons.Toll
        ReminderKind.Custom -> FwIcons.Schedule
    }

    @StringRes
    fun document(type: DocumentType): Int = when (type) {
        DocumentType.Rc -> R.string.document_rc
        DocumentType.Insurance -> R.string.document_insurance
        DocumentType.Puc -> R.string.document_puc
        DocumentType.Licence -> R.string.document_licence
        DocumentType.Fastag -> R.string.document_fastag
        DocumentType.Warranty -> R.string.document_warranty
        DocumentType.ServiceInvoice -> R.string.document_service_invoice
        DocumentType.Loan -> R.string.document_loan
        DocumentType.Other -> R.string.document_other
    }

    @DrawableRes
    fun documentIcon(type: DocumentType): Int = when (type) {
        DocumentType.Rc -> FwIcons.Document
        DocumentType.Insurance -> FwIcons.Shield
        DocumentType.Puc -> FwIcons.Eco
        DocumentType.Licence -> FwIcons.CreditCard
        DocumentType.Fastag -> FwIcons.Toll
        DocumentType.Warranty -> FwIcons.Verified
        DocumentType.ServiceInvoice -> FwIcons.Service
        DocumentType.Loan -> FwIcons.Bank
        DocumentType.Other -> FwIcons.UploadFile
    }

    @StringRes
    fun policy(type: PolicyType): Int = when (type) {
        PolicyType.Comprehensive -> R.string.policy_comprehensive
        PolicyType.ThirdParty -> R.string.policy_third_party
        PolicyType.OwnDamage -> R.string.policy_own_damage
    }

    @StringRes
    fun repeatUnit(unit: RepeatUnit): Int = when (unit) {
        RepeatUnit.Days -> R.string.repeat_days
        RepeatUnit.Weeks -> R.string.repeat_weeks
        RepeatUnit.Months -> R.string.repeat_months
        RepeatUnit.Years -> R.string.repeat_years
    }
}
