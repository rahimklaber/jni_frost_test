package me.rahimklaber.frosttestapp

import androidx.lifecycle.ViewModel
import generated.SchnorrKeyWrapper
import kotlinx.coroutines.channels.Channel
import me.rahimklaber.frosttestapp.ipv8.FrostCommunity
import me.rahimklaber.frosttestapp.ipv8.FrostState

class MainViewModel(
    private val frostCommunity: FrostCommunity
) : ViewModel() {
    private var schnorrAgent : SchnorrAgent? = null

    // will attempt to join a frost group
    suspend fun joinFrostGroup() {
        // Otherwise we are allready trying to join/joined
//        check(frostCommunity.state == FrostState.ReadyForKeyGen)



        val sendChannel = Channel<SchnorrAgentMessage>(1)
        var receiveMessage = Channel<SchnorrKeyWrapper>(1)



    }
}