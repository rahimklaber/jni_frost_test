package me.rahimklaber.frosttestapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.squareup.sqldelight.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.launch
import me.rahimklaber.frosttestapp.ipv8.FrostCommunity
import me.rahimklaber.frosttestapp.ipv8.FrostManager
import me.rahimklaber.frosttestapp.ipv8.NetworkManager
import me.rahimklaber.frosttestapp.ipv8.message.FrostMessage
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.android.keyvault.AndroidCryptoProvider
import nl.tudelft.ipv8.android.peerdiscovery.NetworkServiceDiscovery
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.peerdiscovery.DiscoveryCommunity
import nl.tudelft.ipv8.peerdiscovery.strategy.PeriodicSimilarity
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomChurn
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import kotlin.math.sign
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    lateinit var frostManager: FrostManager
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("rust_code")
        initIPv8()
        val frostCommunity = IPv8Android.getInstance().getOverlay<FrostCommunity>()
            ?: error("FROSTCOMMUNITY should be initialized")
        frostManager = FrostManager(frostCommunity.channel,
            networkManager = object : NetworkManager {
                override fun send(peer: Peer, msg: FrostMessage) {
                    Log.d("FROST","sending: $msg")
                    frostCommunity.sendForPublic(peer, msg)
                }

                override fun broadcast(msg: FrostMessage) {
                    Log.d("FROST","broadcasting: $msg")
                    frostCommunity.broadcast(msg)
                }

                override fun getMyPeer(): Peer = frostCommunity.myPeer

                override fun getPeerFromMid(mid: String): Peer =
                    frostCommunity.getPeers().find { it.mid == mid } ?: error("Could not find peer")

            },
//            getFrostInfo = {
//                null
//            }
        )
        setContentView(R.layout.activity_main)

        val button = findViewById<Button>(R.id.JOIN_BUTTON)
        val signbutton= findViewById<Button>(R.id.SIGN_BUTTON)
        val signDataTextview = findViewById<TextView>(R.id.SIGN_DATA)
        val textview = findViewById<TextView>(R.id.FROST_UPDATES)
        var peerstestview = findViewById<TextView>(R.id.peers)
        GlobalScope.launch(Dispatchers.Main) {
            launch {
               while(true){
                   peerstestview.text = ""
                   peerstestview.text = frostCommunity.getPeers().map {
                       it.mid
                   }.joinToString("\n")
                   delay(2000)
               }
            }
            frostManager.updatesChannel.collect {
//                Toast.makeText(applicationContext,"$it",Toast.LENGTH_LONG).show()
                textview.text= "${textview.text}\n $it "
                Log.d("FROST","RECEIVED U IUPDATE $it")
            }
        }
        button.setOnClickListener {

            GlobalScope.launch {
                textview.text = "${textview.text}\n started keygen"
                frostManager.joinGroup()
            }
        }
        signbutton.setOnClickListener {
            GlobalScope.launch {
                frostManager.proposeSign(Random.Default.nextBytes(32))
//                textview.text = "${textview.text}\n started sign"
                Log.d("FROST", "started sign")
            }
        }

    }

    private fun initIPv8() {
        val settings = TrustChainSettings()
        val driver = AndroidSqliteDriver(Database.Schema, this, "trustchain.db")
        val store = TrustChainSQLiteStore(Database(driver))
        val randomWalk = RandomWalk.Factory()
        val trustChainCommunity = OverlayConfiguration(
            TrustChainCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
        val config = IPv8Configuration(
            overlays = listOf(
                createDiscoveryCommunity(),
                trustChainCommunity,
                createFrostCommunity()
            ), walkerInterval = 5.0
        )

        IPv8Android.Factory(application)
            .setConfiguration(config)
            .setPrivateKey(getPrivateKey())
            .init()


    }

    private fun createFrostCommunity() : OverlayConfiguration<FrostCommunity>{
        val randomWalk = RandomWalk.Factory()
        val randomChurn = RandomChurn.Factory()
        val periodicSimilarity = PeriodicSimilarity.Factory()

        val nsd = NetworkServiceDiscovery.Factory(
            getSystemService() ?: error("could not find nsdmanager")
        )
//        val bluetoothManager = getSystemService<BluetoothManager>()
//            ?: throw IllegalStateException("BluetoothManager not available")
        val strategies = mutableListOf(
            randomWalk/*, randomChurn*//*, periodicSimilarity*/, nsd
        )
//        if (bluetoothManager.adapter != null && Build.VERSION.SDK_INT >= 24) {
//            val ble = BluetoothLeDiscovery.Factory()
//            strategies += ble
//        }

        return OverlayConfiguration(
            Overlay.Factory(FrostCommunity::class.java),
            strategies
        )
    }

    private fun createDiscoveryCommunity(): OverlayConfiguration<DiscoveryCommunity> {
        val randomWalk = RandomWalk.Factory()
        val randomChurn = RandomChurn.Factory()
        val periodicSimilarity = PeriodicSimilarity.Factory()

        val nsd = NetworkServiceDiscovery.Factory(
            getSystemService() ?: error("could not find nsdmanager")
        )
//        val bluetoothManager = getSystemService<BluetoothManager>()
//            ?: throw IllegalStateException("BluetoothManager not available")
        val strategies = mutableListOf(
            randomWalk, randomChurn, periodicSimilarity, nsd
        )
//        if (bluetoothManager.adapter != null && Build.VERSION.SDK_INT >= 24) {
//            val ble = BluetoothLeDiscovery.Factory()
//            strategies += ble
//        }

        return OverlayConfiguration(
            DiscoveryCommunity.Factory(),
            strategies
        )
    }


    private fun getPrivateKey(): PrivateKey {
        // Load a key from the shared preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val privateKey = prefs.getString(PREF_PRIVATE_KEY, null)
        return if (privateKey == null) {
            // Generate a new key on the first launch
            val newKey = AndroidCryptoProvider.generateKey()
            prefs.edit()
                .putString(PREF_PRIVATE_KEY, newKey.keyToBin().toHex())
                .apply()
            newKey
        } else {
            AndroidCryptoProvider.keyFromPrivateBin(privateKey.hexToBytes())
        }
    }

    companion object {
        private const val PREF_PRIVATE_KEY = "private_key"
    }
}