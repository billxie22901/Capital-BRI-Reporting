package com.capitalbri.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.capitalbri.android.ui.AppNavigation
import com.capitalbri.android.ui.theme.CapitalBRITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CapitalBRITheme {
                AppNavigation()
            }
        }
    }
}
