import generated.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

sealed interface SchnorrAgentMessage{
    data class KeyCommitment(val commitment: ByteArray, val fromIndex: Int) : SchnorrAgentMessage
    data class DkgShare(val share: ByteArray, val fromIndex: Int) : SchnorrAgentMessage
}

sealed interface SchnorrAgentOutput{

    data class KeyCommitment(val commitment: ByteArray, val fromIndex: Int) : SchnorrAgentOutput

    data class DkgShare(val share: ByteArray, val fromIndex: Int, val forIndex: Int) : SchnorrAgentOutput

    data class Done(val index: Int): SchnorrAgentOutput
}


suspend fun CoroutineScope.SchnorrAgent(amount: Int, index: Int, threshold: Int,receiveChannel : ReceiveChannel<SchnorrAgentMessage>, outputChannel : SendChannel<SchnorrAgentOutput>) = launch {
    var key_gen_machine = SchnorrKeyGenWrapper(threshold, amount, index, "test")
    // create commitment
    val res_key_1 = SchnorrKeyGenWrapper.key_gen_1_create_commitments(key_gen_machine)
    val commitment = res_key_1._res
    key_gen_machine = ResultKeygen1.get_keygen(res_key_1)

    outputChannel.send(SchnorrAgentOutput.KeyCommitment(commitment, index))


    // receive commitments
    val param_keygen_2 = ParamsKeygen2()
    var count = 0

    while (count < amount - 1){
        val (bytes, from) = receiveChannel.receive() as SchnorrAgentMessage.KeyCommitment

        param_keygen_2.add_commitment_from_user(from, bytes)
        count++
    }

    // create key share for others ????
    val res_key_2 = SchnorrKeyGenWrapper.key_gen_2_generate_shares(key_gen_machine,param_keygen_2)
    // have to do this first, since getting the key gen machine consumes `self`
    res_key_2._user_indices.forEachIndexed{ arrayIdx, userIndex ->
        outputChannel.send(SchnorrAgentOutput.DkgShare(res_key_2.get_shares_at(arrayIdx.toLong()),index,userIndex))
    }

    key_gen_machine = ResultKeygen2.get_keygen(res_key_2)

    //receive shares
    val params_keygen_3 = ParamsKeygen3()
    count = 0

    while (count < amount - 1){
        val (bytes, from) = receiveChannel.receive() as SchnorrAgentMessage.DkgShare

        params_keygen_3.add_share_from_user(from,bytes)
        count++
    }

    val keyWrapper = SchnorrKeyGenWrapper.key_gen_3_complete(key_gen_machine,params_keygen_3)

    outputChannel.send(SchnorrAgentOutput.Done(index))



}

suspend fun main(args: Array<String>) = withContext(Dispatchers.Default){
    try {
        System.load("C:\\Users\\rahim\\Desktop\\thesis\\jna_frost_test\\src\\main\\resources\\mobcore.dll")

    } catch (e: UnsatisfiedLinkError) {
        println(e)
        System.err.println("Exiting: Could not link mobcore library.")
//        return
    }

    val amount = 2
    val threshold = 1

    val inputChannels = mutableListOf<Channel<SchnorrAgentMessage>>()
    val outputChannel = Channel<SchnorrAgentOutput>()
    val jobs = IntRange(1,amount)
        .map  {
            val channel = Channel<SchnorrAgentMessage>()
            inputChannels.add(channel)
            SchnorrAgent(amount,it,threshold, channel, outputChannel)
        }

    var donecount = 0
    for(msg in outputChannel){
        when(msg){
            is SchnorrAgentOutput.DkgShare -> {
                val (bytes, fromIndex ,forIndex) = msg
                println("$fromIndex sending dkgshare to $forIndex")
                launch{
                    inputChannels[forIndex-1].send(SchnorrAgentMessage.DkgShare(bytes, fromIndex))
                }
            }
            is SchnorrAgentOutput.KeyCommitment -> {
                val (bytes, from) = msg
                inputChannels.forEachIndexed {index, it ->
                    if (from == index+1)
                        return@forEachIndexed
                    println("$from sending keycommitment to ${index + 1}")
                    launch{
                        it.send(SchnorrAgentMessage.KeyCommitment(bytes,from))
                    }
                }
            }
            is SchnorrAgentOutput.Done -> {
                println("agent ${msg.index} is done ")
                donecount++
                if (donecount == amount - 1)
                    println("keyGen done!")
            }
        }
    }

    println("hi")








}

