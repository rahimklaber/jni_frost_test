package me.rahimklaber.frosttestapp.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import me.rahimklaber.frosttestapp.FrostPeerStatus
import me.rahimklaber.frosttestapp.FrostViewModel
import me.rahimklaber.frosttestapp.ipv8.FrostState
import me.rahimklaber.frosttestapp.ui.settings.FrostSettings

data class Rect(
    val x: Float,
    val y: Float,
    val size: Float,
    val status: FrostPeerStatus,
    val mid: String
){
    operator fun contains(offset: Offset): Boolean {
        return offset.x > x && offset.y > y && (offset.x  < x + size) && (offset.y < y + size)
    }
}

@Composable
//probably should reference the view model but shrug
fun ActivityGrid(viewModel: FrostViewModel){
    val offsetX = 100

    val rects = remember{mutableStateListOf<Rect>()}
    var selectedRect by remember { mutableStateOf<Rect?>(null)}
    Text(text = "Activity Grid", fontSize = 18.sp)

    Canvas(modifier = Modifier
        .fillMaxHeight(0.5f)
        .pointerInput(null) {
            detectTapGestures { tapoffset ->
                Log.d("FROST", "tap offset: $tapoffset")
                for (rect in rects) {
                    Log.d("FROST", "rect: $rect")
                    if (tapoffset in rect) {
                        Log.d("FROST", "offset is in rect")
                        selectedRect = rect
                        return@detectTapGestures
                    }
                }
                selectedRect = null
            }

        }
    ){
        if(viewModel.index == null)
            return@Canvas
        if (viewModel.peers.isNotEmpty()){
            rects.removeIf { true }
            for ((index, mid) in viewModel.peers.withIndex()) {
                val x = offsetX * index * 1f
                val y = 0.0f
                val state = viewModel.stateMap[mid]?: FrostPeerStatus.Pending
                rects.add(Rect(x,y,100.0f, state,mid))
                drawRect(state.color, topLeft = Offset( x,y), size = Size(100.0f,100.0f))
            }
        }

    }
    if (selectedRect != null){
        Text("Mid: ${selectedRect?.mid}")
        Text("Status: ${selectedRect?.status}")
    }

}