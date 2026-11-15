package app.sitewrap

import android.app.Activity
import android.app.AlertDialog
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.net.Uri

/** Launcher: a grid of site tiles + a "+" tile. D-pad friendly (GridView handles focus). Long-press / long-OK = edit/delete. */
class MainActivity : Activity() {
    private lateinit var sites: MutableList<Site>
    private val isTv get() = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = sites.size + 1
        override fun getItem(i: Int) = i
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, v: View?, p: ViewGroup): View {
            val s = sites.getOrNull(i)
            return TextView(this@MainActivity).apply {
                text = s?.let { "${it.name}\n${Uri.parse(it.url).host ?: it.url}  ·  ${MODES[it.mode]}" } ?: "+  Add site"
                setTextColor(Color.WHITE)
                textSize = if (s == null) 20f else 17f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply { setColor(0xFF2B2F36.toInt()); cornerRadius = dp(12).toFloat() }
                layoutParams = AbsListViewParams(dp(110))
            }
        }
    }

    private fun AbsListViewParams(h: Int) = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sites = loadSites(this)
        val grid = GridView(this).apply {
            setBackgroundColor(0xFF14161A.toInt())
            numColumns = if (isTv) 4 else 2
            horizontalSpacing = dp(12); verticalSpacing = dp(12)
            setPadding(dp(24), dp(24), dp(24), dp(24)); clipToPadding = false
            // focus ring, drawn over the tile; also serves as the pressed state
            selector = GradientDrawable().apply { setStroke(dp(4), Color.WHITE); cornerRadius = dp(12).toFloat(); setColor(0) }
            setDrawSelectorOnTop(true)
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, i, _ -> if (i == sites.size) edit(null) else open(sites[i]) }
            setOnItemLongClickListener { _, _, i, _ -> if (i < sites.size) { menu(i); true } else false }
        }
        setContentView(grid)
    }

    private fun open(s: Site) = startActivity(
        Intent(this, SiteActivity::class.java).putExtra("url", s.url).putExtra("mode", s.mode).putExtra("cursor", s.cursor)
    )

    private fun menu(i: Int) = AlertDialog.Builder(this).setItems(arrayOf("Edit", "Delete")) { _, w ->
        if (w == 0) edit(i) else { sites.removeAt(i); commit() }
    }.show()

    private fun commit() { saveSites(this, sites); adapter.notifyDataSetChanged() }

    private fun edit(i: Int?) {
        val old = i?.let { sites[it] }
        val name = EditText(this).apply { hint = "Name"; setText(old?.name) }
        val url = EditText(this).apply { hint = "https://example.com"; setText(old?.url); inputType = InputType.TYPE_TEXT_VARIATION_URI }
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, MODES)
            setSelection(old?.mode ?: if (isTv) 1 else 0)
        }
        val cursor = CheckBox(this).apply { text = "Remote-control cursor (D-pad)"; isChecked = old?.cursor ?: isTv }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
            listOf(name, url, mode, cursor).forEach { addView(it) }
        }
        AlertDialog.Builder(this).setTitle(if (old == null) "Add site" else "Edit site").setView(box)
            .setPositiveButton("Save") { _, _ ->
                var u = url.text.toString().trim()
                if (u.isEmpty()) return@setPositiveButton
                if (!u.contains("://")) u = "https://$u"
                val n = name.text.toString().trim().ifEmpty { Uri.parse(u).host ?: u }
                val s = Site(n, u, mode.selectedItemPosition, cursor.isChecked)
                if (i == null) sites.add(s) else sites[i] = s
                commit()
            }
            .setNegativeButton("Cancel", null).show()
    }
}
