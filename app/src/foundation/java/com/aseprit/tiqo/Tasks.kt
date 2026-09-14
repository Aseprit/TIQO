package com.aseprit.tiqo

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "tasks", indices = [Index("date")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: Long,
    val completed: Boolean = false,
    val locked: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY completed, createdAt, id")
    fun observeDay(date: Long): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE date BETWEEN :start AND :end ORDER BY date, createdAt, id LIMIT :limit")
    suspend fun range(start: Long, end: Long, limit: Int = 81): List<Task>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?
    @Insert suspend fun insert(task: Task): Long
    @Update suspend fun update(task: Task): Int
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    suspend fun complete(id: Long, completed: Boolean)
}

@Database(entities = [Task::class], version = 3, exportSchema = true)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    companion object {
        val MIGRATION_1_3 = migrateLegacy(1)
        val MIGRATION_2_3 = migrateLegacy(2)
        private fun migrateLegacy(from: Int) = object : Migration(from, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, date INTEGER NOT NULL, completed INTEGER NOT NULL, locked INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_date ON tasks(date)")
                val hasLegacy = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='planner_items'").use { it.moveToFirst() }
                if (hasLegacy) db.execSQL("""INSERT OR IGNORE INTO tasks (id,title,date,completed,locked,createdAt)
                    SELECT p.id,p.title,p.startDateEpochDay,
                    EXISTS(SELECT 1 FROM occurrence_completions c WHERE c.itemId=p.id AND c.occurrenceDateEpochDay=p.startDateEpochDay),
                    1,p.createdAtMillis FROM planner_items p WHERE p.type='TASK' AND p.recurrenceType='NONE'""")
                // Legacy tables remain intact; this version exposes only one-off Tasks.
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context, TaskDatabase::class.java, "tiqo.db")
            .addMigrations(MIGRATION_1_3, MIGRATION_2_3).build()
    }
}

class TaskRepository(val db: TaskDatabase) {
    val dao = db.tasks()
    suspend fun saveManual(id: Long?, title: String, date: LocalDate) {
        require(title.isNotBlank() && title.trim().length <= 300) { "Tytuł: od 1 do 300 znaków" }
        db.withTransaction {
            if (id == null) dao.insert(Task(title = title.trim(), date = date.toEpochDay(), locked = true))
            else {
                val old = requireNotNull(dao.get(id)) { "Zadanie już nie istnieje" }
                dao.update(old.copy(title = title.trim(), date = date.toEpochDay()))
            }
        }
    }
}
