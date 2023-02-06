package me.rahimklaber.frosttestapp.ui.make_proposal

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rahimklaber.frosttestapp.FrostViewModel
import javax.inject.Inject
import kotlin.random.Random


/**
 * A simple [Fragment] subclass.
 * create an instance of this fragment.
 */
@AndroidEntryPoint
class Propose : Fragment() {
    @Inject
    lateinit var frostViewModel: FrostViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.Gray)){
                    Column(Modifier.padding(horizontal = 4.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Propose Signing", fontSize = 24.sp)

                        TextField(placeholder = { Text("Destination address")},value = "", onValueChange = {})
                        TextField(placeholder = { Text("Amount in BTC")},value = "", onValueChange = {})

                        Button(onClick = {
                            frostViewModel.viewModelScope.launch (Dispatchers.Default){
                                frostViewModel.frostManager.proposeSign(Random.nextBytes(32))
                            }
                        }) {
                            Text("Propose")
                        }
                    }
                }
            }
        }
    }
}