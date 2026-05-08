package com.baseras.fieldpharma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.baseras.fieldpharma.nav.AppNav
import com.baseras.fieldpharma.ui.theme.FieldPharmaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FieldPharmaTheme {
                AppNav()
            }
        }
    }
}
