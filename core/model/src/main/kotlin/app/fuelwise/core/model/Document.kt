package app.fuelwise.core.model

/** Document types (PRD F-12). */
enum class DocumentType { Rc, Insurance, Puc, Licence, Fastag, Warranty, ServiceInvoice, Loan, Other }

enum class PolicyType { Comprehensive, ThirdParty, OwnDamage }

/** Insurance extras (PRD F-12). IDV is money, so paise. */
data class InsuranceDetails(
    val insurer: String? = null,
    val policyType: PolicyType? = null,
    val idv: Money? = null,
    val ncbPercent: Int? = null,
    val addOns: String? = null,
)

/**
 * A personal reference copy of a vehicle or driver document. Files are encrypted at rest (Tink AEAD); legally
 * valid digital copies are the ones in DigiLocker / mParivahan.
 */
data class Document(
    val id: String,
    /** Null for driver documents such as a licence. */
    val vehicleId: String?,
    val type: DocumentType,
    val number: String? = null,
    val issuer: String? = null,
    val issueDateUtcMs: Long? = null,
    val expiryDateUtcMs: Long? = null,
    val notes: String? = null,
    val insurance: InsuranceDetails? = null,
    /** Keep a reminder in step with [expiryDateUtcMs] (PRD F-11.5). */
    val remindOnExpiry: Boolean = true,
)

/**
 * A photo or PDF attached to a document, an expense, a trip or a fill-up. [storedName] is the encrypted file in app
 * storage.
 */
data class Attachment(
    val id: String,
    val ownerId: String,
    val ownerType: OwnerType,
    val storedName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Of the plaintext, for content-addressed backups (M7). */
    val sha256: String,
) {
    /**
     * [Trip] holds a GPS trip's simplified route (PRD F-07.2); [Refuel] a pump or receipt scan photo (PRD F-15);
     * [Vehicle] the vehicle's photo (PRD F-01.1).
     */
    enum class OwnerType { Document, Expense, Trip, Refuel, Vehicle }
}
