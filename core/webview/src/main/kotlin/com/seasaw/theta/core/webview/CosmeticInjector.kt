package com.seasaw.theta.core.webview

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Hides parts of a page, such as cookie consent pop-ups, with CSS. Only hides; never clicks, accepts or removes
 * anything, so a site that wants consent simply sees none given.
 */
interface CosmeticFilter {
    /** False skips all page scripting, so turning the feature off costs nothing. */
    val isActive: Boolean

    /**
     * The selectors to hide on [pageUrl], given the ids and classes the page uses, or an empty list. Runs on the main
     * thread, so it must only look things up.
     */
    fun selectorsFor(pageUrl: String, ids: List<String>, classes: List<String>): HiddenSelectors

    companion object {
        val None = object : CosmeticFilter {
            override val isActive = false
            override fun selectorsFor(pageUrl: String, ids: List<String>, classes: List<String>) = HiddenSelectors.Empty
        }
    }
}

/**
 * @param all every selector to hide
 * @param probes the ones known to match something on the page, used to tell whether a pop-up was actually hidden
 */
class HiddenSelectors(val all: List<String>, val probes: List<String>) {
    companion object {
        val Empty = HiddenSelectors(emptyList(), emptyList())
    }
}

/**
 * Runs a [CosmeticFilter] on a page. The page lists the ids and classes it uses, the filter picks the matching rules,
 * and one style sheet goes back in. Run again on later passes, it replaces that sheet, so pop-ups added after load
 * are caught without stacking up styles.
 */
internal object CosmeticInjector {
    fun run(view: WebView, filter: CosmeticFilter) {
        if (!filter.isActive) return
        val pageUrl = view.url ?: return
        if (!pageUrl.startsWith("https://") && !pageUrl.startsWith("http://")) return
        view.evaluateJavascript(COLLECT) { result ->
            // The page may have navigated while it was collecting. Those names belong to the old page.
            if (view.url != pageUrl) return@evaluateJavascript
            val names = parse(result) ?: return@evaluateJavascript
            val hidden = filter.selectorsFor(pageUrl, names.first, names.second)
            if (hidden.all.isEmpty()) return@evaluateJavascript
            view.evaluateJavascript(applyScript(hidden), null)
        }
    }

    private fun parse(result: String?): Pair<List<String>, List<String>>? = try {
        val json = JSONObject(result ?: return null)
        json.getJSONArray("i").strings() to json.getJSONArray("c").strings()
    } catch (ignored: JSONException) {
        null
    }

    private fun JSONArray.strings(): List<String> = List(length()) { optString(it) }.filter { it.isNotEmpty() }

    /** Both values are passed as JSON literals, which escapes anything that could end the script early. */
    private fun applyScript(hidden: HiddenSelectors): String {
        val css = hidden.all.joinToString("") { "$it{display:none!important}" }
        val probes = JSONArray(hidden.probes.take(MAX_PROBES)).toString()
        return "($APPLY)(${JSONObject.quote(css)},$probes)"
    }

    private const val MAX_PROBES = 40

    /** Capped, so a page with a huge DOM costs a few milliseconds at most. */
    private const val COLLECT = """(function(){try{var ids={},cls={},n=0,max=3000;
var els=document.querySelectorAll('[id],[class]');
for(var i=0;i<els.length&&n<max;i++){var e=els[i];
if(typeof e.id==='string'&&e.id){ids[e.id]=1;n++;}
var c=e.classList;if(c){for(var j=0;j<c.length;j++){cls[c[j]]=1;n++;}}}
return {i:Object.keys(ids),c:Object.keys(cls)};}catch(x){return null;}})()"""

    /**
     * Adds or replaces Theta's style sheet. Pop-ups often lock scrolling while open, so if one was really there and
     * fixed on screen, scrolling is given back too.
     */
    private const val APPLY = """function(css,probes){try{
var s=document.getElementById('theta-cn');
if(!s){s=document.createElement('style');s.id='theta-cn';(document.head||document.documentElement).appendChild(s);}
s.textContent=css;var hit=false;
for(var i=0;i<probes.length&&!hit;i++){try{var el=document.querySelector(probes[i]);
if(el&&getComputedStyle(el).position==='fixed'){hit=true;}}catch(x){}}
if(hit){[document.documentElement,document.body].forEach(function(e){
if(e&&getComputedStyle(e).overflow==='hidden'){e.style.setProperty('overflow','auto','important');}});}
}catch(x){}}"""
}
