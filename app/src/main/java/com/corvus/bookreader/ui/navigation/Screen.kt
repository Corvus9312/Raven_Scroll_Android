package ravens.scroll.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Recent  : Screen("recent")
    data object Drive   : Screen("drive")
    data class  Reader(val uri: String = "{uri}", val isDrive: Boolean = false) :
        Screen("reader/{uri}/{isDrive}") {
        fun buildRoute(uri: String, isDrive: Boolean = false): String =
            "reader/${URLEncoder.encode(uri, "UTF-8")}/$isDrive"
    }

    companion object {
        fun readerRoute(uri: String, isDrive: Boolean): String =
            "reader/${URLEncoder.encode(uri, "UTF-8")}/$isDrive"

        fun decodeUri(encoded: String): String =
            URLDecoder.decode(encoded, "UTF-8")
    }
}
