package me.rahimklaber.frosttestapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import generated.SchnorrKeyWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import me.rahimklaber.frosttestapp.ipv8.FrostCommunity
import me.rahimklaber.frosttestapp.ipv8.FrostManager
import me.rahimklaber.frosttestapp.ipv8.Update

class FrostViewModel(
    private val frostCommunity: FrostCommunity,
    val frostManager: FrostManager
) : ViewModel() {
    var state by mutableStateOf(frostManager.state)
    var index by mutableStateOf(frostManager.frostInfo?.myIndex)
    var amountOfMembers by mutableStateOf(frostManager.frostInfo?.amount)
    var threshold by mutableStateOf(frostManager.frostInfo?.threshold)
    init {
        viewModelScope.launch {
            frostManager.updatesChannel.collect{
                when(it){
                    is Update.KeyGenDone, is Update.StartedKeyGen -> {
                        refreshFrostData()
                    }
                    is Update.TextUpdate -> TODO()
                }
            }
        }
    }

    fun refreshFrostData(){
        state = frostManager.state
        index = frostManager.frostInfo?.myIndex
        amountOfMembers = frostManager.frostInfo?.amount
        threshold = frostManager.frostInfo?.threshold
    }

    fun joinFrost(){
        viewModelScope.launch(Dispatchers.Default){
            frostManager.joinGroup()
        }
    }
}