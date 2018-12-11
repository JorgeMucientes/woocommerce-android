package com.woocommerce.android.ui.notifications

import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton.OnCheckedChangeListener
import com.woocommerce.android.R
import com.woocommerce.android.di.GlideApp
import com.woocommerce.android.extensions.canMarkAsSpam
import com.woocommerce.android.extensions.canModerate
import com.woocommerce.android.extensions.canTrash
import com.woocommerce.android.extensions.getCommentId
import com.woocommerce.android.extensions.getConvertedTimestamp
import com.woocommerce.android.extensions.getProductInfo
import com.woocommerce.android.extensions.getRating
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.ui.base.UIMessageResolver
import com.woocommerce.android.util.ActivityUtils
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T.NOTIFICATIONS
import com.woocommerce.android.widgets.SkeletonView
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_review_detail.*
import org.wordpress.android.fluxc.model.CommentModel
import org.wordpress.android.fluxc.model.CommentStatus
import org.wordpress.android.fluxc.model.notification.NotificationModel
import org.wordpress.android.util.DateTimeUtils
import javax.inject.Inject

class ReviewDetailFragment : Fragment(), ReviewDetailContract.View {
    companion object {
        const val TAG = "ReviewDetailFragment"
        const val FIELD_REMOTE_NOTIF_ID = "notif-remote-id"
        const val FIELD_REMOTE_COMMENT_ID = "remote-comment-id"

        fun newInstance(notification: NotificationModel): ReviewDetailFragment {
            val args = Bundle()
            args.putLong(FIELD_REMOTE_NOTIF_ID, notification.remoteNoteId)
            args.putLong(FIELD_REMOTE_COMMENT_ID, notification.getCommentId())

            val fragment = ReviewDetailFragment()
            fragment.arguments = args
            return fragment
        }
    }

    @Inject lateinit var presenter: ReviewDetailContract.Presenter
    @Inject lateinit var uiMessageResolver: UIMessageResolver
    @Inject lateinit var networkStatus: NetworkStatus

    private val skeletonView = SkeletonView()
    private var remoteNoteId: Long = 0L
    private var remoteCommentId: Long = 0L
    private var productUrl: String? = null
    private val moderateListener = OnCheckedChangeListener { _, isChecked ->
        when (isChecked) {
            true -> approveReview()
            false -> disapproveReview()
        }
    }

    private var changeCommentStatusSnackbar: Snackbar? = null

    override fun onAttach(context: Context?) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_review_detail, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        presenter.takeView(this)

        arguments?.let {
            remoteNoteId = it.getLong(FIELD_REMOTE_NOTIF_ID)
            remoteCommentId = it.getLong(FIELD_REMOTE_COMMENT_ID)
        }

        presenter.loadNotificationDetail(remoteNoteId, remoteCommentId)
    }

    override fun onStop() {
        changeCommentStatusSnackbar?.dismiss()
        super.onStop()
    }

    override fun onDestroyView() {
        presenter.dropView()
        super.onDestroyView()
    }

    override fun showSkeleton(show: Boolean) {
        if (show) {
            skeletonView.show(container, R.layout.skeleton_notif_detail, delayed = true)
        } else {
            skeletonView.hide()
        }
    }

    override fun setNotification(note: NotificationModel, comment: CommentModel) {
        // Populate reviewer section
        GlideApp.with(review_gravatar.context)
                .load(comment.authorProfileImageUrl)
                .placeholder(R.drawable.ic_user_circle_grey_24dp)
                .circleCrop()
                .into(review_gravatar)
        review_user_name.text = comment.authorName
        review_time.text = DateTimeUtils.timeSpanFromTimestamp(note.getConvertedTimestamp(), activity as Context)

        // Populate reviewed product info
        review_product_name.text = comment.postTitle
        note.getProductInfo()?.url?.let { url ->
            review_open_product.setOnClickListener { ActivityUtils.openUrlExternal(activity as Context, url) }
        }
        productUrl = note.getProductInfo()?.url

        // Set the rating if available, or hide
        note.getRating()?.let { rating ->
            review_rating_bar.rating = rating
            review_rating_bar.visibility = View.VISIBLE
        }

        // Set the review text
        review_description.text = comment.content

        // Initialize moderation buttons and set comment status
        configureModerationButtons(note)
        updateStatus(CommentStatus.fromString(comment.status))
    }

    private fun configureModerationButtons(note: NotificationModel) {
        // Configure the moderate button
        with (review_approve) {
            if (note.canModerate()) {
                visibility = View.VISIBLE
                setOnCheckedChangeListener(moderateListener)
            } else {
                visibility = View.GONE
            }
        }

        // Configure the spam button
        with (review_spam) {
            if (note.canMarkAsSpam()) {
                visibility = View.VISIBLE
                setOnClickListener { spamReview() }
            } else {
                visibility = View.GONE
            }
        }

        // Configure the trash button
        with (review_trash) {
            if (note.canTrash()) {
                visibility = View.VISIBLE
                setOnClickListener { trashReview() }
            } else {
                visibility = View.GONE
            }
        }
    }

    override fun updateStatus(status: CommentStatus) {
        review_approve.setOnCheckedChangeListener(null)
        when (status) {
            CommentStatus.APPROVED -> review_approve.isChecked = true
            CommentStatus.UNAPPROVED -> review_approve.isChecked = false
            else -> WooLog.w(NOTIFICATIONS, "Unable to process Notification with a status of $status")
        }
        review_approve.setOnCheckedChangeListener(moderateListener)
    }

    private fun trashReview() {
        showChangeCommentStatusSnackbar(CommentStatus.TRASH)
    }

    private fun spamReview() {
        showChangeCommentStatusSnackbar(CommentStatus.SPAM)
    }

    private fun approveReview() {
        showChangeCommentStatusSnackbar(CommentStatus.APPROVED)
    }

    private fun disapproveReview() {
        showChangeCommentStatusSnackbar(CommentStatus.UNAPPROVED)
    }

    private fun showChangeCommentStatusSnackbar(newStatus: CommentStatus) {
        if (networkStatus.isConnected()) {
            // Disable the moderation buttons
            disableModerationButtons()

            var changeCommentStatusCanceled = false

            // TODO Add tracks events

            // Listener for the UNDO button in the snackbar
            val actionListener = View.OnClickListener {
                // TODO tracks events

                // User canceled the action to change the order status
                changeCommentStatusCanceled = true

                enableModerationButtons()
                presenter.comment?.let { updateStatus(CommentStatus.fromString(it.status)) }
            }

            val callback = object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    if (!changeCommentStatusCanceled) {
                        enableModerationButtons()
                        presenter.comment?.let { comment ->
                            comment.status = newStatus.toString()
                            presenter.moderateComment(comment)
                        }
                        activity?.onBackPressed()
                    }
                }
            }

            changeCommentStatusSnackbar = uiMessageResolver
                    .getUndoSnack(
                            R.string.notifs_review_moderation_undo,
                            newStatus.toString(),
                            actionListener = actionListener
                    ).also {
                        it.addCallback(callback)
                        it.show()
                    }
        }
    }

    private fun disableModerationButtons() {
        review_approve.isEnabled = false
        review_spam.isEnabled = false
        review_trash.isEnabled = false
    }

    private fun enableModerationButtons() {
        review_approve.isEnabled = true
        review_spam.isEnabled = true
        review_trash.isEnabled = true
    }
}
