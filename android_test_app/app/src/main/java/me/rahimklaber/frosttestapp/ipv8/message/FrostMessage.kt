package me.rahimklaber.frosttestapp.ipv8.message

import nl.tudelft.ipv8.messaging.Serializable

sealed interface FrostMessage: Serializable{
    val id: Long
}
fun messageIdFromMsg(msg: FrostMessage) : Int =
    when(msg){
        is KeyGenCommitments -> KeyGenCommitments.MESSAGE_ID
        is KeyGenShare -> KeyGenShare.MESSAGE_ID
        is RequestToJoinMessage -> RequestToJoinMessage.MESSAGE_ID
        is RequestToJoinResponseMessage -> RequestToJoinResponseMessage.MESSAGE_ID
        is Preprocess -> Preprocess.MESSAGE_ID
        is SignShare -> SignShare.MESSAGE_ID
        is SignRequest -> SignRequest.MESSAGE_ID
        is SignRequestResponse -> SignRequestResponse.MESSAGE_ID
    }