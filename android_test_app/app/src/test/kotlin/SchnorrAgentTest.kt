import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import me.rahimklaber.frosttestapp.SchnorrAgent
import me.rahimklaber.frosttestapp.SchnorrAgentMessage
import me.rahimklaber.frosttestapp.SchnorrAgentOutput
import nl.tudelft.ipv8.util.toHex
import org.junit.Test

class SchnorrAgentTest{

    @Test
    fun `frost test`() = runBlocking {
        System.load("/home/rahim/jni_frost_test/android_test_app/app/src/test/kotlin/librust_code.so")
        withContext(Dispatchers.Default){
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
            val msgHandlerJob = launch {
                    for(msg in outputChannel){
                        when(msg){
                            is SchnorrAgentOutput.DkgShare -> {
                                val (bytes, fromIndex ,forIndex) = msg
//                println("$fromIndex sending dkgshare to $forIndex")
                                launch{
                                    inputChannels[forIndex-1].send(
                                        SchnorrAgentMessage.DkgShare(
                                            bytes,
                                            fromIndex
                                        )
                                    )
                                }
                            }
                            is SchnorrAgentOutput.KeyCommitment -> {
                                val (bytes, from) = msg
                                inputChannels.mapIndexed {index, it ->
                                    if (from == index+1)
                                        return@mapIndexed
                                    launch{
                                        it.send(SchnorrAgentMessage.KeyCommitment(bytes, from))
                                    }
                                }

                            }
                            is SchnorrAgentOutput.KeyGenDone -> {
                                donecount++


                                if (donecount == amount){
                                    keyGenDone.unlock()
                                    pubKey = msg.pubkey
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
//                            println("signing complete\n Signature: ${sigBytes.toHex()}")

                                println("took ${System.currentTimeMillis() - startTime} ms")
                            }
                        }
                    }
                }
            agents.forEach{
                launch {
                    it.startKeygen()
                }
            }
            keyGenDone.lock()
            println(pubKey.toHex())

            suspend fun sign(msg: ByteArray): ByteArray {
                agents.take(threshold).mapIndexed { index, it ->
                    launch{
                        println(it.startSigningSession(1, msg, byteArrayOf(),inputChannels[index],outputChannel))
                    }
                }.forEach { it.join() }

                return sig
            }
            msgHandlerJob.cancel()
        }

    }



}