//var machine1 = SchnorrKeyGenWrapper(2, 2, 1, "test")
//var machine2 = SchnorrKeyGenWrapper(2, 2, 2, "test")
//
//// create commitments
//val res1m1 = SchnorrKeyGenWrapper.key_gen_1_create_commitments(machine1)
//val res1m1_share = res1m1._res
//machine1 = ResultKeygen1.get_keygen(res1m1)
//
//val res1m2 = SchnorrKeyGenWrapper.key_gen_1_create_commitments(machine2)
//val res1m2_share = res1m2._res
//machine2 = ResultKeygen1.get_keygen(res1m2)
//
////create shares
//val param_keygen_2_m1 = ParamsKeygen2()
//param_keygen_2_m1.add_commitment_from_user(2, res1m2_share)
//val res2m1 = SchnorrKeyGenWrapper.key_gen_2_generate_shares(machine1,param_keygen_2_m1)
//val shares_for_2 = res2m1.get_shares_at(0)
//machine1 = ResultKeygen2.get_keygen(res2m1)
//
//val param_keygen_2_m2 = ParamsKeygen2()
//param_keygen_2_m2.add_commitment_from_user(1, res1m1_share)
//val res2m2 = SchnorrKeyGenWrapper.key_gen_2_generate_shares(machine2,param_keygen_2_m2)
//val shares_for_1 = res2m2.get_shares_at(0)
//machine2 = ResultKeygen2.get_keygen(res2m2)
//
////create key share
//val params_keygen_3_m1 = ParamsKeygen3()
//params_keygen_3_m1.add_share_from_user(2,shares_for_1)
//val keyWrapper1 = SchnorrKeyGenWrapper.key_gen_3_complete(machine1,params_keygen_3_m1)
//
//val params_keygen_3_m2 = ParamsKeygen3()
//params_keygen_3_m2.add_share_from_user(1,shares_for_2)
//val keyWrapper2 = SchnorrKeyGenWrapper.key_gen_3_complete(machine2,params_keygen_3_m2)
//
//// signing
//
//var sign_wrapper_1 = SchnorrSignWrapper.new_instance_for_signing(keyWrapper1)
//var sign_wrapper_2 = SchnorrSignWrapper.new_instance_for_signing(keyWrapper2)
//
//// preprocess
//var sign_res_1_m1 = SchnorrSignWrapper.sign_1_preprocess(sign_wrapper_1)
//var preprocess_1 = sign_res_1_m1._preprocess
//sign_wrapper_1 = SignResult1.get_wrapper(sign_res_1_m1)
//
//var sign_res_1_m2 = SchnorrSignWrapper.sign_1_preprocess(sign_wrapper_2)
//var preprocess_2 = sign_res_1_m2._preprocess
//sign_wrapper_2 = SignResult1.get_wrapper(sign_res_1_m2)
//
//var sign_param_2_m1 = SignParams2()
//sign_param_2_m1.add_commitment_from_user(2,preprocess_2)
//var sign_res_2_m1 = SchnorrSignWrapper.sign_2_sign(sign_wrapper_1,sign_param_2_m1,"hello".toByteArray())
//val sign_res_2_share_m1 = sign_res_2_m1._share
//sign_wrapper_1 = SignResult2.get_wrapper(sign_res_2_m1)
//
//var sign_param_2_m2 = SignParams2()
//sign_param_2_m2.add_commitment_from_user(1,preprocess_1)
//var sign_res_2_m2 = SchnorrSignWrapper.sign_2_sign(sign_wrapper_2,sign_param_2_m2,"hello".toByteArray())
//val sign_res_2_share_m2 = sign_res_2_m2._share
//
//var sign_params_3_m1 = SignParams3()
//sign_params_3_m1.add_share_of_user(2,sign_res_2_share_m2)
//
//var final_sign_res = SchnorrSignWrapper.sign_3_complete(sign_wrapper_1,sign_params_3_m1)
//
//println("final sig: ${final_sign_res.toHex()}")