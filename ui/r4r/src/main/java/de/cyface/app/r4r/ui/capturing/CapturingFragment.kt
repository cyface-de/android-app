package de.cyface.app.r4r.ui.capturing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.tabs.TabLayout
import de.cyface.app.r4r.R
import de.cyface.app.r4r.databinding.FragmentCapturingBinding
import de.cyface.app.r4r.ui.capturing.map.MapFragment
import de.cyface.app.r4r.ui.capturing.speed.SpeedFragment


class CapturingFragment : Fragment() {

    private var _binding: FragmentCapturingBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // When requested, this adapter returns a DemoObjectFragment,
    // representing an object in the collection.
    private lateinit var pagerAdapter: PagerAdapter

    /**
     * The pager widget, which handles animation and allows swiping horizontally
     * to access previous and next wizard steps.
     */
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val capturingViewModel =
            ViewModelProvider(this).get(CapturingViewModel::class.java)

        _binding = FragmentCapturingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textView9
        capturingViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Instantiate a ViewPager2 and a PagerAdapter.
        viewPager = view.findViewById(R.id.pager)

        // Connect adapter to ViewPager (which provides pages to the view pager)
        val fragmentManager: FragmentManager = childFragmentManager
        pagerAdapter = PagerAdapter(fragmentManager, lifecycle)
        viewPager.adapter = pagerAdapter

        // Add TabItems to TabLayout
        val tabLayout: TabLayout = view.findViewById(R.id.tabLayout)

        // Connect TabLayout to Adapter
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Change active tab when swiping
        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Supplies the fragments to the ViewPager
    private class PagerAdapter(fragmentManager: FragmentManager?, lifecycle: Lifecycle?) :
        FragmentStateAdapter(
            fragmentManager!!, lifecycle!!
        ) {
        override fun createFragment(position: Int): Fragment {
            // Ordered list, make sure titles list match this order
            return if (position == 0) {
                MapFragment()
            } else SpeedFragment()
        }

        override fun getItemCount(): Int {
            // List size
            return 2
        }
    }
}