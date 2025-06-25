package com.example.minrobotiot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.minrobotiot.ui.IoTControlScreen
import com.example.minrobotiot.ui.theme.MinrobotIotTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        enableEdgeToEdge()
        setContent {
            MinrobotIotTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    IoTControlScreen()
                }
            }
        }
    }
}