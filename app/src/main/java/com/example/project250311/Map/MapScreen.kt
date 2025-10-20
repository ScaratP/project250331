package com.example.project250311.Map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.project250311.Map.network.RetrofitInstance
import com.example.project250311.Map.utils.PolylineUtils
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.project250311.R
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem

// 1. CustomPoint 包含 description，用於顯示介紹對話框
data class CustomPoint(
    val location: LatLng,
    val name: String,
    val description: String
)

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(navController: NavHostController) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()

    // 2. 初始鏡頭：校園中心
    val defaultLatLng = LatLng(22.7366, 121.0675)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 15f)
    }

    // 虛線樣式
    val dashPattern = listOf<PatternItem>(Dot(), Gap(10f))

    // 3. 狀態變數
    var permissionGranted by remember { mutableStateOf(false) }
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }
    var lastRerouteLoc by remember { mutableStateOf<LatLng?>(null) } // 上次重新路線用的位置
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isRouting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedPoint by remember { mutableStateOf<CustomPoint?>(null) }
    var travelTimeText by remember { mutableStateOf<String?>(null) }

    // Search UI states
    var searchExpanded by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("") }
    var destText by remember { mutableStateOf("") }
    var startSelection by remember { mutableStateOf<LatLng?>(null) }
    var destSelection by remember { mutableStateOf<LatLng?>(null) }
    var startExpanded by remember { mutableStateOf(false) }
    var destExpanded by remember { mutableStateOf(false) }

    // 4. 自訂地點列表（保留你的完整項目）
    val customPoints = remember {
        listOf(
            // C棟教室 - 第一層
            CustomPoint(LatLng(31.297901153564453, 83.31011199951172), "sec112", "理工學院 C棟 C112 教室"),
            CustomPoint(LatLng(42.17558670043945, 82.67748260498047), "sec111f", "理工學院 C棟 C111 教室-前門"),
            CustomPoint(LatLng(43.05496597290039, 88.41331481933594), "sec111b", "理工學院 C棟 C111 教室-後門"),
            CustomPoint(LatLng(47.11360168457031, 81.99949645996094), "sec110f", "理工學院 C棟 C110 教室-前門"),
            CustomPoint(LatLng(55.860633850097656, 81.97007751464844), "sec110b", "理工學院 C棟 C110 教室-後門"),
            CustomPoint(LatLng(53.858638763427734, 79.76398468017578), "sec101", "理工學院 C棟 C101 教室"),
            CustomPoint(LatLng(57.482391357421875, 81.64453125), "sec109", "理工學院 C棟 C109 教室"),
            CustomPoint(LatLng( 56.883663177490234, 79.9090805053711), "sec102f", "理工學院 C棟 C102 教室-前門"),
            CustomPoint(LatLng(63.0668830871582, 79.71481323242188), "sec102b", "理工學院 C棟 C102 教室-後門"),
            CustomPoint(LatLng(65.35888671875, 79.84718322753906), "sec103f", "理工學院 C棟 C103 教室-前門"),
            CustomPoint(LatLng(70.23289489746094, 79.83248138427734), "sec103b", "理工學院 C棟 C103 教室-後門"),
            CustomPoint(LatLng(82.47122192382812, 79.19624328613281), "sec104f", "理工學院 C棟 C104 教室-前門"),
            CustomPoint(LatLng(90.61561584472656, 82.97599792480469), "sec105", "理工學院 C棟 C105 教室"),
            CustomPoint(LatLng(82.17730712890625, 81.4611587524414), "sec106", "理工學院 C棟 C106 教室"),
            CustomPoint(LatLng(64.7601547241211, 81.7297134399414), "sec107f", "理工學院 C棟 C107 教室-前門"),
            CustomPoint(LatLng(71.50485229492188, 81.60088348388672), "sec107b", "理工學院 C棟 C107 教室-後門"),
            CustomPoint(LatLng(60.943782806396484, 81.4974594116211), "sec108", "理工學院 C棟 C108 教室"),

            // C棟教室 - 第二層
            CustomPoint(LatLng(60.36102294921875, 83.49526977539062), "sec201", "理工學院 C棟 C201 教室"),
            CustomPoint(LatLng(66.22871398925781, 83.43477630615234), "sec202", "理工學院 C棟 C202 教室"),
            CustomPoint(LatLng(71.75079345703125, 83.53558349609375), "sec203", "理工學院 C棟 C203 教室"),
            CustomPoint(LatLng(77.11966705322266, 83.41966247558594), "sec204", "理工學院 C棟 C204 教室"),
            CustomPoint(LatLng(76.24322509765625, 85.37017822265625), "sec205", "理工學院 C棟 C205 教室"),
            CustomPoint(LatLng(73.5570068359375, 85.18116760253906), "sec206", "理工學院 C棟 C206 教室"),
            CustomPoint(LatLng(70.89215850830078, 85.24166107177734), "sec207", "理工學院 C棟 C207 教室"),
            CustomPoint(LatLng(68.4232406616211, 85.20890808105469), "sec208", "理工學院 C棟 C208 教室"),
            CustomPoint(LatLng(65.46757507324219, 85.21044158935547), "sec209", "理工學院 C棟 C209 教室"),
            CustomPoint(LatLng(62.51058578491211, 85.3389892578125), "sec210", "理工學院 C棟 C210 教室"),
            CustomPoint(LatLng(59.85283660888672, 85.10965728759766), "sec211", "理工學院 C棟 C211 教室"),
            CustomPoint(LatLng(57.12031173706055, 85.23062133789062), "sec212", "理工學院 C棟 C212 教室"),
            CustomPoint(LatLng(54.644264221191406, 85.21548461914062), "sec213", "理工學院 C棟 C213 教室"),
            CustomPoint(LatLng(51.61960220336914, 85.13485717773438), "sec214", "理工學院 C棟 C214 教室"),
            CustomPoint(LatLng(48.57292175292969, 86.13847351074219), "sec215", "理工學院 C棟 C215 教室"),
            CustomPoint(LatLng(48.82587814331055, 89.62120056152344), "sec216", "理工學院 C棟 C216 教室"),
            CustomPoint(LatLng(37.728912353515625, 84.5806884765625), "sec217", "理工學院 C棟 C217 教室"),
            CustomPoint(LatLng(37.13917541503906, 83.12316131591797), "sec218", "理工學院 C棟 C218 教室"),
            CustomPoint(LatLng(87.96910858154297, 83.68539428710938), "sec219f", "理工學院 C棟 C219 教室-前門"),
            CustomPoint(LatLng(93.48053741455078, 83.66523742675781), "sec219b", "理工學院 C棟 C219 教室-後門"),
            CustomPoint(LatLng(87.40264892578125, 85.75760650634766), "sec221", "理工學院 C棟 C221 教室"),

            // C棟教室 - 第三層
            CustomPoint(LatLng(37.122840881347656, 82.91508483886719), "sec313", "理工學院 C棟 C313 教室"),
            CustomPoint(LatLng(37.010528564453125, 84.51817321777344), "sec312", "理工學院 C棟 C312 教室"),
            CustomPoint(LatLng(49.0062370300293, 86.35113525390625), "sec311f", "理工學院 C棟 C311 教室-前門"),
            CustomPoint(LatLng(49.42385482788086, 94.34064483642578), "sec311b", "理工學院 C棟 C311 教室-後門"),
            CustomPoint(LatLng(60.82547378540039, 85.48767852783203), "sec310", "理工學院 C棟 C310 教室"),
            CustomPoint(LatLng(62.88628005981445, 85.82139587402344), "sec309", "理工學院 C棟 C309 教室"),
            CustomPoint(LatLng(66.63400268554688, 85.8162841796875), "sec308", "理工學院 C棟 C308 教室"),
            CustomPoint(LatLng(59.85283660888672, 85.10965728759766), "sec307", "理工學院 C棟 C307 教室"),
            CustomPoint(LatLng(62.51058578491211, 85.3389892578125), "sec306", "理工學院 C棟 C306 教室"),
            CustomPoint(LatLng(65.46757507324219, 85.21044158935547), "sec305", "理工學院 C棟 C305 教室"),
            CustomPoint(LatLng(68.4232406616211, 85.20890808105469), "sec304", "理工學院 C棟 C304 教室"),
            CustomPoint(LatLng(88.12644958496094, 84.04352569580078), "sec303", "理工學院 C棟 C303 教室"),
            CustomPoint(LatLng(77.01409149169922, 83.9059829711914), "sec302b", "理工學院 C棟 C302 教室-後門"),
            CustomPoint(LatLng(63.350074768066406, 83.59292602539062), "sec302f", "理工學院 C棟 C302 教室-前門"),
            CustomPoint(LatLng(57.911407470703125, 83.7061538696289), "sec301", "理工學院 C棟 C301 教室"),

            // C棟教室 - 第四層
            CustomPoint(LatLng(34.798728942871094, 59.05976867675781), "sec401", "理工學院 C棟 C401 教室"),
            CustomPoint(LatLng(41.9062614440918, 59.50159454345703), "sec402f", "理工學院 C棟 C402 教室-前門"),
            CustomPoint(LatLng(52.2175178527832, 59.50159454345703), "sec402b", "理工學院 C棟 C402 教室-後門"),
            CustomPoint(LatLng(55.67565155029297, 59.366397857666016), "sec403f", "理工學院 C棟 C403 教室-前門"),
            CustomPoint(LatLng(66.4501953125, 59.075225830078125), "sec403b", "理工學院 C棟 C403 教室-後門"),
            CustomPoint(LatLng(80.09688568115234, 59.34395217895508), "sec404", "理工學院 C棟 C404 教室"),
            CustomPoint(LatLng(89.43525695800781, 67.76742553710938), "sec405", "理工學院 C棟 C405 教室"),
            CustomPoint(LatLng(86.19230651855469, 67.03947448730469), "sec406", "理工學院 C棟 C406 教室"),
            CustomPoint(LatLng(82.65815734863281, 65.29237365722656), "sec407", "理工學院 C棟 C407 教室"),
            CustomPoint(LatLng(79.60713195800781, 64.0860595703125), "sec408", "理工學院 C棟 C408 教室"),
            CustomPoint(LatLng(65.48392486572266, 63.98371505737305), "sec409", "理工學院 C棟 C409 教室"),
            CustomPoint(LatLng(62.06228256225586, 63.609344482421875), "sec410", "理工學院 C棟 C410 教室"),
            CustomPoint(LatLng(58.97816848754883, 63.8381233215332), "sec411", "理工學院 C棟 C411 教室"),
            CustomPoint(LatLng(55.268550872802734, 63.97332000732422), "sec412", "理工學院 C棟 C412 教室"),
            CustomPoint(LatLng(51.7608642578125, 63.72373580932617), "sec413", "理工學院 C棟 C413 教室"),
            CustomPoint(LatLng(48.53562927246094, 63.74387741088867), "sec414", "理工學院 C棟 C414 教室"),
            CustomPoint(LatLng(44.75922393798828, 63.55229568481445), "sec415", "理工學院 C棟 C415 教室"),
            CustomPoint(LatLng(41.3574333190918, 63.406700134277344), "sec416", "理工學院 C棟 C416 教室"),
            CustomPoint(LatLng(37.737239837646484, 63.80187225341797), "sec417", "理工學院 C棟 C417 教室"),
            CustomPoint(LatLng(34.69944763183594, 62.88039016723633), "sec418", "理工學院 C棟 C418 教室"),
            CustomPoint(LatLng(30.19976234436035, 66.63758850097656), "sec419", "理工學院 C棟 C419 教室"),
            CustomPoint(LatLng(30.762313842773438, 75.2066650390625), "sec420f", "理工學院 C棟 C420 教室-前門"),
            CustomPoint(LatLng(30.570384979248047, 88.87684631347656), "sec420b", "理工學院 C棟 C420 教室-後門"),

            // C棟教室 - 第五層
            CustomPoint(LatLng(30.19976234436035, 54.377628), "sec501", "理工學院 C棟 C501 教室"),
            CustomPoint(LatLng(33.7484130859375, 54.377628), "sec502", "理工學院 C棟 C502 教室"),
            CustomPoint(LatLng(37.10984802246094, 54.377628), "sec503", "理工學院 C棟 C503 教室"),
            CustomPoint(LatLng(40.64947509765625, 54.377628), "sec504", "理工學院 C棟 C504 教室"),
            CustomPoint(LatLng(44.02111053466797, 54.377628), "sec505", "理工學院 C棟 C505 教室"),
            CustomPoint(LatLng(47.53451156616211, 54.377628), "sec506", "理工學院 C棟 C506 教室"),
            CustomPoint(LatLng(50.938175201416016, 54.377628), "sec507", "理工學院 C棟 C507 教室"),
            CustomPoint(LatLng(54.41509246826172, 54.377628), "sec508", "理工學院 C棟 C508 教室"),
            CustomPoint(LatLng(57.78739547729492, 54.377628), "sec509", "理工學院 C棟 C509 教室"),
            CustomPoint(LatLng(61.25969696044922, 54.377628), "sec510", "理工學院 C棟 C510 教室"),
            CustomPoint(LatLng(64.63133239746094, 54.377628), "sec511", "理工學院 C棟 C511 教室"),
            CustomPoint(LatLng(68.10363006591797, 54.377628), "sec512", "理工學院 C棟 C512 教室"),
            CustomPoint(LatLng(71.47526550292969, 54.377628), "sec513", "理工學院 C棟 C513 教室"),
            CustomPoint(LatLng(74.94756317138672, 54.377628), "sec514", "理工學院 C棟 C514 教室"),
            CustomPoint(LatLng(78.31919860839844, 54.377628), "sec515", "理工學院 C棟 C515 教室"),
            CustomPoint(LatLng(81.79150390625, 54.377628), "sec516", "理工學院 C棟 C516 教室"),
            CustomPoint(LatLng(85.16313171386719, 54.377628), "sec517", "理工學院 C棟 C517 教室"),
            CustomPoint(LatLng(88.63543701171875, 54.377628), "sec518", "理工學院 C棟 C518 教室"),
            CustomPoint(LatLng(89.95914459228516, 59.947513580322266), "sec519", "理工學院 C棟 C519 教室"),
            CustomPoint(LatLng(89.95914459228516, 66.91790771484375), "sec520f", "理工學院 C棟 C520 教室-前門"),
            CustomPoint(LatLng(89.95914459228516, 75.41790771484375), "sec520b", "理工學院 C棟 C520 教室-後門"),

            // B棟教室 - 第一層
            CustomPoint(LatLng(23.815876007080078, 61.124855041503906), "seb101", "理工學院 B棟 B101 教室"),
            CustomPoint(LatLng(24.330406188964844, 57.81572341918945), "seb102", "理工學院 B棟 B102 教室"),
            CustomPoint(LatLng(41.30967712402344, 53.41679382324219), "seb103", "理工學院 B棟 B103 教室"),
            CustomPoint(LatLng(46.36882781982422, 52.11393356323242), "seb104f", "理工學院 B棟 B104 教室-前門"),
            CustomPoint(LatLng(53.48806381225586, 49.775474548339844), "seb104b", "理工學院 B棟 B104 教室-後門"),
            CustomPoint(LatLng(56.89087677001953, 49.12571716308594), "seb105f", "理工學院 B棟 B105 教室-前門"),
            CustomPoint(LatLng(64.67432403564453, 46.86079788208008), "seb105b", "理工學院 B棟 B105 教室-後門"),
            CustomPoint(LatLng(57.70476531982422, 50.68468475341797), "seb106f", "理工學院 B棟 B106 教室-前門"),
            CustomPoint(LatLng(66.13372802734375, 48.33152770996094), "seb106b", "理工學院 B棟 B106 教室-後門"),
            CustomPoint(LatLng(47.41659927368164, 53.864097595214844), "seb107-a", "理工學院 B棟 B107a 教室"),
            CustomPoint(LatLng(55.375343322753906, 51.36121368408203), "seb107-b", "理工學院 B棟 B107b 教室"),
            CustomPoint(LatLng(44.061641693115234, 70.91503143310547), "seb108f", "理工學院 B棟 B108 教室-前門"),
            CustomPoint(LatLng(45.549102783203125, 64.25264739990234), "seb108b", "理工學院 B棟 B108 教室-後門"),

            // B棟教室 - 第二層
            CustomPoint(LatLng(38.17783737182617, 60.49416732788086), "seb201", "理工學院 B棟 B201 教室"),
            CustomPoint(LatLng(50.08774948120117, 57.14501190185547), "seb202f", "理工學院 B棟 B202 教室-前門"),
            CustomPoint(LatLng(57.78660583496094, 55.416236877441406), "seb202b", "理工學院 B棟 B202 教室-後門"),
            CustomPoint(LatLng(59.763877868652344, 55.05335235595703), "seb203f", "理工學院 B棟 B203 教室-前門"),
            CustomPoint(LatLng(65.89517211914062, 53.629512786865234), "seb203b", "理工學院 B棟 B203 教室-後門"),
            CustomPoint(LatLng(67.44860076904297, 53.17101287841797), "seb204f", "理工學院 B棟 B204 教室-前門"),
            CustomPoint(LatLng(73.25212097167969, 51.93618392944336), "seb204b", "理工學院 B棟 B204 教室-後門"),
            CustomPoint(LatLng(74.10002899169922, 53.33229446411133), "seb205", "理工學院 B棟 B205 教室"),
            CustomPoint(LatLng(65.52476501464844, 55.41132736206055), "seb206", "理工學院 B棟 B206 教室"),
            CustomPoint(LatLng(55.18241500854492, 57.712154388427734), "seb207f", "理工學院 B棟 B207 教室-前門"),
            CustomPoint(LatLng(60.34468460083008, 56.65120315551758), "seb207b", "理工學院 B棟 B207 教室-後門"),
            CustomPoint(LatLng(51.2869758605957, 68.33016967773438), "seb208f", "理工學院 B棟 B208 教室-前門"),
            CustomPoint(LatLng(51.2174072265625, 74.63314056396484), "seb208b", "理工學院 B棟 B208 教室-後門"),

            // B棟教室 - 第三層
            CustomPoint(LatLng(31.403541564941406, 63.401424407958984), "seb301", "理工學院 B棟 B301 教室"),
            CustomPoint(LatLng(40.003231048583984, 60.01270294189453), "seb302", "理工學院 B棟 B302 教室"),
            CustomPoint(LatLng(49.835819244384766, 57.48321533203125), "seb303f", "理工學院 B棟 B303 教室-前門"),
            CustomPoint(LatLng(57.248260498046875, 55.56953430175781), "seb303b", "理工學院 B棟 B303 教室-後門"),
            CustomPoint(LatLng(59.186988830566406, 55.21876907348633), "seb304f", "理工學院 B棟 B304 教室-前門"),
            CustomPoint(LatLng(67.04540252685547, 52.75032424926758), "seb304b", "理工學院 B棟 B304 教室-後門"),
            CustomPoint(LatLng(72.44236755371094, 52.07304382324219), "seb305", "理工學院 B棟 B305 教室"),
            CustomPoint(LatLng(70.57686614990234, 54.260643005371094), "seb306", "理工學院 B棟 B306 教室"),
            CustomPoint(LatLng(65.37165832519531, 55.534786224365234), "seb307", "理工學院 B棟 B307 教室"),
            CustomPoint(LatLng(61.777889251708984, 56.537288665771484), "seb308", "理工學院 B棟 B308 教室"),
            CustomPoint(LatLng(55.474735260009766, 58.4857292175293), "seb309", "理工學院 B棟 B309 教室"),
            CustomPoint(LatLng(51.31575012207031, 59.25801467895508), "seb310", "理工學院 B棟 B310 教室"),

            // B棟教室 - 第四層
            CustomPoint(LatLng(21.5928897857666, 45.721649169921875), "seb401", "理工學院 B棟 B401 教室"),
            CustomPoint(LatLng(34.64710235595703, 63.057010650634766), "seb402f", "理工學院 B棟 B402 教室-前門"),
            CustomPoint(LatLng(23.973342895507812, 62.98015213012695), "seb402b", "理工學院 B棟 B402 教室-後門"),
            CustomPoint(LatLng(50.87617111206055, 62.87931823730469), "seb403f", "理工學院 B棟 B403 教室-前門"),
            CustomPoint(LatLng(64.64363098144531, 63.14189529418945), "seb403b", "理工學院 B棟 B403 教室-後門"),
            CustomPoint(LatLng(67.83889770507812, 63.20636749267578), "seb404f", "理工學院 B棟 B404 教室-前門"),
            CustomPoint(LatLng(76.18531799316406, 63.21170425415039), "seb405", "理工學院 B棟 B405 教室"),
            CustomPoint(LatLng(90.3544692993164, 63.158546447753906), "seb406", "理工學院 B棟 B406 教室"),
            CustomPoint(LatLng(88.89725494384766, 68.37963104248047), "seb407", "理工學院 B棟 B407 教室"),
            CustomPoint(LatLng(85.19001770019531, 68.35755920410156), "seb408", "理工學院 B棟 B408 教室"),
            CustomPoint(LatLng(80.58969116210938, 68.43694305419922), "seb409", "理工學院 B棟 B409 教室"),
            CustomPoint(LatLng(76.52371215820312, 68.52718353271484), "seb410", "理工學院 B棟 B410 教室"),
            CustomPoint(LatLng(71.66710662841797, 68.64998626708984), "seb411", "理工學院 B棟 B411 教室"),
            CustomPoint(LatLng(67.24075317382812, 68.6009521484375), "seb412", "理工學院 B棟 B412 教室"),
            CustomPoint(LatLng(63.010520935058594, 68.58194732666016), "seb413", "理工學院 B棟 B413 教室"),
            CustomPoint(LatLng(58.46512985229492, 68.32197570800781), "seb414", "理工學院 B棟 B414 教室"),
            CustomPoint(LatLng(54.223899841308594, 68.60690307617188), "seb415", "理工學院 B棟 B415 教室"),
            CustomPoint(LatLng(49.79465103149414, 68.3743667602539), "seb416", "理工學院 B棟 B416 教室"),

            // A棟教室 - 第一層
            CustomPoint(LatLng(13.271364212036133, 36.39988708496094), "sea101", "理工學院 A棟 A101 教室"),
            CustomPoint(LatLng(20.14449119567871, 29.89427947998047), "sea102", "理工學院 A棟 A102 教室"),
            CustomPoint(LatLng(23.82387924194336, 28.580760955810547), "sea103f", "理工學院 A棟 A103 教室-前門"),
            CustomPoint(LatLng(31.046024322509766, 24.88923454284668), "sea103b", "理工學院 A棟 A103 教室-後門"),
            CustomPoint(LatLng(36.26740646362305, 22.947391510009766), "sea104", "理工學院 A棟 A104 教室"),
            CustomPoint(LatLng(37.82120895385742, 21.650352478027344), "sea105f", "理工學院 A棟 A105 教室-前門"),
            CustomPoint(LatLng(44.940452575683594, 17.87058448791504), "sea105b", "理工學院 A棟 A105 教室-後門"),
            CustomPoint(LatLng(45.88531494140625, 20.238454818725586), "sea108", "理工學院 A棟 A108 教室"),
            CustomPoint(LatLng(43.92074203491211, 21.35620880126953), "sea109", "理工學院 A棟 A109 教室"),
            CustomPoint(LatLng(41.63809585571289, 22.31218147277832), "sea110", "理工學院 A棟 A110 教室"),
            CustomPoint(LatLng(39.233829498291016, 23.429933547973633), "sea111", "理工學院 A棟 A111 教室"),
            CustomPoint(LatLng(37.269256591796875, 24.95948600769043), "sea112", "理工學院 A棟 A112 教室"),
            CustomPoint(LatLng(35.183067321777344, 26.106653213500977), "sea113", "理工學院 A棟 A113 教室"),
            CustomPoint(LatLng(32.68525314331055, 27.297943115234375), "sea114", "理工學院 A棟 A114 教室"),
            CustomPoint(LatLng(30.392396926879883, 28.55086326599121), "sea115", "理工學院 A棟 A115 教室"),
            CustomPoint(LatLng(25.676191329956055, 31.022167205810547), "sea116f", "理工學院 A棟 A116 教室-前門"),
            CustomPoint(LatLng(29.146940231323242, 29.036685943603516), "sea116b", "理工學院 A棟 A116 教室-後門"),

            // A棟教室 - 第二層
            CustomPoint(LatLng(27.1607608795166, 42.274879455566406), "sea201f", "理工學院 A棟 A201 教室-前門"),
            CustomPoint(LatLng(20.86558723449707, 35.70256805419922), "sea201b", "理工學院 A棟 A201 教室-後門"),
            CustomPoint(LatLng(31.67758560180664, 33.307857513427734), "sea202f", "理工學院 A棟 A202 教室-前門"),
            CustomPoint(LatLng(27.556201934814453, 28.596010208129883), "sea202b", "理工學院 A棟 A202 教室-後門"),
            CustomPoint(LatLng(33.855525970458984, 31.27509307861328), "sea203", "理工學院 A棟 A203 教室"),
            CustomPoint(LatLng(40.25783920288086, 29.962125778198242), "sea205f", "理工學院 A棟 A205 教室-前門"),
            CustomPoint(LatLng(47.55054473876953, 26.998550415039062), "sea205b", "理工學院 A棟 A205 教室-後門"),
            CustomPoint(LatLng(56.841922760009766, 22.890857696533203), "sea206", "理工學院 A棟 A206 教室"),
            CustomPoint(LatLng(49.520687103271484, 26.285371780395508), "sea207", "理工學院 A棟 A207 教室"),
            CustomPoint(LatLng(66.3443603515625, 18.83325958251953), "sea208", "理工學院 A棟 A208 教室"),
            CustomPoint(LatLng(65.09033203125, 21.877511978149414), "sea209f", "理工學院 A棟 A209 教室-前門"),
            CustomPoint(LatLng(69.5364990234375, 19.88665771484375), "sea209b", "理工學院 A棟 A209 教室-後門"),
            CustomPoint(LatLng(55.68429946899414, 26.140832901000977), "sea210", "理工學院 A棟 A210 教室"),
            CustomPoint(LatLng(53.34721755981445, 27.174074172973633), "sea211", "理工學院 A棟 A211 教室"),
            CustomPoint(LatLng(46.120418548583984, 29.976346969604492), "sea212f", "理工學院 A棟 A212 教室-前門"),
            CustomPoint(LatLng(51.6373291015625, 27.7109317779541), "sea212b", "理工學院 A棟 A212 教室-後門"),
            CustomPoint(LatLng(43.79401397705078, 31.042348861694336), "sea213", "理工學院 A棟 A213 教室"),
            CustomPoint(LatLng(36.56644058227539, 34.33987808227539), "sea214f", "理工學院 A棟 A214 教室-前門"),
            CustomPoint(LatLng(42.440189361572266, 31.73285675048828), "sea214b", "理工學院 A棟 A214 教室-後門"),

            // A棟教室 - 第三層
            CustomPoint(LatLng(24.581771850585938, 41.899017333984375), "sea301", "理工學院 A棟 A301 教室"),
            CustomPoint(LatLng(23.275197982788086, 40.36946105957031), "sea302", "理工學院 A棟 A302 教室"),
            CustomPoint(LatLng(27.713125228881836, 31.877595901489258), "sea303", "理工學院 A棟 A303 教室"),
            CustomPoint(LatLng(26.018539428710938, 30.26356315612793), "sea304", "理工學院 A棟 A304 教室"),
            CustomPoint(LatLng(35.209068298339844, 33.56096267700195), "sea305", "理工學院 A棟 A305 教室"),
            CustomPoint(LatLng(38.05083465576172, 30.929719924926758), "sea306f", "理工學院 A棟 A306 教室-前門"),
            CustomPoint(LatLng(45.727378845214844, 27.683198928833008), "sea306b", "理工學院 A棟 A306 教室-後門"),
            CustomPoint(LatLng(47.18032455444336, 26.25664520263672), "sea307f", "理工學院 A棟 A307 教室-前門"),
            CustomPoint(LatLng(54.789608001708984, 23.008777618408203), "sea307b", "理工學院 A棟 A307 教室-後門"),
            CustomPoint(LatLng(49.793701171875, 29.087871551513672), "sea308f", "理工學院 A棟 A308 教室-前門"),
            CustomPoint(LatLng(57.01210403442383, 25.17099952697754), "sea308b", "理工學院 A棟 A308 教室-後門"),
            CustomPoint(LatLng(47.17616653442383, 29.784103393554688), "sea309", "理工學院 A棟 A309 教室"),
            CustomPoint(LatLng(44.8295783996582, 30.743545532226562), "sea310", "理工學院 A棟 A310 教室"),
            CustomPoint(LatLng(42.69187927246094, 31.959978103637695), "sea311", "理工學院 A棟 A311 教室"),
            CustomPoint(LatLng(40.43357849121094, 33.15655517578125), "sea312", "理工學院 A棟 A312 教室"),
            CustomPoint(LatLng(38.3000373840332, 34.225830078125), "sea313", "理工學院 A棟 A313 教室"),
            CustomPoint(LatLng(35.98874282836914, 35.208709716796875), "sea314", "理工學院 A棟 A314 教室"),
            CustomPoint(LatLng(63.525413513183594, 19.030677795410156), "sea315", "理工學院 A棟 A315 教室"),
            CustomPoint(LatLng(62.77527618408203, 22.467670440673828), "sea316f", "理工學院 A棟 A316 教室-前門"),
            CustomPoint(LatLng(67.33075714111328, 20.421546936035156), "sea316b", "理工學院 A棟 A316 教室-後門"),

            // A棟教室 - 第四層
            CustomPoint(LatLng(15.417096138000488, 64.93428802490234), "sea401", "理工學院 A棟 A401 教室"),
            CustomPoint(LatLng(30.550701141357422, 58.46754455566406), "sea403f", "理工學院 A棟 A403 教室-前門"),
            CustomPoint(LatLng(29.9885311126709, 43.00105285644531), "sea404f", "理工學院 A棟 A404 教室-前門"),
            CustomPoint(LatLng(37.372188568115234, 59.67949676513672), "sea407f", "理工學院 A棟 A407 教室-前門"),
            CustomPoint(LatLng(52.95795822143555, 58.89101028442383), "sea407b", "理工學院 A棟 A407 教室-後門"),
            CustomPoint(LatLng(56.526554107666016, 59.51247787475586), "sea408f", "理工學院 A棟 A408 教室-前門"),
            CustomPoint(LatLng(71.50880432128906, 59.18362808227539), "sea408b", "理工學院 A棟 A408 教室-後門"),
            CustomPoint(LatLng(86.63111114501953, 59.327239990234375), "sea409", "理工學院 A棟 A409 教室"),
            CustomPoint(LatLng(88.79692840576172, 65.49458312988281), "sea410", "理工學院 A棟 A410 教室"),
            CustomPoint(LatLng(85.3043441772461, 76.60122680664062), "sea411", "理工學院 A棟 A411 教室"),
            CustomPoint(LatLng(81.53604888916016, 65.26834106445312), "sea412", "理工學院 A棟 A412 教室"),
            CustomPoint(LatLng(70.07524108886719, 65.33300018310547), "sea413", "理工學院 A棟 A413 教室"),
            CustomPoint(LatLng(58.54875946044922, 66.65852355957031), "sea414f", "理工學院 A棟 A414 教室-前門"),
            CustomPoint(LatLng(68.06903839111328, 66.29591369628906), "sea414b", "理工學院 A棟 A414 教室-後門"),
            CustomPoint(LatLng(51.43730545043945, 65.38606262207031), "sea415f", "理工學院 A棟 A415 教室-前門"),
            CustomPoint(LatLng(47.144500732421875, 65.1932601928711), "sea416", "理工學院 A棟 A416 教室"),
            CustomPoint(LatLng(35.862728118896484, 65.26189422607422), "sea417f", "理工學院 A棟 A417 教室-前門"),

            // A棟教室 - 第五層
            CustomPoint(LatLng(25.605928421020508, 61.12555694580078), "sea501", "理工學院 A棟 A501 教室"),
            CustomPoint(LatLng(32.16361999511719, 61.97163391113281), "sea504", "理工學院 A棟 A504 教室"),
            CustomPoint(LatLng(39.7947883605957, 62.2108154296875), "sea505f", "理工學院 A棟 A505 教室-前門"),
            CustomPoint(LatLng(48.8352165222168, 61.818050384521484), "sea505b", "理工學院 A棟 A505 教室-後門"),
            CustomPoint(LatLng(52.54081344604492, 61.628456115722656), "sea507f", "理工學院 A棟 A507 教室-前門"),
            CustomPoint(LatLng(62.16032409667969, 63.765411376953125), "sea507b", "理工學院 A棟 A507 教室-後門"),
            CustomPoint(LatLng(64.42755889892578, 61.895572662353516), "sea509", "理工學院 A棟 A509 教室"),
            CustomPoint(LatLng(85.91199493408203, 62.81660079956055), "sea510", "理工學院 A棟 A510 教室"),
            CustomPoint(LatLng(68.2611083984375, 69.04708862304688), "sea511", "理工學院 A棟 A511 教室"),
            CustomPoint(LatLng(64.34971618652344, 69.04093170166016), "sea512", "理工學院 A棟 A512 教室"),
            CustomPoint(LatLng(52.15821838378906, 71.19689178466797), "sea513f", "理工學院 A棟 A513 教室-前門"),
            CustomPoint(LatLng(62.26993942260742, 71.58380126953125), "sea513b", "理工學院 A棟 A513 教室-後門"),
            CustomPoint(LatLng(48.095272064208984, 69.49008178710938), "sea514", "理工學院 A棟 A514 教室"),
            CustomPoint(LatLng(35.585914611816406, 71.01056671142578), "sea515f", "理工學院 A棟 A515 教室-前門"),
            CustomPoint(LatLng(45.7668571472168, 71.31716918945312), "sea515b", "理工學院 A棟 A515 教室-後門"),
            CustomPoint(LatLng(31.434926986694336, 69.18673706054688), "sea516", "理工學院 A棟 A516 教室")
        )
        listOf(
            CustomPoint(LatLng(22.73881963044863, 121.06574371741712), "理工學院A棟入口", "臺東大學理工學院A棟，設有多間專業教學研究實驗室與系所辦公室，提供師生學習與科研環境。"),
            CustomPoint(LatLng(22.7384658854431, 121.06639817640176), "理工學院B棟入口", "B 棟主要集合與資訊科技、綠能科技、應用數學等理工相關科系的教學與研究空間，教學設施與系辦公室齊備，有利於跨領域合作與科技應用課程的推動。"),
            CustomPoint(LatLng(22.738103481392233, 121.06599785671906), "理工學院廣場", "距離學餐最近的入口，同時可以進熱AB棟。"),
            CustomPoint(LatLng(22.73773241822817, 121.06606692360623), "理工學院C棟入口", "C 棟在理工學院三大棟之一，用於資訊類、管理類系所，較多的教學與研究用途，此外三樓以上還有實驗室和教授研究室。"),
            CustomPoint(LatLng(22.737859271065286, 121.06536003955178), "學餐入口(近理工)", "一宿餐廳提供多種大學餐選項滿足不同學生口味，並且全天開放。"),
            CustomPoint(LatLng(22.736882703556198, 121.06535922599979), "學餐入口(近7-11)", "一宿餐廳提供多種大學餐選項滿足不同學生口味，並且全天開放。"),
            CustomPoint(LatLng(22.73667924113307, 121.06540546477464), "7-11(東大門市)", "提供思樂冰、ATM、座位區、Ibon、ibon WiFi、現萃茶、現蒸地瓜等服務。"),
            CustomPoint(LatLng(22.73604427120851, 121.06556193495481), "第二學生宿舍", "第二學生宿舍新落成，房間採現代化設計，附有公共休息室。"),
            CustomPoint(LatLng(22.737447706638765, 121.06513631521499), "第一學生宿舍", "第一學生宿舍是校園內最早啟用的一棟，設有單人間與雙人間。"),
            CustomPoint(LatLng(22.733205795559435, 121.06580202471531), "操場", "校園操場，可供足球、慢跑與排球等活動使用。"),
            CustomPoint(LatLng(22.733471492854004, 121.06703820593026), "東大游泳池", "游泳池設有25公尺長的主池，並提供兒童戲水池、超音波池、SPA池等設施，適合各年齡層使用。\n" +
                    "\n" +
                    "開放時間：\n" +
                    "\n" +
                    "每學期第一至十七週： 每週一至週五，18:00 至 21:00。\n" +
                    "\n" +
                    "每年11月及12月： 僅開放週二至週四，18:00 至 21:00。\n" +
                    "\n" +
                    "國定假日、例假日、寒暑假： 開放時間及收費標準另定。"),
            CustomPoint(LatLng(22.733005518553, 121.06721074863363), "體育館", "體育館內有籃球場、羽球場與健身房，對外開放時段請參考公告。"),
            CustomPoint(LatLng(22.73611793378069, 121.06653276459784), "共同教學大樓", "共同教學大樓是一座多功能的教學設施，主要用於舉辦通識課程、共同必修課程及選修課程等。"),
            CustomPoint(LatLng(22.73650880319903, 121.06655617651063), "靜心書院入口(近理工)", "該書院結合了教室、會議室與休憩區，旨在為學生、教職員及外部來賓提供舒適的學習與生活環境。"),
            CustomPoint(LatLng(22.73651940864311, 121.06721064437424), "靜心書院入口(近圖書館)", "該書院結合了教室、會議室與休憩區，旨在為學生、教職員及外部來賓提供舒適的學習與生活環境。"),
            CustomPoint(LatLng(22.738700601981595, 121.06497484121572), "資源回收站", "校園資源回收站，提供紙張、塑膠、金屬等回收服務。"),
            CustomPoint(LatLng(22.73667908810683, 121.065407893042), "7-11", "校園門口的 7-11，方便師生隨時購買飲料與零食。"),
            CustomPoint(LatLng(22.733878454879942, 121.06840153239384), "籃球場", "室外籃球場，夜間有照明，適合休閒籃球活動。"),
            CustomPoint(LatLng(22.735963782832926, 121.06770378016085), "圖書館", "為全校師生提供豐富的學術資源與舒適的閱讀環境。圖書館設有多樣化的閱覽區、電子資料庫、視聽設備及自習空間，也被稱為全球八度獨特圖書館之一。"),
            CustomPoint(LatLng(22.736834829795928, 121.06865836893749), "行政大樓", "國立臺東大學的行政服務大樓位於校本部，是學校行政運作的核心建築。該大樓內設有多個行政單位，包括秘書室、總務處、教務處、學生事務處等，負責學校日常行政管理與服務。"),
            CustomPoint(LatLng(22.73583698679327, 121.0682959798721), "颯德固講堂", "為於圖書館旁的一個長廊，主要拿來邀請著名講師來演講。"),
            CustomPoint(LatLng(22.73954853435361, 121.06738946442893), "師範學院A棟", "主要作為師範學院的行政與教學中心，包含院辦公室、教師研究室及會議室等，提供師生辦公與學術交流的空間。"),
            CustomPoint(LatLng(22.73916983421318, 121.06759949777546), "師範學院B棟", "B 棟內的「淑真講堂」為大型教學與活動空間，適合舉辦講座、研討會等。"),
            CustomPoint(LatLng(22.738813950809405, 121.06710692651174), "師範學院C棟", "C 棟主要用於體育與休閒相關課程的教學，設有體育系教室及相關設施支援學生的實習與學習需求。"),
            CustomPoint(LatLng(22.73863578654702, 121.06753201693554), "淑貞講堂", "淑貞講堂常舉辦演講與表演活動。"),
            CustomPoint(LatLng(22.737955956237787, 121.06842922214723), "演藝廳", "是學校內主要的表演與活動場地，適合舉辦音樂會、戲劇、講座等文化與學術活動其座位數約有300席。"),
            CustomPoint(LatLng(22.738135710196378, 121.06869042365535), "人文學院(近演藝廳)", "是學校的核心學術單位之一，致力於培養學生的語言能力、文化素養、批判思維與跨文化理解。學院內設有多個系所，涵蓋中文、外語、歷史、哲學等領域，並積極推動國際交流與跨領域合作。"),
            CustomPoint(LatLng(22.73762494117075, 121.06907164188596), "人文學院(近大門)", "是學校的核心學術單位之一，致力於培養學生的語言能力、文化素養、批判思維與跨文化理解。學院內設有多個系所，涵蓋中文、外語、歷史、哲學等領域，並積極推動國際交流與跨領域合作。")
        )
    }

    // 5. 申請定位權限
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionGranted = granted }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissionGranted = true
        }
    }

    // 6. 設定持續定位，但不自動移動鏡頭
    val locationRequest = remember {
        LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location? = result.lastLocation
                if (loc != null) {
                    val newLatLng = LatLng(loc.latitude, loc.longitude)
                    currentLoc = newLatLng

                    // 只有使用者移動超過 20 公尺時才重新路線
                    destination?.let { dest ->
                        val prev = lastRerouteLoc
                        if (prev == null || distanceBetween(prev, newLatLng) > 20f) {
                            lastRerouteLoc = newLatLng
                            drawRoute(
                                origin = newLatLng,
                                dest = dest,
                                onStart = { isRouting = true },
                                onSuccess = { points -> isRouting = false; routePoints = points },
                                onTime = { timeText -> travelTimeText = timeText },
                                onError = {
                                    isRouting = false
                                    errorMsg = it
                                    scope.launch {
                                        delay(3000)
                                        errorMsg = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }
    DisposableEffect(Unit) { onDispose { fusedClient.removeLocationUpdates(locationCallback) } }

    // 7. 初次定位
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fusedClient.lastLocation
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val ll = LatLng(loc.latitude, loc.longitude)
                        currentLoc = ll
                        cameraState.move(CameraUpdateFactory.newLatLng(ll))
                    } else {
                        errorMsg = "無法取得目前位置"
                        scope.launch { delay(3000); errorMsg = null }
                    }
                }
                .addOnFailureListener {
                    errorMsg = "定位失敗：${it.message}"
                    scope.launch { delay(3000); errorMsg = null }
                }
        }
    }

    // 8. Map 畫面
    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = permissionGranted),
            onMapClick = { latLng ->
                destination = latLng
                routePoints = emptyList()
                lastRerouteLoc = currentLoc
                travelTimeText = null
                drawRoute(
                    origin = currentLoc,
                    dest = latLng,
                    onStart = { isRouting = true },
                    onSuccess = { points -> isRouting = false; routePoints = points },
                    onTime = { timeText -> travelTimeText = timeText },
                    onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } }
                )
                // 點地圖也順便關掉 selectedPoint（避免資訊卡蓋住）
                selectedPoint = null
            }
        ) {
            // A. 顯示「你的位置」Marker
            currentLoc?.let {
                Marker(state = MarkerState(it), title = "你的位置", icon = getResizedBitmapDescriptor(context, R.drawable.marker, 120, 120))
            }

            // B. 顯示「目的地」Marker，點擊直接清除
            destination?.let { destLatLng ->
                Marker(
                    state = MarkerState(destLatLng),
                    title = "目的地",
                    icon = getResizedBitmapDescriptor(context, R.drawable.marker, 120, 120),
                    onClick = {
                        destination = null
                        routePoints = emptyList()
                        travelTimeText = null
                        true
                    }
                )
            }

            // C. 顯示自訂地點 Marker
            customPoints.forEach { custom ->
                Marker(state = MarkerState(custom.location), title = custom.name, icon = getResizedBitmapDescriptor(context, R.drawable.marker, 80, 80), onClick = {
                    selectedPoint = custom
                    false
                })
            }

            // D. 畫三段 Polyline：灰色虛線 + 藍色實線 + 灰色虛線
            if (currentLoc != null && routePoints.isNotEmpty()) {
                val firstOnRoad = routePoints.first()
                Polyline(points = listOf(currentLoc!!, firstOnRoad), width = 6f, color = Color.Gray, pattern = dashPattern)
            }
            if (routePoints.isNotEmpty()) Polyline(points = routePoints, width = 12f, color = Color.Blue)
            if (routePoints.isNotEmpty() && destination != null) {
                val lastOnRoad = routePoints.last()
                Polyline(points = listOf(lastOnRoad, destination!!), width = 6f, color = Color.Gray, pattern = dashPattern)
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {

            // 搜尋卡（加上 zIndex 確保置頂）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5).copy(alpha = 0.95f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {

                    // 標題 + 箭頭
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (searchExpanded) "搜尋目的地" else "點擊展開搜尋", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
                            Icon(imageVector = if (searchExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "收合搜尋欄")
                        }
                    }

                    // 內容動畫
                    AnimatedVisibility(
                        visible = searchExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            // ---- 起點欄位 ----
                            var startFieldWidth by remember { mutableStateOf(0) }
                            Box {
                                OutlinedTextField(
                                    value = startText,
                                    onValueChange = {
                                        startText = it
                                        startSelection = null
                                        startExpanded = it.isNotBlank()
                                        customPoints.firstOrNull { cp -> cp.name.equals(it, true) }?.let { cp -> startSelection = cp.location }
                                    },
                                    placeholder = { Text("起點") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clickable { startExpanded = true }
                                        .onGloballyPositioned { coordinates -> startFieldWidth = coordinates.size.width },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = "起點", tint = Color(0xFFFF69B4)) },
                                    shape = RoundedCornerShape(16.dp)
                                )

                                // 替換 DropdownMenu -> 直接在下方顯示可捲動清單
                                if (startExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .width(with(LocalDensity.current) { startFieldWidth.toDp() })
                                            .padding(top = 55.dp)
                                            .heightIn(max = 200.dp)   // 當建議過多時就會捲動，不會超過高度
                                            .zIndex(3f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5))
                                    ) {
                                        val startSuggestions = if (startText.isBlank()) customPoints else customPoints.filter { it.name.contains(startText, true) }
                                        LazyColumn {
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            startText = "目前位置"
                                                            startSelection = null
                                                            startExpanded = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Text(text = "目前位置")
                                                }
                                            }
                                            items(startSuggestions) { cp ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            startText = cp.name
                                                            startSelection = cp.location
                                                            startExpanded = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Text(text = cp.name)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // ---- 目的地欄位 ----
                            var destFieldWidth by remember { mutableStateOf(0) }
                            Box {
                                OutlinedTextField(
                                    value = destText,
                                    onValueChange = {
                                        destText = it
                                        destSelection = null
                                        destExpanded = true
                                    },
                                    placeholder = { Text("輸入教室編號（如：C101、理工C101）或地點名稱") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                destExpanded = true
                                            }
                                        }
                                        .onGloballyPositioned { coordinates -> destFieldWidth = coordinates.size.width },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = "目的地", tint = Color(0xFF87CEFA)) },
                                    shape = RoundedCornerShape(16.dp)
                                )

                                // 替換 DropdownMenu -> 直接在下方顯示可捲動清單
                                if (destExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .width(with(LocalDensity.current) { destFieldWidth.toDp() })
                                            .padding(top = 55.dp)
                                            .heightIn(max = 300.dp)
                                            .zIndex(3f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF87CEFA).copy(alpha = 0.15f))
                                    ) {
                                        val searchText = destText.trim()
                                        // 分離教室和其他地點
                                        val locations = customPoints.filter { !it.name.startsWith("se", true) }
                                        val classrooms = customPoints.filter { it.name.startsWith("se", true) }

                                        // 過濾搜尋結果，支援多種輸入格式
                                        fun normalizeInput(input: String): String {
                                            return input.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                                        }

                                        fun formatClassroomName(name: String): String {
                                            return when {
                                                name.startsWith("sea", true) -> {
                                                    val number = name.substring(3).uppercase()
                                                    "理工 A${number.replace(Regex("(\\d+)"), " $1 ").trim()}"
                                                }
                                                name.startsWith("seb", true) -> {
                                                    val number = name.substring(3).uppercase()
                                                    "理工 B${number.replace(Regex("(\\d+)"), " $1 ").trim()}"
                                                }
                                                name.startsWith("sec", true) -> {
                                                    val number = name.substring(3).uppercase()
                                                    "理工 C${number.replace(Regex("(\\d+)"), " $1 ").trim()}"
                                                }
                                                else -> name.uppercase()
                                            }
                                        }

                                        fun matchesClassroom(classroom: CustomPoint, query: String): Boolean {
                                            if (query.isBlank()) return true

                                            val normalizedQuery = normalizeInput(query)
                                            val normalizedName = normalizeInput(classroom.name)
                                            val displayName = formatClassroomName(classroom.name).lowercase()

                                            // 支援多種搜尋格式：
                                            return when {
                                                // 1. 完整編號 (sec101)
                                                normalizedName.contains(normalizedQuery) -> true
                                                // 2. 簡化編號 (c101) - 檢查字母和數字
                                                normalizedName.substring(3).contains(normalizedQuery) -> true
                                                // 3. 中文搜尋 (理工c101)
                                                displayName.contains(query.lowercase()) -> true
                                                // 4. 純數字 (101) - 只比對數字部分
                                                normalizedQuery.all { it.isDigit() } &&
                                                        normalizedName.contains(normalizedQuery) -> true
                                                // 5. 檢查描述
                                                classroom.description.contains(query, true) -> true
                                                else -> false
                                            }
                                        }

                                        val filteredLocations = locations.filter {
                                            searchText.isBlank() ||
                                                    it.name.contains(searchText, true) ||
                                                    it.description.contains(searchText, true)
                                        }
                                        val filteredClassrooms = classrooms.filter { matchesClassroom(it, searchText) }

                                        LazyColumn {
                                            // 顯示教室
                                            if (filteredClassrooms.isNotEmpty() || searchText.isBlank()) {
                                                item {
                                                    Text(
                                                        text = "教室清單",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                        color = Color.Gray
                                                    )
                                                }
                                                items(if (searchText.isBlank()) classrooms else filteredClassrooms) { cp ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                destText = formatClassroomName(cp.name)
                                                                destSelection = cp.location
                                                                destExpanded = false
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = formatClassroomName(cp.name),
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                            if (cp.name.endsWith("f") || cp.name.endsWith("b")) {
                                                                Text(
                                                                    text = if (cp.name.endsWith("f")) "前門" else "後門",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = Color.Gray
                                                                )
                                                            }
                                                            Text(
                                                                text = cp.description,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // 顯示其他地點
                                            if (filteredLocations.isNotEmpty()) {
                                                item {
                                                    if (filteredClassrooms.isNotEmpty()) {
                                                        Divider(
                                                            modifier = Modifier.padding(vertical = 8.dp),
                                                            color = Color.Gray.copy(alpha = 0.3f)
                                                        )
                                                    }
                                                    Text(
                                                        text = "校園地點",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                        color = Color.Gray
                                                    )
                                                }
                                                items(filteredLocations) { cp ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                destText = cp.name
                                                                destSelection = cp.location
                                                                destExpanded = false
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = cp.name,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 清除與導航按鈕
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = {
                                        startText = ""
                                        destText = ""
                                        startSelection = null
                                        destSelection = null
                                        destination = null
                                        routePoints = emptyList()
                                        travelTimeText = null
                                    },
                                    modifier = Modifier.height(35.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("清除", style = MaterialTheme.typography.labelSmall) }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        val originLatLng: LatLng? = when {
                                            startSelection != null -> startSelection
                                            startText.equals("目前位置", true) -> currentLoc
                                            startText.isNotBlank() -> customPoints.firstOrNull { it.name.equals(startText, true) }?.location
                                            else -> currentLoc
                                        }

                                        val destLatLng: LatLng? = destSelection ?: customPoints.firstOrNull { it.name.equals(destText, true) }?.location

                                        if (originLatLng == null) {
                                            errorMsg = "找不到起點位置（請確認輸入或開啟定位）"
                                            scope.launch { delay(3000); errorMsg = null }
                                            return@Button
                                        }
                                        if (destLatLng == null) {
                                            errorMsg = "請選擇有效的目的地（請從建議列表選擇）"
                                            scope.launch { delay(3000); errorMsg = null }
                                            return@Button
                                        }

                                        destination = destLatLng
                                        routePoints = emptyList()
                                        lastRerouteLoc = originLatLng
                                        travelTimeText = null
                                        drawRoute(
                                            origin = originLatLng, dest = destLatLng,
                                            onStart = { isRouting = true },
                                            onSuccess = { points -> isRouting = false; routePoints = points },
                                            onTime = { timeText -> travelTimeText = timeText },
                                            onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } }
                                        )

                                        cameraState.move(CameraUpdateFactory.newLatLng(originLatLng))
                                    },
                                    modifier = Modifier.height(35.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("導航", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }



        // 顯示「路線計算中」圓形指示器
        if (isRouting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp).background(Color.White.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small).padding(8.dp))
        }

        // 顯示費時文字於底部
        travelTimeText?.let { text ->
            Box(modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color.White.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium).align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = "預計花費：$text", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
            }
        }

        // 顯示錯誤訊息（三秒後自動消失）
        AnimatedVisibility(visible = errorMsg != null, enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -100 }, animationSpec = tween(300)), exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -100 }, animationSpec = tween(300))) {
            Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopCenter).padding(top = 16.dp)) {
                Box(modifier = Modifier.background(color = Color(0xFFFFCDD2), shape = MaterialTheme.shapes.large).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(text = errorMsg ?: "", color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }

        // 9. 顯示自訂介紹卡片（改為顯示在下方）
        AnimatedVisibility(
            visible = selectedPoint != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(tween(200))
        ) {
            // 把 Card 放在底部，並確保不會被地圖其他 overlay 完全覆蓋
            Box(modifier = Modifier.fillMaxSize()) {
                selectedPoint?.let { point ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .fillMaxWidth(0.94f)
                            .shadow(8.dp, shape = RoundedCornerShape(12.dp))
                            .zIndex(2f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = point.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = { selectedPoint = null }) { Text("關閉") }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = point.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { selectedPoint = null }) { Text("取消") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    // 導航到該地點
                                    destination = point.location
                                    routePoints = emptyList()
                                    lastRerouteLoc = currentLoc
                                    travelTimeText = null
                                    drawRoute(origin = currentLoc, dest = point.location,
                                        onStart = { isRouting = true },
                                        onSuccess = { points -> isRouting = false; routePoints = points },
                                        onTime = { timeText -> travelTimeText = timeText },
                                        onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } })
                                    selectedPoint = null
                                    cameraState.move(CameraUpdateFactory.newLatLng(point.location))
                                }) { Text("導航") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility：計算兩點距離（單位：公尺）
fun distanceBetween(a: LatLng, b: LatLng): Float {
    val result = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
    return result[0]
}

// Utility：縮放 Bitmap 並回傳給 Marker 用
fun getResizedBitmapDescriptor(context: Context, resId: Int, width: Int, height: Int): com.google.android.gms.maps.model.BitmapDescriptor {
    val imageBitmap = BitmapFactory.decodeResource(context.resources, resId)
    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(imageBitmap, width, height, false)
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(scaledBitmap)
}

/**
 * drawRoute 新增 onTime callback：從 API 回應中取出「duration.text」並回傳
 */
fun drawRoute(
    origin: LatLng?,
    dest: LatLng,
    onStart: () -> Unit = {},
    onSuccess: (List<LatLng>) -> Unit,
    onTime: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (origin == null) {
        onError("尚未取得目前位置")
        return
    }
    onStart()
    val o = "${origin.latitude},${origin.longitude}"
    val d = "${dest.latitude},${dest.longitude}"
    RetrofitInstance.api.getDirections(origin = o, destination = d, mode = "walking", apiKey = "AIzaSyDj1CTmLJMsvCTRwwVJrCFHp6Cqt7wVKp8")
        .enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
            override fun onResponse(call: Call<com.example.project250311.Map.model.DirectionsResponse>, response: Response<com.example.project250311.Map.model.DirectionsResponse>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    val route = body?.routes?.firstOrNull()
                    val leg = route?.legs?.firstOrNull()
                    val points = route?.overview_polyline?.points

                    val durationText = leg?.duration?.text ?: "未知時間"
                    onTime(durationText)

                    if (!points.isNullOrEmpty()) {
                        onSuccess(PolylineUtils.decodePolyline(points))
                    } else {
                        onError("找不到路線")
                    }
                } else {
                    onError("API 回傳 ${response.code()}")
                }
            }

            override fun onFailure(call: Call<com.example.project250311.Map.model.DirectionsResponse>, t: Throwable) {
                onError("網路錯誤：${t.localizedMessage}")
            }
        })
}