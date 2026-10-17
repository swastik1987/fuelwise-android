package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import app.fuelwise.core.database.model.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE deletedAt IS NULL ORDER BY sortOrder")
    fun observeCategories(): Flow<List<CategoryEntity>>
}
