package com.android.contacts.fragments

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import com.android.contacts.R
import com.android.contacts.activities.EditContactActivity
import com.android.contacts.activities.InsertOrEditContactActivity
import com.android.contacts.activities.MainActivity
import com.android.contacts.activities.SimpleActivity
import com.android.contacts.adapters.ContactsAdapter
import com.android.contacts.databinding.FragmentContactsBinding
import com.android.contacts.databinding.FragmentLettersLayoutBinding
import com.android.contacts.extensions.config
import com.android.contacts.helpers.LOCATION_CONTACTS_TAB
import com.android.contacts.interfaces.RefreshContactsListener

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LetterLayout>(context, attributeSet) {

    private lateinit var binding: FragmentContactsBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentContactsBinding.bind(this)
        innerBinding = LetterLayout(FragmentLettersLayoutBinding.bind(binding.root))
    }

    override fun fabClicked() {
        activity?.hideKeyboard()
        Intent(context, EditContactActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    override fun placeholderClicked() {
        if (activity is MainActivity) {
            (activity as MainActivity).showFilterDialog()
        } else if (activity is InsertOrEditContactActivity) {
            (activity as InsertOrEditContactActivity).showFilterDialog()
        }
    }

    fun setupContactsAdapter(contacts: List<Contact>) {
        setupViewVisibility(contacts.isNotEmpty())
        val currAdapter = innerBinding.fragmentList.adapter
        val showFastscroller = contacts.size > 10
        innerBinding.letterFastscroller.beVisibleIf(showFastscroller)
//        innerBinding.fragmentList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
//            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
//                super.onScrollStateChanged(recyclerView, newState)
//                activity?.hideKeyboard()
//            }
//        })
        innerBinding.fragmentList.setOnTouchListener { _, _ ->
            activity?.hideKeyboard()
            false
        }

        // Optimize RecyclerView for large lists
        innerBinding.fragmentList.setHasFixedSize(true)
        // Disable animations for large lists to improve scrolling performance
        if (contacts.size > 100) {
            innerBinding.fragmentList.itemAnimator = null
        }
        innerBinding.fragmentList.setItemViewCacheSize(30) // Increase cache size for smoother scrolling
        innerBinding.fragmentList.recycledViewPool.setMaxRecycledViews(0, 30) // Increase pool size

        if (showFastscroller) {
            // Check screen size on main thread before background processing
            val isHighScreen = isHighScreenSize()
            
            // Move expensive calculation to background thread
            ensureBackgroundThread {
                try {
                    //Decrease the font size based on the number of letters in the letter scroller
                    val allNotEmpty = contacts.filter { it.getNameToDisplay().isNotEmpty() }
                    val all = allNotEmpty.map { it.getNameToDisplay().substring(0, 1) }
                    val unique: Set<String> = HashSet(all)
                    val sizeUnique = unique.size
                    val textAppearanceRes = if (isHighScreen) {
                        when {
                            sizeUnique > 48 -> R.style.LetterFastscrollerStyleTooTiny
                            sizeUnique > 37 -> R.style.LetterFastscrollerStyleTiny
                            else -> R.style.LetterFastscrollerStyleSmall
                        }
                    } else {
                        when {
                            sizeUnique > 36 -> R.style.LetterFastscrollerStyleTooTiny
                            sizeUnique > 30 -> R.style.LetterFastscrollerStyleTiny
                            else -> R.style.LetterFastscrollerStyleSmall
                        }
                    }
                    
                    activity?.runOnUiThread {
                        innerBinding.letterFastscroller.textAppearanceRes = textAppearanceRes
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        activity?.copyToClipboard(e.toString())
                    }
                }
            }
        }

        if (currAdapter == null || forceListRedraw) {
            forceListRedraw = false
            val location = LOCATION_CONTACTS_TAB

            ContactsAdapter(
                activity = activity as SimpleActivity,
                contactItems = contacts.toMutableList(),
                refreshListener = activity as RefreshContactsListener,
                location = location,
                removeListener = null,
                recyclerView = innerBinding.fragmentList,
                enableDrag = false,
            ) {
                (activity as RefreshContactsListener).contactClicked(it as Contact)
            }.apply {
                innerBinding.fragmentList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                innerBinding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).apply {
                startNameWithSurname = context.config.startNameWithSurname
                showNicknameInsteadNames = context.config.showNicknameInsteadNames
                showPhoneNumbers = context.config.showPhoneNumbers
                showContactThumbnails = context.config.showContactThumbnails
                updateItems(contacts)
            }
        }
    }

    private fun isHighScreenSize(): Boolean {
        return when (resources.configuration.screenLayout
            and Configuration.SCREENLAYOUT_LONG_MASK) {
            Configuration.SCREENLAYOUT_LONG_NO -> false
            else -> true
        }
    }

    override fun myRecyclerView() = innerBinding.fragmentList
}
