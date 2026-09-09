package com.ipterebi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ipterebi.app.ui.AppNav
import com.ipterebi.app.ui.theme.IPTerebiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as IPTerebiApp).container
        setContent {
            IPTerebiTheme {
                AppNav(container)
            }
        }
    }
}
