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
import me.rahimklaber.frosttestapp.database.FrostDatabase
import me.rahimklaber.frosttestapp.database.Me
import me.rahimklaber.frosttestapp.database.ReceivedMessage
import me.rahimklaber.frosttestapp.database.Request
import me.rahimklaber.frosttestapp.ipv8.message.*
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.Key
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.toHex
import java.util.*
import kotlin.math.sign
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

fun FrostGroup.getIndex(mid: String) = members.find { it.peer == mid }?.index
fun FrostGroup.getMidForIndex(index: Int) = members.find { it.index == index }?.peer

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
    data class SignRequestReceived(val id: Long, val fromMid: String ,val data: ByteArray) : Update
    data class SignDone(val id: Long, val signature: String) : Update
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
    val db: FrostDatabase,
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

    val signJobs = mutableMapOf<Long, Job>()

    var agent : SchnorrAgent? = null
    var agentSendChannel = Channel<SchnorrAgentMessage>(1)
    var agentReceiveChannel = Channel<SchnorrAgentOutput>(1)

    var joinId = -1L;
    var joining = false;

    lateinit var dbMe: Me

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
                    Log.d("FROST", "received msg in frostmanager ${it.second}")
                    db.receivedMessageDao()
                        .insertReceivedMessage(
                            ReceivedMessage(
                                unixTime = Date().time / 1000,
                                type = messageIdFromMsg(it.second),
                                messageId = it.second.id,
                                data = it.second.serialize(),
                                fromMid = it.first.mid,
                            )
                        )
                    if (messageIdFromMsg(it.second) == SignRequest.MESSAGE_ID){
                        db.requestDao()
                            .insertRequest(
                                Request(
                                    unixTime = Date().time / 1000,
                                    type = messageIdFromMsg(it.second),
                                    requestId = it.second.id,
                                    data = it.second.serialize(),
                                    fromMid = it.first.mid,
                                )
                            )
                    }
                    processMsg(it)
                }
        }

        scope.launch(Dispatchers.Default) {
            val storedMe = db.meDao().get()

            if (storedMe != null){
                SchnorrAgent(storedMe.frostKeyShare,storedMe.frostN,storedMe.frostIndex,storedMe.frostThresholod, agentSendChannel, agentReceiveChannel)
                dbMe = storedMe
//                delay(5000)
                frostInfo = FrostGroup(
                    members = storedMe.frostMembers.map {
                        FrostMemberInfo(
                           it,
                            -1//todo
                        )
                    },
                    threshold = dbMe.frostThresholod,
                    myIndex = dbMe.frostIndex
                )
                state = FrostState.ReadyForSign
            }else{
                dbMe = Me(
                    -1,
                    byteArrayOf(0),0,1,1, listOf("")
                )
            }
        }

    }

    suspend fun proposeSignAsync(data: ByteArray): Pair<Boolean,Long> {
        // we want to make multiple props at the same time?
        if(state !is FrostState.ReadyForSign){
            return false to 0
        }

        val signId = Random.nextLong()
//        state = FrostState.ProposedSign(signId)

        val job = scope.launch {
            var responseCounter = 0
            val mutex = Mutex(true)
            val participatingIndices = mutableListOf<Int>()
            val callbacId = addOnSignRequestResponseCallbac { peer, signRequestResponse ->
                if (signRequestResponse.id != signId){
                    return@addOnSignRequestResponseCallbac
                }
                if (signRequestResponse.ok)
                    responseCounter += 1
                participatingIndices.add(
                    frostInfo?.getIndex(peer.mid)
                        ?: error(" FrostInfo is null. This is a bug. Maybe you are trying to sign without having first joined a group")
                )
                if (responseCounter >= frostInfo!!.threshold - 1) {
                    mutex.unlock()
                }
            }
            networkManager.broadcast(SignRequest(signId, data))
            mutex.lock()// make sure that enough peeps are available

            onSignRequestResponseCallbacks.remove(callbacId)

            Log.d("FROST", "started sign")

            val agentSendChannel = Channel<SchnorrAgentMessage>(1)
            val agentReceiveChannel = Channel<SchnorrAgentOutput>(1)

            signJob = startSign(
                signId, data,
                agentSendChannel, agentReceiveChannel,
                true,
                (participatingIndices.plus(
                    frostInfo?.myIndex
                        ?: error(" FrostInfo is null. This is a bug. Maybe you are trying to sign without having first joined a group")
                ))
            )
        }

        signJobs[signId] = job
        return true to signId
    }

    suspend fun acceptProposedSign(id: Long, fromMid: String, data: ByteArray){
        networkManager.send(networkManager.getPeerFromMid(fromMid),SignRequestResponse(id,true))
        val agentSendChannel = Channel<SchnorrAgentMessage>(1)
        val agentReceiveChannel = Channel<SchnorrAgentOutput>(1)

        signJobs[id] = startSign(
            id,
            data,
            agentSendChannel, agentReceiveChannel
        )
    }


