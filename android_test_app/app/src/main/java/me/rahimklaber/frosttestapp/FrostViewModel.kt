package me.rahimklaber.frosttestapp

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goterl.lazysodium.interfaces.Sign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import me.rahimklaber.frosttestapp.ipv8.FrostCommunity
import me.rahimklaber.frosttestapp.ipv8.FrostManager
import me.rahimklaber.frosttestapp.ipv8.Update
import me.rahimklaber.frosttestapp.ipv8.message.*

sealed interface Proposal{
    val fromMid: String

    fun type() : String
}

data class SignProposal(
    override val fromMid: String,
    val msg: ByteArray
) : Proposal {
    override fun type(): String = "Sign"
}

data class JoinProposal(
    override val fromMid: String
) : Proposal {
    override fun type(): String = "Join"

}


class FrostViewModel(
    private val frostCommunity: FrostCommunity,
    val frostManager: FrostManager
) : ViewModel() {
    var state by mutableStateOf(frostManager.state)
    var index by mutableStateOf(frostManager.frostInfo?.myIndex)
    var amountOfMembers by mutableStateOf(frostManager.frostInfo?.amount)
    var threshold by mutableStateOf(frostManager.frostInfo?.threshold)

    private var _peers = mutableStateOf<List<String>>(listOf())
    val peers by _peers
    //todo figure out how to hide this from consumers and make it a non-mutable list
    val proposals = mutableStateListOf<Proposal>()

    init {
        viewModelScope.launch(Dispatchers.Default) {

            launch {
                while (true){
                    delay(5000)
                    _peers.value = frostCommunity.getPeers().map { it.mid }
                }
            }
            launch {
                frostCommunity
                    .channel
                    .filter {
                        it.second is RequestToJoinMessage || it.second is SignRequest
                    }
                    .collect{
                        when(it.second){
                            is RequestToJoinMessage -> {
                                proposals.add(JoinProposal(it.first.mid))
                            }
                            is SignRequest -> {
                                proposals.add(SignProposal(it.first.mid, (it.second as SignRequest).data))
                            }
                            else -> Unit
                        }
                    }
            }
            frostManager.updatesChannel.collect{
                Log.d("FROST","received msg in viewmodel : $it")
                when(it){
                    is Update.KeyGenDone, is Update.StartedKeyGen, is Update.ProposedKeyGen-> {
                        refreshFrostData()
                    }
                    is Update.TextUpdate -> {

                    }
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
