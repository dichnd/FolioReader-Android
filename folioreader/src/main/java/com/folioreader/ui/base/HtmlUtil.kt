package com.folioreader.ui.base

import android.content.Context
import com.folioreader.Config
import com.folioreader.Constants
import com.folioreader.R

/**
 * @author gautam chibde on 14/6/17.
 */
object HtmlUtil {

    /**
     * Function modifies input html string by adding extra css,js and font information.
     *
     * @param context     Activity Context
     * @param htmlContent input html raw data
     * @return modified raw html string
     */
    fun getHtmlContent(context: Context, htmlContent: String, config: Config): String {
        val cssPath = String.format(context.getString(R.string.css_tag), "file:///android_asset/css/Style.css")
        var jsPath = arrayOf(
            "jsface.min",
            "jquery-3.1.1.min",
            "rangy-core",
            "rangy-highlighter",
            "rangy-classapplier",
            "rangy-serializer",
            "Bridge",
            "rangefix",
            "readium-cfi.umd"
        ).joinToString("\n", postfix = "\n") {
            String.format(
                context.getString(R.string.script_tag),
                "file:///android_asset/js/$it.js")
        }

        jsPath += String.format(
            context.getString(R.string.script_tag_method_call),
            "setMediaOverlayStyleColors('#C0ED72','#C0ED72')"
        ) + "\n"

        jsPath += "<meta name=\"viewport\" content=\"height=device-height, user-scalable=no\" />"

        val toInject = "\n$cssPath\n$jsPath\n</head>"
        var html = htmlContent.replace("</head>", toInject)

        var classes =
        when (config.font) {
            Constants.FONT_ANDADA -> "andada"
            Constants.FONT_LATO -> "lato"
            Constants.FONT_LORA -> "lora"
            Constants.FONT_RALEWAY -> "raleway"
            else -> ""
        }

        if (config.isNightMode) {
            classes += " nightMode"
        }

        when (config.fontSize) {
            0 -> classes += " textSizeOne"
            1 -> classes += " textSizeTwo"
            2 -> classes += " textSizeThree"
            3 -> classes += " textSizeFour"
            4 -> classes += " textSizeFive"
            else -> {
            }
        }

        html = html.replace(
            "<html", "<html class=\"" + classes + "\"" +
                    " onclick=\"onClickHtml()\""
        )
        return html
    }
}
