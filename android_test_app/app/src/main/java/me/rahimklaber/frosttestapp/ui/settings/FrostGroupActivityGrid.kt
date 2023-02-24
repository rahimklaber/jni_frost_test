package me.rahimklaber.frosttestapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import me.rahimklaber.frosttestapp.FrostPeerStatus
import me.rahimklaber.frosttestapp.FrostViewModel
import me.rahimklaber.frosttestapp.ui.settings.FrostSettings

@Composable
//probably should reference the view model but shrug
fun ActivityGrid(viewModel: FrostViewModel){
    val offsetX = 0
    Canvas(modifier = Modifier.fillMaxSize()){
        if(viewModel.index == null)
            return@Canvas
        for (mid in viewModel.peers) {
//            drawRect(Color.Green, size = Size(100.0f,100.0f))
            val state = viewModel.stateMap[mid]?: FrostPeerStatus.Pending
            drawRect(state.color, topLeft = Offset( offsetX *.0f,0.0f), size = Size(100.0f,100.0f))
        }

    }
}