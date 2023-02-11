package de.cyface.app.r4r.ui.capturing.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.cyface.app.r4r.databinding.FragmentMapBinding
import de.cyface.app.r4r.ui.capturing.CapturingViewModel

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val capturingViewModel =
            ViewModelProvider(this).get(CapturingViewModel::class.java)

        _binding = FragmentMapBinding.inflate(inflater, container, false)
        val root: View = binding.root

        /*val textView: TextView = binding.textView9
        capturingViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }*/
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}