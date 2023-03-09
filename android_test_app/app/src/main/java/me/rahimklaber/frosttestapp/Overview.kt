package me.rahimklaber.frosttestapp

import android.Manifest
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.squareup.sqldelight.android.AndroidSqliteDriver
import dagger.hilt.android.AndroidEntryPoint
import me.rahimklaber.frosttestapp.database.FrostDatabase
import me.rahimklaber.frosttestapp.databinding.ActivityOverviewBinding
import me.rahimklaber.frosttestapp.ipv8.FrostCommunity
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.OverlayConfiguration
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
import javax.inject.Inject

@AndroidEntryPoint
class Overview : AppCompatActivity() {

    private lateinit var binding: ActivityOverviewBinding

        @Inject
         lateinit var db: FrostDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("rust_code")

        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S){
            ActivityCompat.requestPermissions(this, listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ).toTypedArray(),1)
        }


        initIPv8()
        val frostCommunity = IPv8Android.getInstance().getOverlay<FrostCommunity>()
            ?: error("FROSTCOMMUNITY should be initialized")
        frostCommunity.initDb(db)
        binding = ActivityOverviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

//        val navController = findNavController(R.id.nav_host_fragment_activity_overview)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_overview) as NavHostFragment
        val navController = navHostFragment.navController
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.frostSettings, R.id.propose
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
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

    private fun createFrostCommunity() : OverlayConfiguration<FrostCommunity> {
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