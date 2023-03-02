package me.rahimklaber.frosttestapp.hilt

import android.content.Context
import android.util.Log
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.rahimklaber.frosttestapp.FrostViewModel
import me.rahimklaber.frosttestapp.database.FrostDatabase
import me.rahimklaber.frosttestapp.database.Request
import me.rahimklaber.frosttestapp.database.SentMessage
import me.rahimklaber.frosttestapp.ipv8.FrostCommunity
import me.rahimklaber.frosttestapp.ipv8.FrostManager
import me.rahimklaber.frosttestapp.ipv8.NetworkManager
import me.rahimklaber.frosttestapp.ipv8.message.FrostMessage
import me.rahimklaber.frosttestapp.ipv8.message.SignRequest
import me.rahimklaber.frosttestapp.ipv8.message.messageIdFromMsg
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import java.util.Date
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FrostManagerModule {
    @Provides
    @Singleton
    fun provideFrostViewModel(db: FrostDatabase) : FrostViewModel {
        val frostCommunity = IPv8Android.getInstance().getOverlay<FrostCommunity>()
            ?: error("FROSTCOMMUNITY should be initialized")
        val frostManager = FrostManager(frostCommunity.channel,
            db= db,
            networkManager = object : NetworkManager {
                override fun send(peer: Peer, msg: FrostMessage) {
                    Log.d("FROST","sending: $msg")
                    db.sentMessageDao().insertSentMessage(
                        SentMessage(
                            toMid = peer.mid,
                            data = msg.serialize(),
                            broadcast = false,
                            messageId = msg.id,
                            type = messageIdFromMsg(msg),
                            unixTime = Date().time / 1000 // in seconds
                        )
                    )
                    if (messageIdFromMsg(msg) == SignRequest.MESSAGE_ID){
                        db.requestDao()
                            .insertRequest(
                                Request(
                                    unixTime = Date().time / 1000,
                                    type = messageIdFromMsg(msg),
                                    requestId = msg.id,
                                    data = msg.serialize(),
                                    fromMid = frostCommunity.myPeer.mid,
                                )
                            )
                    }
                    frostCommunity.sendEva(peer, msg)
                }

                override fun broadcast(msg: FrostMessage) {
                    Log.d("FROST","broadcasting: $msg")
                    db.sentMessageDao().insertSentMessage(
                        SentMessage(
                            toMid = null,
                            data = msg.serialize(),
                            broadcast = true,
                            messageId = msg.id,
                            type = messageIdFromMsg(msg),
                            unixTime = Date().time / 1000 // in seconds
                        )
                    )
                    if (messageIdFromMsg(msg) == SignRequest.MESSAGE_ID){
                        db.requestDao()
                            .insertRequest(
                                Request(
                                    unixTime = Date().time / 1000,
                                    type = messageIdFromMsg(msg),
                                    requestId = msg.id,
                                    data = msg.serialize(),
                                    fromMid = frostCommunity.myPeer.mid,
                                )
                            )
                    }
                    frostCommunity.broadcastEva(msg)
                }

                override fun getMyPeer(): Peer = frostCommunity.myPeer

                override fun getPeerFromMid(mid: String): Peer =
                    frostCommunity.getPeers().find { it.mid == mid } ?: error("Could not find peer")

            },
        )
        return FrostViewModel(frostCommunity,frostManager)
    }
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext app: Context
    )=
        Room.databaseBuilder(
            app,
            FrostDatabase::class.java,
            "frost_db"
        ).build()


}