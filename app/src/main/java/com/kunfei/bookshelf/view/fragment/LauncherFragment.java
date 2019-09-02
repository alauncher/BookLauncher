package com.kunfei.bookshelf.view.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.kunfei.bookshelf.MApplication;
import com.kunfei.bookshelf.R;
import com.kunfei.bookshelf.base.MBaseFragment;
import com.kunfei.bookshelf.help.permission.Permissions;
import com.kunfei.bookshelf.help.permission.PermissionsCompat;
import com.kunfei.bookshelf.launcher.ImageLoadingTask;
import com.kunfei.bookshelf.launcher.LaunchableActivity;
import com.kunfei.bookshelf.launcher.LaunchableActivityPrefs;
import com.kunfei.bookshelf.launcher.LoadLaunchableActivityTask;
import com.kunfei.bookshelf.launcher.Trie;
import com.kunfei.bookshelf.launcher.threading.SimpleTaskConsumerManager;
import com.kunfei.bookshelf.launcher.util.ContentShare;
import com.kunfei.bookshelf.presenter.contract.BookListContract;
import com.kunfei.bookshelf.utils.FileUtils;
import com.kunfei.bookshelf.view.activity.SettingActivity;
import com.kunfei.bookshelf.widget.filepicker.picker.FilePicker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import kotlin.Unit;

import static org.apache.commons.lang3.StringUtils.stripAccents;

/**
 * Created by GKF on 2017/12/16.
 * 设置
 */
public class LauncherFragment extends MBaseFragment<BookListContract.Presenter> {

    private SettingActivity settingActivity;

    //
    private ArrayList<LaunchableActivity> mActivityInfos;
    private ArrayList<LaunchableActivity> mShareableActivityInfos;
    private Trie<LaunchableActivity> mTrie;

    private int mGridViewTopRowHeight;
    private int mGridViewBottomRowHeight;

    private StringBuilder mWordSinceLastSpaceBuilder;
    private StringBuilder mWordSinceLastCapitalBuilder;

    private ArrayAdapter<LaunchableActivity> mArrayAdapter;

    private int mColumnCount;

    private ImageLoadingTask.SharedData mImageTasksSharedData;

    private SimpleTaskConsumerManager mImageLoadingConsumersManager;

    private LaunchableActivityPrefs mLaunchableActivityPrefs;

    private int mIconSizePixels;

    private Drawable mDefaultAppIcon;

    private PackageManager mPm;

    private int mNumOfCores;

    private Context mContext;

    private Comparator<LaunchableActivity> mPinToTopComparator;
    private Comparator<LaunchableActivity> mRecentOrderComparator;
    private Comparator<LaunchableActivity> mAlphabeticalOrderComparator;
    private Comparator<LaunchableActivity> mUsageOrderComparator;

    private boolean mShouldOrderByRecents;
    private boolean mShouldOrderByUsages;

    private static final int sNavigationBarHeightMultiplier = 1;
    private static final int sGridViewTopRowExtraPaddingInDP = 56;
    private static final int sMarginFromNavigationBarInDp = 16;
    private static final int sGridItemHeightInDp = 96;
    private static final int sInitialArrayListSize = 300;

    private HashMap<String, List<LaunchableActivity>> mLaunchableActivityPackageNameHashMap;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Resources resources = getResources();
        mContext = getContext();

        mPm = getActivity().getPackageManager();

        mLaunchableActivityPackageNameHashMap = new HashMap<>();
        mShareableActivityInfos = new ArrayList<>(sInitialArrayListSize);
        mActivityInfos = new ArrayList<>(sInitialArrayListSize);
        mTrie = new Trie<>();
        mWordSinceLastSpaceBuilder = new StringBuilder(64);
        mWordSinceLastCapitalBuilder = new StringBuilder(64);

        mTrie = new Trie<>();

        mLaunchableActivityPrefs = new LaunchableActivityPrefs(mContext);

        mNumOfCores = Runtime.getRuntime().availableProcessors();

        mDefaultAppIcon = Resources.getSystem().getDrawable(
                android.R.mipmap.sym_def_app_icon);

        mIconSizePixels = resources.getDimensionPixelSize(R.dimen.app_icon_size);

