import me.rahimklaber.frosttestapp.ipv8.message.GossipResponse
import me.rahimklaber.frosttestapp.ipv8.message.RequestToJoinMessage
import me.rahimklaber.frosttestapp.ipv8.message.SignRequest
import org.junit.Test

class MsgTest {
    @Test
    fun gossipResponseTest(){
        val response = GossipResponse(
            "1111111111111111111111111111111111111111",
            SignRequest(10L, byteArrayOf(1,2,3,4))
        )


        GossipResponse.deserialize(response.serialize())
    }
}