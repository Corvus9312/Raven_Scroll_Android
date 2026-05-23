package ravens.scroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ravens.scroll.ui.navigation.AppNavigation
import ravens.scroll.ui.theme.BookReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openUri = intent?.data?.toString()

        setContent {
            BookReaderTheme {
                AppNavigation(openFileUri = openUri)
            }
        }
    }
}
