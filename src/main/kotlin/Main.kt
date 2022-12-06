import generated.*
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.PeerAddress
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.wallet.SendRequest
import java.io.File
import java.net.InetAddress
import java.util.*
fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun main(args: Array<String>) {
    try {
    } catch (e: UnsatisfiedLinkError) {
        println(e)
        System.err.println("Exiting: Could not link mobcore library.")
        return
    }

    System.load("C:\\Users\\rahim\\Desktop\\thesis\\jna_frost_test\\src\\main\\resources\\mobcore.dll")
    var machine1 = SchnorrKeyGenWrapper(2, 2, 1, "test")
    var machine2 = SchnorrKeyGenWrapper(2, 2, 2, "test")

    // create commitments
    val res1m1 = SchnorrKeyGenWrapper.key_gen_1_create_commitments(machine1)
    val res1m1_share = res1m1._res
    machine1 = ResultKeygen1.get_keygen(res1m1)

    val res1m2 = SchnorrKeyGenWrapper.key_gen_1_create_commitments(machine2)
    val res1m2_share = res1m2._res
    machine2 = ResultKeygen1.get_keygen(res1m2)

    //create shares
    val param_keygen_2_m1 = ParamsKeygen2()
    param_keygen_2_m1.add_commitment_from_user(2, res1m2_share)
    val res2m1 = SchnorrKeyGenWrapper.key_gen_2_generate_shares(machine1,param_keygen_2_m1)
    val shares_for_2 = res2m1.get_shares_at(0)
    machine1 = ResultKeygen2.get_keygen(res2m1)

    val param_keygen_2_m2 = ParamsKeygen2()
    param_keygen_2_m2.add_commitment_from_user(1, res1m1_share)
    val res2m2 = SchnorrKeyGenWrapper.key_gen_2_generate_shares(machine2,param_keygen_2_m2)
    val shares_for_1 = res2m2.get_shares_at(0)
    machine2 = ResultKeygen2.get_keygen(res2m2)

    //create key share
    val params_keygen_3_m1 = ParamsKeygen3()
    params_keygen_3_m1.add_share_from_user(2,shares_for_1)
    val keyWrapper1 = SchnorrKeyGenWrapper.key_gen_3_complete(machine1,params_keygen_3_m1)

    val params_keygen_3_m2 = ParamsKeygen3()
    params_keygen_3_m2.add_share_from_user(1,shares_for_2)
    val keyWrapper2 = SchnorrKeyGenWrapper.key_gen_3_complete(machine2,params_keygen_3_m2)

    // signing

    var sign_wrapper_1 = SchnorrSignWrapper.new_instance_for_signing(keyWrapper1)
    var sign_wrapper_2 = SchnorrSignWrapper.new_instance_for_signing(keyWrapper2)

    // preprocess
    var sign_res_1_m1 = SchnorrSignWrapper.sign_1_preprocess(sign_wrapper_1)
    var preprocess_1 = sign_res_1_m1._preprocess
    sign_wrapper_1 = SignResult1.get_wrapper(sign_res_1_m1)

    var sign_res_1_m2 = SchnorrSignWrapper.sign_1_preprocess(sign_wrapper_2)
    var preprocess_2 = sign_res_1_m2._preprocess
    sign_wrapper_2 = SignResult1.get_wrapper(sign_res_1_m2)

    var sign_param_2_m1 = SignParams2()
    sign_param_2_m1.add_commitment_from_user(2,preprocess_2)
    var sign_res_2_m1 = SchnorrSignWrapper.sign_2_sign(sign_wrapper_1,sign_param_2_m1,"hello".toByteArray())
    val sign_res_2_share_m1 = sign_res_2_m1._share
    sign_wrapper_1 = SignResult2.get_wrapper(sign_res_2_m1)

    var sign_param_2_m2 = SignParams2()
    sign_param_2_m2.add_commitment_from_user(1,preprocess_1)
    var sign_res_2_m2 = SchnorrSignWrapper.sign_2_sign(sign_wrapper_2,sign_param_2_m2,"hello".toByteArray())
    val sign_res_2_share_m2 = sign_res_2_m2._share

    var sign_params_3_m1 = SignParams3()
    sign_params_3_m1.add_share_of_user(2,sign_res_2_share_m2)

    var final_sign_res = SchnorrSignWrapper.sign_3_complete(sign_wrapper_1,sign_params_3_m1)

    println("final sig: ${final_sign_res.toHex()}")




//    val reg_test_node = "131.180.27.224"
//    val localHost = InetAddress.getByName(reg_test_node)
//    val kit = WalletAppKit(RegTestParams(), File("bitcoinj_dir"),"_f")
//
//    kit.setPeerNodes(PeerAddress(kit.params(), localHost, kit.params().port))
//
//    kit.setDownloadListener(object : DownloadProgressTracker() {
//        override fun progress(
//            pct: Double,
//            blocksSoFar: Int,
//            date: Date?
//        ) {
//            super.progress(pct, blocksSoFar, date)
//            val percentage = pct.toInt()
//            println("Progress: $percentage")
//        }
//
//        override fun doneDownload() {
//            super.doneDownload()
//            println("Coin Download Complete!")
//        }
//    })
//
//    kit.setBlockingStartup(true)
//    print("done")
//
//    Thread.sleep(100000)
}