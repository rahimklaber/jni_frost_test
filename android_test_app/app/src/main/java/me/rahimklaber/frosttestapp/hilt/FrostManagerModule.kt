package me.rahimklaber.frosttestapp.hilt

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.rahimklaber.frosttestapp.FrostViewModel
import me.rahimklaber.frosttestapp.ipv8.FrostCommunity
import me.rahimklaber.frosttestapp.ipv8.FrostManager
import me.rahimklaber.frosttestapp.ipv8.NetworkManager
import me.rahimklaber.frosttestapp.ipv8.message.FrostMessage
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FrostManagerModule {
    @Provides
    @Singleton
    fun provideFrostViewModel() : FrostViewModel {
        val frostCommunity = IPv8Android.getInstance().getOverlay<FrostCommunity>()
            ?: error("FROSTCOMMUNITY should be initialized")
        val frostManager = FrostManager(frostCommunity.channel,
            networkManager = object : NetworkManager {
                override fun send(peer: Peer, msg: FrostMessage) {
                    Log.d("FROST","sending: $msg")
                    frostCommunity.sendEva(peer, msg)
                }

                override fun broadcast(msg: FrostMessage) {
                    Log.d("FROST","broadcasting: $msg")
                    frostCommunity.broadcastEva(msg)
                }

                override fun getMyPeer(): Peer = frostCommunity.myPeer

                override fun getPeerFromMid(mid: String): Peer =
                    frostCommunity.getPeers().find { it.mid == mid } ?: error("Could not find peer")

            },
        )
        return FrostViewModel(frostCommunity,frostManager)
    }

}