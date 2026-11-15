package app.sitewrap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** mode: 0 = Mobile, 1 = Desktop, 2 = Smart TV (the "emulation" = which browser the site thinks it is talking to) */
data class Site(val name: String, val url: String, val mode: Int, val cursor: Boolean)

val MODES = arrayOf("Mobile", "Desktop", "Smart TV")

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val TV_UA =
    "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko) 76.0.3809.146/6.0 TV Safari/537.36"

/** Mobile = the device's own WebView UA minus the "wv" markers that make some sites refuse to load. */
fun userAgent(mode: Int, default: String): String = when (mode) {
    1 -> DESKTOP_UA
    2 -> TV_UA
    else -> default.replace("; wv", "").replace("Version/4.0 ", "")
}

private val DEFAULTS = listOf(
    Site("Wikipedia", "https://www.wikipedia.org", 0, false),
    Site("YouTube TV UI", "https://www.youtube.com/tv", 2, true),
    Site("Twitch", "https://www.twitch.tv", 1, true),
    Site("Google", "https://www.google.com", 1, true),
)

fun loadSites(c: Context): MutableList<Site> {
    val raw = c.getSharedPreferences("sites", 0).getString("list", null) ?: return DEFAULTS.toMutableList()
    val a = JSONArray(raw)
    return MutableList(a.length()) {
        val o = a.getJSONObject(it)
        Site(o.getString("n"), o.getString("u"), o.getInt("m"), o.getBoolean("c"))
    }
}

fun saveSites(c: Context, list: List<Site>) {
    val a = JSONArray()
    list.forEach { a.put(JSONObject().put("n", it.name).put("u", it.url).put("m", it.mode).put("c", it.cursor)) }
    c.getSharedPreferences("sites", 0).edit().putString("list", a.toString()).apply()
}
