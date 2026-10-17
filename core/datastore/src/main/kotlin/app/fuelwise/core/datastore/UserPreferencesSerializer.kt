package app.fuelwise.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class UserPreferencesSerializer @Inject constructor() : Serializer<UserPreferences> {
    /** First-run defaults: the monthly recap is on, everything else off (design.md screen 4). */
    override val defaultValue: UserPreferences = userPreferences { monthlyRecap = true }

    override suspend fun readFrom(input: InputStream): UserPreferences = try {
        UserPreferences.parseFrom(input)
    } catch (e: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read user preferences", e)
    }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) = t.writeTo(output)
}
