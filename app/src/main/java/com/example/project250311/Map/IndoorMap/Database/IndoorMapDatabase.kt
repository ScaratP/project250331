package com.example.project250311.Map.IndoorMap.Database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.project250311.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// 新增：室內點位領域模型與型別（供 Entity 映射與 Repository 回傳使用）
enum class PointType {
    ENTRANCE,
    CLASSROOM,
    CORRIDOR,
    STAIRS,
    ELEVATOR,
    TOILET,
    OTHER
}

data class ReferencePoint(
        val id: String,
        val name: String,
        val x: Double,
        val y: Double,
        val imageId: Int,
        val scanCount: Int = 0,
        val type: PointType = PointType.OTHER,
        val connectedCorridorIds: List<String> = emptyList()
)

// 實體類定義
@Entity(tableName = "buildings")
data class BuildingEntity(
        @PrimaryKey val id: String,
        val name: String,
        val description: String,
        val entranceX: Double,
        val entranceY: Double,
        val entranceFloorId: Int,
        val entranceImageId: Int
)

@Entity(
        tableName = "floors",
        foreignKeys =
                [
                        ForeignKey(
                                entity = BuildingEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["buildingId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices = [Index("buildingId")]
)
data class FloorEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val buildingId: String,
        val floorNumber: Int,
        val name: String,
        val imageId: Int
)

@Entity(
        tableName = "reference_points",
        foreignKeys =
                [
                        ForeignKey(
                                entity = BuildingEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["buildingId"],
                                onDelete = ForeignKey.CASCADE
                        ),
                        ForeignKey(
                                entity = FloorEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["floorId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices = [Index("buildingId"), Index("floorId")]
)
data class ReferencePointEntity(
        @PrimaryKey val id: String,
        val name: String,
        val x: Double,
        val y: Double,
        val imageId: Int,
        val scanCount: Int = 0,
        val type: String,
        val buildingId: String,
        val floorId: Int,
        val isUserDefined: Boolean = false
) {
    fun toReferencePoint(): ReferencePoint {
        return ReferencePoint(
                id = id,
                name = name,
                x = x,
                y = y,
                imageId = imageId,
                scanCount = scanCount,
                type =
                        try {
                            PointType.valueOf(type)
                        } catch (e: IllegalArgumentException) {
                            PointType.OTHER
                        },
                connectedCorridorIds = emptyList()
        )
    }

    companion object {
        fun fromReferencePoint(
                point: ReferencePoint,
                buildingId: String,
                floorId: Int,
                isUserDefined: Boolean = false
        ): ReferencePointEntity {
            return ReferencePointEntity(
                    id = point.id,
                    name = point.name,
                    x = point.x,
                    y = point.y,
                    imageId = point.imageId,
                    scanCount = point.scanCount,
                    type = point.type.name,
                    buildingId = buildingId,
                    floorId = floorId,
                    isUserDefined = isUserDefined
            )
        }
    }
}

@Entity(
        tableName = "corridor_vectors",
        foreignKeys =
                [
                        ForeignKey(
                                entity = BuildingEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["buildingId"],
                                onDelete = ForeignKey.CASCADE
                        ),
                        ForeignKey(
                                entity = FloorEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["floorId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices = [Index("buildingId"), Index("floorId")]
)
data class CorridorVectorEntity(
        @PrimaryKey val id: String,
        val buildingId: String,
        val floorId: Int,
        val startX: Double,
        val startY: Double,
        val endX: Double,
        val endY: Double,
        val label: String
)

@Entity(
        tableName = "area_connectivity",
        foreignKeys =
                [
                        ForeignKey(
                                entity = BuildingEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["buildingId"],
                                onDelete = ForeignKey.CASCADE
                        ),
                        ForeignKey(
                                entity = FloorEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["floorId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices = [Index("buildingId"), Index("floorId")]
)
data class AreaConnectivityEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val buildingId: String,
        val floorId: Int,
        val areaStartX: Double,
        val areaStartY: Double,
        val areaEndX: Double,
        val areaEndY: Double
)

// 快取實體
@Entity(tableName = "grid_cache", primaryKeys = ["imageId", "sample"])
data class GridCacheEntity(
        val imageId: Int,
        val sample: Int,
        val width: Int,
        val height: Int,
        val cells: ByteArray // bit-packed boolean array
)

// DAO 接口定義
@Dao
interface BuildingDao {
    @Query("SELECT * FROM buildings") fun getAllBuildings(): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings WHERE id = :id")
    suspend fun getBuildingById(id: String): BuildingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuilding(building: BuildingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBuildings(buildings: List<BuildingEntity>)
}

@Dao
interface FloorDao {
    @Query("SELECT * FROM floors WHERE buildingId = :buildingId")
    fun getFloorsByBuilding(buildingId: String): Flow<List<FloorEntity>>

    @Query("SELECT * FROM floors WHERE id = :id") suspend fun getFloorById(id: Int): FloorEntity?

    @Query("SELECT * FROM floors WHERE buildingId = :buildingId AND floorNumber = :floorNumber")
    suspend fun getFloorByBuildingAndNumber(buildingId: String, floorNumber: Int): FloorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFloor(floor: FloorEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFloors(floors: List<FloorEntity>): List<Long>
}

@Dao
interface ReferencePointDao {
    @Query("SELECT * FROM reference_points")
    fun getAllReferencePoints(): Flow<List<ReferencePointEntity>>

    @Query("SELECT * FROM reference_points WHERE imageId = :imageId")
    fun getReferencePointsByImageId(imageId: Int): Flow<List<ReferencePointEntity>>

    @Query("SELECT * FROM reference_points WHERE buildingId = :buildingId AND floorId = :floorId")
    fun getReferencePointsByFloor(
            buildingId: String,
            floorId: Int
    ): Flow<List<ReferencePointEntity>>

    @Query("SELECT * FROM reference_points WHERE name LIKE :nameQuery")
    fun searchReferencePointsByName(nameQuery: String): Flow<List<ReferencePointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferencePoint(referencePoint: ReferencePointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReferencePoints(referencePoints: List<ReferencePointEntity>)

    @Update suspend fun updateReferencePoint(referencePoint: ReferencePointEntity)

    @Query("DELETE FROM reference_points WHERE id = :id")
    suspend fun deleteReferencePointById(id: String)

    @Query("SELECT * FROM reference_points WHERE type = 'CLASSROOM'")
    fun getClassroomPoints(): Flow<List<ReferencePointEntity>>

    @Query("SELECT * FROM reference_points WHERE imageId = :imageId AND type = 'CLASSROOM'")
    fun getClassroomPointsByImageId(imageId: Int): Flow<List<ReferencePointEntity>>

    @Query(
            "SELECT * FROM reference_points " +
                    "WHERE buildingId = :buildingId AND floorId = :floorId AND type = 'CLASSROOM'"
    )
    fun getClassroomPointsByFloor(
            buildingId: String,
            floorId: Int
    ): Flow<List<ReferencePointEntity>>

    // 可用於偵測是否需要補資料（目前改為每次 onOpen 都回填，保留此API以備未來需求）
    @Query("SELECT COUNT(*) FROM reference_points WHERE type = 'CLASSROOM'")
    suspend fun countClassrooms(): Int
}

@Dao
interface CorridorVectorDao {
    @Query("SELECT * FROM corridor_vectors WHERE buildingId = :buildingId AND floorId = :floorId")
    fun getCorridorVectorsByFloor(
            buildingId: String,
            floorId: Int
    ): Flow<List<CorridorVectorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorridorVector(corridor: CorridorVectorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCorridorVectors(corridors: List<CorridorVectorEntity>)
}

@Dao
interface AreaConnectivityDao {
    @Query("SELECT * FROM area_connectivity WHERE buildingId = :buildingId AND floorId = :floorId")
    fun getAreasByFloor(buildingId: String, floorId: Int): Flow<List<AreaConnectivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArea(area: AreaConnectivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAreas(areas: List<AreaConnectivityEntity>): List<Long>
}

@Dao
interface GridCacheDao {
    @Query("SELECT * FROM grid_cache WHERE imageId = :imageId AND sample = :sample LIMIT 1")
    suspend fun get(imageId: Int, sample: Int): GridCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: GridCacheEntity)

    @Query("DELETE FROM grid_cache WHERE imageId = :imageId AND sample = :sample")
    suspend fun delete(imageId: Int, sample: Int)
}

// 資料庫類
@Database(
        entities =
                [
                        BuildingEntity::class,
                        FloorEntity::class,
                        ReferencePointEntity::class,
                        CorridorVectorEntity::class,
                        AreaConnectivityEntity::class,
                        GridCacheEntity::class],
        version = 2, // 由 1 調升至 2
        exportSchema = false
)
abstract class IndoorMapDatabase : RoomDatabase() {
    abstract fun buildingDao(): BuildingDao
    abstract fun floorDao(): FloorDao
    abstract fun referencePointDao(): ReferencePointDao
    abstract fun corridorVectorDao(): CorridorVectorDao
    abstract fun areaConnectivityDao(): AreaConnectivityDao
    abstract fun gridCacheDao(): GridCacheDao

    companion object {
        @Volatile private var INSTANCE: IndoorMapDatabase? = null

        // 新增：1→2 遷移，建立 grid_cache 表
        private val MIGRATION_1_2 =
                object : Migration(1, 2) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                                """
                    CREATE TABLE IF NOT EXISTS `grid_cache` (
                        `imageId` INTEGER NOT NULL,
                        `sample` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `cells` BLOB NOT NULL,
                        PRIMARY KEY(`imageId`, `sample`)
                    )
                    """.trimIndent()
                        )
                    }
                }

        fun getDatabase(context: Context): IndoorMapDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                IndoorMapDatabase::class.java,
                                                "indoor_map_database"
                                        )
                                        .addMigrations(MIGRATION_1_2) // 註冊遷移
                                        .addCallback(IndoorMapDatabaseCallback(context))
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }

    private class IndoorMapDatabaseCallback(private val context: Context) :
            RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch { prepopulateDatabase(database) }
            }
        }

        // 新增：每次開啟資料庫都執行回填，修正舊DB缺資料或 imageId 不匹配
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch { backfillOnOpen(database) }
            }
        }

        private suspend fun prepopulateDatabase(database: IndoorMapDatabase) {
            // 1. 添加建築物
            val building =
                    BuildingEntity(
                            id = "SE",
                            name = "理工學院",
                            description = "綜合教學大樓",
                            entranceX = 36.21,
                            entranceY = 68.26,
                            entranceFloorId = 1,
                            entranceImageId = R.drawable.se1
                    )
            database.buildingDao().insertBuilding(building)

            // 新增：SEB、SEC 建築
            val buildingSEB =
                    BuildingEntity(
                            id = "SEB",
                            name = "理工學院B棟",
                            description = "B棟",
                            entranceX = 50.0,
                            entranceY = 50.0,
                            entranceFloorId = 4,
                            entranceImageId = R.drawable.seb4
                    )
            val buildingSEC =
                    BuildingEntity(
                            id = "SEC",
                            name = "理工學院C棟",
                            description = "C棟",
                            entranceX = 50.0,
                            entranceY = 50.0,
                            entranceFloorId = 4,
                            entranceImageId = R.drawable.sec4
                    )
            database.buildingDao().insertAllBuildings(listOf(buildingSEB, buildingSEC))

            // 2. 添加樓層（SE 1~5）
            val floors =
                    listOf(
                            FloorEntity(
                                    id = 1,
                                    buildingId = "SE",
                                    floorNumber = 1,
                                    name = "1樓",
                                    imageId = R.drawable.se1
                            ),
                            FloorEntity(
                                    id = 2,
                                    buildingId = "SE",
                                    floorNumber = 2,
                                    name = "2樓",
                                    imageId = R.drawable.se2
                            ),
                            FloorEntity(
                                    id = 3,
                                    buildingId = "SE",
                                    floorNumber = 3,
                                    name = "3樓",
                                    imageId = R.drawable.se3
                            ),
                            FloorEntity(
                                    id = 4,
                                    buildingId = "SE",
                                    floorNumber = 4,
                                    name = "4樓",
                                    imageId = R.drawable.sea4
                            ),
                            FloorEntity(
                                    id = 5,
                                    buildingId = "SE",
                                    floorNumber = 5,
                                    name = "5樓",
                                    imageId = R.drawable.sea5
                            )
                    )
            database.floorDao().insertAllFloors(floors)

            // 新增：SEB 4F、SEC 4F/5F
            val extraFloors =
                    listOf(
                            FloorEntity(
                                    buildingId = "SEB",
                                    floorNumber = 4,
                                    name = "B棟4樓",
                                    imageId = R.drawable.seb4
                            ),
                            FloorEntity(
                                    buildingId = "SEC",
                                    floorNumber = 4,
                                    name = "C棟4樓",
                                    imageId = R.drawable.sec4
                            ),
                            FloorEntity(
                                    buildingId = "SEC",
                                    floorNumber = 5,
                                    name = "C棟5樓",
                                    imageId = R.drawable.sec5
                            )
                    )
            database.floorDao().insertAllFloors(extraFloors)

            // 3. 創建理工學院入口參考點
            val entrancePoint =
                    ReferencePointEntity(
                            id = "entrance_se",
                            name = "理工學院入口",
                            x = 36.21,
                            y = 68.26,
                            imageId = R.drawable.se1,
                            scanCount = 0,
                            type = "ENTRANCE",
                            buildingId = "SE",
                            floorId = 1
                    )
            database.referencePointDao().insertReferencePoint(entrancePoint)

            // 4. 匯入教室點（改由 raw 檔案解析）
            importClassroomPointsFromRaw(context, database)

            // 5. 添加走廊向量
            val corridorVectors = getDefaultCorridorVectors()
            database.corridorVectorDao().insertAllCorridorVectors(corridorVectors)

            // 6. 添加區域連通性
            val areas = getDefaultAreas()
            database.areaConnectivityDao().insertAllAreas(areas)
        }

        // 新增：每次開啟資料庫都執行回填，修正舊DB缺資料或 imageId 不匹配
        private suspend fun backfillOnOpen(database: IndoorMapDatabase) {
            // 1) 建築 SEB/SEC 若不存在則插入（REPLACE 保險）
            val buildingSEB =
                    BuildingEntity(
                            id = "SEB",
                            name = "理工學院B棟",
                            description = "B棟",
                            entranceX = 50.0,
                            entranceY = 50.0,
                            entranceFloorId = 4,
                            entranceImageId = R.drawable.seb4
                    )
            val buildingSEC =
                    BuildingEntity(
                            id = "SEC",
                            name = "理工學院C棟",
                            description = "C棟",
                            entranceX = 50.0,
                            entranceY = 50.0,
                            entranceFloorId = 4,
                            entranceImageId = R.drawable.sec4
                    )
            database.buildingDao().insertAllBuildings(listOf(buildingSEB, buildingSEC))

            // 2) 樓層若不存在就補（查無才插入）
            suspend fun ensureFloor(bid: String, num: Int, name: String, imageId: Int) {
                val exist = database.floorDao().getFloorByBuildingAndNumber(bid, num)
                if (exist == null) {
                    database.floorDao()
                            .insertFloor(
                                    FloorEntity(
                                            buildingId = bid,
                                            floorNumber = num,
                                            name = name,
                                            imageId = imageId
                                    )
                            )
                }
            }
            // SE 1~5
            ensureFloor("SE", 1, "1樓", R.drawable.se1)
            ensureFloor("SE", 2, "2樓", R.drawable.se2)
            ensureFloor("SE", 3, "3樓", R.drawable.se3)
            ensureFloor("SE", 4, "4樓", R.drawable.sea4)
            ensureFloor("SE", 5, "5樓", R.drawable.sea5)
            // SEB 4、SEC 4/5
            ensureFloor("SEB", 4, "B棟4樓", R.drawable.seb4)
            ensureFloor("SEC", 4, "C棟4樓", R.drawable.sec4)
            ensureFloor("SEC", 5, "C棟5樓", R.drawable.sec5)

            // 3) 重新匯入所有教室點（REPLACE upsert，修正舊 imageId 與缺漏）
            importClassroomPointsFromRaw(context, database)
        }

        // 由 res/raw/reference_points_output.txt 解析教室點並寫入 DB
        private suspend fun importClassroomPointsFromRaw(
                context: Context,
                database: IndoorMapDatabase
        ) {
            // 改為：圖片 -> (建築ID, 樓層)
            val allowedImageToBuildingFloor =
                    mapOf(
                            // SE 1~3
                            "se1" to ("SE" to 1),
                            "se2" to ("SE" to 2),
                            "se3" to ("SE" to 3),
                            // SE(A棟) 4~5
                            "sea4" to ("SE" to 4),
                            "sea5" to ("SE" to 5),
                            // SEB(B棟) 4
                            "seb4" to ("SEB" to 4),
                            // SEC(C棟) 4~5
                            "sec4" to ("SEC" to 4),
                            "sec5" to ("SEC" to 5)
                    )
            val pattern =
                    Regex(
                            """ReferencePointEntity\("([^"]+)",\s*"([^"]+)",\s*([-0-9.]+),\s*([-0-9.]+),\s*R\.drawable\.([A-Za-z0-9_]+),\s*\d+,\s*"([A-Z_]+)""""
                    )

            val imported = mutableListOf<ReferencePointEntity>()
            val resId = R.raw.reference_points_output
            context.resources.openRawResource(resId).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val m = pattern.find(line) ?: return@forEach
                    val (id, name, xStr, yStr, imageName, type) = m.destructured
                    if (type != "CLASSROOM") return@forEach

                    val buildingFloor = allowedImageToBuildingFloor[imageName] ?: return@forEach
                    val (buildingId, floorNumber) = buildingFloor

                    val imageId =
                            context.resources.getIdentifier(
                                    imageName,
                                    "drawable",
                                    context.packageName
                            )
                    if (imageId == 0) return@forEach

                    val floorEntity =
                            database.floorDao().getFloorByBuildingAndNumber(buildingId, floorNumber)
                                    ?: return@forEach

                    imported +=
                            ReferencePointEntity(
                                    id = id,
                                    name = name,
                                    x = xStr.toDoubleOrNull() ?: return@forEach,
                                    y = yStr.toDoubleOrNull() ?: return@forEach,
                                    imageId = imageId,
                                    scanCount = 0,
                                    type = "CLASSROOM",
                                    buildingId = buildingId, // 關鍵：使用對應建築
                                    floorId = floorEntity.id, // 關鍵：使用該建築 + 樓層的 floorId
                                    isUserDefined = false
                            )
                }
            }

            if (imported.isNotEmpty()) {
                database.referencePointDao().insertAllReferencePoints(imported)
            }
        }

        // 保留：原本的預設教室資料方法，但不再呼叫
        private fun getDefaultClassroomPoints(): List<ReferencePointEntity> {
            // 將所有教室資料轉換為實體
            return listOf(
                    // 1樓教室
                    ReferencePointEntity(
                            "classroom_sec112",
                            "sec112",
                            31.297901153564453,
                            83.31011199951172,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec111f",
                            "sec111f",
                            42.17558670043945,
                            82.67748260498047,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec111b",
                            "sec111b",
                            43.05496597290039,
                            88.41331481933594,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec110f",
                            "sec110f",
                            47.11360168457031,
                            81.99949645996094,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec101",
                            "sec101",
                            53.858638763427734,
                            79.76398468017578,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec110b",
                            "sec110b",
                            55.860633850097656,
                            81.97007751464844,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec109",
                            "sec109",
                            57.482391357421875,
                            81.64453125,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec102f",
                            "sec102f",
                            56.883663177490234,
                            79.9090805053711,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec108",
                            "sec108",
                            60.943782806396484,
                            81.4974594116211,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "classroom_sec102b",
                            "sec102b",
                            63.0668830871582,
                            79.71481323242188,
                            R.drawable.se1,
                            0,
                            "CLASSROOM",
                            "SE",
                            1
                    ),
                    // ... 可以繼續添加更多教室資料

                    // 走廊與設施點位
                    ReferencePointEntity(
                            "corridor_1f_north",
                            "1F-走廊-北",
                            36.5,
                            30.0,
                            R.drawable.se1,
                            0,
                            "CORRIDOR",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "corridor_1f_center",
                            "1F-走廊-中",
                            50.0,
                            50.0,
                            R.drawable.se1,
                            0,
                            "CORRIDOR",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "corridor_1f_south",
                            "1F-走廊-南",
                            65.0,
                            72.0,
                            R.drawable.se1,
                            0,
                            "CORRIDOR",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "stairs_1f_1",
                            "1F-樓梯-1",
                            40.0,
                            35.0,
                            R.drawable.se1,
                            0,
                            "STAIRS",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "elevator_1f",
                            "1F-電梯",
                            60.0,
                            35.0,
                            R.drawable.se1,
                            0,
                            "ELEVATOR",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "toilet_1f_male",
                            "1F-廁所-男",
                            30.0,
                            40.0,
                            R.drawable.se1,
                            0,
                            "TOILET",
                            "SE",
                            1
                    ),
                    ReferencePointEntity(
                            "toilet_1f_female",
                            "1F-廁所-女",
                            30.0,
                            60.0,
                            R.drawable.se1,
                            0,
                            "TOILET",
                            "SE",
                            1
                    )
            )
        }

        private fun getDefaultCorridorVectors(): List<CorridorVectorEntity> {
            return listOf(
                    CorridorVectorEntity(
                            "corridor_1f_main",
                            "SE",
                            1,
                            20.0,
                            50.0,
                            80.0,
                            50.0,
                            "1樓主走廊"
                    ),
                    CorridorVectorEntity(
                            "corridor_1f_vertical",
                            "SE",
                            1,
                            50.0,
                            30.0,
                            50.0,
                            70.0,
                            "1樓垂直走廊"
                    ),
                    CorridorVectorEntity(
                            "corridor_2f_main",
                            "SE",
                            2,
                            20.0,
                            50.0,
                            80.0,
                            50.0,
                            "2樓主走廊"
                    ),
                    CorridorVectorEntity(
                            "corridor_3f_main",
                            "SE",
                            3,
                            20.0,
                            50.0,
                            80.0,
                            50.0,
                            "3樓主走廊"
                    ),
                    CorridorVectorEntity(
                            "corridor_4f_main",
                            "SE",
                            4,
                            20.0,
                            50.0,
                            80.0,
                            50.0,
                            "4樓主走廊"
                    ),
                    CorridorVectorEntity(
                            "corridor_5f_main",
                            "SE",
                            5,
                            20.0,
                            50.0,
                            80.0,
                            50.0,
                            "5樓主走廊"
                    )
            )
        }

        private fun getDefaultAreas(): List<AreaConnectivityEntity> {
            return listOf(
                    AreaConnectivityEntity(0, "SE", 1, 20.0, 40.0, 80.0, 60.0),
                    AreaConnectivityEntity(0, "SE", 2, 20.0, 40.0, 80.0, 60.0),
                    AreaConnectivityEntity(0, "SE", 3, 20.0, 40.0, 80.0, 60.0),
                    AreaConnectivityEntity(0, "SE", 4, 20.0, 40.0, 80.0, 60.0),
                    AreaConnectivityEntity(0, "SE", 5, 20.0, 40.0, 80.0, 60.0)
            )
        }
    }
}

// 資料庫存儲庫
class IndoorMapRepository(private val context: Context) {
    private val database = IndoorMapDatabase.getDatabase(context)
    private val referencePointDao = database.referencePointDao()
    private val buildingDao = database.buildingDao()
    private val floorDao = database.floorDao()
    private val corridorVectorDao = database.corridorVectorDao()
    private val areaConnectivityDao = database.areaConnectivityDao()
    private val gridCacheDao = database.gridCacheDao()

    // 參考點相關
    fun getAllReferencePoints(): Flow<List<ReferencePoint>> {
        return referencePointDao.getAllReferencePoints().map { entities ->
            entities.map { it.toReferencePoint() }
        }
    }

    fun getReferencePointsByImageId(imageId: Int): Flow<List<ReferencePoint>> {
        return referencePointDao.getReferencePointsByImageId(imageId).map { entities ->
            entities.map { it.toReferencePoint() }
        }
    }

    fun getReferencePointsByFloor(buildingId: String, floorId: Int): Flow<List<ReferencePoint>> {
        return referencePointDao.getReferencePointsByFloor(buildingId, floorId).map { entities ->
            entities.map { it.toReferencePoint() }
        }
    }

    suspend fun addReferencePoint(point: ReferencePoint, buildingId: String, floorId: Int) {
        val entity = ReferencePointEntity.fromReferencePoint(point, buildingId, floorId, true)
        referencePointDao.insertReferencePoint(entity)
    }

    suspend fun updateReferencePoint(point: ReferencePoint, buildingId: String, floorId: Int) {
        val entity = ReferencePointEntity.fromReferencePoint(point, buildingId, floorId)
        referencePointDao.updateReferencePoint(entity)
    }

    suspend fun deleteReferencePoint(id: String) {
        referencePointDao.deleteReferencePointById(id)
    }

    fun searchReferencePointsByName(query: String): Flow<List<ReferencePoint>> {
        return referencePointDao.searchReferencePointsByName("%$query%").map { entities ->
            entities.map { it.toReferencePoint() }
        }
    }

    // 建築物相關
    fun getAllBuildings(): Flow<List<BuildingEntity>> {
        return buildingDao.getAllBuildings()
    }

    suspend fun getBuildingById(id: String): BuildingEntity? {
        return buildingDao.getBuildingById(id)
    }

    // 樓層相關
    fun getFloorsByBuilding(buildingId: String): Flow<List<FloorEntity>> {
        return floorDao.getFloorsByBuilding(buildingId)
    }

    // 走廊向量相關
    fun getCorridorVectorsByFloor(
            buildingId: String,
            floorId: Int
    ): Flow<List<CorridorVectorEntity>> {
        return corridorVectorDao.getCorridorVectorsByFloor(buildingId, floorId)
    }

    // 區域連通性相關
    fun getAreasByFloor(buildingId: String, floorId: Int): Flow<List<AreaConnectivityEntity>> {
        return areaConnectivityDao.getAreasByFloor(buildingId, floorId)
    }

    // 只取教室點（全部）
    fun getClassroomPoints(): Flow<List<ReferencePoint>> {
        return referencePointDao.getClassroomPoints().map { entities ->
            entities.map { it.toReferencePoint() }
        }
    }

    // 只取教室點（依影像資源 id，可用於 UI 的樓層圖切換）
    fun getClassroomPointsByImageId(imageId: Int): Flow<List<ReferencePoint>> {
        return referencePointDao.getClassroomPointsByImageId(imageId).map { entities ->
            entities.map { it.toReferencePoint() }
        }
    }

    // 只取教室點（依建築/樓層）
    fun getClassroomPointsByFloor(buildingId: String, floorId: Int): Flow<List<ReferencePoint>> {
        return referencePointDao.getClassroomPointsByFloor(buildingId, floorId).map { entities ->
            entities.map { it.toReferencePoint() }
        }
    }
}