        loadLaunchableApps();
    }

    @Override
    public int createLayoutId() {
        return R.layout.fragment_book_list;
    }

    @Override
    protected BookListContract.Presenter initInjector() {
        return null;
    }

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (Preference preference, Object value) -> {
        String stringValue = value.toString();

        if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);
            // Set the summary to reflect the new value.
            preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
        } else {
            // For all other preferences, set the summary to the value's
            preference.setSummary(stringValue);
        }
        return true;
    };

    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                preference.getContext().getSharedPreferences("CONFIG", Context.MODE_PRIVATE).getString(preference.getKey(), ""));
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void selectDownloadPath(Preference preference) {
        new PermissionsCompat.Builder(settingActivity)
                .addPermissions(Permissions.READ_EXTERNAL_STORAGE, Permissions.WRITE_EXTERNAL_STORAGE)
                .rationale(R.string.set_download_per)
                .onGranted((requestCode) -> {
                    FilePicker picker = new FilePicker(getActivity(), FilePicker.DIRECTORY);
                    picker.setBackgroundColor(getResources().getColor(R.color.background));
                    picker.setTopBackgroundColor(getResources().getColor(R.color.background));
                    picker.setRootPath(preference.getSummary().toString());
                    picker.setItemHeight(30);
                    picker.setOnFilePickListener(currentPath -> {
                        if (!currentPath.contains(FileUtils.getSdCardPath())) {
                            MApplication.getInstance().setDownloadPath(null);
                        } else {
                            MApplication.getInstance().setDownloadPath(currentPath);
                        }
                        preference.setSummary(MApplication.downloadPath);
                    });
                    picker.show();
                    picker.getCancelButton().setText(R.string.restore_default);
                    picker.getCancelButton().setOnClickListener(view -> {
                        picker.dismiss();
                        MApplication.getInstance().setDownloadPath(null);
                        preference.setSummary(MApplication.downloadPath);
                    });
                    return Unit.INSTANCE;
                })
                .request();
    }

    private void loadLaunchableApps() {

        List<ResolveInfo> infoList = ContentShare.getLaunchableResolveInfos(mPm);
        mArrayAdapter = new ActivityInfoArrayAdapter(getContext(),
                R.layout.app_grid_item, mActivityInfos);
        ArrayList<LaunchableActivity> launchablesFromResolve = new ArrayList<>(infoList.size());

        if (mNumOfCores <= 1) {
            for (ResolveInfo info : infoList) {
                final LaunchableActivity launchableActivity = new LaunchableActivity(
                        info.activityInfo, info.activityInfo.loadLabel(mPm).toString(), false);
                launchablesFromResolve.add(launchableActivity);
            }
        } else {
            SimpleTaskConsumerManager simpleTaskConsumerManager =
                    new SimpleTaskConsumerManager(mNumOfCores, infoList.size());

            LoadLaunchableActivityTask.SharedData sharedAppLoadData =
                    new LoadLaunchableActivityTask.SharedData(mPm, launchablesFromResolve);
            for (ResolveInfo info : infoList) {
                LoadLaunchableActivityTask loadLaunchableActivityTask =
                        new LoadLaunchableActivityTask(info, sharedAppLoadData);
                simpleTaskConsumerManager.addTask(loadLaunchableActivityTask);
            }

            //Log.d("MultithreadStartup","waiting for completion of all tasks");
            simpleTaskConsumerManager.destroyAllConsumers(true, true);
            //Log.d("MultithreadStartup", "all tasks ok");
        }
        updateApps(launchablesFromResolve, true);
    }

    private void updateApps(final List<LaunchableActivity> updatedActivityInfos, boolean addToTrie) {

        for (LaunchableActivity launchableActivity : updatedActivityInfos) {
            final String packageName = launchableActivity.getComponent().getPackageName();
            mLaunchableActivityPackageNameHashMap.remove(packageName);
        }

        final String thisClassCanonicalName = this.getClass().getCanonicalName();
        for (LaunchableActivity launchableActivity : updatedActivityInfos) {
            final String className = launchableActivity.getComponent().getClassName();
            //don't show this activity in the launcher
            if (className.equals(thisClassCanonicalName)) {
                continue;
            }

            if (addToTrie) {
                final String activityLabel = launchableActivity.getActivityLabel();
                final List<String> subwords = getAllSubwords(stripAccents(activityLabel));
                for (String subword : subwords) {
                    mTrie.put(subword, launchableActivity);
                }
            }
            final String packageName = launchableActivity.getComponent().getPackageName();

            List<LaunchableActivity> launchableActivitiesToUpdate =
                    mLaunchableActivityPackageNameHashMap.remove(packageName);
            if (launchableActivitiesToUpdate == null) {
                launchableActivitiesToUpdate = new LinkedList<>();
            }
            launchableActivitiesToUpdate.add(launchableActivity);
            mLaunchableActivityPackageNameHashMap.put(packageName, launchableActivitiesToUpdate);
        }
        Log.d("SearchActivity", "updated activities: " + updatedActivityInfos.size());
        mLaunchableActivityPrefs.setAllPreferences(updatedActivityInfos);
        // updateVisibleApps();
    }

    /*
    private void updateVisibleApps() {
        final HashSet<LaunchableActivity> infoList =
                mTrie.getAllStartingWith(stripAccents(mSearchEditText.getText()
                        .toString().toLowerCase().trim()));
        mActivityInfos.clear();
        mActivityInfos.addAll(infoList);
        mActivityInfos.addAll(mShareableActivityInfos);
        sortApps();
        Log.d("DEBUG_SEARCH", mActivityInfos.size() + "");

        mArrayAdapter.notifyDataSetChanged();
    }
    */

    private void sortApps() {
        Collections.sort(mActivityInfos, mAlphabeticalOrderComparator);

        if (mShouldOrderByRecents) {
            Collections.sort(mActivityInfos, mRecentOrderComparator);
        } else if (mShouldOrderByUsages) {
            Collections.sort(mActivityInfos, mUsageOrderComparator);
        }

        Collections.sort(mActivityInfos, mPinToTopComparator);
    }

    private List<String> getAllSubwords(String line) {
        final ArrayList<String> subwords = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            final char character = line.charAt(i);

            if (Character.isUpperCase(character) || Character.isDigit(character)) {
                if (mWordSinceLastCapitalBuilder.length() > 1) {
                    subwords.add(mWordSinceLastCapitalBuilder.toString().toLowerCase());
                }
                mWordSinceLastCapitalBuilder.setLength(0);
            }
            if (Character.isSpaceChar(character)) {
                subwords.add(mWordSinceLastSpaceBuilder.toString().toLowerCase());
                if (mWordSinceLastCapitalBuilder.length() > 1 &&
                        mWordSinceLastCapitalBuilder.length() !=
                                mWordSinceLastSpaceBuilder.length()) {
                    subwords.add(mWordSinceLastCapitalBuilder.toString().toLowerCase());
                }
                mWordSinceLastCapitalBuilder.setLength(0);
                mWordSinceLastSpaceBuilder.setLength(0);
            } else {
                mWordSinceLastCapitalBuilder.append(character);
                mWordSinceLastSpaceBuilder.append(character);
            }
        }
        if (mWordSinceLastSpaceBuilder.length() > 0) {
            subwords.add(mWordSinceLastSpaceBuilder.toString().toLowerCase());
        }
        if (mWordSinceLastCapitalBuilder.length() > 1
                && mWordSinceLastCapitalBuilder.length() != mWordSinceLastSpaceBuilder.length()) {
            subwords.add(mWordSinceLastCapitalBuilder.toString().toLowerCase());
        }
        mWordSinceLastSpaceBuilder.setLength(0);
        mWordSinceLastCapitalBuilder.setLength(0);
        return subwords;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean mDisableIcons;
    private boolean mAutoKeyboard;
    private boolean mIsCacheClear;

    class ActivityInfoArrayAdapter extends ArrayAdapter<LaunchableActivity> {
        final LayoutInflater inflater;

        public ActivityInfoArrayAdapter(final Context context, final int resource,
                                        final List<LaunchableActivity> activityInfos) {

            super(context, resource, activityInfos);
            inflater = getLayoutInflater();
        }

        @Override
        public int getCount() {
            return super.getCount() + mColumnCount;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            final View view =
                    convertView != null ?
                            convertView : inflater.inflate(R.layout.app_grid_item, parent, false);
            final AbsListView.LayoutParams params =
                    (AbsListView.LayoutParams) view.getLayoutParams();

            if (position < mColumnCount) {
                params.height = mGridViewTopRowHeight;
                view.setLayoutParams(params);
                view.setVisibility(View.INVISIBLE);
            } else {
                if (position == (getCount() - 1)) {
                    params.height = mGridViewBottomRowHeight;
                } else {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                view.setLayoutParams(params);
                view.setVisibility(View.VISIBLE);
                final LaunchableActivity launchableActivity = getItem(position - mColumnCount);
                final CharSequence label = launchableActivity.getActivityLabel();
                final TextView appLabelView = (TextView) view.findViewById(R.id.appLabel);
                final ImageView appIconView = (ImageView) view.findViewById(R.id.appIcon);
                final View appShareIndicator = view.findViewById(R.id.appShareIndicator);
                final View appPinToTop = view.findViewById(R.id.appPinToTop);

                appLabelView.setText(label);

                appIconView.setTag(launchableActivity);
                if (!launchableActivity.isIconLoaded()) {
                    appIconView.setImageDrawable(mDefaultAppIcon);
                    if (!mDisableIcons)
                        mImageLoadingConsumersManager.addTask(
                                new ImageLoadingTask(appIconView, launchableActivity,
                                        mImageTasksSharedData));
                } else {
                    appIconView.setImageDrawable(
                            launchableActivity.getActivityIcon(mPm, mContext, mIconSizePixels));
                }
                appShareIndicator.setVisibility(
                        launchableActivity.isShareable() ? View.VISIBLE : View.GONE);
                appPinToTop.setVisibility(
                        launchableActivity.getPriority() > 0 ? View.VISIBLE : View.GONE);

            }
            return view;
        }
    }
}
