package com.example.project250311.Map.data

import com.google.android.gms.maps.model.LatLng
/**
 * 合併檔案：將 CustomPoint 與 SEEntrances 也放入此檔，方便管理
 */

// 自訂地點資料類別
data class CustomPoint(
    val location: LatLng,
    val name: String,
    val description: String
)

// 理工學院各棟大樓出入口位置
object SEEntrances {
    val entrancePoints = listOf(
        CustomPoint(
            LatLng(22.738571, 121.065730),
            "SEA_MAIN",
            "理工A棟主要出入口"
        ),
        CustomPoint(
            LatLng(22.738315, 121.065829),
            "SEB_MAIN",
            "理工B棟主要出入口"
        ),
        CustomPoint(
            LatLng(22.737967, 121.066180),
            "SEC_MAIN",
            "理工C棟主要出入口"
        )
    )

    /**
     * 根據教室編號找到最近的出入口
     * @param classroomCode 教室編號（如：SEA101, SEB201, SEC301等）
     * @return 最近的出入口點
     */
    fun getNearestEntrance(classroomCode: String): CustomPoint {
        return when {
            classroomCode.startsWith("SEA") -> entrancePoints[0]
            classroomCode.startsWith("SEB") -> entrancePoints[1]
            classroomCode.startsWith("SEC") -> entrancePoints[2]
            else -> entrancePoints[1] // 默認返回B棟出入口
        }
    }
}

/**
 * 理工學院教室數據
 */
