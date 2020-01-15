package kr.ac.kaist.jypark.keylogger

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.RadioGroup
import android.util.Log


class MainActivity : AppCompatActivity() {
    internal var keyboardType = ""      // 사용자가 선택한 키보드 유형 정보를 저장하는 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val radioGroup = findViewById(R.id.keylogRadioGroup) as RadioGroup
        radioGroup.setOnCheckedChangeListener(mRadioCheck)

        /* accessibility 서비스 허용이 되어 있는지 체크해서 안되어 있으면 세팅창으로 이동*/
        if (!isAccessibilitySettingsOn(applicationContext))
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    var mRadioCheck: RadioGroup.OnCheckedChangeListener =
        RadioGroup.OnCheckedChangeListener { group, checkedId ->
            if (group.id == R.id.keylogRadioGroup) {
                when (checkedId) {
                    R.id.qwertyBtn -> {
                        keyboardType = "Q"
                        Log.i("Activity", "Select Qwerty")
                    }
                    R.id.chujinBtn -> {
                        keyboardType = "C"
                        Log.i("Activity", "Select Chunjin")
                    }
                }
            }
        }

    /* accessibility 서비스 세팅이 활성화되어 있는지 체크하는 함수 */
    private fun isAccessibilitySettingsOn(mContext: Context): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + KeyTrackingService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                mContext.applicationContext.contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) { }

        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')

        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                mContext.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) return true
                }
            }
        }

        return false
    }


}
