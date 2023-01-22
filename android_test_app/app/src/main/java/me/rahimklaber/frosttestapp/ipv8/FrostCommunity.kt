package me.rahimklaber.frosttestapp.ipv8

import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import me.rahimklaber.frosttestapp.ipv8.message.*
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.payload.IntroductionResponsePayload

data class FrostMemberInfo(
    val peer : Peer, //todo will it matter if the peer ip changes?
    val index: Int, // index in FROST scheme
)

//enum class FrostState(val id: Long){
//    NotReady,
//    ReadyForKeyGen,
//    RequestedToJoin(,
//    KeyGenStep1,
//    KeyGenStep2,
//    KeyGenStep3,
//    ReadyForSign
//}

sealed interface FrostState{
    object NotReady: FrostState
    object ReadyForKeyGen: FrostState
    data class RequestedToJoin(val id: Long): FrostState
    data class KeyGenStep1(val id: Long): FrostState
    data class KeyGenStep2(val id: Long): FrostState
    data class KeyGenStep3(val id: Long): FrostState
    object ReadyForSign: FrostState
}


data class FrostGroup(
    val members: MutableList<FrostMemberInfo>,
    val myIndex: Int
){
    val amount : Int
        get() = members.size
}

typealias OnJoinRequestCallBack = (Peer, RequestToJoinMessage) -> Unit
typealias onJoinRequestResponseCallback = (Peer, RequestToJoinResponseMessage) -> Unit

class FrostCommunity : Community() {
    override val serviceId: String
        get() = "5ce0aab9123b60537030b1312783a0ebcf5fd92f"

    val channel = MutableSharedFlow<Pair<Peer,FrostMessage>>(extraBufferCapacity = 10000) //todo check this


    init {
        //todo maybe deserialize
        messageHandlers[RequestToJoinMessage.MESSAGE_ID] = {packet ->
            val pair = packet.getAuthPayload(RequestToJoinMessage.Deserializer)
//            error("received ${pair.second}")
            //todo check this
            scope.launch {
                channel.emit(pair)
            }
        }
        messageHandlers[RequestToJoinResponseMessage.MESSAGE_ID] = {packet ->
            val pair = packet.getAuthPayload(RequestToJoinResponseMessage.Deserializer)
            //todo check this
            scope.launch {
                channel.emit(pair)
            }
        }
        messageHandlers[KeyGenCommitments.MESSAGE_ID] = { packet ->
            val pair = packet.getAuthPayload(KeyGenCommitments.Deserializer)
            //todo check this
            scope.launch {
                channel.emit(pair)
            }
        }
        messageHandlers[KeyGenShare.MESSAGE_ID] = { packet ->
            val pair = packet.getAuthPayload(KeyGenShare.Deserializer)
            //todo check this
            scope.launch {
                channel.emit(pair)
            }
        }
    }

    // better name lol
    fun sendForPublic(peer: Peer, msg: FrostMessage) {
        //todo fix this
        val id = when(msg){
            is KeyGenCommitments -> KeyGenCommitments.MESSAGE_ID
            is KeyGenShare -> KeyGenShare.MESSAGE_ID
            is RequestToJoinMessage -> RequestToJoinMessage.MESSAGE_ID
            is RequestToJoinResponseMessage -> RequestToJoinResponseMessage.MESSAGE_ID
        }
        val packet = serializePacket(id,msg)
        send(peer,packet)
    }

    fun broadcast(msg : FrostMessage){
        //todo fix this
        val id = when(msg){
            is KeyGenCommitments -> KeyGenCommitments.MESSAGE_ID
            is KeyGenShare -> KeyGenShare.MESSAGE_ID
            is RequestToJoinMessage -> RequestToJoinMessage.MESSAGE_ID
            is RequestToJoinResponseMessage -> RequestToJoinResponseMessage.MESSAGE_ID
        }
        val packet = serializePacket(id,msg)
        for (peer in getPeers()) {
            send(peer,packet)
        }
    }


}