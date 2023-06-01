/*
 * Copyright 2016 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cyface.app.r4r.auth

import android.app.Activity
import android.app.LoaderManager
import android.content.AsyncTaskLoader
import android.content.Context
import android.content.Loader
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import de.cyface.app.r4r.R
import net.openid.appauth.browser.BrowserDescriptor
import net.openid.appauth.browser.BrowserSelector

/**
 * Loads the list of browsers on the device for selection in a list or spinner.
 */
class BrowserSelectionAdapter(activity: Activity) : BaseAdapter() {
    private val mContext: Context
    private var mBrowsers: ArrayList<BrowserInfo?>? = null

    /**
     * Creates the adapter, using the loader manager from the specified activity.
     */
    init {
        mContext = activity
        initializeItemList()
        activity.loaderManager.initLoader(
            LOADER_ID,
            null,
            BrowserLoaderCallbacks()
        )
    }

    class BrowserInfo(
        val mDescriptor: BrowserDescriptor,
        val mLabel: CharSequence,
        val mIcon: Drawable?
    )

    override fun getCount(): Int {
        return mBrowsers!!.size
    }

    override fun getItem(position: Int): BrowserInfo? {
        return mBrowsers!![position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var convertView: View? = view
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext)
                .inflate(R.layout.browser_selector_layout, parent, false)
        }
        val info = mBrowsers!![position]
        val labelView = convertView!!.findViewById<View>(R.id.browser_label) as TextView
        val iconView = convertView.findViewById<View>(R.id.browser_icon) as ImageView
        if (info == null) {
            labelView.text = "browser_appauth_default_label"
            iconView.setImageResource(R.drawable.ic_launcher_foreground)
        } else {
            var label = info.mLabel
            if (info.mDescriptor.useCustomTab) {
                label = String.format(mContext.getString(R.string.custom_tab_label), label)
            }
            labelView.text = label
            iconView.setImageDrawable(info.mIcon)
        }
        return convertView
    }

    private fun initializeItemList() {
        mBrowsers = ArrayList()
        mBrowsers!!.add(null)
    }

    private inner class BrowserLoaderCallbacks : LoaderManager.LoaderCallbacks<List<BrowserInfo>> {
        override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<BrowserInfo>> {
            return BrowserLoader(mContext)
        }

        override fun onLoadFinished(loader: Loader<List<BrowserInfo>>, data: List<BrowserInfo>) {
            initializeItemList()
            mBrowsers!!.addAll(data)
            notifyDataSetChanged()
        }

        override fun onLoaderReset(loader: Loader<List<BrowserInfo>>) {
            initializeItemList()
            notifyDataSetChanged()
        }
    }

    private class BrowserLoader(context: Context?) :
        AsyncTaskLoader<List<BrowserInfo>>(context) {
        private var mResult: List<BrowserInfo>? = null
        override fun loadInBackground(): List<BrowserInfo> {
            val descriptors = BrowserSelector.getAllBrowsers(context)
            val infoList = ArrayList<BrowserInfo>(descriptors.size)
            val pm = context.packageManager
            for (descriptor in descriptors) {
                try {
                    val info = pm.getApplicationInfo(descriptor.packageName, 0)
                    val label = pm.getApplicationLabel(info)
                    val icon = pm.getApplicationIcon(descriptor.packageName)
                    infoList.add(BrowserInfo(descriptor, label, icon))
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                    infoList.add(BrowserInfo(descriptor, descriptor.packageName, null))
                }
            }
            return infoList
        }

        override fun deliverResult(data: List<BrowserInfo>) {
            if (isReset) {
                mResult = null
                return
            }
            mResult = data
            super.deliverResult(mResult)
        }

        override fun onStartLoading() {
            if (mResult != null) {
                deliverResult(mResult!!)
            }
            forceLoad()
        }

        override fun onReset() {
            mResult = null
        }

        override fun onCanceled(data: List<BrowserInfo>) {
            mResult = null
            super.onCanceled(data)
        }
    }

    companion object {
        private const val LOADER_ID = 101
    }
}