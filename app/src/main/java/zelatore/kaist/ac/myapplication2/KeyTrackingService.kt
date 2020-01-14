package zelatore.kaist.ac.myapplication2

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.HashMap

class KeyTrackingService : AccessibilityService() {
    //
    private var totalStr = ""
    private var isLocked = false
    val inputKeyObj = HashMap<Int, KeyObject>()
    internal var keyIdx = 0
    internal var keyType: String? = "E"
    internal var isChunjin: Boolean = false


    override fun onInterrupt() {}

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        val eventTypeStr = AccessibilityEvent.eventTypeToString(accessibilityEvent.eventType)

        /** 키 입력 분석  */
        Log.i("AccessibilityService", "-------------------------------")
        if (eventTypeStr == "TYPE_VIEW_TEXT_CHANGED") isLocked = true
        if (eventTypeStr == "TYPE_VIEW_TEXT_SELECTION_CHANGED" && isLocked) {
            isLocked = false
            return
        }

        if (accessibilityEvent.packageName != null) {
            val packageName = accessibilityEvent.packageName.toString()

            if (eventTypeStr == "TYPE_VIEW_TEXT_CHANGED" || eventTypeStr == "TYPE_VIEW_TEXT_SELECTION_CHANGED") {
                val accessibilityNodeInfo = accessibilityEvent.source
                trackingViewResources2(accessibilityNodeInfo)
            }

            if (eventTypeStr == "TYPE_VIEW_FOCUSED") {
                val accessibilityNodeInfo = accessibilityEvent.source
                trackingViewResources3(accessibilityNodeInfo)
            }
        }
        Log.i("AccessibilityService", "-------------------------------")
    }

    private fun trackingViewResources2(parentView: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (parentView == null) return null

        if (parentView.text != null && parentView.text.length >= 0 && parentView.className.toString().contains("EditText"))
            getCurrentInputChar(parentView.text, parentView.packageName)


        for (i in 0 until parentView.childCount) {
            val child = parentView.getChild(i)
            if (child != null)  trackingViewResources2(child)
            else                return null
        }

        return null
    }

    private fun trackingViewResources3(parentView: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (parentView == null) return null

        if (parentView.text != null && parentView.text.length >= 0 && parentView.className.toString().contains("EditText"))
            totalStr = parentView.text.toString()

        for (i in 0 until parentView.childCount) {
            val child = parentView.getChild(i)
            if (child != null)  trackingViewResources2(child)
            else                return null
        }

        return null
    }

    private fun getCurrentInputChar(str: CharSequence?, packageName: CharSequence) {
        if (str == null && totalStr.length > 0) {
            inputKeyObj[keyIdx++] = KeyObject(getAppNameByPackageName(applicationContext, packageName.toString()), "backspace", isChunjin)
            totalStr = ""
            isLocked = false
            return
        }
        val currentStr = str!!.toString()

        if (!currentStr.contains(totalStr) && !totalStr.contains(currentStr)) totalStr = ""

        val decomposeCurrentStr = hangulToJaso(currentStr)
        val decomposeTotalStr = hangulToJaso(totalStr)

        /* 사용자가 새로운 키를 입력한 경우 */
        if (decomposeCurrentStr.length >= decomposeTotalStr.length && decomposeCurrentStr.length != 0) {
            val ch = decomposeCurrentStr.get(decomposeCurrentStr.length - 1) + ""
            val currentKeyType = getInputCharType(ch)
            if (currentKeyType != keyType && keyType != null && ch != "ㆍ") {
                keyType = currentKeyType
                if (isChunjin) {
                    /* 천지인 좌표로 변환 */
                    for ((_, value) in inputKeyObj) {
                        value.setupChunjin(true)
                    }
                }

                /* 터치 이동 거리 계산*/
                var idx = 0
                var prevPosX = 0f
                var prevPosY = 0f
                for ((key, value) in inputKeyObj) {
                    idx = key

                    if (idx == 0)   value.keyDistance = "0"
                    else            value.keyDistance = calculateDistance(value.posX, prevPosX, value.posY, prevPosY)

                    Log.d("AA", "글자: " + value.inputChar + ", posX: " + value.posX + ", posY: " + value.posY + ", distance: " + value.keyDistance)

                    /**서버에 저장 */
                    val jsonEntity: String = "{\"InputTime\":"+value.eventTime+", \"AppName\":\""+value.appName+"\", \"keyType\":\""+value.inputType+"\", \"keyDistance\":"+ value.keyDistance +"}"

                    prevPosX = value.posX
                    prevPosY = value.posY
                    idx++
                }

                /* Map 초기화 */
                inputKeyObj.clear()
                keyIdx = 0
            }

            if (currentKeyType == "E") {
                isChunjin = false
                inputKeyObj[keyIdx++] = KeyObject(getAppNameByPackageName(applicationContext, packageName.toString()), ch, isChunjin)
            } else if (currentKeyType == "H") {
                inputKeyObj[keyIdx++] = KeyObject(getAppNameByPackageName(applicationContext, packageName.toString()), ch, isChunjin)
            } else if (currentKeyType == "S") {
                if (ch == "ㆍ") {
                    keyType = "H"
                    isChunjin = true
                }
            }

        } else if (decomposeCurrentStr.length < decomposeTotalStr.length || currentStr.length < totalStr.length) {
            inputKeyObj[keyIdx++] = KeyObject(getAppNameByPackageName(applicationContext, packageName.toString()), "backspace", isChunjin)
        }

        totalStr = currentStr
    }

    private fun calculateDistance(posX: Float, prevPosX: Float, posY: Float, prevPosY: Float): String {
        val ac = Math.abs(posY - prevPosY).toDouble()
        val cb = Math.abs(posX - prevPosX).toDouble()
        return String.format("%.3f", Math.hypot(ac, cb))
    }

    private fun getInputCharType(str: String): String {
        return if (str.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*".toRegex()))
            "H"
        else if (str.matches("^[a-zA-Z]*$".toRegex()))
            "E"
        else if (str.matches("^[0-9]*$".toRegex()))
            "N"
        else
            "S"
    }

    fun getAppNameByPackageName(context: Context, packageName: String): String {
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null.toString()
        }

    }

    class KeyObject(var _appName: String, var _inputChar: String, isChunjin: Boolean) {
        private val c_key = arrayOf("l", "ㆍ", "ㅡ", "backspace", "ㄱ", "ㅋ", "ㄴ", "ㄹ", "ㄷ", "ㅌ", "ㅂ", "ㅍ", "ㅅ", "ㅎ", "ㅈ", "ㅊ", ".", "?", "!", "ㅇ", "ㅁ", " ", "@", "ㅏ", "ㅑ", "ㅓ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ", "ㅡ", "ㅣ", "ㅐ", "ㅔ", "ㅒ", "ㅖ", "ㅙ", "ㅞ", "ㅘ", "ㅝ", "ㅚ", "ㅟ", "ㅢ")
        private val c_coordinateX = floatArrayOf(1f, 2f, 3f, 4f, 1f, 1f, 2f, 2f, 3f, 3f, 1f, 1f, 2f, 2f, 3f, 3f, 4f, 4f, 4f, 2f, 2f, 3f, 4f, 2f, 2f, 1f, 1f, 3f, 3f, 2f, 2f, 3f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 1f, 1f)
        private val c_coordinateY = floatArrayOf(1f, 1f, 1f, 1f, 2f, 2f, 2f, 2f, 2f, 2f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 4f, 4f, 4f, 4f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
        private val q_kor_key = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ", "ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", "ㅒ", "ㅖ", "ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ", "ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ", "backspace", "@", " ", ".")
        private val q_eng_key = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "a", "s", "d", "f", "g", "h", "j", "k", "l", "z", "x", "c", "v", "b", "n", "m", "backspace", "@", " ", ".")
        private val q_kor_coordinateX = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1f, 2f, 3f, 4f, 5f, 8f, 9f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 3f, 6f, 8.5f)
        private val q_kor_coordinateY = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 5f, 5f, 5f)
        private val q_eng_coordinateX = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 3f, 6f, 8.5f)
        private val q_eng_coordinateY = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 5f, 5f, 5f)

        var posX: Float = 0.toFloat()
        var posY: Float = 0.toFloat()
        var eventTime: Long = 0
        var inputType: String
        var appName: String
        var keyDistance: String = ""
        var inputChar: String

        init {
            this.appName = _appName
            this.inputChar = _inputChar
            this.inputType = getInputCharType(_inputChar)
            this.eventTime = System.currentTimeMillis()
            this.posX = findPositionX(_inputChar, isChunjin)
            this.posY = findPositionY(_inputChar, isChunjin)
        }

        fun setupChunjin(isChunjin: Boolean) {
            this.posX = findPositionX(this.inputChar, isChunjin)
            this.posY = findPositionY(this.inputChar, isChunjin)
        }

        private fun findPositionX(ch: String, chunjinFlag: Boolean): Float {
            if (this.inputType == "H") {
                if (chunjinFlag) {
                    for (i in c_key.indices) {
                        if (c_key[i] == ch)
                            return c_coordinateX[i]
                    }
                } else {
                    for (i in q_kor_key.indices) {
                        if (q_kor_key[i] == ch)
                            return q_kor_coordinateX[i]
                    }
                }
            } else if (this.inputType == "E") {
                for (i in q_eng_key.indices) {
                    if (q_eng_key[i] == ch)
                        return q_eng_coordinateX[i]
                }
            }
            return 0f
        }

        private fun findPositionY(ch: String, chunjinFlag: Boolean): Float {
            if (this.inputType == "H") {
                if (chunjinFlag) {
                    for (i in c_key.indices) {
                        if (c_key[i] == ch)
                            return c_coordinateY[i]
                    }
                } else {
                    for (i in q_kor_key.indices) {
                        if (q_kor_key[i] == ch)
                            return q_kor_coordinateY[i]
                    }
                }
            } else if (this.inputType == "E") {
                for (i in q_eng_key.indices) {
                    if (q_eng_key[i] == ch)
                        return q_eng_coordinateY[i]
                }
            }

            return 0f
        }

        fun getInputCharType(str: String): String {
            return if (str.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*".toRegex()))
                "H"
            else if (str.matches("^[a-zA-Z]*$".toRegex()))
                "E"
            else if (str.matches("^[0-9]*$".toRegex()))
                "N"
            else
                "S"
        }


    }

    // ㄱ             ㄲ            ㄴ               ㄷ            ㄸ             ㄹ
    // ㅁ             ㅂ            ㅃ               ㅅ            ㅆ             ㅇ
    // ㅈ             ㅉ           ㅊ                ㅋ            ㅌ              ㅍ      ㅎ
    internal val ChoSung = charArrayOf(0x3131.toChar(), 0x3132.toChar(), 0x3134.toChar(), 0x3137.toChar(), 0x3138.toChar(), 0x3139.toChar(), 0x3141.toChar(), 0x3142.toChar(), 0x3143.toChar(), 0x3145.toChar(), 0x3146.toChar(), 0x3147.toChar(), 0x3148.toChar(), 0x3149.toChar(), 0x314a.toChar(), 0x314b.toChar(), 0x314c.toChar(), 0x314d.toChar(), 0x314e.toChar())


    // ㅏ            ㅐ             ㅑ             ㅒ            ㅓ             ㅔ
    // ㅕ            ㅖ              ㅗ           ㅘ            ㅙ              ㅚ
    // ㅛ            ㅜ              ㅝ           ㅞ             ㅟ             ㅠ
    // ㅡ           ㅢ              ㅣ
    internal val JwungSung = charArrayOf(0x314f.toChar(), 0x3150.toChar(), 0x3151.toChar(), 0x3152.toChar(), 0x3153.toChar(), 0x3154.toChar(), 0x3155.toChar(), 0x3156.toChar(), 0x3157.toChar(), 0x3158.toChar(), 0x3159.toChar(), 0x315a.toChar(), 0x315b.toChar(), 0x315c.toChar(), 0x315d.toChar(), 0x315e.toChar(), 0x315f.toChar(), 0x3160.toChar(), 0x3161.toChar(), 0x3162.toChar(), 0x3163.toChar())


    //         ㄱ            ㄲ             ㄳ            ㄴ              ㄵ
    // ㄶ             ㄷ            ㄹ             ㄺ            ㄻ              ㄼ
    // ㄽ             ㄾ            ㄿ              ㅀ            ㅁ             ㅂ
    // ㅄ            ㅅ             ㅆ             ㅇ            ㅈ             ㅊ
    // ㅋ            ㅌ            ㅍ              ㅎ
    internal val JongSung = charArrayOf(0.toChar(), 0x3131.toChar(), 0x3132.toChar(), 0x3133.toChar(), 0x3134.toChar(), 0x3135.toChar(), 0x3136.toChar(), 0x3137.toChar(), 0x3139.toChar(), 0x313a.toChar(), 0x313b.toChar(), 0x313c.toChar(), 0x313d.toChar(), 0x313e.toChar(), 0x313f.toChar(), 0x3140.toChar(), 0x3141.toChar(), 0x3142.toChar(), 0x3144.toChar(), 0x3145.toChar(), 0x3146.toChar(), 0x3147.toChar(), 0x3148.toChar(), 0x314a.toChar(), 0x314b.toChar(), 0x314c.toChar(), 0x314d.toChar(), 0x314e.toChar())

    fun hangulToJaso(s: String): String {
        var a: Int
        var b: Int
        var c: Int // 자소 버퍼: 초성/중성/종성 순
        var result = ""

        for (i in 0 until s.length) {
            val ch = s[i]

            if (ch.toInt() >= 0xAC00 && ch.toInt() <= 0xD7A3) { // "AC00:가" ~ "D7A3:힣" 에 속한 글자면 분해
                c = ch.toInt() - 0xAC00
                a = c / (21 * 28)
                c = c % (21 * 28)
                b = c / 28
                c = c % 28

                result = result + ChoSung[a] + JwungSung[b]
                if (c != 0) result = result + JongSung[c] // c가 0이 아니면, 즉 받침이 있으면
            } else {
                result = result + ch
            }
        }
        return result
    }
}
