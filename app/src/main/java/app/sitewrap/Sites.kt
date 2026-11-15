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

/** Same JSON shape the admin panel serves: [{"name","url","mode","cursor"}] */
fun parseSites(raw: String): MutableList<Site> {
    val a = JSONArray(raw)
    return MutableList(a.length()) {
        val o = a.getJSONObject(it)
        Site(o.getString("name"), o.getString("url"), o.optInt("mode", 0), o.optBoolean("cursor", false))
    }
}

fun loadSites(c: Context): MutableList<Site> =
    c.getSharedPreferences("sites", 0).getString("list", null)?.let { parseSites(it) } ?: DEFAULTS.toMutableList()

fun saveSites(c: Context, list: List<Site>) {
    val a = JSONArray()
    list.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url).put("mode", it.mode).put("cursor", it.cursor)) }
    c.getSharedPreferences("sites", 0).edit().putString("list", a.toString()).apply()
}

/** Optional: put your admin panel address here before building (e.g. "http://192.168.1.10:8080") to skip typing it on a TV. */
const val DEFAULT_SYNC = ""

fun syncBase(c: Context): String = c.getSharedPreferences("sites", 0).getString("sync", DEFAULT_SYNC) ?: ""
fun setSyncBase(c: Context, v: String) = c.getSharedPreferences("sites", 0).edit().putString("sync", v).apply()
