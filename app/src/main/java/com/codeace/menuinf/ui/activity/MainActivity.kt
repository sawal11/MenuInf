package com.codeace.menuinf.ui.activity

import android.content.Intent
import android.os.Bundle
import android.transition.Explode
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.codeace.menuinf.R
import com.codeace.menuinf.adapters.FoodAdapter
import com.codeace.menuinf.entity.FoodData
import com.codeace.menuinf.ui.fragments.FoodDialog
import com.codeace.menuinf.viewModel.FoodViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.innovattic.rangeseekbar.RangeSeekBar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_layout.*
import kotlinx.android.synthetic.main.nav_layout.*
import kotlinx.android.synthetic.main.toolbar.*
import java.io.Serializable

class MainActivity : AppCompatActivity(), FoodDialog.FoodDialogListener,
    RangeSeekBar.SeekBarChangeListener, FoodAdapter.ItemListeners {
    private lateinit var mAuth: FirebaseAuth
    private var foodVM: FoodViewModel? = null
    private var foodAdapter = FoodAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.enterTransition = Explode()
        window.exitTransition = Explode()

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        statusBar.minimumHeight = resources.getDimensionPixelSize(
            resources.getIdentifier(
                "status_bar_height",
                "dimen",
                "android"
            )
        )

        Glide.with(this).load("").centerCrop()
            .placeholder(R.drawable.background).into(foodImageNav)
        mAuth = FirebaseAuth.getInstance()

        simpleSwipeRefreshLayout.setOnRefreshListener {
            Log.d(com.codeace.menuinf.helpers.TAG, "Refreshed")
        }

        simpleSwipeRefreshLayout.isRefreshing = true

        itemRecyclerView.layoutManager = LinearLayoutManager(this)
        foodAdapter.setItemListeners = this
        itemRecyclerView.adapter = foodAdapter
        itemRecyclerView.setHasFixedSize(true)

        val toggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        rangeSeekBar.seekBarChangeListener = this
        categoryList.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        categoryList.divider = null
        categoryList.setOnItemClickListener { _, view, position, _ ->
            if (foodVM?.selectedCategories!!.contains(position)) foodVM?.selectedCategories?.remove(
                position
            )
            else foodVM?.selectedCategories!!.add(position)

            view.background = resources.getDrawable(
                if (foodVM?.selectedCategories!!.contains(position)) R.drawable.selected else R.drawable.dselected,
                null
            )

            filterItems()
            drawer_layout.closeDrawer(GravityCompat.START)
        }

        logOut.setOnClickListener {
            drawer_layout.closeDrawer(GravityCompat.START)
            mAuth.signOut()
            drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
                override fun onDrawerOpened(drawerView: View) {}
                override fun onDrawerStateChanged(newState: Int) {}
                override fun onDrawerClosed(drawerView: View) {
                    getUser()
                }
            })
        }

    }

    override fun onStart() {
        super.onStart()
        Log.d(com.codeace.menuinf.helpers.TAG, "GetUser")
        getUser()
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                filterItems()
                return true
            }
        })
        val searchView = searchItem.actionView as SearchView
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.action_search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                searchItems(newText)
                return true
            }

            override fun onQueryTextSubmit(newText: String): Boolean {
                searchItems(newText)
                return true
            }
        })
        return true
    }

    override fun onDialogPositiveClick(dialog: DialogFragment, foodData: FoodData) {
        if (dialog.tag.equals(getString(R.string.fda))) {
            foodVM?.insert(foodData, mAuth.currentUser?.uid!!)
        } else {
            foodVM?.update(foodData, mAuth.currentUser?.uid!!)
            foodAdapter.notifyDataSetChanged()
        }
    }

    override fun onStartedSeeking() {}

    override fun onStoppedSeeking() {}

    override fun onValueChanged(minThumbValue: Int, maxThumbValue: Int) {
        minText.text = "$minThumbValue"
        maxText.text = "$maxThumbValue"
        filterItems()
    }

    override fun onFoodItemClicked(pos: Int, pair: Pair<View, String>) {
        val intent = Intent(this, FoodItemDetails::class.java)
        val option = ActivityOptionsCompat.makeSceneTransitionAnimation(this, pair)
        intent.putExtra("extra_object", foodAdapter.getDataAt(pos) as Serializable)
        startActivity(intent, option.toBundle())
    }

    override fun onItemDelete(pos: Int) {
        foodVM?.delete(
            foodAdapter.getDataAt(pos).food_name,
            foodAdapter.getDataAt(pos).id!!,
            mAuth.currentUser?.uid!!
        )
    }

    override fun onItemUpdate(pos: Int) {
        val dialog = FoodDialog()
        dialog.dataToUpdate(foodAdapter.getDataAt(pos))
        dialog.show(supportFragmentManager, foodAdapter.getDataAt(pos).id.toString())
    }

    private fun filterItems() {
        foodVM?.setLiveData(
            foodVM?.filterByData(
                rangeSeekBar.getMinThumbValue().toDouble(),
                rangeSeekBar.getMaxThumbValue().toDouble()
            )!!
        )
    }

    private fun searchItems(newText: String) {
        if (newText.isNotEmpty()) {
            foodVM?.setLiveData(foodVM!!.searchItem(newText))
        } else {
            foodVM?.setDefault()
        }
    }

    private fun getUser() {
        foodAdapter.visibility = false
        floatingActionButton.isVisible = false
        avatar.isVisible = false
        Glide.with(this).load("").centerCrop()
            .placeholder(R.drawable.imageplaceholder).into(avatar)
        mail.text = ""
        position.text = ""
        Log.d(com.codeace.menuinf.helpers.TAG, "UserCleared")
        if (mAuth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            Toast.makeText(this, "Please Login/SignUp to use the app", Toast.LENGTH_LONG).show()
            finish()
            Log.d(com.codeace.menuinf.helpers.TAG, "LoginRequest")
        } else {
            Log.d(com.codeace.menuinf.helpers.TAG, "UpdateUI")
            updateUi(mAuth.currentUser!!)
        }
    }

    private fun initViewModel() {
        foodVM = ViewModelProviders.of(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                return FoodViewModel(application) as T
            }
        }).get(FoodViewModel::class.java)
        Log.d(com.codeace.menuinf.helpers.TAG, "View Model Initialized")

        foodVM?.getLiveData()!!.observe(this, Observer { foodList ->
            Log.d(com.codeace.menuinf.helpers.TAG, "New List : ".plus(foodList.toString()))
            foodAdapter.submitList(foodList)
            getCategories(foodList.isNotEmpty())
        })
    }

    private fun getCategories(isListEmpty: Boolean) {
        Log.d(com.codeace.menuinf.helpers.TAG, "Disabling Loading Animation")
        if (isListEmpty) {
            simpleSwipeRefreshLayout.isRefreshing = false
            Log.d(com.codeace.menuinf.helpers.TAG, "Loading Animation Disabled")
        } else {
            simpleSwipeRefreshLayout.isRefreshing = false
            Log.d(com.codeace.menuinf.helpers.TAG, "List Empty")
        }
        Log.d(com.codeace.menuinf.helpers.TAG, "Checking dataChange")
        if (foodVM?.getIsDataChanged()!!) {
            Log.d(com.codeace.menuinf.helpers.TAG, "Data Changed Detected")
            foodVM?.getCategories()
            Log.d(
                com.codeace.menuinf.helpers.TAG,
                "New Categories : ".plus(foodVM?.categoryListItems.toString())
            )
            categoryList.adapter = ArrayAdapter(
                this@MainActivity,
                R.layout.nav_header, foodVM?.categoryListItems!!.toList()
            )
            rangeSeekBar.max = foodVM?.maxPrice!!.toInt()

            minText.text = rangeSeekBar.getMinThumbValue().toString()
            maxText.text = rangeSeekBar.getMaxThumbValue().toString()
            foodVM?.setIsDataChanged(false)
            Log.d(com.codeace.menuinf.helpers.TAG, "Initialized SeekBar and Categories List")
        }
    }

    private fun updateUi(currentUser: FirebaseUser) {
        if (currentUser.uid == resources.getString(R.string.admin)) {
            foodAdapter.visibility = true
            floatingActionButton.isVisible = true
            floatingActionButton.setOnClickListener {
                val dialog = FoodDialog()
                dialog.show(supportFragmentManager, "FoodDialogAdd")
            }

            floatingActionButton.setOnLongClickListener {
                foodVM?.deleteAll()
                true
            }
            Log.d(com.codeace.menuinf.helpers.TAG, "Buttons Enabled")
        }
        avatar.isVisible = true
        Glide.with(this).load(currentUser.photoUrl.toString()).centerCrop()
            .placeholder(R.drawable.ic_user).into(avatar)
        mail.text = currentUser.displayName
        position.text = currentUser.email
        Log.d(com.codeace.menuinf.helpers.TAG, "UserAdded")

        Log.d(com.codeace.menuinf.helpers.TAG, "View Model Initialize")
        initViewModel()
    }
}
