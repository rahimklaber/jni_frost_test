package me.rahimklaber.frosttestapp.ui.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
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
import me.rahimklaber.frosttestapp.ui.ActivityGrid
import javax.inject.Inject

/**
 * A simple [Fragment] subclass.
 * Use the [FrostSettings.newInstance] factory method to
 * create an instance of this fragment.
 */
@AndroidEntryPoint
class FrostSettings : Fragment() {
    @Inject lateinit var frostViewModel : FrostViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View{
        // Inflate the layout for this fragment
        return ComposeView(requireContext()).apply {
            setContent {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.Gray)){
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        Text("State: ${frostViewModel.state}", fontSize = 16.sp)
                        Text("Frost Index ${frostViewModel.index ?: "N/A"}", fontSize = 16.sp)
                        Text("Threshold: ${frostViewModel.threshold ?: "N/A"}", fontSize = 16.sp)
                        Text("Amount of Members: ${frostViewModel.amountOfMembers ?: "N/A"}", fontSize = 16.sp)
                        Divider(thickness = 2.dp)
                        Text("Peers")
                        LazyColumn{
                            items(frostViewModel.peers){
                                Text(it)
                            }
                        }
//                        Box(modifier = Modifier.fillMaxHeight())
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ){
                            Button(onClick = {
                                frostViewModel.viewModelScope.launch(Dispatchers.Default) {
                                    frostViewModel.frostManager.joinGroup()
                                }
                            }) {
                                Text(text = "Join Group")
                            }

                            Button(onClick = {
                                frostViewModel.viewModelScope.launch(Dispatchers.Default) {
                                    frostViewModel.panic()
                                }
                            }) {
                                Text(text = "Panic")
                            }

                        }


                        ActivityGrid(frostViewModel)
                    }
                }
            }
        }
    }

}