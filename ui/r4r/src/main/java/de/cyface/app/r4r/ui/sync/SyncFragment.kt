package de.cyface.app.r4r.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.cyface.app.r4r.databinding.FragmentSyncBinding

class SyncFragment : Fragment() {

    private var _binding: FragmentSyncBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val syncViewModel =
            ViewModelProvider(this).get(SyncViewModel::class.java)

        _binding = FragmentSyncBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textSync
        syncViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}