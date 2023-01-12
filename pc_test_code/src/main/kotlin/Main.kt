import generated.SchnorrSingleSignTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptOpCodes
import org.bitcoinj.wallet.SendRequest
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import kotlin.coroutines.CoroutineContext

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
fun String.hexToBytes(): ByteArray {
    if (length % 2 != 0) throw IllegalArgumentException("String length must be even")
     val HEX_CHARS = "0123456789abcdef"

    val result = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        val firstIndex = HEX_CHARS.indexOf(this[i].toLowerCase())
        val secondIndex = HEX_CHARS.indexOf(this[i + 1].toLowerCase())

        val octet = firstIndex.shl(4).or(secondIndex)
        result[i.shr(1)] = octet.toByte()
    }

    return result
}

sealed interface SchnorrAgentMessage{
    data class KeyCommitment(val commitment: ByteArray, val fromIndex: Int) : SchnorrAgentMessage
    data class DkgShare(val share: ByteArray, val fromIndex: Int) : SchnorrAgentMessage

}

sealed interface SchnorrAgentOutput{

    data class KeyCommitment(val commitment: ByteArray, val fromIndex: Int) : SchnorrAgentOutput

    data class DkgShare(val share: ByteArray, val fromIndex: Int, val forIndex: Int) : SchnorrAgentOutput

    data class KeyGenDone(val index: Int, val pubkey: ByteArray): SchnorrAgentOutput

    data class SignPreprocess(val preprocess: ByteArray, val fromIndex: Int) : SchnorrAgentOutput, SchnorrAgentMessage

    data class SignShare(val share:ByteArray, val fromIndex: Int): SchnorrAgentOutput, SchnorrAgentMessage

    data class Signature(val signature : ByteArray) : SchnorrAgentOutput
}




