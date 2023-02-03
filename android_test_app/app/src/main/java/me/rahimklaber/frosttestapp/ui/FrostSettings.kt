package me.rahimklaber.frosttestapp.ui

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
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rahimklaber.frosttestapp.FrostViewModel
import me.rahimklaber.frosttestapp.R
import me.rahimklaber.frosttestapp.ipv8.FrostManager
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
                        Text("State: ${frostViewModel.frostManager.state}", fontSize = 16.sp)
                        Text("Frost Index ${frostViewModel.frostManager.frostInfo?.myIndex ?: "N/A"}", fontSize = 16.sp)
                        Text("Threshold: ${frostViewModel.frostManager.frostInfo?.threshold ?: "N/A"}", fontSize = 16.sp)
                        Text("Amount of Members: ${frostViewModel.frostManager.frostInfo?.amount ?: "N/A"}", fontSize = 16.sp)

                        Button(onClick = {
                            frostViewModel.viewModelScope.launch(Dispatchers.Default) {
                                frostViewModel.frostManager.joinGroup()
                            }
                        }) {
                            Text(text = "Join Group")
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            FrostSettings().apply {
                arguments = Bundle().apply {

                }
            }
    }
}