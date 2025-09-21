//package com.example.project250311.Map.IndoorMap.Database
//
//import android.content.Context
//import androidx.room.*
//import androidx.sqlite.db.SupportSQLiteDatabase
////import com.example.project250311.Map.IndoorMap.PointType
////import com.example.project250311.Map.IndoorMap.ReferencePoint
//import com.example.project250311.R
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.map
//import kotlinx.coroutines.launch
//
//// 實體類定義
//@Entity(tableName = "buildings")
//data class BuildingEntity(
//        @PrimaryKey val id: String,
//        val name: String,
//        val description: String,
//        val entranceX: Double,
//        val entranceY: Double,
//        val entranceFloorId: Int,
//        val entranceImageId: Int
//)
//
//@Entity(
//        tableName = "floors",
//        foreignKeys =
//                [
//                        ForeignKey(
//                                entity = BuildingEntity::class,
//                                parentColumns = ["id"],
//                                childColumns = ["buildingId"],
//                                onDelete = ForeignKey.CASCADE
//                        )],
//        indices = [Index("buildingId")]
//)
//data class FloorEntity(
//        @PrimaryKey(autoGenerate = true) val id: Int = 0,
//        val buildingId: String,
//        val floorNumber: Int,
//        val name: String,
//        val imageId: Int
//)
//
//@Entity(
//        tableName = "reference_points",
//        foreignKeys =
//                [
//                        ForeignKey(
//                                entity = BuildingEntity::class,
//                                parentColumns = ["id"],
//                                childColumns = ["buildingId"],
//                                onDelete = ForeignKey.CASCADE
//                        ),
//                        ForeignKey(
//                                entity = FloorEntity::class,
//                                parentColumns = ["id"],
//                                childColumns = ["floorId"],
//                                onDelete = ForeignKey.CASCADE
//                        )],
//        indices = [Index("buildingId"), Index("floorId")]
//)
//data class ReferencePointEntity(
//        @PrimaryKey val id: String,
//        val name: String,
//        val x: Double,
//        val y: Double,
//        val imageId: Int,
//        val scanCount: Int = 0,
//        val type: String,
//        val buildingId: String,
//        val floorId: Int,
//        val isUserDefined: Boolean = false
//) {
//    fun toReferencePoint(): ReferencePoint {
//        return ReferencePoint(
//                id = id,
//                name = name,
//                x = x,
//                y = y,
//                imageId = imageId,
//                scanCount = scanCount,
//                type =
//                        try {
//                            PointType.valueOf(type)
//                        } catch (e: IllegalArgumentException) {
//                            PointType.OTHER
//                        },
//                connectedCorridorIds = emptyList()
//        )
//    }
//
//    companion object {
//        fun fromReferencePoint(
//                point: ReferencePoint,
//                buildingId: String,
//                floorId: Int,
//                isUserDefined: Boolean = false
//        ): ReferencePointEntity {
//            return ReferencePointEntity(
//                    id = point.id,
//                    name = point.name,
//                    x = point.x,
//                    y = point.y,
//                    imageId = point.imageId,
//                    scanCount = point.scanCount,
//                    type = point.type.name,
//                    buildingId = buildingId,
//                    floorId = floorId,
//                    isUserDefined = isUserDefined
//            )
//        }
//    }
//}
//
//@Entity(
//        tableName = "corridor_vectors",
//        foreignKeys =
//                [
//                        ForeignKey(
//                                entity = BuildingEntity::class,
//                                parentColumns = ["id"],
//                                childColumns = ["buildingId"],
//                                onDelete = ForeignKey.CASCADE
//                        ),
//                        ForeignKey(
//                                entity = FloorEntity::class,
//                                parentColumns = ["id"],
//                                childColumns = ["floorId"],
//                                onDelete = ForeignKey.CASCADE
//                        )],
//        indices = [Index("buildingId"), Index("floorId")]
//)
//data class CorridorVectorEntity(
//        @PrimaryKey val id: String,
//        val buildingId: String,
//        val floorId: Int,
//        val startX: Double,
//        val startY: Double,
//        val endX: Double,
//        val endY: Double,
//        val label: String
//)
//
//@Entity(
//        tableName = "area_connectivity",
//        foreignKeys =
//                [
//                        ForeignKey(
//                                entity = BuildingEntity::class,
//                                parentColumns = ["id"],
//                                childColumns = ["buildingId"],
//                                onDelete = ForeignKey.CASCADE
//                        ),
//                        ForeignKey(
//                                entity = FloorEntity::class,
//                                parentColumns = ["id"],
//                                childColumns = ["floorId"],
//                                onDelete = ForeignKey.CASCADE
//                        )],
//        indices = [Index("buildingId"), Index("floorId")]
//)
//data class AreaConnectivityEntity(
//        @PrimaryKey(autoGenerate = true) val id: Int = 0,
//        val buildingId: String,
//        val floorId: Int,
//        val areaStartX: Double,
//        val areaStartY: Double,
//        val areaEndX: Double,
//        val areaEndY: Double
//)
//
//// DAO 接口定義
//@Dao
//interface BuildingDao {
//    @Query("SELECT * FROM buildings") fun getAllBuildings(): Flow<List<BuildingEntity>>
//
//    @Query("SELECT * FROM buildings WHERE id = :id")
//    suspend fun getBuildingById(id: String): BuildingEntity?
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertBuilding(building: BuildingEntity)
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertAllBuildings(buildings: List<BuildingEntity>)
//}
//
//@Dao
//interface FloorDao {
//    @Query("SELECT * FROM floors WHERE buildingId = :buildingId")
//    fun getFloorsByBuilding(buildingId: String): Flow<List<FloorEntity>>
//
//    @Query("SELECT * FROM floors WHERE id = :id") suspend fun getFloorById(id: Int): FloorEntity?
//
//    @Query("SELECT * FROM floors WHERE buildingId = :buildingId AND floorNumber = :floorNumber")
//    suspend fun getFloorByBuildingAndNumber(buildingId: String, floorNumber: Int): FloorEntity?
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertFloor(floor: FloorEntity): Long
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertAllFloors(floors: List<FloorEntity>): List<Long>
//}
//
//@Dao
//interface ReferencePointDao {
//    @Query("SELECT * FROM reference_points")
//    fun getAllReferencePoints(): Flow<List<ReferencePointEntity>>
//
//    @Query("SELECT * FROM reference_points WHERE imageId = :imageId")
//    fun getReferencePointsByImageId(imageId: Int): Flow<List<ReferencePointEntity>>
//
//    @Query("SELECT * FROM reference_points WHERE buildingId = :buildingId AND floorId = :floorId")
//    fun getReferencePointsByFloor(
//            buildingId: String,
//            floorId: Int
//    ): Flow<List<ReferencePointEntity>>
//
//    @Query("SELECT * FROM reference_points WHERE name LIKE :nameQuery")
//    fun searchReferencePointsByName(nameQuery: String): Flow<List<ReferencePointEntity>>
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertReferencePoint(referencePoint: ReferencePointEntity)
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertAllReferencePoints(referencePoints: List<ReferencePointEntity>)
//
//    @Update suspend fun updateReferencePoint(referencePoint: ReferencePointEntity)
//
//    @Query("DELETE FROM reference_points WHERE id = :id")
//    suspend fun deleteReferencePointById(id: String)
//}
//
//@Dao
//interface CorridorVectorDao {
//    @Query("SELECT * FROM corridor_vectors WHERE buildingId = :buildingId AND floorId = :floorId")
//    fun getCorridorVectorsByFloor(
//            buildingId: String,
//            floorId: Int
//    ): Flow<List<CorridorVectorEntity>>
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertCorridorVector(corridor: CorridorVectorEntity)
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertAllCorridorVectors(corridors: List<CorridorVectorEntity>)
//}
//
//@Dao
//interface AreaConnectivityDao {
//    @Query("SELECT * FROM area_connectivity WHERE buildingId = :buildingId AND floorId = :floorId")
//    fun getAreasByFloor(buildingId: String, floorId: Int): Flow<List<AreaConnectivityEntity>>
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertArea(area: AreaConnectivityEntity): Long
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertAllAreas(areas: List<AreaConnectivityEntity>): List<Long>
//}
//
//// 資料庫類
//@Database(
//        entities =
//                [
//                        BuildingEntity::class,
//                        FloorEntity::class,
//                        ReferencePointEntity::class,
//                        CorridorVectorEntity::class,
//                        AreaConnectivityEntity::class],
//        version = 1,
//        exportSchema = false
//)
//abstract class IndoorMapDatabase : RoomDatabase() {
//    abstract fun buildingDao(): BuildingDao
//    abstract fun floorDao(): FloorDao
//    abstract fun referencePointDao(): ReferencePointDao
//    abstract fun corridorVectorDao(): CorridorVectorDao
//    abstract fun areaConnectivityDao(): AreaConnectivityDao
//
//    companion object {
//        @Volatile private var INSTANCE: IndoorMapDatabase? = null
//
//        fun getDatabase(context: Context): IndoorMapDatabase {
//            return INSTANCE
//                    ?: synchronized(this) {
//                        val instance =
//                                Room.databaseBuilder(
//                                                context.applicationContext,
//                                                IndoorMapDatabase::class.java,
//                                                "indoor_map_database"
//                                        )
//                                        .addCallback(IndoorMapDatabaseCallback(context))
//                                        .build()
//                        INSTANCE = instance
//                        instance
//                    }
//        }
//    }
//
//    private class IndoorMapDatabaseCallback(private val context: Context) :
//            RoomDatabase.Callback() {
//        override fun onCreate(db: SupportSQLiteDatabase) {
//            super.onCreate(db)
//            INSTANCE?.let { database ->
//                CoroutineScope(Dispatchers.IO).launch { prepopulateDatabase(database) }
//            }
//        }
//
//        private suspend fun prepopulateDatabase(database: IndoorMapDatabase) {
//            // 1. 添加建築物
//            val building =
//                    BuildingEntity(
//                            id = "SE",
//                            name = "理工學院",
//                            description = "綜合教學大樓",
//                            entranceX = 36.21,
//                            entranceY = 68.26,
//                            entranceFloorId = 1,
//                            entranceImageId = R.drawable.se1
//                    )
//            database.buildingDao().insertBuilding(building)
//
//            // 2. 添加樓層
//            val floors =
//                    listOf(
//                            FloorEntity(
//                                    id = 1,
//                                    buildingId = "SE",
//                                    floorNumber = 1,
//                                    name = "1樓",
//                                    imageId = R.drawable.se1
//                            ),
//                            FloorEntity(
//                                    id = 2,
//                                    buildingId = "SE",
//                                    floorNumber = 2,
//                                    name = "2樓",
//                                    imageId = R.drawable.se2
//                            ),
//                            FloorEntity(
//                                    id = 3,
//                                    buildingId = "SE",
//                                    floorNumber = 3,
//                                    name = "3樓",
//                                    imageId = R.drawable.se3
//                            ),
//                            FloorEntity(
//                                    id = 4,
//                                    buildingId = "SE",
//                                    floorNumber = 4,
//                                    name = "4樓",
//                                    imageId = R.drawable.sea4
//                            ),
//                            FloorEntity(
//                                    id = 5,
//                                    buildingId = "SE",
//                                    floorNumber = 5,
//                                    name = "5樓",
//                                    imageId = R.drawable.sea5
//                            )
//                    )
//            database.floorDao().insertAllFloors(floors)
//
//            // 3. 創建理工學院入口參考點
//            val entrancePoint =
//                    ReferencePointEntity(
//                            id = "entrance_se",
//                            name = "理工學院入口",
//                            x = 36.21,
//                            y = 68.26,
//                            imageId = R.drawable.se1,
//                            scanCount = 0,
//                            type = "ENTRANCE",
//                            buildingId = "SE",
//                            floorId = 1
//                    )
//            database.referencePointDao().insertReferencePoint(entrancePoint)
//
//            // 4. 添加預設教室資料
//            val classroomPoints = getDefaultClassroomPoints()
//            database.referencePointDao().insertAllReferencePoints(classroomPoints)
//
//            // 5. 添加走廊向量
//            val corridorVectors = getDefaultCorridorVectors()
//            database.corridorVectorDao().insertAllCorridorVectors(corridorVectors)
//
//            // 6. 添加區域連通性
//            val areas = getDefaultAreas()
//            database.areaConnectivityDao().insertAllAreas(areas)
//        }
//
//        private fun getDefaultClassroomPoints(): List<ReferencePointEntity> {
//            // 將所有教室資料轉換為實體
//            return listOf(
//                    // 1樓教室
//                    ReferencePointEntity(
//                            "classroom_sec112",
//                            "sec112",
//                            31.297901153564453,
//                            83.31011199951172,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec111f",
//                            "sec111f",
//                            42.17558670043945,
//                            82.67748260498047,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec111b",
//                            "sec111b",
//                            43.05496597290039,
//                            88.41331481933594,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec110f",
//                            "sec110f",
//                            47.11360168457031,
//                            81.99949645996094,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec101",
//                            "sec101",
//                            53.858638763427734,
//                            79.76398468017578,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec110b",
//                            "sec110b",
//                            55.860633850097656,
//                            81.97007751464844,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec109",
//                            "sec109",
//                            57.482391357421875,
//                            81.64453125,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec102f",
//                            "sec102f",
//                            56.883663177490234,
//                            79.9090805053711,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec108",
//                            "sec108",
//                            60.943782806396484,
//                            81.4974594116211,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "classroom_sec102b",
//                            "sec102b",
//                            63.0668830871582,
//                            79.71481323242188,
//                            R.drawable.se1,
//                            0,
//                            "CLASSROOM",
//                            "SE",
//                            1
//                    ),
//                    // ... 可以繼續添加更多教室資料
//
//                    // 走廊與設施點位
//                    ReferencePointEntity(
//                            "corridor_1f_north",
//                            "1F-走廊-北",
//                            36.5,
//                            30.0,
//                            R.drawable.se1,
//                            0,
//                            "CORRIDOR",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "corridor_1f_center",
//                            "1F-走廊-中",
//                            50.0,
//                            50.0,
//                            R.drawable.se1,
//                            0,
//                            "CORRIDOR",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "corridor_1f_south",
//                            "1F-走廊-南",
//                            65.0,
//                            72.0,
//                            R.drawable.se1,
//                            0,
//                            "CORRIDOR",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "stairs_1f_1",
//                            "1F-樓梯-1",
//                            40.0,
//                            35.0,
//                            R.drawable.se1,
//                            0,
//                            "STAIRS",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "elevator_1f",
//                            "1F-電梯",
//                            60.0,
//                            35.0,
//                            R.drawable.se1,
//                            0,
//                            "ELEVATOR",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "toilet_1f_male",
//                            "1F-廁所-男",
//                            30.0,
//                            40.0,
//                            R.drawable.se1,
//                            0,
//                            "TOILET",
//                            "SE",
//                            1
//                    ),
//                    ReferencePointEntity(
//                            "toilet_1f_female",
//                            "1F-廁所-女",
//                            30.0,
//                            60.0,
//                            R.drawable.se1,
//                            0,
//                            "TOILET",
//                            "SE",
//                            1
//                    )
//            )
//        }
//
//        private fun getDefaultCorridorVectors(): List<CorridorVectorEntity> {
//            return listOf(
//                    CorridorVectorEntity(
//                            "corridor_1f_main",
//                            "SE",
//                            1,
//                            20.0,
//                            50.0,
//                            80.0,
//                            50.0,
//                            "1樓主走廊"
//                    ),
//                    CorridorVectorEntity(
//                            "corridor_1f_vertical",
//                            "SE",
//                            1,
//                            50.0,
//                            30.0,
//                            50.0,
//                            70.0,
//                            "1樓垂直走廊"
//                    ),
//                    CorridorVectorEntity(
//                            "corridor_2f_main",
//                            "SE",
//                            2,
//                            20.0,
//                            50.0,
//                            80.0,
//                            50.0,
//                            "2樓主走廊"
//                    ),
//                    CorridorVectorEntity(
//                            "corridor_3f_main",
//                            "SE",
//                            3,
//                            20.0,
//                            50.0,
//                            80.0,
//                            50.0,
//                            "3樓主走廊"
//                    ),
//                    CorridorVectorEntity(
//                            "corridor_4f_main",
//                            "SE",
//                            4,
//                            20.0,
//                            50.0,
//                            80.0,
//                            50.0,
//                            "4樓主走廊"
//                    ),
//                    CorridorVectorEntity(
//                            "corridor_5f_main",
//                            "SE",
//                            5,
//                            20.0,
//                            50.0,
//                            80.0,
//                            50.0,
//                            "5樓主走廊"
//                    )
//            )
//        }
//
//        private fun getDefaultAreas(): List<AreaConnectivityEntity> {
//            return listOf(
//                    AreaConnectivityEntity(0, "SE", 1, 20.0, 40.0, 80.0, 60.0),
//                    AreaConnectivityEntity(0, "SE", 2, 20.0, 40.0, 80.0, 60.0),
//                    AreaConnectivityEntity(0, "SE", 3, 20.0, 40.0, 80.0, 60.0),
//                    AreaConnectivityEntity(0, "SE", 4, 20.0, 40.0, 80.0, 60.0),
//                    AreaConnectivityEntity(0, "SE", 5, 20.0, 40.0, 80.0, 60.0)
//            )
//        }
//    }
//}
//
//// 資料庫存儲庫
//class IndoorMapRepository(private val context: Context) {
//    private val database = IndoorMapDatabase.getDatabase(context)
//    private val referencePointDao = database.referencePointDao()
//    private val buildingDao = database.buildingDao()
//    private val floorDao = database.floorDao()
//    private val corridorVectorDao = database.corridorVectorDao()
//    private val areaConnectivityDao = database.areaConnectivityDao()
//
//    // 參考點相關
//    fun getAllReferencePoints(): Flow<List<ReferencePoint>> {
//        return referencePointDao.getAllReferencePoints().map { entities ->
//            entities.map { it.toReferencePoint() }
//        }
//    }
//
//    fun getReferencePointsByImageId(imageId: Int): Flow<List<ReferencePoint>> {
//        return referencePointDao.getReferencePointsByImageId(imageId).map { entities ->
//            entities.map { it.toReferencePoint() }
//        }
//    }
//
//    fun getReferencePointsByFloor(buildingId: String, floorId: Int): Flow<List<ReferencePoint>> {
//        return referencePointDao.getReferencePointsByFloor(buildingId, floorId).map { entities ->
//            entities.map { it.toReferencePoint() }
//        }
//    }
//
//    suspend fun addReferencePoint(point: ReferencePoint, buildingId: String, floorId: Int) {
//        val entity = ReferencePointEntity.fromReferencePoint(point, buildingId, floorId, true)
//        referencePointDao.insertReferencePoint(entity)
//    }
//
//    suspend fun updateReferencePoint(point: ReferencePoint, buildingId: String, floorId: Int) {
//        val entity = ReferencePointEntity.fromReferencePoint(point, buildingId, floorId)
//        referencePointDao.updateReferencePoint(entity)
//    }
//
//    suspend fun deleteReferencePoint(id: String) {
//        referencePointDao.deleteReferencePointById(id)
//    }
//
//    fun searchReferencePointsByName(query: String): Flow<List<ReferencePoint>> {
//        return referencePointDao.searchReferencePointsByName("%$query%").map { entities ->
//            entities.map { it.toReferencePoint() }
//        }
//    }
//
//    // 建築物相關
//    fun getAllBuildings(): Flow<List<BuildingEntity>> {
//        return buildingDao.getAllBuildings()
//    }
//
//    suspend fun getBuildingById(id: String): BuildingEntity? {
//        return buildingDao.getBuildingById(id)
//    }
//
//    // 樓層相關
//    fun getFloorsByBuilding(buildingId: String): Flow<List<FloorEntity>> {
//        return floorDao.getFloorsByBuilding(buildingId)
//    }
//
//    // 走廊向量相關
//    fun getCorridorVectorsByFloor(
//        buildingId: String,
//        floorId: Int
//    ): Flow<List<CorridorVectorEntity>> {
//        return corridorVectorDao.getCorridorVectorsByFloor(buildingId, floorId)
//    }
//
//    // 區域連通性相關
//    fun getAreasByFloor(buildingId: String, floorId: Int): Flow<List<AreaConnectivityEntity>> {
//        return areaConnectivityDao.getAreasByFloor(buildingId, floorId)
//    }
//}
