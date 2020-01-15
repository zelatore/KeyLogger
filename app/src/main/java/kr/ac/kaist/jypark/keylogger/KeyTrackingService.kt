package kr.ac.kaist.jypark.keylogger

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.HashMap

class KeyTrackingService : AccessibilityService() {


//    val inputKeyObj = HashMap<Int, KeyObject>()
//    internal var keyIdx = 0
//    internal var keyType: String? = "E"
//    internal var isChunjin: Boolean = false


    override fun onInterrupt() {}

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        val eventTypeStr = AccessibilityEvent.eventTypeToString(accessibilityEvent.eventType)

        /* line 26~31:
        *  TYPE_VIEW_TEXT_SELECTION_CHANGED 이벤트가 동시에 여러번 트래킹되어 같은 작업을 되풀이하는 것을 방지하기 위한 로직
        * */
        if (eventTypeStr == "TYPE_VIEW_TEXT_CHANGED") isLocked = true
        if (eventTypeStr == "TYPE_VIEW_TEXT_SELECTION_CHANGED" && isLocked) {
            isLocked = false
            return
        }

        if (accessibilityEvent.packageName != null) {
            /* 새로운 키를 입력할 때 발생되는 이벤트 */
            if (eventTypeStr == "TYPE_VIEW_TEXT_CHANGED" || eventTypeStr == "TYPE_VIEW_TEXT_SELECTION_CHANGED") {
                val accessibilityNodeInfo = accessibilityEvent.source
                trackingNewInputKey(accessibilityNodeInfo)
            }

            /* 키 입력을 위해 EditText창을 터치할 때 발생되는 이벤트.
            *  TYPE_VIEW_FOCUSED 이벤트를 트래킹하는 목적은 터치한 EditText에 예전에 입력했던 키들이 남아 있는지 체크하기 위함
            * */
            if (eventTypeStr == "TYPE_VIEW_FOCUSED") {
                val accessibilityNodeInfo = accessibilityEvent.source
                checkRemainKeysAndTrackingNewInputKey(accessibilityNodeInfo)
            }
        }

    }

    /* 새로 입력한 키 정보(키 타입, 거리 등)를 분석하기 위해 입력 진행 중인 EditText 트래킹 */
    private fun trackingNewInputKey(parentView: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (parentView == null) return null

        /* 현재 입력이 진행 중인 EditText를 트래킹 */
        if (parentView.text != null && parentView.text.length >= 0 && parentView.className.toString().contains("EditText"))
            analyzeEditTextString(parentView.text, parentView.packageName)    // 새로 입력한 키 값을 분석

        for (i in 0 until parentView.childCount) {
            val child = parentView.getChild(i)
            if (child != null)  trackingNewInputKey(child)
            else                return null
        }

        return null
    }

    /* 현재 focus되어 있는 EditText를 찾아서 기존에 남아있던 입력문자들을 가져온 후, 기존 입력 문자들을 기준으로 새로 입력한 키 정보(키 타입, 거리 등)를 분석 */
    private fun checkRemainKeysAndTrackingNewInputKey(parentView: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (parentView == null) return null

        /* 현재 focus되어 있는(사용자가 터치한) EditText를 트래킹 */
        if (parentView.text != null && parentView.text.length >= 0 && parentView.className.toString().contains("EditText"))
            totalStr = parentView.text.toString()

        for (i in 0 until parentView.childCount) {
            val child = parentView.getChild(i)
            if (child != null)  trackingNewInputKey(child)
            else                return null
        }

        return null
    }

    /* 새로 입력한 키를 분석하는 함수 */
    private fun analyzeEditTextString(str: CharSequence?, packageName: CharSequence) {
        /* backspace를 입력해서 EditText에 남아있는 글자가 모두 없어진 경우
        *  이 경우를 조건식으로 표현하면, 현재 EditText 창에 문자열(str 변수)는 null이면서 새로운 키 입력 전까지의 문자열(totalStr 변수) 길이는 1 */
        if (str == null && totalStr.length > 0) {
            totalStr = ""
            isLocked = false
            return
        }

        /* 현재 EditText에 입력되어있는 문자열 가져오기 */
        val currentStr = str!!.toString()
        //if (!currentStr.contains(totalStr) && !totalStr.contains(currentStr)) totalStr = ""


        /* 현재 EditText에 있는 문자열(currentStr 변수)과 이전까지 입력된 문자열(totalStr 변수)에 대한 자소 분석
        *  예. 입력문자열: 간다  -> 자소분석 결과: ㄱ ㅏ ㄴ ㄷ ㅏ  -> 자소분석 결과를 decomposeCurrentStr에 저장
        *  두 문자열 자소분석 후에 길이를 서로 비교하여, 새로 입력한 키 값에 대한 구체적인 분석을 수행
        *  */
        val decomposeCurrentStr = hangulToJaso(currentStr)
        val decomposeTotalStr = hangulToJaso(totalStr)

        /* 자소분석 결과를 가지고 현재 입력한 키 정보를 분석 */
        analyzeInputKey(decomposeCurrentStr, decomposeTotalStr, currentStr)

        totalStr = currentStr
    }

    /* 현재 입력한 키를 분석하는 함수 (키 타입은 무엇인지, 이전 입력한 키와의 거리는 얼마인지 등) */
    private fun analyzeInputKey(decomposeCurrentStr: String, decomposeTotalStr: String, currentStr: String) {
        /* 자소분석된 문자열 길이를 비교하여 현재 입력한 키가 일반 문자인지 backspace인지를 판단
        *  아래 if문은 입력한 키가 일반문자(backspace 아님)인지를 판별하여 일반문자인 경우 구체적인 분석을 수행 */
        if (decomposeCurrentStr.length >= decomposeTotalStr.length && decomposeCurrentStr.length != 0) {
            /* 키 타입 정보 추출 */
            currentInputKeyType = getInputKeyType(decomposeCurrentStr)

            /* 키 입력 거리(이전 키 값과 비교)정보 추출 */
            keyDistance = getInputKeyDistance(previousInputKey, currentInputKey)


        }
        /* backspace 입력을 한 경우 */
        else if (decomposeCurrentStr.length < decomposeTotalStr.length || currentStr.length < totalStr.length) {
            /* 키 타입 정보 추출 */
            currentInputKeyType = "S"

            /* 키 입력 거리(이전 키 값과 비교)정보 추출 */
            keyDistance = "0"
        }

        /** 서버에 저장할 데이터 값 **/
        Log.i("Accessibility", "Current Time: "+System.currentTimeMillis()+", InputType: "+ currentInputKeyType+", KeyDistance: "+ keyDistance)

        previousInputKey = currentInputKey
        previousInputKeyType = currentInputKeyType
    }

    /* 현재 입력한 키의 타입(예. 한글, 영어 등)을 알아내는 함수 */
    private fun getInputKeyType(decomposeCurrentStr: String): String {
        /* 마지막 자소값(현재 입력한 키 값)을 추출 */
        currentInputKey = decomposeCurrentStr.get(decomposeCurrentStr.length - 1) + ""
        return getCharType(currentInputKey)
    }

    /* 현재 입력한 키와 이전 입력 키 간 거리(좌표 거리)를 구하는 함수 */
    private fun getInputKeyDistance(previousInputKey: String, currentInputKey: String): String  {
        var tmpKeyDistance = "0"
        /* 입력 키 타입이 변경된 경우 (예. 한글 -> 영어): 거리값 0
        *  입력 키가 한글 또는 영어가 아닌 경우(트래킹 대상에서 제외): 거리값 0
        * */
        if((previousInputKeyType != currentInputKeyType) || currentInputKeyType == "N" || currentInputKeyType == "S") return "0"
        else {

            /* 이전 입력 키의 키보드 좌표를 구함
               두번째 파라미터: false -> 쿼티 키보드 좌표계, true -> 천지인 키보드 좌표계
             */
            val previousInputKeyPosX = findPositionX(previousInputKey, previousInputKeyType, isChunjin)
            val previousInputKeyPosY = findPositionY(previousInputKey, previousInputKeyType, isChunjin)

            /* 현재 입력 키의 키보드 좌표를 구함 */
            val currentInputKeyPosX = findPositionX(currentInputKey, currentInputKeyType, isChunjin)
            val currentInputKeyPosY = findPositionY(currentInputKey, currentInputKeyType, isChunjin)

            tmpKeyDistance = calculateDistance(currentInputKeyPosX, previousInputKeyPosX, currentInputKeyPosY, previousInputKeyPosY)
        }

        return tmpKeyDistance
    }

    /* 입력 키 타입과 키보드 유형을 토대로 x 좌표 값을 얻음 */
    private fun findPositionX(ch: String, chType: String, chunjinFlag: Boolean): Float {
        if (chType == "H") {
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
        } else {
            for (i in q_eng_key.indices) {
                if (q_eng_key[i] == ch)
                    return q_eng_coordinateX[i]
            }
        }

        return 0f
    }

    /* 입력 키 타입과 키보드 유형을 토대로 y 좌표 값을 얻음 */
    private fun findPositionY(ch: String, chType: String, chunjinFlag: Boolean): Float {
        if (chType == "H") {
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
        } else {
            for (i in q_eng_key.indices) {
                if (q_eng_key[i] == ch)
                    return q_eng_coordinateY[i]
            }
        }

        return 0f
    }

    /* 두 좌표 간 거리를 계산하는 함수*/
    private fun calculateDistance(posX: Float, prevPosX: Float, posY: Float, prevPosY: Float): String {
        val ac = Math.abs(posY - prevPosY).toDouble()
        val cb = Math.abs(posX - prevPosX).toDouble()
        return String.format("%.3f", Math.hypot(ac, cb))
    }

    /* 현재 입력한 키의 타입(예. 한글, 영어 등)을 반환하는 함수 */
    private fun getCharType(str: String): String {
        return if (str.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*".toRegex()))    "H"    // 한글
        else if (str.matches("^[a-zA-Z]*$".toRegex()))                 "E"    // 영어
        else if (str.matches("^[0-9]*$".toRegex()))                    "N"    // 숫자
        else                                                            "S"   // 특수문자
    }


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


    companion object {
        private const val isChunjin = true  // 키보드 유형을 나타내는 변수 (true: 천지인 키보드, false: 쿼티 키보드)
        private var totalStr = ""           // 현재 입력이 있기 전까지 EditText에 남아있던 문자열
        private var previousInputKey = ""   // 이전(직전)에 입력했던 키 값
        private var previousInputKeyType = ""   // 이전(직전)에 입력했던 키의 타입
        private var currentInputKey  = ""       // 현재 입력한 키 값
        private var currentInputKeyType = ""    // 현재 입력한 키의 타입
        private var keyDistance = "";           // 입력 키(현재 키 vs 이전 키) 간 거리 (String 타입으로 저장)
        private var isLocked = false            // accessibility 이벤트(TYPE_VIEW_TEXT_SELECTION_CHANGED)가 여러번 트리거되는 것을 방지하기 위한 flag 변수



        /* 각 키보드 유형별 키 좌표 */
        private val c_key = arrayOf("l", "ㆍ", "ㅡ", "backspace", "ㄱ", "ㅋ", "ㄴ", "ㄹ", "ㄷ", "ㅌ", "ㅂ", "ㅍ", "ㅅ", "ㅎ", "ㅈ", "ㅊ", ".", "?", "!", "ㅇ", "ㅁ", " ", "@", "ㅏ", "ㅑ", "ㅓ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ", "ㅡ", "ㅣ", "ㅐ", "ㅔ", "ㅒ", "ㅖ", "ㅙ", "ㅞ", "ㅘ", "ㅝ", "ㅚ", "ㅟ", "ㅢ")
        private val c_coordinateX = floatArrayOf(1f, 2f, 3f, 4f, 1f, 1f, 2f, 2f, 3f, 3f, 1f, 1f, 2f, 2f, 3f, 3f, 4f, 4f, 4f, 2f, 2f, 3f, 4f, 2f, 2f, 1f, 1f, 3f, 3f, 2f, 2f, 3f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 1f, 1f)
        private val c_coordinateY = floatArrayOf(1f, 1f, 1f, 1f, 2f, 2f, 2f, 2f, 2f, 2f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 4f, 4f, 4f, 4f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
        private val q_kor_key = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ", "ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", "ㅒ", "ㅖ", "ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ", "ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ", "backspace", "@", " ", ".")
        private val q_eng_key = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "a", "s", "d", "f", "g", "h", "j", "k", "l", "z", "x", "c", "v", "b", "n", "m", "backspace", "@", " ", ".")
        private val q_kor_coordinateX = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1f, 2f, 3f, 4f, 5f, 8f, 9f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 3f, 6f, 8.5f)
        private val q_kor_coordinateY = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 5f, 5f, 5f)
        private val q_eng_coordinateX = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 3f, 6f, 8.5f)
        private val q_eng_coordinateY = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 5f, 5f, 5f)

        /*한글 초성,중성,종성 별 ASCII 코드*/
        internal val ChoSung = charArrayOf(0x3131.toChar(), 0x3132.toChar(), 0x3134.toChar(), 0x3137.toChar(), 0x3138.toChar(), 0x3139.toChar(), 0x3141.toChar(), 0x3142.toChar(), 0x3143.toChar(), 0x3145.toChar(), 0x3146.toChar(), 0x3147.toChar(), 0x3148.toChar(), 0x3149.toChar(), 0x314a.toChar(), 0x314b.toChar(), 0x314c.toChar(), 0x314d.toChar(), 0x314e.toChar())
        internal val JwungSung = charArrayOf(0x314f.toChar(), 0x3150.toChar(), 0x3151.toChar(), 0x3152.toChar(), 0x3153.toChar(), 0x3154.toChar(), 0x3155.toChar(), 0x3156.toChar(), 0x3157.toChar(), 0x3158.toChar(), 0x3159.toChar(), 0x315a.toChar(), 0x315b.toChar(), 0x315c.toChar(), 0x315d.toChar(), 0x315e.toChar(), 0x315f.toChar(), 0x3160.toChar(), 0x3161.toChar(), 0x3162.toChar(), 0x3163.toChar())
        internal val JongSung = charArrayOf(0.toChar(), 0x3131.toChar(), 0x3132.toChar(), 0x3133.toChar(), 0x3134.toChar(), 0x3135.toChar(), 0x3136.toChar(), 0x3137.toChar(), 0x3139.toChar(), 0x313a.toChar(), 0x313b.toChar(), 0x313c.toChar(), 0x313d.toChar(), 0x313e.toChar(), 0x313f.toChar(), 0x3140.toChar(), 0x3141.toChar(), 0x3142.toChar(), 0x3144.toChar(), 0x3145.toChar(), 0x3146.toChar(), 0x3147.toChar(), 0x3148.toChar(), 0x314a.toChar(), 0x314b.toChar(), 0x314c.toChar(), 0x314d.toChar(), 0x314e.toChar())
    }
}
