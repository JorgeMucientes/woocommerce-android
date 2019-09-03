package com.woocommerce.android.ui.refunds

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class RefundConfirmationFragment : DaggerFragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreate(savedInstanceState)
        return inflater.inflate(R.layout.fragment_refund_confirmation, container, false)
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViewModel()
    }

    private fun initializeViewModel() {
        ViewModelProviders.of(requireActivity(), viewModelFactory).get(IssueRefundViewModel::class.java).also {
            initializeViews(it)
            setupObservers(it)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupObservers(viewModel: IssueRefundViewModel) {
        viewModel.exit.observe(this, Observer {
            findNavController().navigateUp()
        })
    }

    private fun initializeViews(viewModel: IssueRefundViewModel) {
    }
}
