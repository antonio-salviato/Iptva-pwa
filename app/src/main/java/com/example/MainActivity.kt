package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iptv.ui.screens.HomeScreen
import com.example.iptv.viewmodel.IPTVViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val iptvViewModel: IPTVViewModel = viewModel()
                HomeScreen(viewModel = iptvViewModel)
            }
        }
    }
}
