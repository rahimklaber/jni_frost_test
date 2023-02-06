package me.rahimklaber.frosttestapp.ipv8

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import me.rahimklaber.frosttestapp.SchnorrAgent
import me.rahimklaber.frosttestapp.SchnorrAgentMessage
import me.rahimklaber.frosttestapp.SchnorrAgentOutput
import me.rahimklaber.frosttestapp.ipv8.message.*
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.util.toHex
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KFunction2

fun interface Sender{
    fun send(peer: Peer, bytes: ByteArray)
}

fun interface Broadcaster{
    fun broadcast(bytes: ByteArray)
}
/*
*         val getIndex = { mid: String ->
            midsOfNewGroup.indexOf(mid) + 1
        }
        val getMidFromIndex = { index: Int ->
            midsOfNewGroup[index-1]
        }*/

fun FrostGroup.getIndex(mid: String) = members.find { it.peer.mid == mid }?.index
fun FrostGroup.getMidForIndex(index: Int) = members.find { it.index == index }?.peer?.mid

interface NetworkManager{
    fun send(peer: Peer, msg: FrostMessage)
    fun broadcast(msg: FrostMessage)
    fun getMyPeer() : Peer
    fun getPeerFromMid(mid: String) : Peer
}
sealed interface Update{
    data class KeyGenDone(val pubkey: String) : Update
    data class StartedKeyGen(val id: Long) : Update
    data class ProposedKeyGen(val id: Long): Update
    data class TextUpdate(val text : String) : Update
}


sealed interface FrostState{
    object NotReady : FrostState {
        override fun toString(): String = "NotReady"
    }

    data class RequestedToJoin(val id: Long) : FrostState {
        override fun toString(): String = "RequestedToJoin($id)"
    }

    object ReadyForKeyGen : FrostState {
        override fun toString(): String = "ReadyForKeyGen"
    }

    data class KeyGen(val id: Long) : FrostState {
        override fun toString(): String = "KeyGen($id)"
    }

    object ReadyForSign : FrostState {
        override fun toString(): String = "ReadyForSign"
    }

    data class ProposedSign(val id: Long) : FrostState {
        override fun toString(): String = "ProposedSign($id)"
    }

    data class Sign(val id: Long) : FrostState {
        override fun toString(): String = "Sign($id)"
    }

    data class ProposedJoin(val id: Long) : FrostState{
        override fun toString(): String = "ProposedJoin($id)"
    }
}

typealias OnJoinRequestResponseCallback = (Peer, RequestToJoinResponseMessage) -> Unit
typealias KeyGenCommitmentsCallaback = (Peer, KeyGenCommitments) -> Unit
typealias KeyGenShareCallback = (Peer, KeyGenShare) -> Unit
typealias SignShareCallback = (Peer, SignShare) -> Unit
typealias PreprocessCallback = (Peer, Preprocess) -> Unit
typealias SignRequestCallback = (Peer, SignRequest) -> Unit
typealias SignRequestResponseCallback = (Peer, SignRequestResponse) -> Unit