suspend fun main(args: Array<String>) = withContext(Dispatchers.IO){
    try {
        System.load("C:\\Users\\Rahim\\Desktop\\jni_frost_test\\src\\main\\resources\\mobcore.dll")

    } catch (e: UnsatisfiedLinkError) {
        println(e)
        System.err.println("Exiting: Could not link mobcore library.")
//        return
    }
    val params = RegTestParams()
//    val keystuff =
//        KeyChainGroup.builder(params).fromRandom(Script.ScriptType.P2PKH).build()
//    val wallet = Wallet(params,keystuff)
//
//    val blockchain = BlockChain(params,wallet, MemoryBlockStore(params))
//    val peerGroup = PeerGroup(params,blockchain)
//    peerGroup.addWallet(wallet)
//    peerGroup.start()
    val kit = object : WalletAppKit(params, File("."), "__xd") {
         override fun onSetupCompleted() {
            // This is called in a background thread after startAndWait is called, as setting up various objects
            // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
            // on the main thread.
            if (wallet().keyChainGroupSize < 1) wallet().importKey(ECKey())
        }
    }

//    val localHost = InetAddress.getLocalHost()
//   kit.setPeerNodes(PeerAddress(params, localHost, params.port))
    kit.connectToLocalHost()
// Download the block chain and wait until it's done.
    kit.startAsync()
    Thread.sleep(1000)
//    kit.wallet().addCoinsinofReceivedEventListener { wallet, tx, prevBalance, newBalance ->
//        println("received coins")
//    }

    val amount = 11
    val threshold = 2

    val startTime = System.currentTimeMillis()

    val inputChannels = mutableListOf<Channel<SchnorrAgentMessage>>()
    val outputChannel = Channel<SchnorrAgentOutput>()
    val agents = IntRange(1,amount)
        .map  {
            val channel = Channel<SchnorrAgentMessage>()
            inputChannels.add(channel)
            SchnorrAgent(amount,it,threshold, channel, outputChannel)
        }

    var donecount = 0
    val keyGenDone = Mutex(true)
    // this is actually the tweaked pubkey for key spend
    var pubKey = ByteArray(0)
    var sig = ByteArray(0)
    repeat(1){
        launch {
            for(msg in outputChannel){
                when(msg){
                    is SchnorrAgentOutput.DkgShare -> {
                        val (bytes, fromIndex ,forIndex) = msg
//                println("$fromIndex sending dkgshare to $forIndex")
                        launch{
                            inputChannels[forIndex-1].send(SchnorrAgentMessage.DkgShare(bytes, fromIndex))
                        }
                    }
                    is SchnorrAgentOutput.KeyCommitment -> {
                        val (bytes, from) = msg
                         inputChannels.mapIndexed {index, it ->
                            if (from == index+1)
                                return@mapIndexed
                            launch{
                                it.send(SchnorrAgentMessage.KeyCommitment(bytes,from))
                            }
                        }

                    }
                    is SchnorrAgentOutput.KeyGenDone -> {
                        donecount++


                        if (donecount == amount){
                            keyGenDone.unlock()
                            pubKey = msg.pubkey
                            println("keyGen done!")
                        }
                    }

                    is SchnorrAgentOutput.SignPreprocess -> {
                        val (_,from) = msg
                        inputChannels.take(threshold).forEachIndexed {index, channel ->
                            if (from == index+1)
                                return@forEachIndexed
                            launch {
                                channel.send(msg)
                            }
                        }
                    }

                    is SchnorrAgentOutput.SignShare -> {
                        if (msg.fromIndex == 1)
                            continue
                        inputChannels.first().send(msg)
                    }

                    is SchnorrAgentOutput.Signature -> {
                        val (sigBytes) = msg
                        sig = sigBytes
                        println("signing complete\n Signature: ${sigBytes.toHex()}")

                        println("took ${System.currentTimeMillis() - startTime} ms")
                    }
                }
            }
        }
    }
    agents.forEach{
        launch {
            it.startKeygen()
        }
    }
//    keyGenDone.lock()
    println(pubKey.toHex())

    suspend fun sign(msg: ByteArray,prevoutScript: ByteArray): ByteArray {
         agents.take(threshold).map{
            launch{
                println(it.startSigningSession(1, msg, prevoutScript))
            }
        }.forEach { it.join() }

        return sig
    }

    val singleSignKey = SchnorrSingleSignTest()


    while (true){
        val lineElements = readln().split(" ")
        val (cmd) = lineElements
        when(cmd){
            "create" ->{
                val script = ScriptBuilder().op(ScriptOpCodes.OP_1)
                    .data(pubKey)
                    .build()

                val transaction = Transaction(params)

                transaction.addOutput(Coin.SATOSHI.times(1000000),script)

                val sendRequest = SendRequest.forTx(transaction)
                kit.wallet().completeTx(sendRequest)
//                kit.peerGroup().broadcastTransaction(sendRequest.tx)
                val hash = transaction.txId
                println("tx: ${transaction.bitcoinSerialize().toHex()} ")
                println("txHash: $hash")

                val tosign = Transaction(params)

                val index = transaction.outputs.first {
                    it.value == Coin.SATOSHI.times(1000000)
                }.index

                val input = tosign.addInput(transaction.txId,index.toLong(),ScriptBuilder.createEmpty())
                val output = tosign.addOutput(Coin.MICROCOIN.times(10),script)

//                println("to schnorr sign: ${tosign.bitcoinSerialize().toHex()}")

                val schnorrsig = sign(tosign.bitcoinSerialize(), script.program)

                input.witness = TransactionWitness(1).also {
                    it.setPush(0,schnorrsig)
                }

                println("Schnorr signed: ${tosign.bitcoinSerialize().toHex()}")
            }
            "create2" ->{
//                val (_, tohex) = lineElements

                var script = ScriptBuilder().op(ScriptOpCodes.OP_1)
                    .data(singleSignKey._bitcoin_encoded_key)
                    .build()

//                script = Script("5120fa9e200cc285ddf29759942b900e75371f9d64c6b5bc918a23bf2c99532818dd".hexToBytes())

                val transaction = Transaction(params)

                transaction.addOutput(Coin.SATOSHI.times(1000000),script)

                val sendRequest = SendRequest.forTx(transaction)
                kit.wallet().completeTx(sendRequest)
//                kit.peerGroup().broadcastTransaction(sendRequest.tx)
                val hash = transaction.txId
                println("tx: ${transaction.bitcoinSerialize().toHex()} ")
                println("txHash: $hash")

                val tosign = Transaction(params)

                val index = transaction.outputs.first {
                    it.value == Coin.SATOSHI.times(1000000)
                }.index

                val input = tosign.addInput(transaction.txId,index.toLong(),ScriptBuilder.createEmpty())
                val output = tosign.addOutput(Coin.MICROCOIN.times(10),script)

                val schnorrsig = singleSignKey.sign_tx(tosign.bitcoinSerialize(),script.program)

                input.witness = TransactionWitness(1).also {
                    it.setPush(0,schnorrsig)
                }

                println("Schnorr signed: ${tosign.bitcoinSerialize().toHex()}")

            }
            "spend2" -> {
                val (_,txHash, outIndex) = lineElements
                val transaction = Transaction(params)
                transaction.setVersion(2)

                val script = ScriptBuilder().op(ScriptOpCodes.OP_1)
                    .data(singleSignKey._bitcoin_encoded_key)
                    .build()

                println("script : ${script.program.toHex()}")

                val input = transaction.addInput(Sha256Hash.wrap(txHash),outIndex.toLong(),ScriptBuilder.createEmpty())
                val output = transaction.addOutput(Coin.MICROCOIN.times(10),script)
//                val hash = transaction.hashForWitnessSignature(0,script,Transaction.SigHash.ALL,false)
//                val hash = transaction.hashForWitnessSignature(0,script,Coin.CENT,Transaction.SigHash.ALL,false)
                val txSig = singleSignKey.sign_tx(transaction.bitcoinSerialize(), script.program)

                input.witness = TransactionWitness(1).also {
                    it.setPush(0,txSig)
                }

                println("tx: ${transaction.bitcoinSerialize().toHex()}")

//                kit.peerGroup().broadcastTransaction(transaction)

            }
            // spend tx_hash out_index
            "spend" -> {
                val (_,txHash, outIndex) = lineElements
                val transaction = Transaction(params)

                val script = ScriptBuilder().op(ScriptOpCodes.OP_1)
                    .data(pubKey)
                    .build()

                println("script : ${script.program.toHex()}")

                val input = transaction.addInput(Sha256Hash.wrap(txHash),outIndex.toLong(),ScriptBuilder.createEmpty())
                val output = transaction.addOutput(Coin.MICROCOIN.times(10),script)
//                val hash = transaction.hashForWitnessSignature(0,script,Transaction.SigHash.ALL,false)
//                val hash = transaction.hashForWitnessSignature(0,script,Coin.CENT,Transaction.SigHash.ALL,false)
                val txSig = sign(transaction.bitcoinSerialize(), script.program)

                input.witness = TransactionWitness(1).also {
                    it.setPush(0,txSig)
                }

                println("tx: ${transaction.bitcoinSerialize().toHex()}")

//                kit.peerGroup().broadcastTransaction(transaction)

            }
            "info" -> {
                println(kit.peerGroup().numConnectedPeers())
                println("singlesignkey : ${singleSignKey._bitcoin_encoded_key.toHex()}")
                 println("address: ${kit.wallet().freshReceiveAddress()}")
                println("balance: ${kit.wallet().balance}")
            }
        }
    }
}

