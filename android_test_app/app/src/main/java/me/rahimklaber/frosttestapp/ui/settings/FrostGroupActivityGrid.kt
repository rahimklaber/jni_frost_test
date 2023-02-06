package me.rahimklaber.frosttestapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun ActivityGrid(){
    Canvas(modifier = Modifier.fillMaxSize()){
        drawRect(Color.Green, size = Size(100.0f,100.0f))
        drawRect(Color.Green, topLeft = Offset(100.0f,0.0f), size = Size(100.0f,100.0f))
    }
}