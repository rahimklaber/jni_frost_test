package me.rahimklaber.frosttestapp.ui.proposals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import me.rahimklaber.frosttestapp.FrostViewModel
import javax.inject.Inject


/**
 * A simple [Fragment] subclass.
 * create an instance of this fragment.
 */
@AndroidEntryPoint
class Proposals : Fragment() {
    @Inject
    lateinit var frostViewModel: FrostViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        return ComposeView(requireContext()).apply {
            setContent {
                Column(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.Gray)
                        .padding(horizontal = 8.dp)
                ) {
                    Text("Proposals", fontSize = 32.sp)
                    Divider(thickness = 2.dp)
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(frostViewModel.proposals){
                            Row{
                                Column {
                                    Text("type: ${it.type()}")
                                    Text("proposer: ${it.fromMid}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}