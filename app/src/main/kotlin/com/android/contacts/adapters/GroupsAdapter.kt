package com.android.contacts.adapters

import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.behaviorule.arturdumchev.library.pixels
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.TAB_GROUPS
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Group
import com.goodwy.commons.views.MyRecyclerView
import com.android.contacts.R
import com.android.contacts.activities.SimpleActivity
import com.android.contacts.databinding.ItemGroupBinding
import com.android.contacts.dialogs.RenameGroupDialog
import com.android.contacts.extensions.config
import com.android.contacts.interfaces.RefreshContactsListener

class GroupsAdapter(
    activity: SimpleActivity, var groups: ArrayList<Group>, val refreshListener: RefreshContactsListener?, recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textToHighlight = ""
    var showContactThumbnails = activity.config.showContactThumbnails
    var fontSize = activity.getTextSize()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_groups

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_rename -> renameGroup()
            R.id.cab_select_all -> selectAll()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = groups.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = groups.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = groups.indexOfFirst { it.id!!.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemGroupBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        holder.bindView(group, true, false) { itemView, layoutPosition ->
            setupView(itemView, group)
        }
        // Set custom long click listener to show popup menu
        holder.itemView.setOnLongClickListener {
            showPopupMenu(holder.itemView, position, group)
            true
        }
        bindViewHolder(holder)
    }

    private fun showPopupMenu(anchor: View, position: Int, group: Group) {
        val popupMenu = PopupMenu(activity, anchor)
        activity.menuInflater.inflate(R.menu.cab_groups, popupMenu.menu)
        
        // Prepare menu (similar to prepareActionMode)
        val menu = popupMenu.menu
        menu.findItem(R.id.cab_rename).isVisible = true // Always show rename for single item
        
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            val groupId = group.id!!.toInt()
            
            // Handle the menu item action
            when (item.itemId) {
                R.id.cab_rename -> {
                    // Rename directly without selecting
                    renameGroup(group)
                    true
                }
                R.id.cab_select_all -> {
                    selectAll()
                    true
                }
                R.id.cab_delete -> {
                    // Select the item and delete
                    if (!selectedKeys.contains(groupId)) {
                        toggleItemSelection(true, position, true)
                    }
                    askConfirmDelete()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }

    override fun getItemCount() = groups.size

    private fun getItemWithKey(key: Int): Group? = groups.firstOrNull { it.id!!.toInt() == key }

    fun updateItems(newItems: ArrayList<Group>, highlightText: String = "") {
        if (newItems.hashCode() != groups.hashCode()) {
            groups = newItems
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun renameGroup() {
        val group = getItemWithKey(selectedKeys.first()) ?: return
        RenameGroupDialog(activity, group) {
            finishActMode()
            refreshListener?.refreshContacts(TAB_GROUPS)
        }
    }

    private fun renameGroup(group: Group) {
        RenameGroupDialog(activity, group) {
            refreshListener?.refreshContacts(TAB_GROUPS)
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().first()
        val items = if (itemsCnt == 1) {
            "\"${firstItem.title}\""
        } else {
            resources.getQuantityString(R.plurals.delete_groups, itemsCnt, itemsCnt)
        }

        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteGroups()
            }
        }
    }

    private fun deleteGroups() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val groupsToRemove = groups.filter { selectedKeys.contains(it.id!!.toInt()) } as ArrayList<Group>
        val positions = getSelectedItemPositions()
        groupsToRemove.forEach {
            if (it.isPrivateSecretGroup()) {
                activity.groupsDB.deleteGroupId(it.id!!)
            } else {
                ContactsHelper(activity).deleteGroup(it.id!!)
            }
        }
        groups.removeAll(groupsToRemove)

        activity.runOnUiThread {
            if (groups.isEmpty()) {
                refreshListener?.refreshContacts(TAB_GROUPS)
                finishActMode()
            } else {
                removeSelectedItems(positions)
            }
        }
    }

    private fun getSelectedItems() = groups.filter { selectedKeys.contains(it.id?.toInt()) } as ArrayList<Group>

    private fun getLastItem() = groups.last()

    private fun setupView(view: View, group: Group) {
        ItemGroupBinding.bind(view).apply {
            groupFrame.isSelected = selectedKeys.contains(group.id!!.toInt())
            val titleWithCnt = "${group.title} (${group.contactsCount})"
            val groupTitle = if (textToHighlight.isEmpty()) {
                titleWithCnt
            } else {
                titleWithCnt.highlightTextPart(textToHighlight, properPrimaryColor)
            }

            groupName.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                text = groupTitle
            }

            groupTmb.beVisibleIf(showContactThumbnails)
            if (showContactThumbnails) {
                groupTmb.setImageDrawable(SimpleContactsHelper(activity).getColoredGroupIcon(group.title))
                val size = (root.context.pixels(com.goodwy.commons.R.dimen.normal_icon_size) * contactThumbnailsSize).toInt()
                groupTmb.setHeightAndWidth(size)
            }

            divider.setBackgroundColor(textColor)
            if (getLastItem() == group || !root.context.config.useDividers) divider.visibility = View.INVISIBLE else divider.visibility = View.VISIBLE
        }
    }

    override fun onChange(position: Int) = groups.getOrNull(position)?.getBubbleText() ?: ""
}
