import me.rahimklaber.frosttestapp.ipv8.message.GossipResponse
import me.rahimklaber.frosttestapp.ipv8.message.RequestToJoinMessage
import org.junit.Test

class MsgTest {
    @Test
    fun gossipResponseTest(){
        val response = GossipResponse(
            "1111111111111111111111111111111111111111",
            RequestToJoinMessage(1)
        )

        println("1111111111111111111111111111111111111111")

        GossipResponse.deserialize(response.serialize())
    }
}