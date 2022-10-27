package com.example.avitoweather.presentation.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.avitoweather.App
import com.example.avitoweather.R
import com.example.avitoweather.databinding.FragmentLocationBinding
import com.example.avitoweather.domain.entites.LocationError
import com.example.avitoweather.domain.entites.LocationState
import com.example.avitoweather.domain.entites.LocationSuccess
import com.example.avitoweather.presentation.adapters.LocationListAdapter
import com.example.avitoweather.presentation.viewModels.LocationViewModel
import com.example.avitoweather.presentation.viewModelsFactory.ViewModelFactory
import com.example.avitoweather.utils.Utils.getLocation
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocationFragment : Fragment() {

    private lateinit var binding: FragmentLocationBinding
    private val locationAdapter = LocationListAdapter()

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[LocationViewModel::class.java]
    }

    private val component by lazy {
        ((requireActivity().application) as App).component
    }

    override fun onAttach(context: Context) {
        component.injectLocationFragment(this)
        super.onAttach(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        setupRecyclerView()
        observeLocation()
        observeLoadingFlow()
        findOrShowHistory()
    }

    private fun observeLoadingFlow() {
        lifecycleScope.launch {
            viewModel.isLoadingFlow.collect {
                setupVisibleProgressBar(it)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.locationRv.adapter = locationAdapter
        binding.locationRv.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.VERTICAL,
            false
        )
        locationAdapter.itemClickListener = {
            viewModel.sendLocation(it.lat, it.lon, it.label)
            requireActivity().onBackPressed()
        }
        locationAdapter.deleteItemClickListener = {
            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.deleteElementWithLabel(it.label)
                getHistoryListAndSubmit()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        keyboardClickListener()
        binding.locationImageView.setOnClickListener {
            findCurrentLocation()
        }
        binding.searchEditText.addTextChangedListener {
            findOrShowHistory()
        }
        binding.searchEditText.setOnClickListener {
            findOrShowHistory()
        }
    }

    private fun findOrShowHistory() {
        val text = binding.searchEditText.text.toString()
        val isHistory = text.length <= 2
        locationAdapter.isHistory = isHistory
        if (!isHistory) {
            viewModel.findAndGetLocation(text)
            binding.titleSearch.text = getString(R.string.founded)
        } else {
            getHistoryListAndSubmit()
            binding.titleSearch.text = getString(R.string.recently_request)
        }
    }

    private fun getHistoryListAndSubmit() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = viewModel.getHistoryList()
            locationAdapter.submitList(result)
        }
    }

    private fun findCurrentLocation() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = getLocation(requireContext(), requireActivity())
            if (result.isEmpty()) {
                showError()
            } else {
                //исправить на более читабельное
                viewModel.sendLocation(lat = result[0], lon = result[1], label = CURRENT_POSITION)
                withContext(Dispatchers.Main) {
                    Snackbar.make(requireView(), R.string.city_defined, Snackbar.LENGTH_SHORT)
                        .show()
                    delayAndBackPressed()
                }
            }
        }
    }

    private fun showError() {
        Snackbar.make(requireView(), "Произошла ошибка", Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun keyboardClickListener() {
        binding.searchEditText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                sendFindAndSetLocation()
                return@OnEditorActionListener true
            }
            false
        })
    }

    private fun sendFindAndSetLocation() {
        if (locationAdapter.currentList.isEmpty()) return else {
            val location = locationAdapter.currentList[0]
            viewModel.sendLocation(location.lat, location.lon, location.label)
        }
        binding.searchEditText.onEditorAction(EditorInfo.IME_ACTION_DONE)
        requireActivity().onBackPressed()
    }

    private fun setupVisibleProgressBar(boolean: Boolean) {
        binding.progressBar.isVisible = boolean
    }

    private fun observeLocation() {
        lifecycleScope.launch {
            viewModel.findLocation.collect { listState ->
                if (!locationAdapter.isHistory) {
                    locationAdapter.submitList(newList(listState))
                }
            }
        }
    }

    private fun newList(listState: List<LocationState>?): List<LocationSuccess> {
        if (listState != null || listState?.get(0) !is LocationError) {
            val newList = mutableListOf<LocationSuccess>()
            if (listState != null) {
                for (element in listState) {
                    if (element is LocationError) {
                        return emptyList()
                    }
                    newList.add(element as LocationSuccess)
                }
            }
            return newList
        }
        return emptyList()
    }

    private suspend fun delayAndBackPressed() {
        delay(Snackbar.LENGTH_SHORT.toLong())
        requireActivity().onBackPressed()
    }


    companion object {
        fun newInstance(): LocationFragment {
            return LocationFragment()
        }

        const val FRAGMENT_NAME = "LocationFragment"
        private const val CURRENT_POSITION = "Current Position"
    }
}