class FrostManager(
    val receiveChannel: SharedFlow<Pair<Peer, FrostMessage>>,
    val networkManager: NetworkManager,
//    val getFrostInfo : () -> FrostGroup?,
    val updatesChannel: MutableSharedFlow<Update> = MutableSharedFlow(extraBufferCapacity = 10),
    var state: FrostState = FrostState.ReadyForKeyGen,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    var frostInfo: FrostGroup? = null
//        get() = getFrostInfo()
    val map: Map<KClass<out FrostMessage>, KFunction2<Peer, FrostMessage, Unit>> = mapOf(
        RequestToJoinMessage::class to ::processRequestToJoin,
        RequestToJoinResponseMessage::class to ::processRequestToJoinResponse,
        KeyGenCommitments::class to ::processKeyGenCommitments,
        KeyGenShare::class to ::processKeyGenShare,
        SignShare::class to ::processSignShare,
        Preprocess::class to ::processPreprocess,
        SignRequest::class to ::processSignRequest,
        SignRequestResponse::class to ::processSignRequestResponse
    ) as Map<KClass<out FrostMessage>, KFunction2<Peer, FrostMessage, Unit>>

    var keyGenJob: Job? = null
    var signJob: Job? = null

    var agent : SchnorrAgent? = null
    var agentSendChannel = Channel<SchnorrAgentMessage>(1)
    var agentReceiveChannel = Channel<SchnorrAgentOutput>(1)

    var joinId = -1L;
    var joining = false;

    val onJoinRequestResponseCallbacks = mutableMapOf<Int,OnJoinRequestResponseCallback>()
    val onKeyGenCommitmentsCallBacks = mutableMapOf<Int,KeyGenCommitmentsCallaback>()
    val onKeyGenShareCallbacks = mutableMapOf<Int,KeyGenShareCallback>()
    val onPreprocessCallbacks = mutableMapOf<Int,PreprocessCallback>()
    val onSignShareCallbacks = mutableMapOf<Int,SignShareCallback>()
    val onSignRequestCallbacks = mutableMapOf<Int, SignRequestCallback>()
    val onSignRequestResponseCallbacks = mutableMapOf<Int, SignRequestResponseCallback>()
    private var cbCounter = 0;

    fun addJoinRequestResponseCallback(cb: OnJoinRequestResponseCallback) : Int{
        val id = cbCounter++
        onJoinRequestResponseCallbacks[id] = cb
        return id
    }
    fun addKeyGenCommitmentsCallbacks(cb : KeyGenCommitmentsCallaback) : Int{
        val id = cbCounter++
        onKeyGenCommitmentsCallBacks[id] = cb
        return id
    }
    fun addKeyGenShareCallback(cb: KeyGenShareCallback) : Int{
        val id = cbCounter++
        onKeyGenShareCallbacks[id] = cb
        return id
    }
    fun removeJoinRequestResponseCallback(id: Int){
        onJoinRequestResponseCallbacks.remove(id)
    }
    fun addSignShareCallback(cb: SignShareCallback) : Int{
        val id = cbCounter++
        onSignShareCallbacks[id] = cb
        return id
    }
    fun addPreprocessCallabac(cb: PreprocessCallback) : Int{
        val id = cbCounter++
        onPreprocessCallbacks[id] = cb
        return id
    }
    fun addOnSignRequestCallback(cb: SignRequestCallback) : Int{
        val id = cbCounter++
        onSignRequestCallbacks[id] = cb
        return id
    }
    fun addOnSignRequestResponseCallbac(cb: SignRequestResponseCallback) : Int{
        val id = cbCounter++
        onSignRequestResponseCallbacks[id] = cb
        return id
    }

    init {
        scope.launch {
            receiveChannel
                .collect {
//                    Log.d("FROST", "collected msg ${it.second}")
                    processMsg(it)
                }
        }

    }

    suspend fun proposeSign(data: ByteArray){
        when(state){
            FrostState.ReadyForSign -> Unit
            else -> {
                updatesChannel.emit(Update.TextUpdate("not ready for sign"))
                return
            }
        }
        val signId = Random.nextLong()
        state = FrostState.ProposedSign(signId)


        var responseCounter = 0
        val mutex = Mutex(true)
        val callbacId = addOnSignRequestResponseCallbac { peer, signRequestResponse ->
            if(signRequestResponse.ok)
                responseCounter+=1
            if (responseCounter >= frostInfo!!.threshold - 1){
                mutex.unlock()
            }
        }
        networkManager.broadcast(SignRequest(signId,data))
        mutex.lock()// make sure that enough peeps are available

        onSignRequestResponseCallbacks.remove(callbacId)

        Log.d("FROST","started sign")
        signJob = startSign(signId,data,true)

    }

    private suspend fun startSign(signId: Long,data: ByteArray, isProposer: Boolean = false) = scope.launch {
        state = FrostState.Sign(signId)

        val mutex = Mutex(true)

        addSignShareCallback{peer, msg ->
            launch {
                agentSendChannel.send(SchnorrAgentOutput.SignShare(msg.bytes,frostInfo?.getIndex(peer.mid)!!))
            }
        }
        addPreprocessCallabac { peer, preprocess ->
            launch {
                if (!isProposer && mutex.isLocked)
                    mutex.unlock()
                agentSendChannel.send(SchnorrAgentOutput.SignPreprocess(preprocess.bytes,frostInfo?.getIndex(peer.mid)!!))
            }
        }

        launch {
            for (output in agentReceiveChannel){
                when(output){
                    is SchnorrAgentOutput.SignPreprocess -> networkManager.broadcast(Preprocess(output.preprocess))
                    is SchnorrAgentOutput.SignShare -> networkManager.broadcast(SignShare(output.share))
                    is SchnorrAgentOutput.Signature -> updatesChannel.emit(Update.TextUpdate("sign done: ${output.signature.toHex()}"))
                    else -> {}
                }
            }
        }
        if(!isProposer)
            mutex.lock()
        agent!!.startSigningSession(signId.toInt(),data, byteArrayOf())
        state = FrostState.ReadyForSign
        cancel()

    }

    private suspend fun startKeyGen(id: Long, peersInGroup: List<Peer>, isNew: Boolean = false) = scope.launch{
        joinId = id

        agentSendChannel = Channel(10)
        agentReceiveChannel = Channel(10)

        val amount = peersInGroup.size
        val midsOfNewGroup = peersInGroup
            .map(Peer::mid)
            .sorted()
        Log.d("FROST", "new group size : ${peersInGroup.size}")
        val getIndex = { mid: String ->
            midsOfNewGroup.indexOf(mid) + 1
        }
        val getMidFromIndex = { index: Int ->
            midsOfNewGroup[index-1]
        }

        val index = getIndex(networkManager.getMyPeer().mid)

        agent = SchnorrAgent(amount,index,peersInGroup.size / 2 + 1, agentSendChannel, agentReceiveChannel)

        val mutex = Mutex(true)
        addKeyGenCommitmentsCallbacks{ peer, msg ->
            launch {
                if (!isNew && mutex.isLocked)
                    mutex.unlock()
                agentSendChannel.send(SchnorrAgentMessage.KeyCommitment(msg.byteArray,getIndex(peer.mid)))
            }
        }
        addKeyGenShareCallback { peer, keyGenShare ->
            launch {
                agentSendChannel.send(SchnorrAgentMessage.DkgShare(keyGenShare.byteArray,getIndex(peer.mid)))
            }
        }
        launch {
            for (agentOutput in agentReceiveChannel) {
                Log.d("FROST", "sending $agentOutput")
                when(agentOutput){
                    is SchnorrAgentOutput.DkgShare -> networkManager.send(networkManager.getPeerFromMid(getMidFromIndex(agentOutput.forIndex)),KeyGenShare(joinId,agentOutput.share))
                    is SchnorrAgentOutput.KeyCommitment -> networkManager.broadcast(KeyGenCommitments(joinId,agentOutput.commitment))
                    is SchnorrAgentOutput.KeyGenDone -> updatesChannel.emit(Update.KeyGenDone(agentOutput.pubkey.toHex()))
                    else -> {
                        error("RCEIVED OUTPUT FOR SIGNING WHILE DOING KEYGEN. SHOULD NOT HAPPEN")
                    }
                }
            }
        }
        if(!isNew)
            mutex.lock()
        agent!!.startKeygen()

        this@FrostManager.frostInfo = FrostGroup(
            (peersInGroup.filter { it.mid != networkManager.getMyPeer().mid }).map {
                FrostMemberInfo(
                    it,
                    getIndex(it.mid)
                )
            },
            index,
            threshold = peersInGroup.size / 2 + 1
        )

        state = FrostState.ReadyForSign
        //cancel when done
        cancel()
    }
    suspend fun joinGroup(){
        joinId = Random.nextLong()
        joining = true
        when(state){
            FrostState.NotReady,FrostState.ReadyForKeyGen -> {
                state = FrostState.ProposedJoin(joinId)
                updatesChannel.emit(Update.ProposedKeyGen(joinId))
                networkManager.broadcast(RequestToJoinMessage(joinId))
            }
            else -> {
                return
                // send update to UI?
            }
        }

        val peersInGroup = waitForJoinResponse(joinId)
        state = FrostState.KeyGen(joinId)
        keyGenJob =  startKeyGen(joinId,peersInGroup + networkManager.getMyPeer(),true)
        Log.i("FROST","started keygen")
    }

    private suspend fun waitForJoinResponse(id: Long): List<Peer> {
        var counter = 0;
        val mutex = Mutex(true)
        val peers = mutableListOf<Peer>()
        var amount: Int?
        val cbId = addJoinRequestResponseCallback{ peer, msg ->
            if (msg.id ==id){
                amount = msg.amountOfMembers
                peers.add(peer)
                counter++
                if (counter == amount)
                    mutex.unlock()
            }
        }
        mutex.lock()
        removeJoinRequestResponseCallback(cbId)
        return peers
    }

    fun processMsg(pair: Pair<Peer, FrostMessage>) {
        val (peer, msg) = pair
        Log.d("FROST","received msg $msg")
        map[msg::class]?.let { it(peer, msg) }

    }

    private fun processRequestToJoin(peer: Peer, msg: RequestToJoinMessage) {
        when(state){
            FrostState.ReadyForKeyGen, FrostState.ReadyForSign -> {
                state = FrostState.KeyGen(msg.id)
                scope.launch {
                    updatesChannel.emit(Update.StartedKeyGen(msg.id))

                    networkManager.broadcast(RequestToJoinResponseMessage(msg.id, true, frostInfo?.amount ?: 1,frostInfo?.members?.map { it.peer.mid } ?: listOf(
                        networkManager.getMyPeer().mid
                    )))
                    keyGenJob = startKeyGen(msg.id,
                        frostInfo?.members?.map(FrostMemberInfo::peer)?.plus(peer)
                            ?: (listOf(networkManager.getMyPeer()) + peer)
                    )
                }
            }
            else -> {
                // log cannot do this while in this state?
                // Maybe I should send a message bac to indicate this?
                // Actually I probably should
                networkManager.send(peer, RequestToJoinResponseMessage(msg.id,false,0, listOf()))
            }
        }
    }

    private fun processRequestToJoinResponse(peer: Peer, msg: RequestToJoinResponseMessage) {
        onJoinRequestResponseCallbacks.forEach {
            it.value(peer,msg)
        }
//        when(state){
//            is FrostState.RequestedToJoin ->{
//                if (msg.id != (state as FrostState.RequestedToJoin).id){
//                    //todo deal with this
//                    // send back a msg?
//                    return
//                }
//                state =
//            }
//        }
    }
    private fun processKeyGenCommitments(peer: Peer, msg: KeyGenCommitments){
        onKeyGenCommitmentsCallBacks.forEach {
            it.value(peer,msg)
        }
    }
    private fun processKeyGenShare(peer: Peer, msg: KeyGenShare){
        onKeyGenShareCallbacks.forEach {
            it.value(peer,msg)
        }
    }

    private fun processSignShare(peer: Peer, msg: SignShare){
        onSignShareCallbacks.forEach{
            it.value(peer,msg)
        }
    }
    private fun processPreprocess(peer: Peer, msg: Preprocess){
        onPreprocessCallbacks.forEach {
            it.value(peer,msg)
        }
    }
    private fun processSignRequest(peer: Peer, msg: SignRequest){
        onSignRequestCallbacks.forEach {
            it.value(peer,msg)
        }
        when(state){
            FrostState.ReadyForSign -> {
                scope.launch {
                    updatesChannel.emit(Update.TextUpdate("startet sign"))
                    networkManager.send(peer,SignRequestResponse(msg.id,true))
                    startSign(msg.id,msg.data)
                }
            }
                else -> {}
        }
    }
    private fun processSignRequestResponse(peer: Peer, msg: SignRequestResponse){
        onSignRequestResponseCallbacks.forEach {
            it.value(peer,msg)
        }
    }

}