object SEClassrooms {
    val allClassrooms = listOf(
        // A棟一樓教室
        CustomPoint(LatLng(22.738571, 121.06573), "SEA101", "理工學院A棟 - 光學/近物實驗室"),
        CustomPoint(LatLng(22.738701, 121.065735), "SEA102", "理工學院A棟 - 普物/電子實驗室"),
        CustomPoint(LatLng(22.738794, 121.065836), "SEA103", "理工學院A棟 - 農漁牧產品檢驗中心"),
        CustomPoint(LatLng(22.738852, 121.065915), "SEA104", "理工學院A棟 - 生活科學實驗室"),
        CustomPoint(LatLng(22.738898, 121.06598), "SEA105", "理工學院A棟 - 視聽實驗室"),
        CustomPoint(LatLng(22.738996, 121.066153), "SEA106", "理工學院A棟 - 奈米光電實驗室"),
        CustomPoint(LatLng(22.738839, 121.066205), "SEA107", "理工學院A棟 - 光醫光電實驗室"),
        CustomPoint(LatLng(22.738782, 121.06613), "SEA108", "理工學院A棟 - NMR儀器室"),
        CustomPoint(LatLng(22.738769, 121.066108), "SEA109", "理工學院A棟 - 教師研究室"),
        CustomPoint(LatLng(22.7387569, 121.066091), "SEA110", "理工學院A棟 - 教師研究室"),
        CustomPoint(LatLng(22.7387427, 121.0660729), "SEA111", "理工學院A棟 - 教師研究室"),
        CustomPoint(LatLng(22.7387286, 121.0660526), "SEA112", "理工學院A棟 - 教師研究室"),
        CustomPoint(LatLng(22.7387137, 121.066031), "SEA113", "理工學院A棟 - 教師研究室"),
        CustomPoint(LatLng(22.7386931, 121.066002), "SEA114", "理工學院A棟 - 教師研究室"),
        CustomPoint(LatLng(22.738679, 121.065975), "SEA115", "理工學院A棟 - 教師研究室"),
        CustomPoint(LatLng(22.738657, 121.065944), "SEA116", "理工學院A棟 - 教師研究室"),

        // A棟二樓教室
        CustomPoint(LatLng(22.738571, 121.06573), "SEA201", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738701, 121.065735), "SEA202", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738791, 121.065818), "SEA203", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.73877, 121.065844), "SEA204", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.73882, 121.065886), "SEA205", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738878, 121.065984), "SEA206", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738843, 121.06593), "SEA207", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738992, 121.066172), "SEA208", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738862, 121.066238), "SEA209", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738768, 121.066111), "SEA210", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738738, 121.066069), "SEA211", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738714, 121.066036), "SEA212", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738686, 121.065996), "SEA213", "理工學院A棟2F - 教室"),
        CustomPoint(LatLng(22.738657, 121.065944), "SEA214", "理工學院A棟2F - 教室"),

        // A棟三樓教室
        CustomPoint(LatLng(22.738551, 121.065744), "SEA301", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738651, 121.065688), "SEA302", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738669, 121.065759), "SEA303", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738715, 121.065732), "SEA304", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738794, 121.065836), "SEA305", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738852, 121.065915), "SEA306", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738898, 121.06598), "SEA307", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738778, 121.066133), "SEA308", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738759, 121.066104), "SEA309", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738746, 121.066082), "SEA310", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738727, 121.066061), "SEA311", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738712, 121.066035), "SEA312", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738693, 121.066004), "SEA313", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738671, 121.065971), "SEA314", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.739002, 121.066172), "SEA315", "理工學院A棟3F - 教室"),
        CustomPoint(LatLng(22.738871, 121.066236), "SEA316", "理工學院A棟3F - 教室"),

        // A棟四樓教室
        CustomPoint(LatLng(22.738614, 121.065698), "SEA401", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738661, 121.065655), "SEA402", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738714, 121.065785), "SEA403", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738733, 121.06577), "SEA404", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.73874, 121.065746), "SEA405", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738749, 121.065767), "SEA406", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738816, 121.065911), "SEA407", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738866, 121.065992), "SEA408", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.739, 121.066166), "SEA409", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738898, 121.066251), "SEA410", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738855, 121.06624), "SEA411", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.73887, 121.066197), "SEA412", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738782, 121.06613), "SEA413", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738752, 121.066085), "SEA414", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738712, 121.066036), "SEA415", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738684, 121.065991), "SEA416", "理工學院A棟4F - 教室"),
        CustomPoint(LatLng(22.738651, 121.065951), "SEA417", "理工學院A棟4F - 教室"),

        // A棟五樓教室
        CustomPoint(LatLng(22.73871, 121.065761), "SEA501", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738722, 121.065728), "SEA502", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.73874, 121.065757), "SEA503", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738794, 121.065858), "SEA504", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738833, 121.065922), "SEA505", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738879, 121.065936), "SEA506", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738876, 121.065988), "SEA507", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738923, 121.065998), "SEA508", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738911, 121.066038), "SEA509", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738982, 121.066174), "SEA510", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738782, 121.06613), "SEA511", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738758, 121.066089), "SEA512", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738732, 121.066056), "SEA513", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.73871, 121.066023), "SEA514", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.738683, 121.065987), "SEA515", "理工學院A棟5F - 教室"),
        CustomPoint(LatLng(22.73866, 121.065961), "SEA516", "理工學院A棟5F - 教室"),

        // B棟一樓教室
        CustomPoint(LatLng(22.738315, 121.065829), "SEB101", "理工學院B棟 - 階梯教室"),
        CustomPoint(LatLng(22.738359, 121.065895), "SEB102", "理工學院B棟 - 階梯教室"),
        CustomPoint(LatLng(22.73845, 121.066086), "SEB103", "理工學院B棟 - 研討教室"),
        CustomPoint(LatLng(22.738497, 121.066247), "SEB104", "理工學院B棟 - 應用科學系教室"),
        CustomPoint(LatLng(22.738557, 121.066433), "SEB105", "理工學院B棟 - 共同教室"),
        CustomPoint(LatLng(22.7384, 121.066465), "SEB106", "理工學院B棟 - 共同教室"),
        CustomPoint(LatLng(22.73834, 121.066228), "SEB107", "理工學院B棟 - 研討教室"),
        CustomPoint(LatLng(22.738112, 121.066098), "SEB108", "理工學院B棟 - 展示暨交誼廳"),

        // B棟二樓教室
        CustomPoint(LatLng(22.738299, 121.065876), "SEB201", "理工學院B棟2F - 教室"),
        CustomPoint(LatLng(22.73845, 121.066086), "SEB202", "理工學院B棟2F - 教室"),
        CustomPoint(LatLng(22.738497, 121.066247), "SEB203", "理工學院B棟2F - 教室"),
        CustomPoint(LatLng(22.738548, 121.066402), "SEB204", "理工學院B棟2F - 教室"),
        CustomPoint(LatLng(22.738405, 121.066497), "SEB205", "理工學院B棟2F - 教室"),
        CustomPoint(LatLng(22.738375, 121.066374), "SEB206", "理工學院B棟2F - 教室"),
        CustomPoint(LatLng(22.73834, 121.066228), "SEB207", "理工學院B棟2F - 教室"),
        CustomPoint(LatLng(22.738122, 121.066079), "SEB208", "理工學院B棟2F - 教室"),

        // B棟三樓教室
        CustomPoint(LatLng(22.738315, 121.065829), "SEB301", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.738359, 121.065895), "SEB302", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.73845, 121.066086), "SEB303", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.738497, 121.066247), "SEB304", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.738557, 121.066421), "SEB305", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.7384, 121.066465), "SEB306", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.738386, 121.066407), "SEB307", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.738365, 121.066334), "SEB308", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.738342, 121.06624), "SEB309", "理工學院B棟3F - 教室"),
        CustomPoint(LatLng(22.738316, 121.066146), "SEB310", "理工學院B棟3F - 教室"),

        // B棟四樓教室
        CustomPoint(LatLng(22.738378, 121.065871), "SEB401", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738353, 121.065898), "SEB402", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.73845, 121.066086), "SEB403", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.73849, 121.066215), "SEB404", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738516, 121.066297), "SEB405", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738557, 121.066433), "SEB406", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738428, 121.066557), "SEB407", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738422, 121.066536), "SEB408", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738414, 121.06651), "SEB409", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738406, 121.066484), "SEB410", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738391, 121.066442), "SEB411", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738385, 121.066417), "SEB412", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.73838, 121.0664), "SEB413", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738374, 121.066382), "SEB414", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.738368, 121.066358), "SEB415", "理工學院B棟4F - 教室"),
        CustomPoint(LatLng(22.73836, 121.066339), "SEB416", "理工學院B棟4F - 教室"),

        // C棟一樓教室
        CustomPoint(LatLng(22.737967, 121.06618), "SEC101", "理工學院C棟 - 會議室"),
        CustomPoint(LatLng(22.737984, 121.066274), "SEC102", "理工學院C棟 - 研討教室"),
        CustomPoint(LatLng(22.738003, 121.066371), "SEC103", "理工學院C棟 - 教室"),
        CustomPoint(LatLng(22.73806, 121.066619), "SEC104", "理工學院C棟 - 教室"),
        CustomPoint(LatLng(22.737927, 121.066694), "SEC105", "理工學院C棟 - 產業管理商業智慧實驗室"),
        CustomPoint(LatLng(22.737914, 121.066605), "SEC106", "理工學院C棟 - 教授研究室"),
        CustomPoint(LatLng(22.737887, 121.066403), "SEC107", "理工學院C棟 - 系圖書閱覽室"),
        CustomPoint(LatLng(22.737876, 121.066327), "SEC108", "理工學院C棟 - 系學會辦公室"),
        CustomPoint(LatLng(22.737862, 121.066247), "SEC109", "理工學院C棟 - 系主任辦公室"),
        CustomPoint(LatLng(22.737852, 121.066184), "SEC110", "理工學院C棟 - 系辦公室"),
        CustomPoint(LatLng(22.73787, 121.066012), "SEC111", "理工學院C棟 - MIS電腦教室"),
        CustomPoint(LatLng(22.737918, 121.065919), "SEC112", "理工學院C棟 - 視聽教室"),

        // C棟二樓教室
        CustomPoint(LatLng(22.737974, 121.066179), "SEC201", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737981, 121.06624), "SEC202", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737995, 121.066312), "SEC203", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.738003, 121.066371), "SEC204", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737896, 121.066424), "SEC205", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.73789, 121.06639), "SEC206", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737886, 121.066353), "SEC207", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737877, 121.06632), "SEC208", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737872, 121.0662937), "SEC209", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737868, 121.0662768), "SEC210", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737864, 121.0662514), "SEC211", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.7378581, 121.0662233), "SEC212", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737859, 121.066196), "SEC213", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737854, 121.066159), "SEC214", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737857, 121.066032), "SEC215", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737817, 121.066048), "SEC216", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.73786, 121.065948), "SEC217", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737918, 121.065919), "SEC218", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.73806, 121.066619), "SEC219", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737927, 121.066694), "SEC220", "理工學院C棟2F - 教室"),
        CustomPoint(LatLng(22.737914, 121.066605), "SEC221", "理工學院C棟2F - 教室"),

        // C棟三樓教室
        CustomPoint(LatLng(22.737967, 121.06618), "SEC301", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737994, 121.066308), "SEC302", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.73806, 121.066619), "SEC303", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737927, 121.066694), "SEC304", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737914, 121.066605), "SEC305", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737886, 121.066417), "SEC306", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737878, 121.066359), "SEC307", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737871, 121.066302), "SEC308", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737862, 121.066247), "SEC309", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737852, 121.066184), "SEC310", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737839, 121.066012), "SEC311", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737891, 121.065936), "SEC312", "理工學院C棟3F - 教室"),
        CustomPoint(LatLng(22.737934, 121.065915), "SEC313", "理工學院C棟3F - 教室"),

        // C棟四樓教室
        CustomPoint(LatLng(22.73798, 121.066165), "SEC401", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737998, 121.066275), "SEC402", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.738016, 121.06636), "SEC403", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.73806, 121.066619), "SEC404", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737927, 121.066694), "SEC405", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737924, 121.066666), "SEC406", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737922, 121.066637), "SEC407", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737918, 121.066608), "SEC408", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.73789, 121.066433), "SEC409", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737886, 121.066405), "SEC410", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.73788, 121.066364), "SEC411", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737874, 121.066329), "SEC412", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737864, 121.066287), "SEC413", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737856, 121.066246), "SEC414", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737852, 121.066217), "SEC415", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737846, 121.066186), "SEC416", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737841, 121.066153), "SEC417", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737837, 121.066124), "SEC418", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737893, 121.065999), "SEC419", "理工學院C棟4F - 教室"),
        CustomPoint(LatLng(22.737842, 121.066007), "SEC420", "理工學院C棟4F - 教室"),

        // C棟五樓教室
        CustomPoint(LatLng(22.737973, 121.066171), "SEC501", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.737981, 121.06622), "SEC502", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.737989, 121.066268), "SEC503", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.738001, 121.066331), "SEC504", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.738014, 121.066408), "SEC505", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.738043, 121.06662), "SEC506", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.73789, 121.066425), "SEC507", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.737878, 121.066352), "SEC508", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.737872, 121.066302), "SEC509", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.737864, 121.066253), "SEC510", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.737854, 121.066201), "SEC511", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.737838, 121.066039), "SEC512", "理工學院C棟5F - 教室"),
        CustomPoint(LatLng(22.73783, 121.065998), "SEC513", "理工學院C棟5F - 教室")
    )

    val classroomsByFloor = mapOf(
        "A棟1F" to allClassrooms.filter { it.name.startsWith("SEA1") },
        "A棟2F" to allClassrooms.filter { it.name.startsWith("SEA2") },
        "A棟3F" to allClassrooms.filter { it.name.startsWith("SEA3") },
        "A棟4F" to allClassrooms.filter { it.name.startsWith("SEA4") },
        "A棟5F" to allClassrooms.filter { it.name.startsWith("SEA5") },
        "B棟1F" to allClassrooms.filter { it.name.startsWith("SEB1") },
        "B棟2F" to allClassrooms.filter { it.name.startsWith("SEB2") },
        "B棟3F" to allClassrooms.filter { it.name.startsWith("SEB3") },
        "B棟4F" to allClassrooms.filter { it.name.startsWith("SEB4") },
        "C棟1F" to allClassrooms.filter { it.name.startsWith("SEC1") },
        "C棟2F" to allClassrooms.filter { it.name.startsWith("SEC2") },
        "C棟3F" to allClassrooms.filter { it.name.startsWith("SEC3") },
        "C棟4F" to allClassrooms.filter { it.name.startsWith("SEC4") },
        "C棟5F" to allClassrooms.filter { it.name.startsWith("SEC5") }
    )
}