//    suspend fun proposeSign(data: ByteArray){
//        when(state){
//            FrostState.ReadyForSign -> Unit
//            else -> {
//                updatesChannel.emit(Update.TextUpdate("not ready for sign"))
//                return
//            }
//        }
//        val signId = Random.nextLong()
//        state = FrostState.ProposedSign(signId)
//
//
//        var responseCounter = 0
//        val mutex = Mutex(true)
//        val participatingIndices = mutableListOf<Int>()
//        val callbacId = addOnSignRequestResponseCallbac { peer, signRequestResponse ->
//            if (signRequestResponse.ok)
//                responseCounter += 1
//            participatingIndices.add(
//                frostInfo?.getIndex(peer.mid)
//                    ?: error(" FrostInfo is null. This is a bug. Maybe you are trying to sign without having first joined a group")
//            )
//            if (responseCounter >= frostInfo!!.threshold - 1) {
//                mutex.unlock()
//            }
//        }
//        networkManager.broadcast(SignRequest(signId, data))
//        mutex.lock()// make sure that enough peeps are available
//
//        onSignRequestResponseCallbacks.remove(callbacId)
//
//        Log.d("FROST", "started sign")
//         startSign(
//            signId, data, true,
//            (participatingIndices.plus(
//                frostInfo?.myIndex
//                    ?: error(" FrostInfo is null. This is a bug. Maybe you are trying to sign without having first joined a group")
//            ))
//        )
//
//    }

    private suspend fun startSign(
        signId: Long,
        data: ByteArray,
        agentSendChannel : Channel<SchnorrAgentMessage> ,
        agentReceiveChannel :  Channel<SchnorrAgentOutput>,
        isProposer: Boolean = false,
        participantIndices: List<Int> = listOf(),
    ) = scope.launch {
        state = FrostState.Sign(signId)

        val mutex = Mutex(true)

        // whether we received a preprocess msg from the initiator
        // this signals the other peers to start
        var receivedFromInitiator = false
        val participantIndices = participantIndices.toMutableList()

        addSignShareCallback { peer, msg ->
            if (msg.id  != signId)
                return@addSignShareCallback
            launch {
                agentSendChannel.send(
                    SchnorrAgentOutput.SignShare(
                        msg.bytes,
                        frostInfo?.getIndex(peer.mid)!!
                    )
                )
            }
        }
        addPreprocessCallabac { peer, preprocess ->
            if(preprocess.id != signId){
                return@addPreprocessCallabac
            }
            launch {
                if (!isProposer && mutex.isLocked && preprocess.participants.isNotEmpty()) { // only the init message has size > 0
                    mutex.unlock()
                    receivedFromInitiator = true
                    participantIndices.addAll(preprocess.participants)
                }
                agentSendChannel.send(
                    SchnorrAgentOutput.SignPreprocess(
                        preprocess.bytes,
                        frostInfo?.getIndex(peer.mid)!!,
                    )
                )
            }
        }

        launch {
            for (output in agentReceiveChannel){
                when (output) {
                    is SchnorrAgentOutput.SignPreprocess -> sendToParticipants(participantIndices,
                        Preprocess(
                            signId,
                            output.preprocess,
                            if (isProposer) {
                                participantIndices
                            } else {
                                listOf()
                            }
                        )
                    )
                    is SchnorrAgentOutput.SignShare -> sendToParticipants(participantIndices,SignShare(signId,output.share))
                    is SchnorrAgentOutput.Signature -> updatesChannel.emit(Update.SignDone(signId,output.signature.toHex()))
                    else -> {}
                }
            }
        }
        if(!isProposer)
            mutex.lock()
        agent!!.startSigningSession(signId.toInt(),data, byteArrayOf(), agentSendChannel,agentReceiveChannel)
        state = FrostState.ReadyForSign
        cancel()

    }

    private suspend fun startKeyGen(id: Long, midsOfNewGroup: List<String>, isNew: Boolean = false) = scope.launch{
        joinId = id

        agentSendChannel = Channel(10)
        agentReceiveChannel = Channel(10)

        val amount = midsOfNewGroup.size
        val midsOfNewGroup = midsOfNewGroup
            .sorted()
        Log.d("FROST", "new group size : ${midsOfNewGroup.size}")
        val getIndex = { mid: String ->
            midsOfNewGroup.indexOf(mid) + 1
        }
        val getMidFromIndex = { index: Int ->
            midsOfNewGroup[index-1]
        }

        val index = getIndex(networkManager.getMyPeer().mid)

        agent = SchnorrAgent(amount,index,midsOfNewGroup.size / 2 + 1, agentSendChannel, agentReceiveChannel)

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
                    is SchnorrAgentOutput.KeyGenDone -> {
                        state = FrostState.ReadyForSign
                        updatesChannel.emit(Update.KeyGenDone(agentOutput.pubkey.toHex()))
                    }
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
            (midsOfNewGroup.filter { it != networkManager.getMyPeer().mid }).map {
                FrostMemberInfo(
                    it,
                    getIndex(it)
                )
            },
            index,
            threshold = midsOfNewGroup.size / 2 + 1
        )

        dbMe = dbMe.copy(
            frostKeyShare = agent!!.keyWrapper.serialize(),
            frostMembers = midsOfNewGroup.filter { it != networkManager.getMyPeer().mid },
            frostN = midsOfNewGroup.size,
            frostThresholod = midsOfNewGroup.size / 2 + 1
        )

        db.meDao()
            .insert(dbMe)

        state = FrostState.ReadyForSign
        //cancel when done
        cancel()
    }
    suspend fun joinGroup(){
        joinId = Random.nextLong()
        joining = true
        if (!(state == FrostState.NotReady || state == FrostState.ReadyForKeyGen)) {
            return
        }
        state = FrostState.ProposedJoin(joinId)
        updatesChannel.emit(Update.ProposedKeyGen(joinId))

        scope.launch(Dispatchers.Default) {
            // delay to start waiting before sending msg
            delay(1000)
            networkManager.broadcast(RequestToJoinMessage(joinId))
        }
        val peersInGroup = waitForJoinResponse(joinId)
        state = FrostState.KeyGen(joinId)
        keyGenJob =  startKeyGen(joinId,(peersInGroup + networkManager.getMyPeer()).map { it.mid },true)
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

                    networkManager.broadcast(RequestToJoinResponseMessage(msg.id, true, frostInfo?.amount ?: 1,
                        (frostInfo?.members?.map { it.peer }
                            ?.plus(networkManager.getMyPeer().mid))
                            ?: listOf(
                                networkManager.getMyPeer().mid
                            )))
                    keyGenJob = startKeyGen(msg.id,
                        frostInfo?.members?.map(FrostMemberInfo::peer)?.plus(peer.mid)?.plus(networkManager.getMyPeer().mid)
                            ?: (listOf(networkManager.getMyPeer()) + peer).map { it.mid }
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

    private fun sendToParticipants(participantIndices: List<Int>, frostMessage: FrostMessage){
        (participantIndices - (frostInfo?.myIndex ?: error("frostinfo is null. this is a bug.")))
            .forEach{
            networkManager.send(networkManager.getPeerFromMid(frostInfo?.getMidForIndex(it) ?: error("frostinfo null, this is a bug")), frostMessage)
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
                    updatesChannel.emit(Update.SignRequestReceived(msg.id,peer.mid,msg.data))
//                    updatesChannel.emit(Update.TextUpdate("startet sign"))
//                    networkManager.send(peer,SignRequestResponse(msg.id,true))
//                    startSign(msg.id,msg.data)
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