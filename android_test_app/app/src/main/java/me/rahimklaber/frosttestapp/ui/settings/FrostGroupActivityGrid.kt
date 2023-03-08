package me.rahimklaber.frosttestapp.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.runBlocking
import me.rahimklaber.frosttestapp.FrostPeerStatus
import me.rahimklaber.frosttestapp.FrostViewModel
import java.util.*

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
    val offsetY = 100
    val rects = remember{mutableStateListOf<Rect>()}
    var selectedRect by remember { mutableStateOf<Rect?>(null)}
    Text(text = "Activity Grid", fontSize = 18.sp)

    Canvas(modifier = Modifier
        .fillMaxWidth()
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
                val y = offsetY * (index.div(10)) * 1f
                val state = viewModel.stateMap[mid]?: FrostPeerStatus.Pending
                rects.add(Rect(x,y,100.0f, state,mid))
                drawRect(state.color, topLeft = Offset( x,y), size = Size(100.0f,100.0f))
                drawRect(Color.Black, topLeft = Offset( x,y), size = Size(100.0f,100.0f),
                    style = Stroke(width = 2.dp.toPx())
                )
//                draw(Color.Black, topLeft = Offset( x,y), size = Size(100.0f,100.0f))
            }
        }

    }
    if (selectedRect != null) {
        Text("Mid: ${selectedRect?.mid}")
        Text("Status: ${selectedRect?.status}")
        //todo this only tracks introductions
        Text(text = "last message: ${
            runCatching {
                Date().time.div(1000) - viewModel.lastHeardFrom[selectedRect?.mid]!!
            }.getOrElse { "unknown" }
        } seconds ago")
        Text(text = "Ip Address: ${
            runCatching {
            viewModel.frostManager.networkManager.getPeerFromMid(selectedRect!!.mid).wanAddress.toString()
        }.getOrElse { 
            "unknown"
            }
        }")
    }

}