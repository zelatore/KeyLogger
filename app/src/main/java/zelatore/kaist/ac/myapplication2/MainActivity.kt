package zelatore.kaist.ac.myapplication2

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.RadioGroup
import android.util.Log



class MainActivity : AppCompatActivity() {
    internal var keyboardType = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val radioGroup = findViewById(R.id.keylogRadioGroup) as RadioGroup
        radioGroup.setOnCheckedChangeListener(mRadioCheck)

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
