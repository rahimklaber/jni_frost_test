package me.rahimklaber.frosttestapp.ipv8

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import me.rahimklaber.frosttestapp.database.FrostDatabase
import me.rahimklaber.frosttestapp.ipv8.message.*
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.eva.takeInRange
import nl.tudelft.ipv8.messaging.payload.IntroductionResponsePayload
import java.util.Date

data class FrostMemberInfo(
    val peer : String, //use mid instead of peer. if the peer is offline, then `Peer` wont wor
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



data class FrostGroup(
    // members is without us
    val members: List<FrostMemberInfo>,
    val myIndex: Int,
    val threshold: Int
){
    val amount : Int
        get() = members.size + 1 // we are not included here
}

//typealias OnJoinRequestCallBack = (Peer, RequestToJoinMessage) -> Unit
//typealias onJoinRequestResponseCallback = (Peer, RequestToJoinResponseMessage) -> Unit

class FrostCommunity: Community() {
    override val serviceId: String
        get() = "5ce0aab9123b60537030b1312783a0ebcf5fd92f"

    private val _channel = MutableSharedFlow<Pair<Peer,FrostMessage>>(extraBufferCapacity = 10000) //todo check this
    //todo this should be a mutable flow
    fun getMsgChannel(): Flow<Pair<Peer, FrostMessage>> {
        return _channel.filter {(peer, msg) ->
            val contains = received.containsKey(peer.mid to msg.hashCode())
            if (!contains){
                received[peer.mid to msg.hashCode()]=true
                true
            }else{false}
        }
    }
    val lastResponseFrom = mutableMapOf<String,Date>()

    override fun onIntroductionResponse(peer: Peer, payload: IntroductionResponsePayload) {
        super.onIntroductionResponse(peer, payload)
        lastResponseFrom[peer.mid] = Date()
    }

    lateinit var  db: FrostDatabase
    fun initDb(db: FrostDatabase){
        this.db = db
    }
    init {
        //todo maybe deserialize
        messageHandlers[RequestToJoinMessage.MESSAGE_ID] = {packet ->
            val pair = packet.getAuthPayload(RequestToJoinMessage.Deserializer)
//            error("received ${pair.second}")
            //todo check this
            scope.launch {
                _channel.emit(pair)
            }
        }
        messageHandlers[RequestToJoinResponseMessage.MESSAGE_ID] = {packet ->
            val pair = packet.getAuthPayload(RequestToJoinResponseMessage.Deserializer)
            //todo check this
            scope.launch {
                _channel.emit(pair)
            }
        }
        messageHandlers[KeyGenCommitments.MESSAGE_ID] = { packet ->
            val pair = packet.getAuthPayload(KeyGenCommitments.Deserializer)
            //todo check this
            scope.launch {
                _channel.emit(pair)
            }
        }
        messageHandlers[KeyGenShare.MESSAGE_ID] = { packet ->
            val pair = packet.getAuthPayload(KeyGenShare.Deserializer)
            //todo check this
            scope.launch {
                _channel.emit(pair)
            }
        }
        messageHandlers[Preprocess.MESSAGE_ID] = { packet ->
            val pair = packet.getAuthPayload(Preprocess.Deserializer)
            //todo check this
            scope.launch {
                _channel.emit(pair)
            }
        }
        messageHandlers[SignShare.MESSAGE_ID] = { packet ->
            val pair = packet.getAuthPayload(SignShare.Deserializer)
            //todo check this
            scope.launch {
                _channel.emit(pair)
            }
        }
        messageHandlers[SignRequest.MESSAGE_ID] = { packet ->
            val pair = packet.getAuthPayload(SignRequest.Deserializer)
            //todo check this
            scope.launch {
                _channel.emit(pair)
            }
        }
        messageHandlers[SignRequestResponse.MESSAGE_ID] = { packet ->
            val pair = packet.getAuthPayload(SignRequestResponse.Deserializer)
            //todo check this
            scope.launch {
                _channel.emit(pair)
            }

        }
        messageHandlers[GossipRequest.MESSAGE_ID] = { packet ->
            val (peer,msg) = packet.getAuthPayload(GossipRequest.Deserializer)
            //todo check this
            scope.launch {
                db.requestDao()
                    .getNotDoneAndReceivedAfterTime(msg.afterUnixTime.toInt())
                    .forEach {
                        launch {
                            delay(20)
                            send(peer,it.data)
                        }
                    }
            }

        }

        messageHandlers[GossipResponse.MESSAGE_ID] = {packet ->
            val (peer,msg) = packet.getAuthPayload(GossipResponse.Deserializer)
            getPeers().find{
                it.mid == msg.originallyFromMid
            }// so if we now the peer, then we should do something. Otherwise it probably doesn't matter
                ?.also {originalPeer ->
                scope.launch {
                    _channel.emit(originalPeer to msg.payload)
                }
            }
        }


        evaProtocolEnabled = true
    }

    override fun load() {
        super.load()
        setOnEVAReceiveCompleteCallback { peer, info, id, data ->
            if (data == null){
                Log.d("FROST","received eva data, but is null")
            }
            Log.d("FROST","RECEIVED data via EVA type msg = ${data!![0].toInt()}")
            if (info!= EVA_FROST_DAO_attachment)
                return@setOnEVAReceiveCompleteCallback
            data.let {
                val packet = Packet(peer.address,data.takeInRange(1,data.size))

                messageHandlers[data[0].toInt()]?.let { it1 -> it1(packet) }
            }
        }
        scope.launch {
        val delayAmount = 5 * 60 * 1000L
          while (true){
              delay(delayAmount)
              //after 2 min ( so everything loads), as send gossiprequest
              val request = GossipRequest(Date().time / 1000)
              val packet = serializePacket(GossipRequest.MESSAGE_ID,request)
              for (peer in getPeers()) {
                  scope.launch(Dispatchers.Default) {
                      send(peer,packet)
                  }
                  sent[peer.mid to request.hashCode()] = true
              }
          }
          }
    }


    fun sendEva(peer: Peer, msg: FrostMessage){
        val id = messageIdFromMsg(msg)
        val packet = listOf(id.toByte()) + serializePacket(id,msg).toList()
        evaSendBinary(peer, EVA_FROST_DAO_attachment,"${msg.id}$id",packet.toByteArray())
    }
    fun broadcastEva(msg : FrostMessage){
        val id = messageIdFromMsg(msg)
        val packet = listOf(id.toByte()) + serializePacket(id,msg).toList()
        for (peer in getPeers()) {
            evaSendBinary(peer, EVA_FROST_DAO_attachment,"${msg.id}$id",packet.toByteArray())
        }
    }

    // store that we received a msg.
    // (mid,hash) -> Boolean
    val sent = mutableMapOf<Pair<String,Int>,Boolean>()
    val received  = mutableMapOf<Pair<String,Int>,Boolean>()
    // better name lol
    fun sendForPublic(peer: Peer, msg: FrostMessage) {
        val id = messageIdFromMsg(msg)
        val packet = serializePacket(id,msg)
        scope.launch(Dispatchers.Default) {
            repeat(10){
                send(peer,packet)
            }
        }
        sent[peer.mid to msg.hashCode()] = true

    }




    fun broadcast(msg : FrostMessage){
        //todo fix this

        val packet = serializePacket(messageIdFromMsg(msg),msg)
        for (peer in getPeers()) {
           scope.launch(Dispatchers.Default) {
               repeat(10){
                   delay(50)
                   send(peer,packet)
               }
           }
            sent[peer.mid to msg.hashCode()] = true
        }
    }



    companion object{
        const val EVA_FROST_DAO_attachment = "eva_frost_attachment"
    }


}