package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.fuelwise.core.database.model.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE deletedAt IS NULL ORDER BY sortOrder")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id AND deletedAt IS NULL")
    suspend fun category(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM category")
    suspend fun maxSortOrder(): Int

    /** Expenses and recurring expenses still using the category: a used category can only be hidden (PRD F-04.2). */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM expense WHERE categoryId = :id AND deletedAt IS NULL) +
               (SELECT COUNT(*) FROM recurrence WHERE categoryId = :id AND deletedAt IS NULL)
        """,
    )
    suspend fun usage(id: String): Int

    @Query(
        """
        UPDATE category SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL AND systemKey IS NULL
        """,
    )
    suspend fun tombstoneCustom(id: String, at: Long, deviceId: String)
}
